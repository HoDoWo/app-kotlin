package com.hodowo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A manual saved by the backend, as shown in "Recent Manuals" on the Home screen.
 *
 * @param createdAtMillis creation time in epoch milliseconds, or null when unknown.
 */
data class RecentManual(
    val guide: ManualGuide,
    val createdAtMillis: Long?,
    val isBookmarked: Boolean = false
)

/** Everything the Home screen shows. */
data class HomeUiState(
    val userName: String = DEFAULT_USER_NAME,
    val tip: String = FALLBACK_TIPS.first(),
    val recentManuals: List<RecentManual> = emptyList(),
    val isLoadingManuals: Boolean = false,
    /** True when the manuals could not be loaded (the list may be stale). */
    val manualsFailed: Boolean = false,
    val now: Long = System.currentTimeMillis()
)

/**
 * State of the Home screen: greeting, daily tip and the user's recent manuals.
 *
 * The tip and the manuals come from the backend through [aiService]. When the
 * backend cannot answer, the screen still works: the tip falls back to a local
 * one and the manuals show an error with a retry button.
 *
 * @param userName returns the name to greet (see [greetingName]); called on every refresh,
 *   so a sign-in done on another screen is picked up when Home is shown again.
 * @param clock current time in epoch milliseconds (replaced in tests).
 */
class HomeViewModel(
    private val aiService: HoDoWoAiService = DemoHoDoWoAiService(),
    private val userName: () -> String = { DEFAULT_USER_NAME },
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(tip = fallbackTip(clock()), now = clock())
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var tipLoaded = false

    /** Reloads the greeting, the tip (once per ViewModel) and the recent manuals. */
    fun refresh() {
        _uiState.value = _uiState.value.copy(userName = userName(), now = clock())

        if (!tipLoaded) {
            viewModelScope.launch { loadTip() }
        }
        viewModelScope.launch { loadManuals() }
    }

    private suspend fun loadTip() {
        val tip = try {
            aiService.dailyTip()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        if (!tip.isNullOrBlank()) {
            tipLoaded = true
            _uiState.value = _uiState.value.copy(tip = tip.trim())
        }
    }

    private suspend fun loadManuals() {
        _uiState.value = _uiState.value.copy(isLoadingManuals = true)

        _uiState.value = try {
            val manuals = aiService.recentManuals(RECENT_MANUALS_LIMIT)
            _uiState.value.copy(
                recentManuals = manuals,
                isLoadingManuals = false,
                manualsFailed = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _uiState.value.copy(isLoadingManuals = false, manualsFailed = true)
        }
    }

    companion object {
        const val RECENT_MANUALS_LIMIT = 5
    }
}

const val DEFAULT_USER_NAME = "there"

/** Local tips used when the backend tip cannot be loaded (one per day). */
val FALLBACK_TIPS = listOf(
    "Run the washer after 10:00 PM tonight to save on power.",
    "Wash at 30 °C: most loads come out just as clean with far less energy.",
    "Run the dishwasher only when it is full and use the eco program.",
    "Keep the fridge between 3 and 5 °C and the freezer at -18 °C.",
    "Clean the dryer lint filter after every load to dry faster.",
    "Let hot food cool down before putting it in the fridge.",
    "Descale the kettle and coffee machine regularly: limescale wastes energy."
)

private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** Picks the local tip of the day, so it changes daily but not on every refresh. */
fun fallbackTip(nowMillis: Long): String {
    val day = (nowMillis / MILLIS_PER_DAY).toInt()
    return FALLBACK_TIPS[Math.floorMod(day, FALLBACK_TIPS.size)]
}

/**
 * Name used in "Hello <name>": the display name, otherwise the part of the
 * e-mail before "@" (capitalised), otherwise [DEFAULT_USER_NAME] (guests).
 */
fun greetingName(displayName: String?, email: String?): String {
    displayName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() }?.let {
        return it
    }

    val fromEmail = email
        ?.substringBefore("@")
        ?.split('.', '_', '-', '+')
        ?.firstOrNull()
        ?.trim()
        .orEmpty()

    return if (fromEmail.isNotEmpty()) {
        fromEmail.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    } else {
        DEFAULT_USER_NAME
    }
}

/**
 * Short "when" label of a manual: "Just now", "5 min ago", "2h ago",
 * "Yesterday", "3 days ago", then the date (e.g. "12 Oct").
 */
fun relativeTimeLabel(createdAtMillis: Long?, nowMillis: Long): String? {
    if (createdAtMillis == null) return null

    val minutes = (nowMillis - createdAtMillis).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "${hours}h ago"
        days < 2 -> "Yesterday"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(createdAtMillis))
    }
}
