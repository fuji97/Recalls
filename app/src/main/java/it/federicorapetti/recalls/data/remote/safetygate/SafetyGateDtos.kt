package it.federicorapetti.recalls.data.remote.safetygate

import kotlinx.serialization.Serializable

@Serializable
data class SgPage(
    val content: List<SgNotification> = emptyList(),
    val last: Boolean = true
)
@Serializable
data class SgNotification(
    val id: Long,
    val reference: String? = null,
    val publicationDate: String = "",
    val product: SgProduct = SgProduct(),
    val risk: SgRisk? = null
)

@Serializable
data class SgProduct(
    val name: String? = null,
    val nameSpecific: String? = null,
    val brands: List<SgBrand> = emptyList(),
    val photos: List<SgPhoto> = emptyList(),
    val productCategory: SgNamed? = null,
    val versions: List<SgProductVersion> = emptyList(),
    val barcodes: List<SgBarcode> = emptyList(),
    val batchNumbers: List<SgBatchNumber> = emptyList(),
    val modelTypes: List<SgModelType> = emptyList()
)

@Serializable
data class SgBrand(val brand: String? = null)

@Serializable
data class SgPhoto(val id: Long? = null, val mainPicture: Boolean = false)

@Serializable
data class SgNamed(val name: String? = null)

@Serializable
data class SgProductVersion(
    val language: SgLanguage? = null,
    val name: String? = null,
    val description: String? = null,
    val packageDescription: String? = null
)

@Serializable
data class SgLanguage(val key: String? = null)

@Serializable
data class SgBarcode(val barcode: String? = null)

@Serializable
data class SgBatchNumber(val batchNumber: String? = null)

@Serializable
data class SgModelType(val modelType: String? = null)

@Serializable
data class SgRisk(
    val riskType: List<SgRiskType> = emptyList(),
    val versions: List<SgRiskVersion> = emptyList()
)

@Serializable
data class SgRiskType(val key: String? = null, val name: String? = null)

@Serializable
data class SgRiskVersion(
    val language: SgLanguage? = null,
    val riskDescription: String? = null,
    val legalProvision: String? = null
)

@Serializable
data class SgDetail(
    val country: SgNamed? = null,
    val product: SgProduct = SgProduct(),
    val risk: SgRisk? = null,
    val measureTaken: SgMeasureTaken? = null,
    val traceability: SgTraceability? = null,
    val onlineTraderProductIdentifierReference: List<SgOnlineTrader> = emptyList()
)

@Serializable
data class SgMeasureTaken(val measures: List<SgMeasure> = emptyList())

@Serializable
data class SgMeasure(
    val measureCategory: SgNamed? = null,
    val measureType: SgNamed? = null
)

@Serializable
data class SgTraceability(
    val countryOrigin: SgNamed? = null,
    val isSoldOnline: SgNamed? = null
)

@Serializable
data class SgOnlineTrader(
    val onlineTrader: String? = null,
    val uniqueProductIdentifier: String? = null
)
