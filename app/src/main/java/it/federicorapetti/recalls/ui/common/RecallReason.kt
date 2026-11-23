package it.federicorapetti.recalls.ui.common

import android.content.Context
import it.federicorapetti.recalls.data.local.RecallEntity
import it.federicorapetti.recalls.data.model.RecallSource

/** GLOSSARY: Reason. Null when the Recall states none. */
fun recallReason(context: Context, item: RecallEntity): String? = when (item.source) {
    RecallSource.SAFETY_GATE ->
        item.reason?.split(",")?.filter { it.isNotBlank() }?.joinToString(", ") { riskLabel(context, it) }
    RecallSource.IT_OPERATOR -> item.pdfFields?.reasonDetail ?: item.reason
    RecallSource.IT_MINISTRY -> item.reason
}?.takeIf { it.isNotBlank() }
