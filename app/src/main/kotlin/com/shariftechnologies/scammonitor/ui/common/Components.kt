package com.shariftechnologies.scammonitor.ui.common

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/** "2 min. ago" style time, for lists where the exact clock time is noise. */
fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

@Composable
fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShieldBadge(Tone.Accent, size = 84.dp, pulsing = true) }
}

@Composable
fun MessageState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(title, message)
        action?.invoke()
    }
}

@Composable
fun ErrorState(message: String) = EmptyState("Something went wrong", message, icon = Icons.Filled.Warning, tone = Tone.Danger)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = SectionLabel(text, modifier)

@Composable
fun OutlinedAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ClayButton(text, onClick, modifier, tone = Tone.Neutral, compact = true)

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
