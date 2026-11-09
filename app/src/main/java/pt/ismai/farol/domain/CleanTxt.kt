package pt.ismai.farol.domain

/**
 * TXT from dns.google: removes quotes and joins the pieces of a split record.
 * `"a" "b"` -> `ab`; text without quotes stays the same.
 */
fun String.cleanTxt(): String =
    trim().split("\" \"").joinToString("") { it.trim('"') }
