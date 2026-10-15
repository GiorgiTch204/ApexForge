package com.apexforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.apexforge.app.ui.initialsOf
import java.io.File

/** Round profile picture, falling back to the user's initials when no photo has been chosen. */
@Composable
fun Avatar(name: String, photoPath: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val file = remember(photoPath) { photoPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() } }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null) {
            val context = LocalContext.current
            // The photo is always saved to the same path, so the cache key includes the modified time.
            val request = remember(file.path, file.lastModified()) {
                ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("${file.path}-${file.lastModified()}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(request, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(initialsOf(name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
