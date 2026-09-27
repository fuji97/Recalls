package it.federicorapetti.recalls.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
        val fields = cardFields(item)
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecallCardLeading(item)
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = sourceLabel(item.source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (item.isNew && !item.isRead) {
                    Badge()
                }
            }
            if (fields.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                CardFieldGrid(fields)
            }
        }
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

private data class CardField(val label: String, val value: String)

@Composable
private fun cardFields(item: RecallEntity): List<CardField> {
    val fields = when (item.source) {
        RecallSource.SAFETY_GATE -> {
            val riskTypes = item.reason
                ?.split(",")
                ?.filter { it.isNotBlank() }
                .orEmpty()
                .map { riskLabel(it) }
                .joinToString(", ")
            listOf(
                CardField(stringResource(R.string.detail_label_brand), item.brand.orEmpty()),
                CardField(stringResource(R.string.detail_label_risk_types), riskTypes),
                CardField(stringResource(R.string.detail_label_product), item.subtitle.orEmpty()),
                CardField(stringResource(R.string.detail_label_reference), item.reference.orEmpty())
            )
        }
        RecallSource.IT_OPERATOR -> listOf(
            CardField(stringResource(R.string.detail_label_brand), item.brand.orEmpty()),
            CardField(stringResource(R.string.detail_label_reason), item.reason.orEmpty())
        )
        RecallSource.IT_MINISTRY -> listOf(
            CardField(stringResource(R.string.detail_label_product), item.subtitle.orEmpty()),
            CardField(stringResource(R.string.detail_label_brand), item.brand.orEmpty()),
            CardField(stringResource(R.string.detail_label_hazard), item.reason.orEmpty()),
            CardField(stringResource(R.string.detail_label_country), item.country.orEmpty())
        )
    }
    return fields.filter { it.value.isNotBlank() }
}

@Composable
private fun CardFieldGrid(fields: List<CardField>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { CardFieldCell(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CardFieldCell(field: CardField, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun sourceLabel(source: RecallSource): String = when (source) {
    RecallSource.SAFETY_GATE -> stringResource(R.string.source_eu)
    RecallSource.IT_OPERATOR -> stringResource(R.string.source_it_operator)
    RecallSource.IT_MINISTRY -> stringResource(R.string.source_it_ministry)
}
