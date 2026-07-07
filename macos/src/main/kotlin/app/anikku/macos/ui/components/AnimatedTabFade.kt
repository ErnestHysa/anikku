package app.anikku.macos.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Saveable-state-safe content fade transition.
 *
 * When [contentKey] changes, the content fades in (alpha 0 → 1) over 200ms.
 * Unlike [AnimatedContent][androidx.compose.animation.AnimatedContent] or
 * [Crossfade][androidx.compose.animation.Crossfade], this only ever has ONE
 * content composable in the tree at a time via [key], which avoids saveable
 * state key conflicts (e.g. with Voyager's [CurrentTab][cafe.adriel.voyager.navigator.tab.CurrentTab]).
 *
 * On the very first render, the content appears immediately (no fade-in).
 * Subsequent content switches trigger a smooth fade animation.
 *
 * ## Usage
 *
 * ```kotlin
 * AnimatedTabFade(tabKey = tabNavigator.current.key) {
 *     CurrentTab()
 * }
 * ```
 *
 * @param contentKey The unique key identifying the current content. When this
 *   changes, the old content is removed and the new content fades in.
 * @param durationMillis Duration of the fade animation in milliseconds.
 * @param content The composable content to render.
 */
@Composable
public fun AnimatedTabFade(
    contentKey: String,
    durationMillis: Int = 200,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    var isFirstRender by remember { mutableStateOf(true) }

    // When the content key changes (except the very first render), fade in
    LaunchedEffect(contentKey) {
        if (isFirstRender) {
            isFirstRender = false
            alpha.snapTo(1f) // First render appears immediately
        } else {
            alpha.snapTo(0f)
            alpha.animateTo(1f, animationSpec = tween(durationMillis = durationMillis))
        }
    }

    Box(modifier = Modifier.graphicsLayer { this.alpha = alpha.value }) {
        key(contentKey) {
            content()
        }
    }
}
