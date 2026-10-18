package it.federicorapetti.recalls.data.model

enum class RecallSource {
    SAFETY_GATE,
    IT_OPERATOR,
    IT_MINISTRY;

    val region: Region get() = if (this == SAFETY_GATE) Region.EU else Region.ITALY
}
