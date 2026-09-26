package it.federicorapetti.recalls.data.model

enum class RecallSource {
    SAFETY_GATE,
    IT_OPERATOR,
    IT_MINISTRY;

    val isItaly: Boolean get() = this != SAFETY_GATE
}
