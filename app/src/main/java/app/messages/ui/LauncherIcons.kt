package app.messages.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.messages.R

data class LauncherPreset(
    val id: String,
    val label: String,
    val foreground: Int,
    val background: Int,
    val alias: String,
    /** The bubble color, used for notifications and the recent-apps card. */
    val accent: Long,
    val mipmap: Int,
)

object LauncherIcons {
    val presets: List<LauncherPreset> =
        listOf(
            LauncherPreset(
                id = "cyan",
                label = "Cyan",
                foreground = R.drawable.ic_sms_icon_cyan,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherCyan",
                accent = 0xFF75FBFD,
                mipmap = R.mipmap.ic_launcher_cyan,
            ),
            LauncherPreset(
                id = "black",
                label = "Black",
                foreground = R.drawable.ic_sms_icon_black,
                background = R.color.launcher_bg_light,
                alias = "app.messages.LauncherBlack",
                accent = 0xFF000000,
                mipmap = R.mipmap.ic_launcher_black,
            ),
            LauncherPreset(
                id = "dark_blue",
                label = "Dark blue",
                foreground = R.drawable.ic_sms_icon_dark_blue,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherDarkBlue",
                accent = 0xFF2854C5,
                mipmap = R.mipmap.ic_launcher_dark_blue,
            ),
            LauncherPreset(
                id = "dark_purple",
                label = "Dark purple",
                foreground = R.drawable.ic_sms_icon_dark_purple,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherDarkPurple",
                accent = 0xFF8C1AF6,
                mipmap = R.mipmap.ic_launcher_dark_purple,
            ),
            LauncherPreset(
                id = "dark_red",
                label = "Dark red",
                foreground = R.drawable.ic_sms_icon_dark_red,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherDarkRed",
                accent = 0xFF8B1A10,
                mipmap = R.mipmap.ic_launcher_dark_red,
            ),
            LauncherPreset(
                id = "green",
                label = "Green",
                foreground = R.drawable.ic_sms_icon_green,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherGreen",
                accent = 0xFF75FB4C,
                mipmap = R.mipmap.ic_launcher_green,
            ),
            LauncherPreset(
                id = "light_blue",
                label = "Light blue",
                foreground = R.drawable.ic_sms_icon_light_blue,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherLightBlue",
                accent = 0xFF5985E1,
                mipmap = R.mipmap.ic_launcher_light_blue,
            ),
            LauncherPreset(
                id = "orange",
                label = "Orange",
                foreground = R.drawable.ic_sms_icon_orange,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherOrange",
                accent = 0xFFF19E39,
                mipmap = R.mipmap.ic_launcher_orange,
            ),
            LauncherPreset(
                id = "purple",
                label = "Purple",
                foreground = R.drawable.ic_sms_icon_purple,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherPurple",
                accent = 0xFFEA33F7,
                mipmap = R.mipmap.ic_launcher_purple,
            ),
            LauncherPreset(
                id = "red",
                label = "Red",
                foreground = R.drawable.ic_sms_icon_red,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherRed",
                accent = 0xFFEA3323,
                mipmap = R.mipmap.ic_launcher_red,
            ),
            LauncherPreset(
                id = "white",
                label = "White",
                foreground = R.drawable.ic_sms_icon_white,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherWhite",
                accent = 0xFFFFFFFF,
                mipmap = R.mipmap.ic_launcher_white,
            ),
            LauncherPreset(
                id = "yellow",
                label = "Yellow",
                foreground = R.drawable.ic_sms_icon_yellow,
                background = R.color.launcher_bg_dark,
                alias = "app.messages.LauncherYellow",
                accent = 0xFFFFFF55,
                mipmap = R.mipmap.ic_launcher_yellow,
            ),
        )

    fun canonical(raw: String): String {
        val id = if (raw == "original") "cyan" else raw
        return presets.firstOrNull { it.id == id }?.id ?: "cyan"
    }

    fun preset(raw: String): LauncherPreset = presets.first { it.id == canonical(raw) }

    /** The chosen icon's color as an ARGB int, for notification accents. */
    fun accent(raw: String): Int = preset(raw).accent.toInt()

    /**
     * Shows the chosen icon and color on this app's recent-apps card. Without it Android shows the
     * manifest icon, which is always cyan.
     */
    fun applyToTask(
        activity: Activity,
        raw: String,
    ) {
        val chosen = preset(raw)
        val label = activity.getString(R.string.messages_app_name)
        val description =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ActivityManager.TaskDescription
                    .Builder()
                    .setLabel(label)
                    .setIcon(chosen.mipmap)
                    .build()
            } else {
                taskDescriptionCompat(activity, label, chosen.mipmap)
            }
        activity.setTaskDescription(description)
    }

    @Suppress("DEPRECATION")
    private fun taskDescriptionCompat(
        activity: Activity,
        label: String,
        mipmap: Int,
    ): ActivityManager.TaskDescription =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager.TaskDescription(label, mipmap)
        } else {
            val icon = ContextCompat.getDrawable(activity, mipmap)?.toBitmap(TASK_ICON_PX, TASK_ICON_PX)
            ActivityManager.TaskDescription(label, icon)
        }

    /** Enables the chosen launcher alias first, then disables the others. No-ops when it already matches. */
    fun apply(
        context: Context,
        raw: String,
    ) {
        val id = canonical(raw)
        val pm = context.packageManager
        val enabled =
            presets.associate { preset ->
                preset.id to componentEnabled(pm, ComponentName(context, preset.alias), preset.id == "cyan")
            }
        val originalOn = componentEnabled(pm, ComponentName(context, ORIGINAL_ALIAS), false)
        if (!originalOn && enabled.all { (presetId, on) -> on == (presetId == id) }) return
        val flag = PackageManager.DONT_KILL_APP
        pm.setComponentEnabledSetting(
            ComponentName(context, presets.first { it.id == id }.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            flag,
        )
        presets.filter { it.id != id }.forEach { preset ->
            pm.setComponentEnabledSetting(
                ComponentName(context, preset.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                flag,
            )
        }
        // The green bubble used to be this alias. Keep it off so the home screen shows one icon.
        pm.setComponentEnabledSetting(
            ComponentName(context, ORIGINAL_ALIAS),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            flag,
        )
    }

    private fun componentEnabled(
        pm: PackageManager,
        component: ComponentName,
        manifestDefault: Boolean,
    ): Boolean =
        when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            else -> manifestDefault
        }

    private const val ORIGINAL_ALIAS = "app.messages.LauncherOriginal"
    private const val TASK_ICON_PX = 192
}
