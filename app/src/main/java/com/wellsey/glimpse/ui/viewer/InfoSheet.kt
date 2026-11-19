package com.wellsey.glimpse.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wellsey.glimpse.data.MediaDetails
import com.wellsey.glimpse.data.MediaItem
import com.wellsey.glimpse.data.readDetails

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val details by produceState<MediaDetails?>(null, item.uri) {
        value = readDetails(context, item)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text("Details", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            val d = details
            if (d == null) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                d.rows.forEach { (label, value) ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                d.geo?.let { geo ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val uri = Uri.parse("geo:${geo.lat},${geo.lon}?q=${geo.lat},${geo.lon}")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }) {
                        Icon(Icons.Outlined.Place, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open in maps")
                    }
                }
            }
        }
    }
}
