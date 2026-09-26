package it.federicorapetti.recalls.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.federicorapetti.recalls.R

private val RISK_LABEL_RES: Map<String, Int> = mapOf(
    "ASPHYXIATION" to R.string.risk_asphyxiation,
    "BURNS" to R.string.risk_burns,
    "CHEMICAL" to R.string.risk_chemical,
    "CHOKING" to R.string.risk_choking,
    "CUTS" to R.string.risk_cuts,
    "DAMAGE_TO_HEARING" to R.string.risk_damage_to_hearing,
    "DAMAGE_TO_SIGHT" to R.string.risk_damage_to_sight,
    "DROWNING" to R.string.risk_drowning,
    "ELECTRIC_SHOCK" to R.string.risk_electric_shock,
    "ELECTROMAGNETIC_DISTURBANCE" to R.string.risk_electromagnetic_disturbance,
    "ENERGY_CONSUMPTION" to R.string.risk_energy_consumption,
    "ENTRAPMENT" to R.string.risk_entrapment,
    "ENVIRONMENT" to R.string.risk_environment,
    "FIRE" to R.string.risk_fire,
    "HEALTH_RISK_OTHER" to R.string.risk_health_risk_other,
    "INJURIES" to R.string.risk_injuries,
    "MEASUREMENT_INCORRECT" to R.string.risk_measurement_incorrect,
    "MICROBIOLOGICAL" to R.string.risk_microbiological,
    "SECURITY" to R.string.risk_security,
    "STRANGULATION" to R.string.risk_strangulation,
    "SUFFOCATION" to R.string.risk_suffocation,
    "OTHER" to R.string.risk_other
)

/** "DAMAGE_TO_HEARING" -> "Damage to hearing" for any name without a dedicated string resource. */
fun humanizeEnum(name: String): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
fun riskLabel(name: String): String {
    val resId = RISK_LABEL_RES[name.uppercase()]
    return if (resId != null) stringResource(resId) else humanizeEnum(name)
}
