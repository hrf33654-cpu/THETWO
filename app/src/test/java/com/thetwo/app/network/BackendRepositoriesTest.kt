package com.thetwo.app.network

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendRepositoriesTest {
    @Test
    fun `toUserFacingMessage maps llm auth failures to actionable text`() {
        val error = ApiException(
            errorCode = "LLM_AUTH_FAILED",
            message = "模型服务鉴权失败",
            statusCode = 502,
        )

        assertEquals(
            "聊天模型鉴权失败，请检查 API Key、账号权限或套餐绑定。",
            error.toUserFacingMessage("默认错误"),
        )
    }

    @Test
    fun `toUserFacingMessage maps llm model not found failures to actionable text`() {
        val error = ApiException(
            errorCode = "LLM_MODEL_NOT_FOUND",
            message = "模型不存在",
            statusCode = 502,
        )

        assertEquals(
            "聊天模型名称无效或当前套餐不支持这个模型，请检查后端的 LLM_MODEL 配置。",
            error.toUserFacingMessage("默认错误"),
        )
    }
}
