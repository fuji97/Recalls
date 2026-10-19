package it.federicorapetti.recalls.ui.detail

import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.federicorapetti.recalls.R

/** Button that opens the recall PDF at [url] in an external viewer. */
@Composable
internal fun PdfButton(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
        modifier = modifier
    ) {
        Icon(painterResource(R.drawable.ic_picture_as_pdf), contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.detail_pdf_button))
    }
}
