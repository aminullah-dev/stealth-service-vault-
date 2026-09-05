import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Load signing credentials from keystore.properties (never committed to git)
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

// Google Maps API key. Lives in local.properties (gitignored) and is injected
// into the manifest as a placeholder, so the key never appears in source or in
// git — only in the built APK, where it is protected by the package + SHA-1
// restriction set on the key in Google Cloud Console.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val mapsApiKey: String = (localProps.getProperty("MAPS_API_KEY") ?: "").trim()

// The official Google Play upload key. The release build is verified against this
// fingerprint (see the verifyReleaseSigningKey task at the bottom of this file) so a
// stray or wrong keystore can never produce a bundle that Play would reject — we
// once shipped a build signed with the wrong key and only found out at upload time.
// A certificate fingerprint is public info (it's in every published bundle), so it's
// safe to commit. If you ever legitimately rotate the upload key, update this value.
val expectedReleaseSha1 = "A0:04:BE:C3:6A:A0:D8:BF:A6:C8:8B:7F:DB:09:36:E5:1C:68:A6:F5"

// Reads the SHA-1 fingerprint of the signing certificate for [alias] in [file],
// or null if the keystore can't be opened (missing file / wrong password / alias).
// Tries JKS then PKCS12 so it works regardless of how the keystore was created.
fun releaseCertSha1(file: java.io.File, storePass: String, alias: String): String? {
    if (!file.exists() || storePass.isBlank()) return null
    for (type in listOf("JKS", "PKCS12")) {
        val fp = runCatching {
            val ks = KeyStore.getInstance(type)
            file.inputStream().use { ks.load(it, storePass.toCharArray()) }
            val cert = ks.getCertificate(alias) as? X509Certificate ?: return@runCatching null
            MessageDigest.getInstance("SHA-1").digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }
        }.getOrNull()
        if (fp != null) return fp
    }
    return null
}

android {
    namespace = "com.safebeauty.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.security.stealthapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "2.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        // Consumed by the com.google.android.geo.API_KEY meta-data in the
        // manifest. Empty when local.properties has no key: the app still builds
        // and runs, the map simply renders blank tiles.
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
    }

    // ── Environments ──────────────────────────────────────────────────────────
    //
    // prod talks to the `safebeauty` Firebase project and is what ships to Play.
    // demo talks to `safebeauty-staging` and is what the public demo page hands
    // out. They are different Firebase projects, so the demo cannot read, write
    // or even authenticate against real customer data — which matters more here
    // than in most products, because production holds photographs of women's
    // identity documents.
    //
    // The separation is the applicationId, not a flag: `.demo` makes it a
    // different app to Android, so it installs alongside a real installation
    // instead of replacing it, and nothing in one can reach the other's storage.
    // Each flavour picks up its own google-services.json — demo's lives in
    // src/demo/, prod falls through to app/google-services.json.
    flavorDimensions += "environment"
    productFlavors {
        create("prod") {
            dimension = "environment"
        }
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix   = "-demo"
        }
    }

    signingConfigs {
        create("release") {
            // Resolve the keystore path relative to the repo root so a value like
            // "app/safebeauty-release.jks" doesn't double up to "app/app/...".
            storeFile     = rootProject.file(keystoreProps["storeFile"] ?: "app/safebeauty-release.jks")
            storePassword = keystoreProps["storePassword"]      as String? ?: ""
            keyAlias      = keystoreProps["keyAlias"]           as String? ?: "safebeauty"
            keyPassword   = keystoreProps["keyPassword"]        as String? ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Generates BuildConfig.DEBUG, used to disable crash collection in debug builds.
        buildConfig = true
    }

    composeOptions {
        // Matched to Kotlin 1.9.24 — see the Compose-to-Kotlin compatibility map.
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    // Reads EXIF orientation so portfolio photos aren't stored sideways
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Biometric fast-unlock (BiometricPrompt + CryptoObject). Pulls in
    // androidx.fragment, which MainActivity needs as its base class.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Compose BOM — aligns all Compose library versions
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    // ProcessLifecycleOwner lives in lifecycle-process (separate from lifecycle-runtime-ktx)
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher — physical disk encryption for Room. The `sqlcipher-android`
    // artifact (4.6.0+) ships 16 KB-page-aligned native libs, required by Google
    // Play for apps targeting SDK 35+. Same `net.sqlcipher.*` API as the old
    // `android-database-sqlcipher`, so no code change beyond the coordinate.
    implementation("net.zetetic:sqlcipher-android:4.6.1")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager + Hilt integration
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Encrypted SharedPreferences (stores the DB passphrase)
    implementation("androidx.security:security-crypto:1.0.0")

    // Firebase BOM — aligns all Firebase library versions.
    // 33.x line ships -ktx artifacts; 34.x+ drops them and requires sdk 35.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")

    // Google Maps in Compose — shows verified salons as pins. Map loads through
    // the Maps SDK for Android are not billed, and the app never calls the
    // metered APIs (Directions/Places): tapping "directions" hands off to
    // whatever maps app the user already has via a geo: URI.
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // Coil — URL-based image loading in Compose (replaces in-memory Base64 bitmaps)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines — play-services provides the Task.await() extension used by
    // FirebaseAuthManager / FirestoreRepository (NOT transitive; must be explicit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// ── Signing-key guardrail ───────────────────────────────────────────────────
// Fail the release build immediately if it isn't signed with the official upload
// key, instead of discovering it only when Google Play rejects the upload. Skips
// cleanly when no release keystore is present (debug builds / CI without secrets).
tasks.register("verifyReleaseSigningKey") {
    doLast {
        val f     = rootProject.file(keystoreProps["storeFile"] as String? ?: "app/safebeauty-release.jks")
        val pass  = keystoreProps["storePassword"] as String? ?: ""
        val alias = keystoreProps["keyAlias"]      as String? ?: "safebeauty"
        if (!f.exists() || pass.isBlank()) {
            logger.warn("verifyReleaseSigningKey: no release keystore present — skipping fingerprint check.")
            return@doLast
        }
        val actual = releaseCertSha1(f, pass, alias) ?: throw GradleException(
            "verifyReleaseSigningKey: couldn't read certificate for alias '$alias' in ${f.path} " +
            "(wrong alias or store password?)."
        )
        if (!actual.equals(expectedReleaseSha1, ignoreCase = true)) {
            throw GradleException(
                "\n❌ Wrong signing key — Google Play will reject this bundle.\n" +
                "     expected SHA1: $expectedReleaseSha1\n" +
                "     actual   SHA1: $actual\n" +
                "     keystore:      ${f.path} (alias '$alias')\n" +
                "  Restore the official upload keystore before building the release.\n"
            )
        }
        logger.lifecycle("✅ Release signing key verified ($actual)")
    }
}

// Matched by shape, not by exact name.
//
// Adding a flavour dimension splits every release task in two —
// assembleProdRelease and assembleDemoRelease — while leaving `assembleRelease`
// behind as an aggregate. So an equality check on the old names still matches
// something, and still looks like it is working, while the tasks anyone
// actually runs go unguarded. That would have quietly switched off the
// fingerprint check that exists because a build once went out signed with the
// wrong key. A guard that stops running is worse than no guard: it still looks
// like one. (Verified with `gradlew :app:tasks --all`, not assumed.)
tasks.matching {
    (it.name.startsWith("assemble") || it.name.startsWith("bundle")) && it.name.endsWith("Release")
}.configureEach {
    dependsOn("verifyReleaseSigningKey")
}
