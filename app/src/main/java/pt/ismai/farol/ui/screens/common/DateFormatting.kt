package pt.ismai.farol.ui.screens.common

import java.text.DateFormat
import java.util.Date

/** Human-readable date and time in the device format. */
fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
