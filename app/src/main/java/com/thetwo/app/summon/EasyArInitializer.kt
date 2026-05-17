package com.thetwo.app.summon

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.util.Log
import com.thetwo.app.BuildConfig
import java.security.MessageDigest

data class EasyArInitializationResult(
    val initialized: Boolean,
    val errorMessage: String? = null,
)

object EasyArInitializer {
    fun hasLicenseKey(): Boolean = BuildConfig.EASYAR_LICENSE_KEY.isNotBlank()

    fun initializeIfPossible(context: Context): EasyArInitializationResult {
        val licenseKey = BuildConfig.EASYAR_LICENSE_KEY
        if (licenseKey.isBlank()) {
            return EasyArInitializationResult(
                initialized = false,
                errorMessage = "EasyAR license key is missing.",
            )
        }

        Log.i(
            "THETWO_EASYAR",
            "EasyAR init start package=${context.packageName} keyLength=${licenseKey.length} keyFingerprint=${licenseKey.fingerprint()}",
        )

        return runCatching {
            val engineClass = Class.forName("cn.easyar.Engine")
            val activity = context.findActivity()
                ?: return@runCatching EasyArInitializationResult(
                    initialized = false,
                    errorMessage = "EasyAR requires an Activity context.",
                )

            val initializeWithActivity = engineClass.methods.firstOrNull { method ->
                method.name == "initialize" &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == Activity::class.java &&
                    method.parameterTypes[1] == String::class.java
            }
            val initialized = initializeWithActivity?.invoke(null, activity, licenseKey) as? Boolean ?: false
            if (initialized) {
                EasyArInitializationResult(initialized = true)
            } else {
                val errorMessage = readEngineErrorMessage(engineClass)
                if (!errorMessage.isNullOrBlank()) {
                    Log.e("THETWO_EASYAR", "EasyAR initialize failed: $errorMessage")
                }
                EasyArInitializationResult(
                    initialized = false,
                    errorMessage = errorMessage ?: "EasyAR initialize failed.",
                )
            }
        }.onFailure { error ->
            Log.w("THETWO_EASYAR", "EasyAR initialize skipped", error)
        }.getOrElse { error ->
            EasyArInitializationResult(
                initialized = false,
                errorMessage = error.message ?: "EasyAR initialize failed.",
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun String.fingerprint(): String = runCatching {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    digest.joinToString(separator = "") { byte ->
        "%02X".format(byte)
    }.take(12)
}.getOrElse {
    "unavailable"
}

private fun readEngineErrorMessage(engineClass: Class<*>): String? = runCatching {
    engineClass.methods
        .firstOrNull { it.name == "errorMessage" && it.parameterTypes.isEmpty() }
        ?.invoke(null) as? String
}.getOrNull()
