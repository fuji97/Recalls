# Data sources (verified 2026-09-25)

## EU — Safety Gate (non-food)
- **List:** `POST https://ec.europa.eu/safety-gate-alerts/public/api/search`, `Content-Type: application/json`. Send **all** of these fields. A reduced body returned HTTP 405 in testing.
  ```json
  {"criteria":{"year":[2026,2025]},"searchCriteriaForNotification":false,"isLaunched":true,"isLaunchSearch":true,
   "pagination":{"sortField":"PUBLICATION_DATE","sortOrder":"DESC","totalElements":0,"numberElements":100,"page":0},
   "fullTextSearch":"","language":"it","displayDefaultResults":false,"isForMostRecent":false}
  ```
  The response is a Spring page: `{content:[…], last:Boolean, totalPages, totalElements, number}`. Each item has these fields:
  - `id` (Long) and `reference` (`"SR/02674/26"`).
  - `publicationDate` (`"2026-09-24T13:57:06.082+00:00"`, ISO offset).
  - `product{ name (localized generic name, e.g. "Maschera respiratoria"), nameSpecific, brands[{brand}], photos[{id, mainPicture}] }`.
  - `risk{ riskType[{key:"riskType.chemical", name:"CHEMICAL"}] }`.

  Volume: about 100 alerts every 2–3 days, and 2806 in 2026 up to 24 Sep. Without `criteria.year` the sort order breaks, so always send years.
- **Detail:** `GET https://ec.europa.eu/safety-gate-alerts/public/api/notification/{id}?language={lang}` returns:
  - `country{name}`
  - `product{ productCategory{name}, versions[{language{key}, name, description, packageDescription}], barcodes[{barcode}], batchNumbers[{batchNumber}], modelTypes[{modelType}], brands[{brand}], photos[{id, mainPicture}] }`
  - `risk{ riskType[…], versions[{language{key}, riskDescription, legalProvision}] }`
  - `measureTaken{ measures[{ measureCategory{name}, measureType{name} }] }`
  - `traceability{ countryOrigin{name}, isSoldOnline{name} }`
  - `onlineTraderProductIdentifierReference[{onlineTrader, uniqueProductIdentifier}]`
- **Images:**
  - Thumbnail: `https://ec.europa.eu/safety-gate-alerts/public/api/notification/thumbnail/{photoId}`
  - Full size: `…/public/api/notification/image/{photoId}`

  Both return JPEG.
- **Web page:** `https://ec.europa.eu/safety-gate-alerts/screen/webReport/alertDetail/{id}?lang={lang}`.
- **Risk-type enum** (from `public/api/enum/list`): ASPHYXIATION, BURNS, CHEMICAL, CHOKING, CUTS, DAMAGE_TO_HEARING, DAMAGE_TO_SIGHT, DROWNING, ELECTRIC_SHOCK, ELECTROMAGNETIC_DISTURBANCE, ENERGY_CONSUMPTION, ENTRAPMENT, ENVIRONMENT, FIRE, HEALTH_RISK_OTHER, INJURIES, MEASUREMENT_INCORRECT, MICROBIOLOGICAL, SECURITY, STRANGULATION, SUFFOCATION, OTHER.

## Italy — Ministero della Salute (`www.salute.gov.it`, Gcore bot shield)
- **Every request to this host MUST send the full desktop Chrome UA** `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36`. With the OkHttp UA the site answers 403 with a challenge page, and that includes images.
- Old `/portale/...` URLs return 404. The new portal is a Gatsby site under `/new/`.
- **Operator recalls, full dataset since 2017:** `GET https://www.salute.gov.it/new/page-data/it/avvisi/avvisi-e-richiami-di-prodotti-alimentari/page-data.json`.
  - Size: 13.8 MB raw, 2.5 MB gzip (OkHttp gunzips transparently).
  - Sends an `ETag` header. `If-None-Match` returns **304**.
  - Records are at `result.data.extSicurezzaAlimentare.nodes[]` (3933 nodes, newest first). Each node has:
    - `id` (uuid) and `path.alias` (`"/ext-avviso-sicurezza-alimentare/filetti-di-alici-olio"`).
    - `dataPubblicazione` (`"24/09/2026"`).
    - `field_depubblicato` (Boolean; 723 are true, meaning withdrawn from the site).
    - `field_marca`, `title`.
    - `relationships.field_motivo_segnalazione.name`, e.g. "Richiamo per rischio chimico", "Richiamo per rischio microbiologico", "Richiamo per rischio presenza di allergeni", "Richiamo per rischio fisico". Revocations start with "Revoca", e.g. "Revoca per scadenza prodotto".
    - `relationships.field_file_allegato[{filemime, filename, url}]`, where `url` is relative (`/sites/default/files/external_data/...`).
  - The same JSON also holds large unrelated arrays (`avvisiDiSicurezza`, …). They must be skipped while streaming.
  - PDF URL = `https://www.salute.gov.it/new` + `url`. This is verified. Without `/new` the URL returns 404.
  - Web page URL = `https://www.salute.gov.it/new/it` + `path.alias`.
- **Ministry warnings (rare, 20 items back to 2021):**
  - RSS: `https://www.salute.gov.it/new/rss/RSS_avvisi_sicurezza_alimentare.xml`. Each item has `title`, `link` (`https://www.salute.gov.it/new/it/avvisi-sicurezza-alimentare/{slug}`), `description` (`"Prodotto: X  Marca: Y"`) and `pubDate` (`"19/11/2025"`, dd/MM/yyyy).
  - Enrichment: `https://www.salute.gov.it/new/page-data/it/avvisi-sicurezza-alimentare/{slug}/page-data.json`, then read `result.data.node{ field_prodotto, field_sostanza (hazard), field_marca, field_nazione, relationships.field_images[{uri{url}}] }`.
  - Image URL = `https://www.salute.gov.it/new` + `uri.url`.
