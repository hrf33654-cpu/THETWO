package com.thetwo.app.auth

import kotlinx.coroutines.delay

class MockAuthRepository {
    suspend fun login(email: String, code: String, consentAccepted: Boolean): Result<Unit> {
        delay(500)
        return when {
            !consentAccepted -> Result.failure(IllegalStateException("请先同意隐私说明和数据处理说明"))
            email.isBlank() -> Result.failure(IllegalArgumentException("请输入邮箱"))
            !email.contains("@") -> Result.failure(IllegalArgumentException("请输入有效邮箱"))
            code.length < 4 -> Result.failure(IllegalArgumentException("请输入至少 4 位验证码"))
            else -> Result.success(Unit)
        }
    }
}
