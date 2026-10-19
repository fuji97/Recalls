# Recalls

An Android app that gathers official product-recall notices from EU and Italian authorities into one list, tracks what the user has seen, and notifies them of new ones.

## Language

### Items

**Recall**:
One entry in the unified list, whatever its Source. The umbrella term for a Safety Gate alert, an operator recall or a Ministry warning.
_Avoid_: Notice, item (in user-facing and domain language)

**Safety Gate alert**:
A Recall published by the EU Safety Gate system about a dangerous non-food product.
_Avoid_: Rapex alert

**Operator recall**:
A Recall filed with the Italian Ministry of Health by a food business operator, backed by a PDF form.
_Avoid_: Italian recall, IT recall

**Ministry warning**:
A Recall published by the Italian Ministry of Health itself as a food-safety warning, rather than filed by an operator.
_Avoid_: Ministry recall, avviso

### Origin

**Source**:
The official feed a Recall comes from: Safety Gate, Italian operator recalls, or Italian Ministry warnings.
_Avoid_: Feed, provider, origin

**Region**:
A group of Sources shown and notified together: EU (Safety Gate) or Italy (operator recalls and Ministry warnings).
_Avoid_: Country, market

### Lifecycle

**History window**:
The 90 days before a sync starts; Recalls published before it are not collected.
_Avoid_: Retention period, cutoff

**Baseline sync**:
The first successful sync of a Source, which fills the list silently without marking anything New.
_Avoid_: Initial sync, first sync

**New**:
Marked on a Recall that first appeared in a sync after its Source's baseline sync.
_Avoid_: Fresh, recent

**Read**:
Marked on a Recall once the user has opened it or marked everything read.
_Avoid_: Seen, viewed

**Unread**:
A Recall that is New and not yet Read; this is what badges, counts and the unread-only filter show.
_Avoid_: Unseen

**Revocation**:
An operator recall whose reason states that an earlier recall is lifted; it stays in the list, clearly marked.
_Avoid_: Cancellation, retraction

**Withdrawn**:
A Recall the Ministry has unpublished; it is removed from the list rather than marked.
_Avoid_: Deleted, unpublished

### Lookup

**Barcode index**:
The local mapping from Safety Gate alerts to their product barcodes, used by the scanner to find matching alerts.
_Avoid_: Barcode database
