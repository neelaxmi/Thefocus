package com.neelaxmi.focus.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.neelaxmi.focus.platform.AppCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalCatalog = compositionLocalOf<AppCatalog?> { null }

private object IconCache { val cache = LruCache<String, ImageBitmap>(150) }

/** Real launcher icon of an installed app, decoded off the main thread and cached. Falls back to a neutral tile. */
@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val catalog = LocalCatalog.current
    val bmpState = remember(pkg) { mutableStateOf(IconCache.cache.get(pkg)) }
    LaunchedEffect(pkg, catalog) {
        if (bmpState.value == null && catalog != null) {
            val loaded = withContext(Dispatchers.IO) {
                catalog.icon(pkg)?.toBitmap(96, 96)?.asImageBitmap()
            }
            bmpState.value = loaded
            if (loaded != null) IconCache.cache.put(pkg, loaded)
        }
    }
    val b = bmpState.value
    if (b != null) Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    else Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
}
