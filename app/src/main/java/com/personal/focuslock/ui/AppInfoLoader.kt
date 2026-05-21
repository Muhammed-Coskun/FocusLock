package com.personal.focuslock.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?
)

object AppInfoLoader {
    fun listLauncherApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val self = context.packageName
        return resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != self }
            .map { info -> toInstalledApp(pm, info) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun load(context: Context, pkg: String): InstalledApp? {
        val pm = context.packageManager
        return runCatching { toInstalledApp(pm, pm.getApplicationInfo(pkg, 0)) }.getOrNull()
    }

    private fun toInstalledApp(pm: PackageManager, info: ApplicationInfo): InstalledApp {
        val label = pm.getApplicationLabel(info).toString()
        val icon = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96) }.getOrNull()
        return InstalledApp(info.packageName, label, icon)
    }
}
