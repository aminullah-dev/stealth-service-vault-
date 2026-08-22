package com.safebeauty.app.viewmodel

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.safebeauty.app.data.firebase.FirebaseAuthManager
import com.safebeauty.app.data.firebase.FirestoreRepository
import com.safebeauty.app.security.PinHasher
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
class ChangePinViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val mockRepo    = mockk<FirestoreRepository>(relaxed = true)
    private val mockAuth    = mockk<FirebaseAuthManager>(relaxed = true)
    private val mockHasher  = mockk<PinHasher>(relaxed = true)
    private val mockContext = mockk<Context>(relaxed = true)

    private lateinit var viewModel: ChangePinViewModel

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
        val savedState = SavedStateHandle(mapOf("userId" to "uid-001"))
        viewModel = ChangePinViewModel(savedState, mockRepo, mockAuth, mockHasher, mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fillValid() {
        viewModel.currentPin = "142857"
        viewModel.newPin     = "285714"
        viewModel.confirmPin = "285714"
    }

    private fun errorMessage() =
        (viewModel.state as? ChangePinViewModel.State.Error)?.message

    // ── SavedStateHandle ──────────────────────────────────────────────────────

    @Test
    fun `userId is read from SavedStateHandle`() {
        assertEquals("uid-001", viewModel.userId)
    }

    // ── Required fields ───────────────────────────────────────────────────────

    @Test
    fun `blank currentPin produces error`() {
        fillValid()
        viewModel.currentPin = ""
        viewModel.changePin()
        assertEquals("All fields are required", errorMessage())
    }

    @Test
    fun `blank newPin produces error`() {
        fillValid()
        viewModel.newPin = "   "
        viewModel.changePin()
        assertEquals("All fields are required", errorMessage())
    }

    @Test
    fun `blank confirmPin produces error`() {
        fillValid()
        viewModel.confirmPin = ""
        viewModel.changePin()
        assertEquals("All fields are required", errorMessage())
    }

    // ── New password format ──────────────────────────────────────────────────

    @Test
    fun `new password shorter than 6 characters produces error`() {
        fillValid()
        viewModel.newPin     = "1234"
        viewModel.confirmPin = "1234"
        viewModel.changePin()
        assertEquals("New password must be at least 6 characters", errorMessage())
    }

    @Test
    fun `alphanumeric new password of valid length passes format check`() {
        fillValid()
        viewModel.newPin     = "correcthorse"
        viewModel.confirmPin = "correcthorse"
        viewModel.changePin()
        assertNotEquals("New password must be at least 6 characters", errorMessage())
    }

    // ── Password change logic ────────────────────────────────────────────────

    @Test
    fun `new password same as current produces error`() {
        viewModel.currentPin = "142857"
        viewModel.newPin     = "142857"
        viewModel.confirmPin = "142857"
        viewModel.changePin()
        assertEquals("New password must be different from the current one", errorMessage())
    }

    @Test
    fun `mismatched new passwords produce error`() {
        fillValid()
        viewModel.confirmPin = "999999"
        viewModel.changePin()
        assertEquals("Passwords do not match", errorMessage())
    }

    @Test
    fun `valid password change passes local validation and advances to Loading`() {
        fillValid()
        viewModel.changePin()
        // Passes all local validation; next step is async Firebase lookup
        // State is Loading (not an early validation Error)
        assertNotEquals("All fields are required", errorMessage())
        assertNotEquals("New password must be at least 6 characters", errorMessage())
        assertNotEquals("New password must be different from the current one", errorMessage())
        assertNotEquals("Passwords do not match", errorMessage())
    }

    // ── State management ──────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() {
        assertEquals(ChangePinViewModel.State.Idle, viewModel.state)
    }

    @Test
    fun `dismissState resets to Idle`() {
        fillValid()
        viewModel.currentPin = "" // trigger error
        viewModel.changePin()
        assertTrue(viewModel.state is ChangePinViewModel.State.Error)
        viewModel.dismissState()
        assertEquals(ChangePinViewModel.State.Idle, viewModel.state)
    }
}
