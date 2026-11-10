package app.gamenative.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gamenative.PluviaApp
import app.gamenative.R
import com.winlator.container.Container
import com.winlator.inputcontrols.ControlElement
import java.util.Locale

@Composable
internal fun WoWCameraSensitivityControl(container: Container?) {
    if (container?.id != "wow_forever") return
    val profile = PluviaApp.inputControlsView?.profile ?: return
    val camera = profile.elements.firstOrNull { it.type == ControlElement.Type.WOW_GAMEPAD }?.woWGamepad ?: return
    var sensitivity by remember(camera) { mutableFloatStateOf(camera.cameraSensitivity) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.wow_camera_sensitivity, String.format(Locale.getDefault(), "%.1f", sensitivity)))
        Slider(
            value = sensitivity,
            onValueChange = { sensitivity = it; camera.cameraSensitivity = it },
            onValueChangeFinished = { profile.save() },
            valueRange = .1f..5f,
        )
    }
}
