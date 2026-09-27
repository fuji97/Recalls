# Data sources: licences, legal notes and warnings

*Reviewed 2026-09-27. This is an informational summary, not legal advice.*

Recalls has no backend. Each installed copy fetches data directly from two public-sector sources, as described below.

## Summary

| Source | Operator | Licence | robots.txt | Verdict |
| --- | --- | --- | --- | --- |
| EU Safety Gate (`ec.europa.eu/safety-gate-alerts/public/api/...`) | European Commission, DG Justice and Consumers | **CC BY 4.0** (Commission reuse policy, Decision 2011/833/EU; data.europa.eu dataset metadata) | No rule covering `/safety-gate-alerts` | OK with attribution |
| Ministero della Salute (`www.salute.gov.it/new/...`: page-data JSON, RSS, images, PDFs) | Italian Ministry of Health | **CC BY 4.0** (site *Note legali*); also open by default under CAD art. 52 c.2 | `Allow: /new/` | OK with attribution. See the warnings on operator PDFs/photos and on the User-Agent |

## EU Safety Gate

- **Licence:** EU-owned content is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) ([Commission legal notice](https://commission.europa.eu/legal-notice_en)). The Safety Gate dataset on data.europa.eu is also listed as CC BY 4.0 (CC0 for some distributions).
- **Attribution:** give credit and indicate changes, e.g. *"Source: European Commission – Safety Gate, CC BY 4.0. Data reformatted by Recalls."*, with a link to the alert page.
- **Exclusions:** the licence does not cover trademarks, third-party works or identifiable private individuals. Brand logos visible in product photos are shown only to identify the recalled product.
- **API status:** `/public/api/search` and `/public/api/notification/{id}` are the website's own undocumented JSON API. Using it is lawful, but it can change or disappear without notice. The officially published machine-readable distribution is the weekly XML report (`https://ec.europa.eu/safety-gate-alerts/api/download/weeklyReport/list/xml/en`).
- **Responsibility:** the national authority that notifies an alert is responsible for its content, not the Commission and not this app.

## Ministero della Salute

- **Licence:** the site's *Note legali* (`https://www.salute.gov.it/new/it/altro/note-legali-2/`) says: *"I contenuti pubblicati sul presente sito www.salute.gov.it sono messi a disposizione con licenza CC-BY 4.0 – Attribuzione"*. Content may be redistributed and reused as long as the source is cited and, where possible, the original page URL is given. Independently, CAD (D.Lgs. 82/2005) art. 52 c.2 treats data a public administration publishes without a licence as open data, except personal data. The Open Data Directive (EU) 2019/1024 bars public bodies from using database rights to block reuse.
- **Attribution:** *"Fonte: Ministero della Salute – salute.gov.it, CC BY 4.0"*, with a link to the item's original page.
- **robots.txt:** `Allow: /new/`. Every path the app uses is open to automated clients.
- **Automated access:** *Note legali* says nothing about bots or scraping.

## Warnings

1. **Operator recall PDFs and their photos are third-party content.** *Note legali* says operators publish the food recalls, and that images, logos, trademarks and other third-party content *"non possono essere riprodotti senza il loro consenso"*. The PDFs, and the photos the app extracts from them, are therefore arguably **not** covered by CC BY. The risk is low because the photos are used only to identify a product under a public safety recall. Always show each extracted photo next to a link to the original PDF, and don't use them for anything else (icons, store listing, marketing).
2. **Browser User-Agent on `salute.gov.it`.** The app sends a desktop Chrome User-Agent because the site's Gcore bot shield returns 403 to other clients. Given `Allow: /new/` and the CC BY licence this is low risk, but it is still a disguise. The better long-term fix is to ask the Ministry to allowlist an honest UA, e.g. `Recalls/x.y (+https://github.com/fuji97/Recalls)`. If the site ever blocks the app, **do not escalate** (rotating UAs, headless browsers, solving JS challenges). That would turn a grey area into a clearly objectionable one.
3. **Non-commercial linking clause.** *Note legali* allows links to the portal only *"in formato testuale e per siti senza finalità commerciale"*. Recalls is **FOSS** (MIT-licensed, source public on GitHub) and **free, ad-free, with no monetisation planned** — no ads, no IAP, no paid tier — which fits the clause. Revisit this if that ever changes.
4. **Logos and endorsement.** Don't use the Ministry logo, the EU emblem or Safety Gate branding in the app, its icon or its store listing. Keep the *"independent, unofficial client"* notice. If the app is published on Google Play, its policy for apps showing government information also requires the store description to state non-affiliation and link the official sources.
5. **Personal data.** Recall notices name businesses. For sole traders, names can be personal data, and some PDFs include a contact person. Show only what identifies the product and business, and don't index PDF text beyond that. Hide or clearly mark items withdrawn by the Ministry (`field_depubblicato = true`) or revoked (`"Revoca…"`).
6. **Load and politeness.** The Italian operator dataset is about 13.8 MB (2.5 MB gzipped) per request. Keep the `ETag` / `If-None-Match` → 304 path working, keep sync intervals at a few hours or more, and back off on 403/429/5xx.
7. **Accuracy.** Both operators disclaim responsibility for downstream copies, and the Ministry states that the original texts prevail. Keep the *"always verify with the official source"* notice and the link to the original notice on every detail screen.

## Compliance checklist

*Checked 2026-09-27 against the current codebase.*

- [x] Detail screen: source name, licence (CC BY 4.0) and a link to the original page for every item. Source label + "Open official page" link were already present (`RecallDetailScreen.kt`); added the missing per-item CC BY 4.0 attribution line (`attributionText()`, `R.string.detail_attribution_eu` / `_it`).
- [x] Settings → Data sources: both operators, their licences and a *"data reformatted by Recalls"* note. Both source rows already linked out; added `R.string.settings_source_licence_note` ("CC BY 4.0 · Data reformatted by Recalls") as `supportingContent` on each.
- [x] Extracted PDF photos are always shown next to a link to the original PDF. `RecallDetailViewModel` only fetches PDF photos when `entity.attachmentUrl != null`, and the same field gates the "Recall notice (PDF)" button in `RecallDetailScreen`, so the two are always shown together.
- [x] Withdrawn and revoked items are hidden or clearly marked. `RecallRepository.syncItOperator()` drops `field_depubblicato` nodes before storing and deletes them if already present; `isRevocation` (motivo starting with "Revoca") renders a "Revoked" chip on `RecallCard`. Ministry warnings have no depubblicato-equivalent field to check (confirmed against `docs/DATA_SOURCES.md`).
- [x] ETag caching and error backoff are in place for `salute.gov.it`. `SaluteApi.fetchOperatorRecalls()` sends `If-None-Match` and handles HTTP 304; `SyncScheduler` sets `BackoffPolicy.EXPONENTIAL` (30 min) on the periodic work, and each source's failures are isolated per `RecallRepository.attempt()` so a 403/429/5xx on one source never triggers tight retries of the others.
- [x] No government or EU logos anywhere. The non-affiliation notice is in the README and any store listing. Checked `docs/icon.svg` and every `app/src/main/res/drawable*` asset — all generic Material icons, no EU/Ministry branding. README has the "independent, unofficial client" notice (`README.md` line 24). No store listing exists in this repo yet; add the same notice to it when one is created.
- [x] Before monetising: re-check the Ministry's non-commercial linking clause. **Not applicable today** — Recalls is FOSS (MIT) and no monetisation is planned; re-run this check only if that changes.
