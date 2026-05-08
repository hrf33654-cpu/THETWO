const RESTRICTED_KEYWORDS = [
    "未成年",
    "初中",
    "高中",
    "不想活",
    "自杀",
    "轻生",
    "伤害自己",
];
export function resolveChatMode(message) {
    return RESTRICTED_KEYWORDS.some((keyword) => message.includes(keyword))
        ? "RESTRICTED"
        : "NORMAL";
}
export function generateAssistantReply(message, mode) {
    if (mode === "RESTRICTED") {
        return "我会先把回应收紧一些。如果你现在状态很差，建议优先联系现实中的可信任对象或专业支持。我可以继续陪你聊低风险的话题。";
    }
    if (message.includes("召唤") || message.includes("拍照")) {
        return "我记住你刚才提到的召唤和拍摄了。等你回到聊天页，我会继续沿着这段互动回应你。";
    }
    return "我收到了。现在后端已经开始接管这段对话，接下来我会继续按这个主闭环陪你往下走。";
}
