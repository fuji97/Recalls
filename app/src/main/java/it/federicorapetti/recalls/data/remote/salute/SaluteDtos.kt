package it.federicorapetti.recalls.data.remote.salute

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SalutePageData(val result: SaluteResult = SaluteResult())

@Serializable
data class SaluteResult(val data: SaluteData = SaluteData())

@Serializable
data class SaluteData(
    val extSicurezzaAlimentare: SaluteNodesWrapper = SaluteNodesWrapper()
)

@Serializable
data class SaluteNodesWrapper(val nodes: List<SaluteOperatorNode> = emptyList())

@Serializable
data class SaluteOperatorNode(
    val id: String,
    val path: SalutePath = SalutePath(),
    val dataPubblicazione: String = "",
    @SerialName("field_depubblicato") val depubblicato: Boolean = false,
    @SerialName("field_marca") val marca: String? = null,
    val title: String = "",
    val relationships: SaluteOperatorRelationships = SaluteOperatorRelationships()
)

@Serializable
data class SalutePath(val alias: String = "")

@Serializable
data class SaluteOperatorRelationships(
    @SerialName("field_motivo_segnalazione") val motivo: SaluteNamed? = null,
    @SerialName("field_file_allegato") val allegati: List<SaluteFile>? = null
)

@Serializable
data class SaluteNamed(val name: String? = null)

@Serializable
data class SaluteFile(
    val filemime: String? = null,
    val filename: String? = null,
    val url: String? = null
)

@Serializable
data class MinistryPageData(val result: MinistryResult = MinistryResult())

@Serializable
data class MinistryResult(val data: MinistryDataWrapper = MinistryDataWrapper())

@Serializable
data class MinistryDataWrapper(val node: MinistryNode = MinistryNode())

@Serializable
data class MinistryNode(
    @SerialName("field_prodotto") val fieldProdotto: String? = null,
    @SerialName("field_sostanza") val fieldSostanza: String? = null,
    @SerialName("field_marca") val fieldMarca: String? = null,
    @SerialName("field_nazione") val fieldNazione: String? = null,
    val relationships: MinistryRelationships = MinistryRelationships()
)

@Serializable
data class MinistryRelationships(
    @SerialName("field_images") val fieldImages: List<MinistryImage> = emptyList()
)

@Serializable
data class MinistryImage(val uri: MinistryImageUri = MinistryImageUri())

@Serializable
data class MinistryImageUri(val url: String? = null)
