package pt.ismai.farol.domain

import pt.ismai.farol.domain.model.FavoriteInfo

/** Decides what the background re-check of favorites tells the user. Pure, so it is easy to test. */
object FavoriteAlerts {
    private const val MAX_NAMES = 3

    /** Favorites whose latest analysis was made at or after [sinceMs] and differs from the previous one. */
    fun changedSince(favorites: List<FavoriteInfo>, sinceMs: Long): List<FavoriteInfo> =
        favorites.filter { it.changed && (it.date ?: 0L) >= sinceMs }

    /** One line for the notification. [changed] must not be empty. */
    fun message(changed: List<FavoriteInfo>): String {
        val first = changed.first()
        if (changed.size == 1) return singleLine(first)
        val names = changed.take(MAX_NAMES).joinToString(", ") { it.target }
        val more = if (changed.size > MAX_NAMES) " and ${changed.size - MAX_NAMES} more" else ""
        return "${changed.size} favorites changed: $names$more"
    }

    private fun what(f: FavoriteInfo): String =
        if (f.changedChecks.isEmpty()) "" else " (${f.changedChecks.joinToString(", ")})"

    private fun singleLine(f: FavoriteInfo): String {
        val now = f.score
        val before = f.previousScore
        return when {
            now != null && before != null && now != before -> "${f.target}: score went from $before to $now" + what(f)
            f.changedChecks.isNotEmpty() -> "${f.target}: changed: ${f.changedChecks.joinToString(", ")}"
            else -> "${f.target}: one of the checks changed"
        }
    }
}
