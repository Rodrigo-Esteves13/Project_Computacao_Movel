package pt.ismai.farol.ui.screens.common

import androidx.annotation.StringRes
import pt.ismai.farol.R
import pt.ismai.farol.domain.model.FarolException
import pt.ismai.farol.domain.model.ErrorType

/** Single error -> message mapping, used by all ViewModels. */
@StringRes
fun Throwable.messageRes(): Int = when ((this as? FarolException)?.type) {
    ErrorType.NO_INTERNET -> R.string.error_no_internet
    ErrorType.DOMAIN_NOT_FOUND -> R.string.error_domain
    ErrorType.RATE_LIMITED -> R.string.error_rate_limit
    ErrorType.INVALID_INPUT -> R.string.error_invalid
    ErrorType.UNKNOWN, null -> R.string.error_generic
}
