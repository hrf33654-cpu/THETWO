package com.thetwo.app.chat

import kotlinx.coroutines.delay

class MockChatRepository {
    suspend fun generateReply(
        message: String,
        mode: MockReplyMode,
    ): String {
        delay(800)
        return if (mode == MockReplyMode.RESTRICTED) {
            "我会先把回应收紧一些。如果你现在状态很差，建议优先联系现实中的可信任对象或专业支持。我可以继续陪你聊低风险的话题。"
        } else {
            "我收到了。现在先把主链路跑通，等召唤功能接上以后，我也会记得刚才这段聊天。"
        }
    }
}
