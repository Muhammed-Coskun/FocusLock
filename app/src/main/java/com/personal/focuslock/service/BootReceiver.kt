package com.personal.focuslock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.focuslock.util.Permissions

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            if (Permissions.hasUsageAccess(context) && Permissions.hasOverlay(context)) {
                BlockerService.start(context)
            }
        }
    }
}
