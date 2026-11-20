package ru.amazy.crm.legacy

import android.content.Context
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

// 0.7.0 used a persisted UUID as the device_id in Chatwoot. Reuse it so
// signing in after an update updates that device's existing FCM subscription.
class LegacyPushModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("AmazyLegacyPush")
        Function("deviceId") {
            appContext.reactContext
                ?.getSharedPreferences("amazy_native_push_config", Context.MODE_PRIVATE)
                ?.getString("device_id", null)
        }
    }
}
