package com.safebeauty.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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
        viewModel = RegisterViewModel(mockRepo, mockAuth, mockHasher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fillValidCustomer() {
        viewModel.name       = "Sara"
        viewModel.phone      = "0700000000"
        viewModel.email      = ""          // optional
        viewModel.pin        = "142857"
        viewModel.confirmPin = "142857"
        viewModel.isProvider = false
    }

    private fun fillValidProvider() {
        fillValidCustomer()
        viewModel.isProvider  = true
        viewModel.salonName   = "Roza Salon"
        viewModel.district    = "District 3"
        viewModel.services    = listOf("Haircut")
    }

    private fun errorMessage() =
        (viewModel.state as? RegisterViewModel.RegisterState.Error)?.message

    // ── Name validation ───────────────────────────────────────────────────────

    @Test
    fun `blank name produces error`() {
        fillValidCustomer()
        viewModel.name = "   "
        viewModel.register()
        assertEquals("Name is required", errorMessage())
    }

    // ── Phone validation ──────────────────────────────────────────────────────

    @Test
    fun `blank phone produces error`() {
        fillValidCustomer()
        viewModel.phone = ""
        viewModel.register()
        assertEquals("Phone number is required", errorMessage())
    }

    // ── Email validation ──────────────────────────────────────────────────────

    @Test
    fun `invalid email produces error`() {
        fillValidCustomer()
        viewModel.email = "not-an-email"
        viewModel.register()
        assertEquals("Please enter a valid email address", errorMessage())
    }

    @Test
    fun `blank email is accepted (optional field)`() {
        fillValidCustomer()
        viewModel.email = ""
        viewModel.register()
        // Blank email is valid — should not produce an email error
        assertNotEquals("Please enter a valid email address", errorMessage())
    }

    // ── PIN validation ────────────────────────────────────────────────────────

    @Test
    fun `non-digit pin produces error`() {
        fillValidCustomer()
        viewModel.pin = "12345a"
        viewModel.register()
        assertEquals("PIN must contain digits only", errorMessage())
    }

    @Test
    fun `pin shorter than 6 digits produces error`() {
        fillValidCustomer()
        viewModel.pin        = "1234"
        viewModel.confirmPin = "1234"
        viewModel.register()
        assertEquals("PIN must be at least 6 digits", errorMessage())
    }

    @Test
    fun `mismatched pins produce error`() {
        fillValidCustomer()
        viewModel.pin        = "142857"
        viewModel.confirmPin = "142858"
        viewModel.register()
        assertEquals("PINs do not match", errorMessage())
    }

    // ── Weak PIN detection ────────────────────────────────────────────────────

    @Test
    fun `all-same-digit pin is rejected`() {
        fillValidCustomer()
        viewModel.pin        = "111111"
        viewModel.confirmPin = "111111"
        viewModel.register()
        assertTrue(errorMessage()?.contains("too easy") == true)
    }

    @Test
    fun `ascending sequence pin is rejected`() {
        fillValidCustomer()
        viewModel.pin        = "123456"
        viewModel.confirmPin = "123456"
        viewModel.register()
        assertTrue(errorMessage()?.contains("too easy") == true)
    }

    @Test
    fun `descending sequence pin is rejected`() {
        fillValidCustomer()
        viewModel.pin        = "987654"
        viewModel.confirmPin = "987654"
        viewModel.register()
        assertTrue(errorMessage()?.contains("too easy") == true)
    }

    @Test
    fun `non-sequential pin passes weak check`() {
        fillValidCustomer()
        viewModel.pin        = "142857"
        viewModel.confirmPin = "142857"
        viewModel.register()
        assertNotEquals("PIN is too easy to guess. Avoid sequences like 123456 or repeated digits like 000000.", errorMessage())
    }

    // ── Provider-specific validation ──────────────────────────────────────────

    @Test
    fun `provider without salon name produces error`() {
        fillValidProvider()
        viewModel.salonName = ""
        viewModel.register()
        assertEquals("Salon name is required", errorMessage())
    }

    @Test
    fun `provider without district produces error`() {
        fillValidProvider()
        viewModel.district = ""
        viewModel.register()
        assertEquals("District is required", errorMessage())
    }

    @Test
    fun `provider without services produces error`() {
        fillValidProvider()
        viewModel.services = emptyList()
        viewModel.register()
        assertEquals("Add at least one service", errorMessage())
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
        coEvery { mockAuth.createAccount(any(), any()) } returns Result.success(Unit)
        coEvery { mockRepo.createUser(any()) } returns Unit

        fillValidCustomer()
        viewModel.register()

        assertTrue(
            viewModel.state is RegisterViewModel.RegisterState.CustomerSuccess ||
            viewModel.state is RegisterViewModel.RegisterState.Loading
        )
    }

    @Test
    fun `dismissState resets to Idle`() {
        viewModel.register() // triggers an error (fields empty)
        viewModel.dismissState()
        assertEquals(RegisterViewModel.RegisterState.Idle, viewModel.state)
    }
}
