package it.federicorapetti.recalls.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.federicorapetti.recalls.R
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.ui.common.riskLabel

@Composable
fun RecallCard(item: RecallEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(onClick = onClick, modifier = modifier) {
        val supporting = supportingText(item)
        ListItem(
            headlineContent = {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = if (supporting.isNotBlank()) {
                { Text(supporting, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            } else {
                null
            },
            overlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(sourceLabel(item.source))
                    if (item.isRevocation) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = stringResource(R.string.revoked),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            leadingContent = { RecallCardLeading(item) },
            trailingContent = if (item.isNew && !item.isRead) {
                { Badge() }
            } else {
                null
            }
        )
    }
}

@Composable
private fun RecallCardLeading(item: RecallEntity) {
    var imageFailed by remember(item.imageUrl) { mutableStateOf(false) }
    if (item.imageUrl != null && !imageFailed) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { imageFailed = true },
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
        )
    } else {
        RecallPlaceholderIcon()
    }
}

@Composable
private fun RecallPlaceholderIcon() {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_release_alert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun supportingText(item: RecallEntity): String {
    val reasonText = if (item.source == RecallSource.SAFETY_GATE) {
        val names = item.reason?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        names.map { riskLabel(it) }.joinToString(", ").takeIf { it.isNotBlank() }
    } else {
        item.reason
    }
    return listOfNotNull(item.brand, reasonText).joinToString(" · ")
}

@Composable
private fun sourceLabel(source: RecallSource): String = when (source) {
    RecallSource.SAFETY_GATE -> stringResource(R.string.source_eu)
    RecallSource.IT_OPERATOR -> stringResource(R.string.source_it_operator)
    RecallSource.IT_MINISTRY -> stringResource(R.string.source_it_ministry)
}
