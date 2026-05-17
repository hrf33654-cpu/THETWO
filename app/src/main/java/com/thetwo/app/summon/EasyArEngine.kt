package com.thetwo.app.summon

interface EasyArEngine {
    fun isRuntimeAvailable(): Boolean
    fun canStartImageTracking(): Boolean
}

class ReflectionEasyArEngine : EasyArEngine {
    override fun isRuntimeAvailable(): Boolean = runCatching {
        Class.forName("cn.easyar.Engine")
    }.isSuccess || runCatching {
        Class.forName("cn.easyar.Buffer")
    }.isSuccess

    override fun canStartImageTracking(): Boolean = isRuntimeAvailable()
}
