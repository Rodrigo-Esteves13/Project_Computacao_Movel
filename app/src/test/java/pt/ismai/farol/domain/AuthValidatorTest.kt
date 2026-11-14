package pt.ismai.farol.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {
    @Test fun email_acceptsNormalAddressesAndTrims() {
        assertTrue(AuthValidator.isEmailValid("rodrigo@example.com"))
        assertTrue(AuthValidator.isEmailValid("  a.b+c@sub.example.pt "))
    }

    @Test fun email_rejectsMalformedInput() {
        assertFalse(AuthValidator.isEmailValid(""))
        assertFalse(AuthValidator.isEmailValid("no-at-sign.com"))
        assertFalse(AuthValidator.isEmailValid("a@b"))
        assertFalse(AuthValidator.isEmailValid("a b@c.com"))
    }

    @Test fun password_needsSixCharacters() {
        assertFalse(AuthValidator.isPasswordValid("12345"))
        assertTrue(AuthValidator.isPasswordValid("123456"))
    }
}
