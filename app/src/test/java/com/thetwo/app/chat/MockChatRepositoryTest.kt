package com.thetwo.app.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class MockChatRepositoryTest {
    private val repository = MockChatRepository()

    @Test
    fun `restricted mode returns safer guidance`() = runBlocking {
        val reply = repository.generateReply(
            message = "我不想活了",
            mode = MockReplyMode.RESTRICTED,
        )

        assertTrue(reply.contains("专业支持") || reply.contains("可信任对象"))
    }
}
