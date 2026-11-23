# Operator recall PDFs are processed during sync

Every Operator recall PDF in the History window is downloaded once during sync and parsed. Its form fields are stored on the recall row and its photos as JPEGs in app storage, so cards, notifications and the detail screen work offline and the detail screen never downloads. The cost is about 100 PDF downloads on the first sync and roughly 80 KB per photo kept until the recall is Withdrawn. This was chosen over downloading on demand in the detail screen and in Coil.
