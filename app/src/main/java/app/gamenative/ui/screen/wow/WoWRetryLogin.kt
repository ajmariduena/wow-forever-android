package app.gamenative.ui.screen.wow

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
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
                    "Toca el campo Contraseña de WoW y luego este botón. Solo se escribe la contraseña; después toca Entrar.")
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && message == null, onClick = {
                val login = BattleNetSignIn.load(context)
                val keyboard = PluviaApp.xServerView?.getxServer()?.keyboard
                val steps = login?.let { WoWRetrySequence.build(it.password, true) }
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
