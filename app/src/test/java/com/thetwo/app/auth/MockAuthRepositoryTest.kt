package com.thetwo.app.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockAuthRepositoryTest {
    private val repository = MockAuthRepository()

    @Test
    fun `request code returns debug hint for valid email`() = runBlocking {
        val result = repository.requestCode(email = "user@example.com")

        assertEquals("123456", result.debugCodeHint)
    }

    @Test
    fun `verify code succeeds with valid email and code`() = runBlocking {
        val result = repository.verifyCode(
            email = "user@example.com",
            code = "123456",
        )

        assertTrue(result.sessionToken.isNotBlank())
    }
}
