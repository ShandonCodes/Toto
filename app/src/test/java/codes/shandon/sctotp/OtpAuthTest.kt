package codes.shandon.sctotp

import org.junit.Assert.assertEquals
import org.junit.Test

class OtpAuthTest {
    private fun valid(raw: String) = (OtpAuth.parse(raw) as OtpAuthResult.Valid).account

    @Test
    fun issuerParamAndLabel() {
        assertEquals(
            Account(issuer = "Google", username = "you@gmail.com", secret = "JBSWY3DPEHPK3PXP"),
            valid("otpauth://totp/Google:you@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Google")
        )
    }

    @Test
    fun issuerFromLabelWhenNoParam() {
        assertEquals("GitHub", valid("otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP").issuer)
    }

    @Test
    fun usernameOnlyLabelFallsBackToUsername() {
        val account = valid("otpauth://totp/totp@authenticationtest.com?secret=JBSWY3DPEHPK3PXP")
        assertEquals("totp@authenticationtest.com", account.issuer)
        assertEquals("totp@authenticationtest.com", account.username)
    }

    @Test
    fun decodesEncodedAndRawSpaces() {
        assertEquals("My Bank", valid("otpauth://totp/My%20Bank:me?secret=JBSWY3DPEHPK3PXP").issuer)
        assertEquals("My Bank", valid("otpauth://totp/My Bank:me?secret=JBSWY3DPEHPK3PXP").issuer)
    }

    @Test
    fun normalizesSecret() {
        assertEquals("JBSWY3DPEHPK3PXP", valid("otpauth://totp/X:y?secret=jbsw%20y3dp%20ehpk3pxp").secret)
    }

    @Test
    fun acceptsExplicitDefaults() {
        valid("otpauth://totp/X:y?secret=JBSWY3DPEHPK3PXP&algorithm=SHA1&digits=6&period=30")
    }

    @Test
    fun unsupportedVariants() {
        for (raw in listOf(
            "otpauth://hotp/X:y?secret=JBSWY3DPEHPK3PXP&counter=0",
            "otpauth://totp/X:y?secret=JBSWY3DPEHPK3PXP&digits=8",
            "otpauth://totp/X:y?secret=JBSWY3DPEHPK3PXP&period=60",
            "otpauth://totp/X:y?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256"
        )) {
            assertEquals(raw, OtpAuthResult.Unsupported, OtpAuth.parse(raw))
        }
    }

    @Test
    fun manualEntryNormalizesKey() {
        val account = (OtpAuth.manual(" My Bank ", " me ", "jbsw y3dp-ehpk 3pxp") as OtpAuthResult.Valid).account
        assertEquals(Account(issuer = "My Bank", username = "me", secret = "JBSWY3DPEHPK3PXP"), account)
    }

    @Test
    fun manualEntryRejectsBadInput() {
        assertEquals(OtpAuthResult.Invalid, OtpAuth.manual("", "me", "JBSWY3DPEHPK3PXP"))
        assertEquals(OtpAuthResult.Invalid, OtpAuth.manual("X", "", "JBSWY3DPEHPK3PX1"))
        assertEquals(OtpAuthResult.Invalid, OtpAuth.manual("X", "", "JBSWY3DP"))
    }

    @Test
    fun secretProblems() {
        assertEquals(null, OtpAuth.secretProblem("JBSWY3DPEHPK3PXP"))
        assertEquals(null, OtpAuth.secretProblem("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP===="))
        assertEquals(OtpAuth.SecretProblem.BAD_CHARACTERS, OtpAuth.secretProblem("JBSWY3DPEHPK3PX0"))
        assertEquals(OtpAuth.SecretProblem.TOO_SHORT, OtpAuth.secretProblem("JBSWY3DP"))
    }

    @Test
    fun invalidInputs() {
        for (raw in listOf(
            null,
            "",
            "https://example.com",
            "otpauth://totp/X:y",
            "otpauth://totp/X:y?secret=not-base32!",
            "otpauth://weird/X:y?secret=JBSWY3DPEHPK3PXP",
            "otpauth://totp/?secret=JBSWY3DPEHPK3PXP"
        )) {
            assertEquals(raw.toString(), OtpAuthResult.Invalid, OtpAuth.parse(raw))
        }
    }
}
