package pt.ismai.farol.domain.model

/** State of a check. It is never conveyed by color alone: see StateIcon. */
enum class CheckState { OK, WARNING, FAIL, UNAVAILABLE }

enum class TargetType { DOMAIN, IP }

/** Stable check ids (used in routes and in the JSON stored in the DB). */
object CheckId {
    const val CERTIFICATE = "certificate"
    const val AGE = "age"
    const val HEADERS = "headers"
    const val EMAIL = "email"
    const val EXPOSURE = "exposure"
    const val IP_OWNER = "ip_owner"
}

/**
 * Result of a check.
 * [points] and [weight]: points earned and maximum weight (see ScoreCalculator).
 * If [state] == UNAVAILABLE the check does not count toward the score.
 */
data class Check(
    val id: String,
    val title: String,
    val state: CheckState,
    val summary: String,
    val detail: String,
    val points: Int = 0,
    val weight: Int = 0,
)

/** Target already validated and normalized (see TargetNormalizer). */
data class Target(val value: String, val type: TargetType)

/** Complete analysis. [id] = 0 if not yet saved in the DB. */
data class AnalysisResult(
    val target: String,
    val type: TargetType,
    val checks: List<Check>,
    val score: Int?,
    val date: Long,
    val id: Long = 0,
)

/** Emitted several times during an analysis (progressive UI). */
data class AnalysisProgress(
    val target: String,
    val type: TargetType,
    val completed: List<Check>,
    /** ids of the checks still running (spinner) */
    val pending: List<String>,
    /** only filled when [finished] */
    val score: Int?,
    val finished: Boolean,
    val fromCache: Boolean,
    /** id in the DB, only when [finished] */
    val analysisId: Long?,
)

/** History row. */
data class AnalysisSummary(
    val id: Long,
    val target: String,
    val type: TargetType,
    val score: Int?,
    val date: Long,
)

data class FavoriteInfo(
    val target: String,
    val score: Int?,
    val previousScore: Int?,
    /** true if the last recheck changed the score or the state of any check */
    val changed: Boolean,
    val date: Long?,
    val lastAnalysisId: Long?,
)

data class NetworkResult(
    val publicIp: String,
    /** "Wi-Fi", "Mobile data", "Other" or "No connection" */
    val connectionType: String,
    val ports: List<Int>,
    val cves: List<String>,
    val exposure: Check,
)

enum class ErrorType { NO_INTERNET, DOMAIN_NOT_FOUND, RATE_LIMITED, INVALID_INPUT, UNKNOWN }

/** The repositories throw this; the ViewModel catches it and maps it to an error string. */
class FarolException(val type: ErrorType, cause: Throwable? = null) : Exception(type.name, cause)

enum class ThemePref { SYSTEM, DARK, LIGHT }
