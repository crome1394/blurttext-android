package app.messages.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.messages.sms.ScheduleController
import app.messages.sms.SmsRole

/** How a permission row is granted, and how to ask for it. */
private sealed interface Grant {
    /** Runtime permissions Android asks for with its own prompt. */
    data class Runtime(
        val permissions: List<String>,
        val required: List<String> = permissions,
    ) : Grant

    /** BlurtText as the default SMS app, granted through the role prompt. */
    data object DefaultSms : Grant

    /** Exact alarms, granted on a system settings page (Android 12+). */
    data object ExactAlarms : Grant

    /** Battery optimization off, set on a system settings page. */
    data object Background : Grant
}

private data class PermissionRow(
    val title: String,
    val why: String,
    val icon: ImageVector,
    val grant: Grant,
    val optional: Boolean = false,
    val minSdk: Int = Build.VERSION_CODES.O,
)

private enum class Status { Allowed, Partial, Missing }

/** Settings → Permissions: what each permission is for, whether it is on, and how to turn it on. */
@Composable
internal fun PermissionsPage(
    query: String,
    onRequestRole: () -> Unit,
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh += 1 }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh += 1 }
    val rows = PERMISSION_ROWS.filter { Build.VERSION.SDK_INT >= it.minSdk && matches(query, it.title, it.why) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                "What BlurtText can use and why. Allow shows Android's prompt. If the prompt does not appear, " +
                    "Android has stopped asking; use Open settings instead.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(rows, key = { it.title }) { row ->
            val status = remember(refresh, row) { statusOf(context, row.grant) }
            PermissionItem(
                row = row,
                status = status,
                onAllow = { allow(context, row.grant, onRequestRole) { request.launch(it.toTypedArray()) } },
                onSettings = { context.openSettingsFor(row.grant) },
            )
            HorizontalDivider()
        }
        if (matches(query, "Granted when installed", INSTALLED.joinToString { it.first + it.second })) {
            item { InstalledPermissions() }
        }
        item {
            Text(
                "BlurtText has no ads, analytics, or tracking. About → Privacy lists exactly when it goes online.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionItem(
    row: PermissionRow,
    status: Status,
    onAllow: () -> Unit,
    onSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) =
        when {
            status == Status.Allowed -> "Allowed" to scheme.primary
            status == Status.Partial -> "Approximate only. Choose Precise." to scheme.error
            row.optional -> "Not allowed (optional)" to scheme.onSurfaceVariant
            else -> "Not allowed" to scheme.error
        }
    ListItem(
        headlineContent = { Text(row.title) },
        leadingContent = { Icon(row.icon, contentDescription = null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.why)
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
                if (status != Status.Allowed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = onAllow) { Text("Allow") }
                        TextButton(onClick = onSettings) { Text("Open settings") }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun InstalledPermissions() {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Granted when installed", style = MaterialTheme.typography.titleSmall)
        Text(
            "Android grants these without asking. They cannot reach your messages on their own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        INSTALLED.forEach { (name, why) ->
            Text("$name: $why", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val INSTALLED =
    listOf(
        "Internet and network state" to "GIF search after you add a KLIPY key, and the computer inbox on your Wi-Fi.",
        "Wi-Fi state and multicast" to "Find the Wi-Fi network and answer for blurttext.local on it.",
        "Foreground service" to "Keep the computer inbox running while it is on, with a notification you can see.",
        "Run at startup" to "Re-arm scheduled texts and the computer inbox after the phone restarts.",
        "Keep awake" to "Finish sending or downloading a picture message when the screen turns off.",
        "Receive WAP push" to "Get the carrier's notice that a picture message is waiting.",
    )

/** Every permission row. Rows newer than the running Android version are hidden. */
private val PERMISSION_ROWS =
    listOf(
        PermissionRow(
            "Default SMS app",
            "Only the default SMS app can receive texts, save the ones you send, and restore backups.",
            Icons.Outlined.Star,
            Grant.DefaultSms,
        ),
        PermissionRow(
            "SMS and picture messages",
            "Read, send, and receive texts and picture messages through your carrier. Messages stay on this phone.",
            Icons.Outlined.Sms,
            Grant.Runtime(
                listOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.RECEIVE_MMS,
                ),
            ),
        ),
        PermissionRow(
            "Contacts",
            "Show names and photos instead of numbers, and save a number to your contacts when you ask.",
            Icons.Outlined.Contacts,
            Grant.Runtime(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)),
        ),
        PermissionRow(
            "Phone",
            "Choose which SIM sends a text, and leave your own number out of group messages.",
            Icons.Outlined.Phone,
            Grant.Runtime(listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)),
        ),
        PermissionRow(
            "Notifications",
            "Tell you when a text arrives or a send fails.",
            Icons.Outlined.Notifications,
            Grant.Runtime(listOf(Manifest.permission.POST_NOTIFICATIONS)),
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
        PermissionRow(
            "Camera",
            "Take a photo to send. Used only while the camera is open.",
            Icons.Outlined.PhotoCamera,
            Grant.Runtime(listOf(Manifest.permission.CAMERA)),
            optional = true,
        ),
        PermissionRow(
            "Location",
            "Read the Wi-Fi name so the computer inbox runs only on networks you allow, and share where you are " +
                "when you tap Share location. Choose Precise: with Approximate, Android hides the Wi-Fi name.",
            Icons.Outlined.LocationOn,
            Grant.Runtime(
                listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                required = listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            ),
            optional = true,
        ),
        PermissionRow(
            "Nearby Wi-Fi devices",
            "Read the Wi-Fi name on Android 13 and later, for the computer inbox.",
            Icons.Outlined.Wifi,
            Grant.Runtime(listOf(Manifest.permission.NEARBY_WIFI_DEVICES)),
            optional = true,
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
        PermissionRow(
            "Local network",
            "Let a computer on your Wi-Fi open the computer inbox.",
            Icons.Outlined.Lan,
            Grant.Runtime(listOf(ACCESS_LOCAL_NETWORK)),
            optional = true,
            minSdk = API_ANDROID_17,
        ),
        PermissionRow(
            "Exact alarms",
            "Send a scheduled text at the minute you chose instead of a few minutes late.",
            Icons.Outlined.Alarm,
            Grant.ExactAlarms,
            optional = true,
            minSdk = Build.VERSION_CODES.S,
        ),
        PermissionRow(
            "Background use",
            "Let the computer inbox start by itself after a Wi-Fi change. Some phones, Samsung especially, stop it otherwise.",
            Icons.Outlined.BatteryChargingFull,
            Grant.Background,
            optional = true,
        ),
    )

private fun statusOf(
    context: Context,
    grant: Grant,
): Status =
    when (grant) {
        is Grant.Runtime -> {
            val granted = grant.permissions.filter { context.has(it) }
            when {
                grant.required.all { context.has(it) } && granted.size == grant.permissions.size -> Status.Allowed
                granted.isNotEmpty() && grant.required.none { context.has(it) } -> Status.Partial
                else -> Status.Missing
            }
        }

        Grant.DefaultSms -> {
            if (SmsRole.isDefault(context)) Status.Allowed else Status.Missing
        }

        Grant.ExactAlarms -> {
            if (ScheduleController.canExact(context)) Status.Allowed else Status.Missing
        }

        Grant.Background -> {
            val power = context.getSystemService(PowerManager::class.java)
            if (power?.isIgnoringBatteryOptimizations(context.packageName) == true) Status.Allowed else Status.Missing
        }
    }

private fun allow(
    context: Context,
    grant: Grant,
    onRequestRole: () -> Unit,
    request: (List<String>) -> Unit,
) {
    when (grant) {
        is Grant.Runtime -> request(grant.permissions)
        Grant.DefaultSms -> onRequestRole()
        Grant.ExactAlarms, Grant.Background -> context.openSettingsFor(grant)
    }
}

private fun Context.openSettingsFor(grant: Grant) {
    val appUri = Uri.fromParts("package", packageName, null)
    val intent =
        when {
            grant == Grant.DefaultSms -> {
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            }

            grant == Grant.ExactAlarms && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, appUri)
            }

            grant == Grant.Background -> {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            }

            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)
            }
        }
    // Some phones lack a specific screen. App info always exists and lists every permission.
    runCatching { startActivity(intent) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri)) } }
}

private fun Context.has(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun matches(
    query: String,
    vararg text: String,
): Boolean {
    val q = query.trim()
    return q.isEmpty() || "Permissions".contains(q, true) || text.any { it.contains(q, ignoreCase = true) }
}

/** Android 17, which adds ACCESS_LOCAL_NETWORK. Not in the SDK 36 stubs. */
private const val API_ANDROID_17 = 37
private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
