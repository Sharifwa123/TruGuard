package com.shariftechnologies.scammonitor.ui.common

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shariftechnologies.scammonitor.domain.model.Severity
import com.shariftechnologies.scammonitor.ui.theme.severityColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Success<T>(val value: T) : Loadable<T>
    data class Failure(val message: String) : Loadable<Nothing>
}

fun <T> Flow<T>.asLoadable(): Flow<Loadable<T>> =
    map<T, Loadable<T>> { Loadable.Success(it) }
        .onStart { emit(Loadable.Loading) }
        .catch { emit(Loadable.Failure("Could not read local data (${it.javaClass.simpleName}).")) }

fun formatTime(context: android.content.Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

@Composable
fun SeverityBadge(severity: Severity, modifier: Modifier = Modifier) {
    val color = severityColor(severity)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = severity.name,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun ErrorState(message: String) = MessageState("Something went wrong", message)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(vertical = 2.dp))
}

@Composable
fun OutlinedAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

/** Runs [action] each time the host screen returns to the foreground (e.g. after visiting system Settings). */
@Composable
fun OnResume(action: () -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val current = androidx.compose.runtime.rememberUpdatedState(action)
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) current.value()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
