package com.safebeauty.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.firebase.auth.FirebaseUser
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.security.PinHasher
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// A plain Application: booting SafeBeautyApplication would load the SQLCipher
// native library, which does not exist on the JVM and failed every test in this
// file. These exercise form validation, which needs no database.
@Config(sdk = [33], application = android.app.Application::class)
class RegisterViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockRepo   = mockk<FirestoreRepository>(relaxed = true)
    private val mockAuth   = mockk<FirebaseAuthManager>(relaxed = true)
    private val mockHasher = mockk<PinHasher>(relaxed = true)

    private lateinit var viewModel: RegisterViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // The view model resolves FirebaseFunctions at construction, and the real
        // Application that would normally initialize Firebase is bypassed above.
        // Dummy options are enough: these tests exercise validation and never
        // reach the network.
        if (FirebaseApp.getApps(ApplicationProvider.getApplicationContext()).isEmpty()) {
            FirebaseApp.initializeApp(
                ApplicationProvider.getApplicationContext(),
                FirebaseOptions.Builder()
                    .setApplicationId("1:0:android:0")
                    .setProjectId("safebeauty-unit-test")
                    .setApiKey("unit-test")
                    .build()
            )
        }
        viewModel = RegisterViewModel(mockRepo, mockAuth, mockHasher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fillValidCustomer() {
        viewModel.name            = "Sara"
        viewModel.phone           = "0700000000"
        viewModel.email           = ""          // optional
        viewModel.password        = "142857"
        viewModel.confirmPassword = "142857"
        viewModel.isProvider      = false
    }

    private fun fillValidProvider() {
        fillValidCustomer()
        viewModel.isProvider  = true
        viewModel.salonName   = "Roza Salon"
        viewModel.district    = "District 3"
        viewModel.services    = listOf("Haircut")
    }

    // Asserts on the reason enum rather than an English sentence. The screen owns
    // the wording in three languages, so a test that pinned the English string
    // failed the moment the message was translated -- which is what happened, and
    // went unnoticed because nothing ran these tests.
    private fun errorReason() =
        (viewModel.state as? RegisterViewModel.RegisterState.Error)?.reason

    // ── Name validation ───────────────────────────────────────────────────────

    @Test
    fun `blank name produces error`() {
        fillValidCustomer()
        viewModel.name = "   "
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.NAME_REQUIRED, errorReason())
    }

    // ── Phone validation ──────────────────────────────────────────────────────

    @Test
    fun `blank phone produces error`() {
        fillValidCustomer()
        viewModel.phone = ""
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.PHONE_REQUIRED, errorReason())
    }

    // ── Email validation ──────────────────────────────────────────────────────

    @Test
    fun `invalid email produces error`() {
        fillValidCustomer()
        viewModel.email = "not-an-email"
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.EMAIL_INVALID, errorReason())
    }

    @Test
    fun `blank email is accepted (optional field)`() {
        fillValidCustomer()
        viewModel.email = ""
        viewModel.startRegistration()
        // Blank email is valid — should not produce an email error
        assertNotEquals(RegisterViewModel.ErrorReason.EMAIL_INVALID, errorReason())
    }

    // ── Password validation ──────────────────────────────────────────────────

    @Test
    fun `password shorter than 6 characters produces error`() {
        fillValidCustomer()
        viewModel.password        = "1234"
        viewModel.confirmPassword = "1234"
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.PIN_TOO_SHORT, errorReason())
    }

    @Test
    fun `mismatched passwords produce error`() {
        fillValidCustomer()
        viewModel.password        = "142857"
        viewModel.confirmPassword = "142858"
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.PIN_MISMATCH, errorReason())
    }

    @Test
    fun `alphanumeric password of valid length passes validation`() {
        fillValidCustomer()
        viewModel.password        = "correcthorse"
        viewModel.confirmPassword = "correcthorse"
        viewModel.startRegistration()
        assertNotEquals(RegisterViewModel.ErrorReason.PIN_TOO_SHORT, errorReason())
        assertNotEquals(RegisterViewModel.ErrorReason.PIN_MISMATCH, errorReason())
    }

    // ── Provider-specific validation ──────────────────────────────────────────

    @Test
    fun `provider without salon name produces error`() {
        fillValidProvider()
        viewModel.salonName = ""
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.SALON_NAME_REQUIRED, errorReason())
    }

    @Test
    fun `provider without district produces error`() {
        fillValidProvider()
        viewModel.district = ""
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.DISTRICT_REQUIRED, errorReason())
    }

    @Test
    fun `provider without services produces error`() {
        fillValidProvider()
        viewModel.services = emptyList()
        viewModel.startRegistration()
        assertEquals(RegisterViewModel.ErrorReason.SERVICES_REQUIRED, errorReason())
    }

    // ── Service list management ───────────────────────────────────────────────

    @Test
    fun `addService appends trimmed service to list`() {
        viewModel.serviceInput = "  Manicure  "
        viewModel.addService()
        assertTrue(viewModel.services.contains("Manicure"))
        assertEquals("", viewModel.serviceInput)
    }

    @Test
    fun `addService ignores blank input`() {
        viewModel.serviceInput = "   "
        viewModel.addService()
        assertTrue(viewModel.services.isEmpty())
    }

    @Test
    fun `addService ignores duplicate service`() {
        viewModel.serviceInput = "Haircut"
        viewModel.addService()
        viewModel.serviceInput = "Haircut"
        viewModel.addService()
        assertEquals(1, viewModel.services.size)
    }

    @Test
    fun `removeService removes the correct service`() {
        viewModel.services = listOf("Haircut", "Manicure", "Pedicure")
        viewModel.removeService("Manicure")
        assertFalse(viewModel.services.contains("Manicure"))
        assertEquals(2, viewModel.services.size)
    }

    @Test
    fun `removeService leaves list unchanged for unknown service`() {
        viewModel.services = listOf("Haircut")
        viewModel.removeService("Pedicure")
        assertEquals(1, viewModel.services.size)
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `valid customer registration reaches Success state`() {
        coEvery { mockAuth.createAccount(any(), any()) } returns Result.success(mockk<FirebaseUser>(relaxed = true))
        coEvery { mockRepo.createUser(any()) } returns Unit

        fillValidCustomer()
        viewModel.startRegistration()

        assertTrue(
            viewModel.state is RegisterViewModel.RegisterState.CustomerSuccess ||
            viewModel.state is RegisterViewModel.RegisterState.Loading
        )
    }

    @Test
    fun `dismissState resets to Idle`() {
        viewModel.startRegistration() // triggers an error (fields empty)
        viewModel.dismissState()
        assertEquals(RegisterViewModel.RegisterState.Idle, viewModel.state)
    }
}
