package app.gamenative.ui.screen.wow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.gamenative.PluviaApp
import com.winlator.container.Container
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

object WoWRetryLogin {
    val requested = MutableStateFlow(false)
    fun available(container: Container?, context: android.content.Context): Boolean =
        container?.id == "wow_forever" && BattleNetSignIn.hasSavedLogin(context)
}

@Composable
fun WoWRetryLoginHost(container: Container) {
    val context = LocalContext.current
    val requested by WoWRetryLogin.requested.collectAsState()
    var confirmed by remember(requested) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember(requested) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var typingJob by remember { mutableStateOf<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                typingJob?.cancel()
                WoWRetryLogin.requested.value = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            typingJob?.cancel()
            WoWRetryLogin.requested.value = false
        }
    }
    if (!requested || container.id != "wow_forever") return
    AlertDialog(
        onDismissRequest = { if (!busy) WoWRetryLogin.requested.value = false },
        title = { Text("Entrar otra vez") },
        text = {
            Column {
                Text(message ?: if (busy) "Rellenando contraseña…" else
                    "Antes de abrir este menú, cierra el error de WoW y toca su campo Contraseña. El correo debe seguir guardado en WoW. No podemos detectar la pantalla de inicio de sesión: no continúes si estás dentro del juego o en el chat. Solo se rellenará la contraseña; después toca Entrar en WoW.")
                if (!busy && message == null) Row {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("Estoy en el inicio de sesión y seleccioné Contraseña")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = confirmed && !busy && message == null, onClick = {
                val login = BattleNetSignIn.load(context)
                val keyboard = PluviaApp.xServerView?.getxServer()?.keyboard
                val steps = login?.let { WoWRetrySequence.build(it.password, confirmed) }
                if (steps == null || keyboard == null) {
                    message = "No se puede rellenar esta contraseña. Abre el teclado de la app para escribirla manualmente."
                } else {
                    busy = true
                    typingJob = scope.launch {
                        try {
                            WoWRetrySequence.execute(keyboard, steps)
                            WoWRetryLogin.requested.value = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            message = "No se pudo rellenar la contraseña. Usa el teclado de la app."
                        } finally {
                            busy = false
                        }
                    }
                }
            }) { Text("Rellenar contraseña") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { WoWRetryLogin.requested.value = false }) { Text("Cancelar") }
        },
    )
}
