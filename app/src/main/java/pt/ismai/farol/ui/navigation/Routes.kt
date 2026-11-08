package pt.ismai.farol.ui.navigation

import android.net.Uri

/** App routes. Only strings and ids are passed. */
object Routes {
    const val HOME = "home"
    const val NETWORK = "network"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val ARG_TARGET = "target"
    const val ARG_ANALYSIS_ID = "analysisId"
    const val ARG_CHECK = "check"
    const val NO_ANALYSIS = -1L

    const val RESULT = "result/{$ARG_TARGET}?$ARG_ANALYSIS_ID={$ARG_ANALYSIS_ID}"
    const val DETAIL = "detail/{$ARG_ANALYSIS_ID}/{$ARG_CHECK}"

    fun result(target: String, analysisId: Long = NO_ANALYSIS) =
        "result/${Uri.encode(target)}?$ARG_ANALYSIS_ID=$analysisId"

    fun detail(analysisId: Long, check: String) = "detail/$analysisId/${Uri.encode(check)}"
}
