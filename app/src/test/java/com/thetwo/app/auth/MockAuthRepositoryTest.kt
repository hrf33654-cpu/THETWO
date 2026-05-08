package com.thetwo.app.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAuthRepositoryTest {
    private val repository = MockAuthRepository()

    @Test
    fun `login fails when consent is missing`() = runBlocking {
        val result = repository.login(
            email = "user@example.com",
            code = "1234",
            consentAccepted = false,
        )

        assertFalse(result.isSuccess)
    }

    @Test
    fun `login succeeds with valid email code and consent`() = runBlocking {
        val result = repository.login(
            email = "user@example.com",
            code = "1234",
            consentAccepted = true,
        )

        assertTrue(result.isSuccess)
    }
}
