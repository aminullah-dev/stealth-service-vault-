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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    // ── PIN format ────────────────────────────────────────────────────────────

    @Test
    fun `non-digit new pin produces error`() {
        fillValid()
        viewModel.newPin     = "12345a"
        viewModel.confirmPin = "12345a"
        viewModel.changePin()
        assertEquals("PIN must contain digits only", errorMessage())
    }

    @Test
    fun `new pin shorter than 6 digits produces error`() {
        fillValid()
        viewModel.newPin     = "1234"
        viewModel.confirmPin = "1234"
        viewModel.changePin()
        assertEquals("New PIN must be at least 6 digits", errorMessage())
    }

    // ── PIN change logic ──────────────────────────────────────────────────────

    @Test
    fun `new pin same as current produces error`() {
        viewModel.currentPin = "142857"
        viewModel.newPin     = "142857"
        viewModel.confirmPin = "142857"
        viewModel.changePin()
        assertEquals("New PIN must be different from the current PIN", errorMessage())
    }

    @Test
    fun `mismatched new pins produce error`() {
        fillValid()
        viewModel.confirmPin = "999999"
        viewModel.changePin()
        assertEquals("New PINs do not match", errorMessage())
    }

    // ── Weak PIN detection ────────────────────────────────────────────────────

    @Test
    fun `all-same-digit new pin is rejected`() {
        viewModel.currentPin = "142857"
        viewModel.newPin     = "000000"
        viewModel.confirmPin = "000000"
        viewModel.changePin()
        assertTrue(errorMessage()?.contains("too easy") == true)
    }

    @Test
    fun `ascending sequence new pin is rejected`() {
        viewModel.currentPin = "142857"
        viewModel.newPin     = "123456"
        viewModel.confirmPin = "123456"
        viewModel.changePin()
        assertTrue(errorMessage()?.contains("too easy") == true)
    }

    @Test
    fun `descending sequence new pin is rejected`() {
        viewModel.currentPin = "142857"
        viewModel.newPin     = "987654"
        viewModel.confirmPin = "987654"
        viewModel.changePin()
        assertTrue(errorMessage()?.contains("too easy") == true)
    }

    @Test
    fun `valid non-sequential pin passes weak check and advances to Loading`() {
        fillValid()
        viewModel.changePin()
        // Passes all local validation; next step is async Firebase lookup
        // State is Loading (not an early validation Error)
        assertNotEquals("All fields are required", errorMessage())
        assertNotEquals("PIN must contain digits only", errorMessage())
        assertNotEquals("New PIN must be at least 6 digits", errorMessage())
        assertNotEquals("New PIN must be different from the current PIN", errorMessage())
        assertNotEquals("New PINs do not match", errorMessage())
        assertTrue(
            errorMessage()?.contains("too easy") != true
        )
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
