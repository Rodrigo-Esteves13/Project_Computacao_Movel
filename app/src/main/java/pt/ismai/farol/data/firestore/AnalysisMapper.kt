package pt.ismai.farol.data.firestore

import com.google.gson.Gson
import pt.ismai.farol.domain.model.AnalysisResult

/**
 * Converts an analysis to and from the map stored in Firestore. The queryable fields sit next to
 * the whole result serialized as JSON, so adding a field to a check never needs a migration.
 */
internal object AnalysisMapper {
    const val FIELD_TARGET = "target"
    const val FIELD_TYPE = "type"
    const val FIELD_SCORE = "score"
    const val FIELD_DATE = "date"
    const val FIELD_RESULT_JSON = "resultJson"

    private val gson = Gson()

    fun toMap(result: AnalysisResult): Map<String, Any?> = mapOf(
        FIELD_TARGET to result.target,
        FIELD_TYPE to result.type.name,
        FIELD_SCORE to result.score,
        FIELD_DATE to result.date,
        FIELD_RESULT_JSON to gson.toJson(result),
    )

    /** Null when the document is missing the JSON or it cannot be read (the document is skipped). */
    fun fromMap(id: Long, data: Map<String, Any?>): AnalysisResult? {
        val json = data[FIELD_RESULT_JSON] as? String ?: return null
        return runCatching { gson.fromJson(json, AnalysisResult::class.java) }.getOrNull()?.copy(id = id)
    }
}
