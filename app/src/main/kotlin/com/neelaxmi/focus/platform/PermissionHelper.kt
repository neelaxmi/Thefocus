package com.neelaxmi.focus.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings

object PermissionHelper {
    fun notificationsGranted(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else ctx.service<NotificationManager>().areNotificationsEnabled()
    fun needsNotificationRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= 33
    fun exactAlarmsAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ctx.service<AlarmManager>().canScheduleExactAlarms()
    fun batteryUnrestricted(ctx: Context): Boolean =
        ctx.service<PowerManager>().isIgnoringBatteryOptimizations(ctx.packageName)
    fun exactAlarmSettings(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
        else appDetails(ctx)
    fun notificationSettings(ctx: Context): Intent =
        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
    fun batterySettings(): Intent = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    fun appDetails(ctx: Context): Intent =
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
}
