package it.federicorapetti.recalls.data.remote.salute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Splits [text] on single spaces into one [PdfTextItem] per word, mimicking
 * [OperatorPdfFieldsReader]'s word boundaries: character width is `0.5 × size`, each space
 * advances by `0.28 × size`, and `top`/`bottom` are derived from the baseline the same way the
 * reader derives them from [com.tom_roush.pdfbox.text.TextPosition].
 */
private fun line(text: String, left: Float, baseline: Float, size: Float = 10f): List<PdfTextItem> {
    val top = baseline - 0.75f * size
    val bottom = baseline + 0.2f * size
    var x = left
    val items = mutableListOf<PdfTextItem>()
    for (word in text.split(" ")) {
        val width = word.length * 0.5f * size
        items += PdfTextItem(word, x, top, x + width, bottom, isWidget = false)
        x += width + 0.28f * size
    }
    return items
}

private fun widget(value: String, left: Float, top: Float, right: Float, bottom: Float): PdfTextItem =
    PdfTextItem(value, left, top, right, bottom, isWidget = true)

/** The standard-layout (PDFsharp) label lines from case 1, reused by cases 3 and 6. */
private fun standardLabels(): List<PdfTextItem> =
    line("Data:", 36f, 149f) +
        line("Marchio del prodotto:", 171f, 149f) +
        line("Denominazione di vendita:", 36f, 176f) +
        line("Nome o ragione sociale dell'OSA", 36f, 201f) +
        line("a nome del quale il prodotto è", 36f, 213f) +
        line("commercializzato:", 36f, 225f) +
        line("Lotto di produzione:", 36f, 257f) +
        line("Marchio di identificazione dello stabilimento/del produttore:", 36f, 284f) +
        line("Nome del produttore:", 36f, 311f) +
        line("Sede dello stabilimento:", 36f, 347f) +
        line("Data di scadenza o termine minimo di conservazione:", 36f, 374f) +
        line("Descrizione peso/volume unità di vendita:", 36f, 401f) +
        line("Motivo del richiamo:", 40f, 435f) +
        line("Avvertenze:", 40f, 510f) +
        line("Inserire immagine uno:", 36f, 769f) +
        line("Inserire immagine due:", 324f, 769f)

class OperatorPdfParserTest {

    @Test
    fun `standard layout parses all 11 fields`() {
        val items = mutableListOf<PdfTextItem>()
        items += line("25/09/2026", 77f, 148f)
        items += line("HAMBURGER DI SCOTTONA - GRAN SELEZIONE", 308f, 148f)
        items += line("Data:", 36f, 149f)
        items += line("Marchio del prodotto:", 171f, 149f)
        items += line("HAMBURGER DI SCOTTONA - 200g (100X2) skin", 173f, 175f)
        items += line("Denominazione di vendita:", 36f, 176f)
        items += line("Nome o ragione sociale dell'OSA", 36f, 201f)
        items += line("Ambrosini Carni Srl", 204f, 212f)
        items += line("a nome del quale il prodotto è", 36f, 213f)
        items += line("commercializzato:", 36f, 225f)
        items += line("7726018994", 162f, 256f)
        items += line("Lotto di produzione:", 36f, 257f)
        items += line("IT 2874S UE", 342f, 283f)
        items += line("Marchio di identificazione dello stabilimento/del produttore:", 36f, 284f)
        items += line("Ambrosini Carni Srl", 161f, 310f)
        items += line("Nome del produttore:", 36f, 311f)
        items += line("Via San Domenico 62/64 - 24060 Brusaporto (BG)", 161f, 346f)
        items += line("Sede dello stabilimento:", 36f, 347f)
        items += line("Data di scadenza o termine minimo di conservazione:", 36f, 374f)
        items += line("30/09/2026 - 02-03/10/2026", 310f, 376f, 12f)
        items += line("200grammi (100x2)", 253f, 400f)
        items += line("Descrizione peso/volume unità di vendita:", 36f, 401f)
        items += line("Motivo del richiamo:", 40f, 435f)
        items += line("Presenza di Salmonella spp.", 40f, 451f)
        items += line("Avvertenze:", 40f, 510f)
        items += line("NON CONSUMARE IL PRODOTTO.", 40f, 526f)
        items += line("RESTITUIRE AL PUNTO VENDITA.", 40f, 540f)
        items += line("Inserire immagine uno:", 36f, 769f)
        items += line("Inserire immagine due:", 324f, 769f)

        val fields = OperatorPdfParser.parse(items)

        requireNotNull(fields)
        assertEquals("HAMBURGER DI SCOTTONA - 200g (100X2) skin", fields.productName)
        assertEquals("HAMBURGER DI SCOTTONA - GRAN SELEZIONE", fields.brand)
        assertEquals("Ambrosini Carni Srl", fields.operator)
        assertEquals("7726018994", fields.lot)
        assertEquals("IT 2874S UE", fields.plantMark)
        assertEquals("Ambrosini Carni Srl", fields.producer)
        assertEquals("Via San Domenico 62/64 - 24060 Brusaporto (BG)", fields.plantSite)
        assertEquals("30/09/2026 - 02-03/10/2026", fields.expiry)
        assertEquals("200grammi (100x2)", fields.quantity)
        assertEquals("Presenza di Salmonella spp.", fields.reasonDetail)
        assertEquals("NON CONSUMARE IL PRODOTTO. RESTITUIRE AL PUNTO VENDITA.", fields.warnings)
    }

    @Test
    fun `values offset above labels parse (PDFCreator)`() {
        val items = mutableListOf<PdfTextItem>()
        items += line("ELENKA SPA", 302f, 147f, 16f)
        items += line("Data:", 57f, 151f, 12f)
        items += line("Marchio del prodotto:", 163f, 151f, 12f)
        items += line("04/09/2026", 95f, 152f, 12f)
        items += line("IT 99 X", 365f, 326f, 12f)
        items += line("Marchio di identificazione dello stabilimento/del produttore:", 57f, 330f, 12f)
        items += line("ACME SRL", 176f, 363f, 12f)
        items += line("Nome del produttore:", 57f, 371f, 12f)
        items += line("Sede dello stabilimento:", 57f, 399f, 12f)
        // Minimal filler labels so the >=10-field-kind template gate passes.
        items += line("Denominazione di vendita:", 57f, 420f, 12f)
        items += line("Nome o ragione sociale dell'OSA", 57f, 440f, 12f)
        items += line("Lotto di produzione:", 57f, 460f, 12f)
        items += line("Descrizione peso/volume unità di vendita:", 57f, 480f, 12f)
        items += line("Motivo del richiamo:", 57f, 500f, 12f)
        items += line("Avvertenze:", 57f, 520f, 12f)
        items += line("Data di scadenza o termine minimo di conservazione:", 57f, 540f, 12f)

        val fields = OperatorPdfParser.parse(items)

        requireNotNull(fields)
        assertEquals("ELENKA SPA", fields.brand)
        assertEquals("IT 99 X", fields.plantMark)
        assertEquals("ACME SRL", fields.producer)
    }

    @Test
    fun `LiveCycle widget values land in the correct fields`() {
        val items = mutableListOf<PdfTextItem>()
        items += standardLabels()
        items += widget("PANINO AL LATTE 400g", 172f, 162f, 585f, 182f)
        items += widget("Rossi Alimentari Srl", 203f, 189f, 585f, 228f)
        items += widget("L2026091", 161f, 243f, 342f, 263f)
        items += widget("NON CONSUMARE IL PRODOTTO. RESTITUIRE AL PUNTO VENDITA.", 40f, 516f, 581f, 563f)
        items += widget("PANE FRESCO", 307f, 135f, 585f, 155f)

        val fields = OperatorPdfParser.parse(items)

        requireNotNull(fields)
        assertEquals("PANINO AL LATTE 400g", fields.productName)
        assertEquals("Rossi Alimentari Srl", fields.operator)
        assertEquals("L2026091", fields.lot)
        assertEquals("NON CONSUMARE IL PRODOTTO. RESTITUIRE AL PUNTO VENDITA.", fields.warnings)
        assertEquals("PANE FRESCO", fields.brand)
    }

    @Test
    fun `colon-less labels and split OSA label parse (FORMAZZA)`() {
        val items = mutableListOf<PdfTextItem>()
        items += line("Data: 27/08/2026", 36f, 148f)
        items += line("Marchio del prodotto:", 171f, 148f)
        items += line("FORMAZZA", 306f, 148f)
        items += line("BERTOLINO S.R.L.", 198f, 199f)
        items += line("Nome o ragione sociale", 36f, 200f)
        items += line("VIA DELLA TECNICA, 4", 198f, 211f)
        items += line("dell'OSA a nome del quale il", 36f, 212f)
        items += line("prodotto è", 36f, 224f)
        items += line("commercializzato:", 36f, 236f)
        // Remaining standard labels, at the template's usual positions.
        items += line("Denominazione di vendita:", 36f, 176f)
        items += line("Lotto di produzione:", 36f, 257f)
        items += line("Marchio di identificazione dello stabilimento/del produttore:", 36f, 284f)
        items += line("Nome del produttore:", 36f, 311f)
        items += line("Sede dello stabilimento:", 36f, 347f)
        items += line("Data di scadenza o termine minimo di conservazione VEDI ETICHETTA", 36f, 384f)
        items += line("Descrizione peso/volume unità di vendita:", 36f, 401f)
        items += line("Motivo del richiamo:", 40f, 435f)
        items += line("Avvertenze:", 40f, 510f)

        val fields = OperatorPdfParser.parse(items)

        requireNotNull(fields)
        assertEquals("BERTOLINO S.R.L. VIA DELLA TECNICA, 4", fields.operator)
        assertEquals("VEDI ETICHETTA", fields.expiry)
        assertEquals("FORMAZZA", fields.brand)
    }

    @Test
    fun `unknown template yields null`() {
        val items = line("Motivo del richiamo: PRESENZA DI CADMIO", 40f, 100f) +
            line("Avvertenze: NON CONSUMARE", 40f, 140f)

        assertNull(OperatorPdfParser.parse(items))
    }

    @Test
    fun `label phrase inside a paragraph is ignored, placeholder value is dropped`() {
        val items = mutableListOf<PdfTextItem>()
        items += line("25/09/2026", 77f, 148f)
        items += line("HAMBURGER DI SCOTTONA - GRAN SELEZIONE", 308f, 148f)
        items += line("Data:", 36f, 149f)
        items += line("Marchio del prodotto:", 171f, 149f)
        items += line("HAMBURGER DI SCOTTONA - 200g (100X2) skin", 173f, 175f)
        items += line("Denominazione di vendita:", 36f, 176f)
        items += line("Nome o ragione sociale dell'OSA", 36f, 201f)
        items += line("Ambrosini Carni Srl", 204f, 212f)
        items += line("a nome del quale il prodotto è", 36f, 213f)
        items += line("commercializzato:", 36f, 225f)
        items += line("7726018994", 162f, 256f)
        items += line("Lotto di produzione:", 36f, 257f)
        items += line("IT 2874S UE", 342f, 283f)
        items += line("Marchio di identificazione dello stabilimento/del produttore:", 36f, 284f)
        items += line("Ambrosini Carni Srl", 161f, 310f)
        items += line("Nome del produttore:", 36f, 311f)
        items += line("Via San Domenico 62/64 - 24060 Brusaporto (BG)", 161f, 346f)
        items += line("Sede dello stabilimento:", 36f, 347f)
        items += line("Data di scadenza o termine minimo di conservazione:", 36f, 374f)
        items += line("----------", 310f, 376f, 12f)
        items += line("200grammi (100x2)", 253f, 400f)
        items += line("Descrizione peso/volume unità di vendita:", 36f, 401f)
        items += line("Motivo del richiamo:", 40f, 435f)
        items += line("Presenza di Salmonella spp.", 40f, 451f)
        items += line("Avvertenze:", 40f, 510f)
        items += line("NON CONSUMARE IL PRODOTTO.", 40f, 526f)
        items += line("RESTITUIRE AL PUNTO VENDITA.", 40f, 540f)
        items += line("Lotto di produzione indicato sopra", 40f, 553f)
        items += line("Inserire immagine uno:", 36f, 769f)
        items += line("Inserire immagine due:", 324f, 769f)

        val fields = OperatorPdfParser.parse(items)

        requireNotNull(fields)
        assertEquals("7726018994", fields.lot)
        assertTrue(fields.warnings!!.endsWith("Lotto di produzione indicato sopra"))
        assertNull(fields.expiry)
    }
}
