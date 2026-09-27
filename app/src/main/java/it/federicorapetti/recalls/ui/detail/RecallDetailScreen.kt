package it.federicorapetti.recalls.ui.detail

import android.content.Intent
import android.graphics.Bitmap
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.federicorapetti.recalls.R
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource
import it.federicorapetti.recalls.data.remote.safetygate.SafetyGateApi
import it.federicorapetti.recalls.data.remote.salute.ROME
import it.federicorapetti.recalls.ui.common.riskLabel
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun RecallDetailScreen(
    viewModel: RecallDetailViewModel,
    onBack: () -> Unit
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val sgState by viewModel.sgDetail.collectAsStateWithLifecycle()
    val pdfPhotosState by viewModel.pdfPhotos.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(item?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    val entity = item
                    if (entity != null) {
                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    context.getString(R.string.detail_share_text, entity.title, entity.webUrl)
                                )
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_share),
                                contentDescription = stringResource(R.string.action_share)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val entity = item
        if (entity == null) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                DetailHeader(entity, sgState, pdfPhotosState)
                Text(
                    text = entity.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = "${sourceLabelText(entity.source)} · ${formatDate(entity.publishedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = attributionText(entity.source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                Spacer(Modifier.height(8.dp))

                when (entity.source) {
                    RecallSource.SAFETY_GATE -> SafetyGateSections(sgState, onRetry = viewModel::retry)
                    RecallSource.IT_OPERATOR -> ItOperatorSections(entity)
                    RecallSource.IT_MINISTRY -> ItMinistrySections(entity)
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            CustomTabsIntent.Builder().build().launchUrl(context, entity.webUrl.toUri())
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.detail_open_official))
                    }
                    if (entity.attachmentUrl != null) {
                        FilledTonalButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, entity.attachmentUrl.toUri()))
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Icon(painterResource(R.drawable.ic_picture_as_pdf), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.detail_pdf_button))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DetailHeader(entity: RecallEntity, sgState: SgDetailState, pdfPhotosState: PdfPhotosState) {
    var imageFailed by remember(entity.imageUrl) { mutableStateOf(false) }
    when (entity.source) {
        RecallSource.SAFETY_GATE -> {
            val sgPhotos = (sgState as? SgDetailState.Loaded)?.detail?.product?.photos.orEmpty()
            when {
                sgState is SgDetailState.Loading -> CarouselSkeleton()
                sgPhotos.isNotEmpty() -> PhotoCarousel(count = sgPhotos.size) { index ->
                    val photoId = sgPhotos[index].id
                    if (photoId != null) {
                        AsyncImage(
                            model = SafetyGateApi.imageUrl(photoId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .maskClip(MaterialTheme.shapes.extraLarge)
                        )
                    }
                }
                else -> PlaceholderIcon()
            }
        }

        RecallSource.IT_OPERATOR -> when (pdfPhotosState) {
            PdfPhotosState.Loading -> CarouselSkeleton()
            is PdfPhotosState.Loaded -> {
                val pdfPhotos = pdfPhotosState.photos
                if (pdfPhotos.isNotEmpty()) {
                    PhotoCarousel(count = pdfPhotos.size) { index ->
                        Image(
                            bitmap = pdfPhotos[index].asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .maskClip(MaterialTheme.shapes.extraLarge)
                        )
                    }
                } else {
                    PlaceholderIcon()
                }
            }
        }

        RecallSource.IT_MINISTRY -> if (entity.imageUrl != null && !imageFailed) {
            AsyncImage(
                model = entity.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        } else {
            PlaceholderIcon()
        }
    }
}

/** A [HorizontalMultiBrowseCarousel] sized to match the detail header's photo slot. */
@Composable
private fun PhotoCarousel(count: Int, itemContent: @Composable CarouselItemScope.(Int) -> Unit) {
    val carouselState = rememberCarouselState { count }
    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 240.dp,
        itemSpacing = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(vertical = 8.dp),
        content = itemContent
    )
}

/** Pulsing placeholder shown in the detail header's photo slot while photos are still loading. */
@Composable
private fun CarouselSkeleton() {
    val transition = rememberInfiniteTransition(label = "photoSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "photoSkeletonAlpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .graphicsLayer { this.alpha = alpha }
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraLarge)
            )
        }
    }
}

/** The generic release-alert icon shown when a recall has no usable photo. */
@Composable
private fun PlaceholderIcon() {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .size(96.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialShapes.Cookie9Sided.toShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_release_alert),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
private fun SafetyGateSections(state: SgDetailState, onRetry: () -> Unit) {
    when (state) {
        SgDetailState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        is SgDetailState.Failed -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.detail_loading_error))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.detail_retry)) }
            }
        }

        is SgDetailState.Loaded -> {
            val detail = state.detail
            val lang = SafetyGateApi.contentLanguage()
            val version = detail.product.versions.firstOrNull { it.language?.key == lang }
                ?: detail.product.versions.firstOrNull()
            val riskVersion = detail.risk?.versions?.firstOrNull { it.language?.key == lang }
                ?: detail.risk?.versions?.firstOrNull()
            val brands = detail.product.brands.mapNotNull { it.brand }.filter { it.isNotBlank() }
            val modelTypes = detail.product.modelTypes.mapNotNull { it.modelType }.filter { it.isNotBlank() }
            val batchNumbers = detail.product.batchNumbers.mapNotNull { it.batchNumber }.filter { it.isNotBlank() }
            val barcodes = detail.product.barcodes.mapNotNull { it.barcode }.filter { it.isNotBlank() }
            val riskTypeNames = detail.risk?.riskType.orEmpty().mapNotNull { it.name }.filter { it.isNotBlank() }
            val riskTypeLabels = riskTypeNames.map { riskLabel(it) }
            val measures = detail.measureTaken?.measures.orEmpty().mapNotNull { measure ->
                val category = measure.measureCategory?.name
                val type = measure.measureType?.name
                when {
                    category != null && type != null -> "$category — $type"
                    category != null -> category
                    type != null -> type
                    else -> null
                }
            }
            val traders = detail.onlineTraderProductIdentifierReference.mapNotNull { trader ->
                val name = trader.onlineTrader
                val identifier = trader.uniqueProductIdentifier
                when {
                    name != null && identifier != null -> "$name: $identifier"
                    name != null -> name
                    else -> null
                }
            }

            DetailRow(stringResource(R.string.detail_label_product), version?.name)
            DetailRow(stringResource(R.string.detail_label_description), version?.description)
            DetailRow(stringResource(R.string.detail_label_category), detail.product.productCategory?.name)
            DetailRow(stringResource(R.string.detail_label_brand), joinOrNull(brands))
            DetailRow(stringResource(R.string.detail_label_model_type), joinOrNull(modelTypes))
            DetailRow(stringResource(R.string.detail_label_batch_numbers), joinOrNull(batchNumbers))
            DetailRow(stringResource(R.string.detail_label_barcodes), joinOrNull(barcodes))
            DetailRow(stringResource(R.string.detail_label_risk_types), joinOrNull(riskTypeLabels))
            DetailRow(stringResource(R.string.detail_label_risk_description), riskVersion?.riskDescription)
            DetailRow(stringResource(R.string.detail_label_legal_provision), riskVersion?.legalProvision)
            DetailRow(stringResource(R.string.detail_label_measures), measures.joinToString("\n").ifBlank { null })
            DetailRow(stringResource(R.string.detail_label_country), detail.country?.name)
            DetailRow(stringResource(R.string.detail_label_country_origin), detail.traceability?.countryOrigin?.name)
            DetailRow(stringResource(R.string.detail_label_sold_online), detail.traceability?.isSoldOnline?.name)
            DetailRow(stringResource(R.string.detail_label_online_traders), traders.joinToString("\n").ifBlank { null })
        }
    }
}

@Composable
private fun ItOperatorSections(entity: RecallEntity) {
    DetailRow(stringResource(R.string.detail_label_brand), entity.brand)
    DetailRow(stringResource(R.string.detail_label_reason), entity.reason)
    DetailRow(stringResource(R.string.detail_label_published), formatDate(entity.publishedAt))
}

@Composable
private fun ItMinistrySections(entity: RecallEntity) {
    DetailRow(stringResource(R.string.detail_label_product), entity.subtitle)
    DetailRow(stringResource(R.string.detail_label_brand), entity.brand)
    DetailRow(stringResource(R.string.detail_label_hazard), entity.reason)
    DetailRow(stringResource(R.string.detail_label_country), entity.country)
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    ListItem(
        headlineContent = { Text(value) },
        overlineContent = { Text(label) }
    )
}

private fun joinOrNull(values: List<String>): String? =
    values.takeIf { it.isNotEmpty() }?.joinToString(", ")

@Composable
private fun sourceLabelText(source: RecallSource): String = when (source) {
    RecallSource.SAFETY_GATE -> stringResource(R.string.source_eu)
    RecallSource.IT_OPERATOR -> stringResource(R.string.source_it_operator)
    RecallSource.IT_MINISTRY -> stringResource(R.string.source_it_ministry)
}

/** Per-source CC BY 4.0 attribution shown on every detail screen (see docs/LEGAL.md). */
@Composable
private fun attributionText(source: RecallSource): String = when (source) {
    RecallSource.SAFETY_GATE -> stringResource(R.string.detail_attribution_eu)
    RecallSource.IT_OPERATOR, RecallSource.IT_MINISTRY -> stringResource(R.string.detail_attribution_it)
}

@Composable
private fun formatDate(epochMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = Instant.ofEpochMilli(epochMs).atZone(ROME).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
}
