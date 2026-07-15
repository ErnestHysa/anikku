package app.anikku.macos.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Cached source health status.
 * Health checks are performed once and cached per source ID for the session
 * (the process lifetime). To force a refresh, clear the cache by calling
 * [clearCache] or restart the app.
 */
object SourceHealthChecker {

    /** Health status of a source. */
    enum class Health {
        /** Source returned browse results within timeout — fully working. */
        WORKING,
        /** Source has known issues (DNS, Cloudflare, API changed, etc.). */
        FAILING,
        /** Health check not yet performed. */
        UNKNOWN,
    }

    /**
     * Optional extended diagnostic info when a source is failing.
     * Mirrors the categories from [app.anikku.macos.ui.screens.player.PlayerScreen.ErrorDiagnostic.ErrorCategory].
     */
    data class HealthResult(
        val status: Health,
        val category: String? = null,   // e.g. "DNS", "SSL", "403", "Timeout"
        val detail: String? = null,      // e.g. "UnknownHostException: hexawatch.tv"
        val cachedAt: Long = System.currentTimeMillis(),
    )

    private val healthCache = ConcurrentHashMap<Long, HealthResult>()

    private const val TIMEOUT_MS = 8_000L

    /**
     * Get the cached health result for a source, or UNKNOWN if not yet checked.
     */
    fun getHealth(sourceId: Long): HealthResult {
        return healthCache[sourceId] ?: HealthResult(Health.UNKNOWN)
    }

    /**
     * Perform a health check for the given source and cache the result.
     * This is a suspend function — call it from a coroutine.
     *
     * The check is lightweight: calls [CatalogueSource.getPopularAnime] with a
     * timeout. If that succeeds, the source is marked as WORKING. If the call
     * is not a [CatalogueSource], the source is marked as UNKNOWN.
     */
    suspend fun checkHealth(source: Source): HealthResult {
        val sourceId = source.id

        // Return cached result if available
        healthCache[sourceId]?.let {
            if (it.status != Health.UNKNOWN) return it
        }

        if (source !is CatalogueSource) {
            val result = HealthResult(Health.UNKNOWN)
            healthCache[sourceId] = result
            return result
        }

        return try {
            val startTime = System.currentTimeMillis()
            val page = withContext(Dispatchers.IO) {
                withTimeout(TIMEOUT_MS) {
                    if (source.supportsLatest) {
                        // For sources that support latest updates, try that (faster path)
                        source.getLatestUpdates(page = 1)
                    } else {
                        source.getPopularAnime(page = 1)
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            val count = page.animes.filter { a ->
                try { a.title.isNotBlank() } catch (_: Exception) { false }
            }.size

            val result = if (count > 0) {
                HealthResult(Health.WORKING, cachedAt = System.currentTimeMillis())
            } else {
                HealthResult(Health.FAILING, "Empty", "Returned 0 results in ${elapsed}ms")
            }
            healthCache[sourceId] = result
            result
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val result = HealthResult(Health.FAILING, "Timeout", "Request timed out after ${TIMEOUT_MS}ms")
            healthCache[sourceId] = result
            result
        } catch (e: Exception) {
            val msg = e.message ?: ""
            val category = when {
                msg.contains("Unable to resolve", ignoreCase = true) ||
                    e is java.net.UnknownHostException -> "DNS"
                e is java.net.SocketTimeoutException -> "Timeout"
                e is javax.net.ssl.SSLException -> "SSL"
                msg.contains("403") || msg.contains("forbidden", ignoreCase = true) -> "403"
                msg.contains("404") || msg.contains("not found", ignoreCase = true) -> "404"
                msg.contains("5") && (msg.contains("server") || msg.contains("gateway")) -> "5xx"
                msg.contains("cloudflare", ignoreCase = true) ||
                    msg.contains("cf-", ignoreCase = true) -> "Cloudflare"
                else -> "Error"
            }
            val detail = "${e.javaClass.simpleName}: ${msg.take(60)}"
            logger.debug { "Health check for source #$sourceId: $category — $detail" }
            val result = HealthResult(Health.FAILING, category, detail)
            healthCache[sourceId] = result
            result
        }
    }

    /**
     * Clear the health cache — forces re-check on next access.
     */
    fun clearCache() {
        healthCache.clear()
    }
}

/**
 * A small colored dot that indicates the health of a source.
 *
 * - 🟢 GREEN: Working (browse returns results)
 * - 🔴 RED: Failing (error or timeout)
 * - ⚫ GRAY: Unknown (not yet checked)
 *
 * The health check runs once (as a side effect) and is cached for the session.
 *
 * @param source The source to check.
 * @param modifier Optional modifier for positioning.
 */
@Composable
fun SourceHealthBadge(
    source: Source,
    modifier: Modifier = Modifier,
) {
    // Show cached result immediately, then re-check in background
    var healthResult by remember(source.id) {
        mutableStateOf(SourceHealthChecker.getHealth(source.id))
    }

    // Perform health check once per source per session
    LaunchedEffect(source.id) {
        healthResult = SourceHealthChecker.checkHealth(source)
    }

    val color = when (healthResult.status) {
        SourceHealthChecker.Health.WORKING -> Color(0xFF4ECCA3)  // green
        SourceHealthChecker.Health.FAILING -> Color(0xFFE94560)  // red
        SourceHealthChecker.Health.UNKNOWN -> Color(0xFF666666)  // gray
    }

    val size = when (healthResult.status) {
        SourceHealthChecker.Health.WORKING -> 8.dp
        SourceHealthChecker.Health.FAILING -> 8.dp
        SourceHealthChecker.Health.UNKNOWN -> 6.dp
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}
