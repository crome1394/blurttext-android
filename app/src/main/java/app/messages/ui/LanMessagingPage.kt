package app.messages.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lantext.LanTextApp
import app.lantext.net.WifiGate
import app.lantext.ui.AppViewModel
import app.lantext.ui.LanTextAppUi

@Composable
fun LanMessagingPage(active: Boolean) {
    val viewModel: AppViewModel = viewModel()
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            viewModel.refreshPermissions()
            LanTextApp.instance.wifi.reregister()
            LanTextApp.instance.gateway.onNetworkChanged()
        }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        viewModel.refreshPermissions()
        LanTextApp.instance.wifi.reregister()
        LanTextApp.instance.gateway.onNetworkChanged()
    }
    // Coming back from Android's Location or app settings, re-read what may have changed there.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, active) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (active && event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshPermissions()
                    LanTextApp.instance.wifi.reregister()
                    LanTextApp.instance.gateway.onNetworkChanged()
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val pending by viewModel.pendingPairing.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    LanTextAppUi(
        settings = settings,
        snapshot = snapshot,
        pending = pending,
        devices = devices,
        currentSsid = viewModel.currentSsid(),
        permissions = permissions,
        onRequestPermissions = { permissionLauncher.launch(neededPermissions().toTypedArray()) },
        onFinishOnboarding = viewModel::finishOnboarding,
        onEnabled = viewModel::setEnabled,
        onListenPort = viewModel::setListenPort,
        onGifEnabled = viewModel::setGifEnabled,
        onVoiceEnabled = viewModel::setVoiceEnabled,
        onAddCurrent = viewModel::addCurrentNetwork,
        onAddNetwork = viewModel::addNetwork,
        onRemoveNetwork = viewModel::removeNetwork,
        onForgetNetwork = viewModel::forgetNetwork,
        onRecyclePairing = viewModel::recyclePairing,
        onApprove = viewModel::approvePairing,
        onDeny = viewModel::denyPairing,
        onRename = viewModel::rename,
        onRevoke = viewModel::revoke,
        embedded = true,
        active = active,
        appLabel = "BlurtText",
    )
}

private fun neededPermissions(): List<String> {
    val needed =
        mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
        )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        needed += Manifest.permission.POST_NOTIFICATIONS
        needed += Manifest.permission.NEARBY_WIFI_DEVICES
    }
    if (Build.VERSION.SDK_INT >= API_ANDROID_17) {
        needed += WifiGate.PERMISSION_ACCESS_LOCAL_NETWORK
    }
    needed += Manifest.permission.ACCESS_COARSE_LOCATION
    needed += Manifest.permission.ACCESS_FINE_LOCATION
    return needed
}

/** Android 17, which adds ACCESS_LOCAL_NETWORK. Not in the SDK 36 stubs. */
private const val API_ANDROID_17 = 37
