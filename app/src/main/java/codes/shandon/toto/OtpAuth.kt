package codes.shandon.toto

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

sealed interface OtpAuthResult {
    data class Valid(val account: Account) : OtpAuthResult

    // Not an otpauth URI, or missing/garbled required fields
    data object Invalid : OtpAuthResult

    // A real otpauth URI, but for codes we can't generate correctly (HOTP, 8 digits, 60s, SHA256...)
    data object Unsupported : OtpAuthResult
}

// Parses otpauth://totp/Issuer:username?secret=...&issuer=... from a QR code or an "Open with" link.
// Uses java.net.URI rather than android.net.Uri so it can be unit tested on the JVM.
object OtpAuth {
    private val BASE32 = Regex("^[A-Z2-7]+=*$")

    // 16 base32 characters = 80 bits, the shortest key services hand out in practice
    const val MIN_MANUAL_SECRET_LENGTH = 16

    enum class SecretProblem { BAD_CHARACTERS, TOO_SHORT }

    // Setup keys are often shown grouped ("jbsw y3dp ...") or with dashes; strip those and uppercase
    fun normalizeSecret(raw: String): String =
        raw.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    // Null when [normalized] is usable as a manually entered key
    fun secretProblem(normalized: String): SecretProblem? = when {
        !BASE32.matches(normalized) -> SecretProblem.BAD_CHARACTERS
        normalized.trimEnd('=').length < MIN_MANUAL_SECRET_LENGTH -> SecretProblem.TOO_SHORT
        else -> null
    }

    // An account typed in by hand: service name, optional account name, and the setup key
    fun manual(issuer: String, username: String, secret: String): OtpAuthResult {
        val name = issuer.trim()
        val key = normalizeSecret(secret)
        if (name.isEmpty() || secretProblem(key) != null) return OtpAuthResult.Invalid
        return OtpAuthResult.Valid(Account(issuer = name, username = username.trim(), secret = key))
    }

    fun parse(raw: String?): OtpAuthResult {
        if (raw.isNullOrBlank()) return OtpAuthResult.Invalid
        val uri = try {
            // Some generators put raw spaces in the label; URI would reject them
            URI(raw.trim().replace(" ", "%20"))
        } catch (e: URISyntaxException) {
            return OtpAuthResult.Invalid
        }
        if (!uri.scheme.equals("otpauth", ignoreCase = true)) return OtpAuthResult.Invalid
        when {
            uri.host.equals("totp", ignoreCase = true) -> Unit
            uri.host.equals("hotp", ignoreCase = true) -> return OtpAuthResult.Unsupported
            else -> return OtpAuthResult.Invalid
        }

        val params = parseQuery(uri.rawQuery)
        val secret = params["secret"]?.replace(" ", "")?.uppercase()
        if (secret.isNullOrEmpty() || !BASE32.matches(secret)) return OtpAuthResult.Invalid

        // We only generate the Google Authenticator defaults: SHA1, 6 digits, 30 seconds
        if (params["algorithm"]?.equals("SHA1", ignoreCase = true) == false) return OtpAuthResult.Unsupported
        if (params["digits"]?.let { it != "6" } == true) return OtpAuthResult.Unsupported
        if (params["period"]?.let { it != "30" } == true) return OtpAuthResult.Unsupported

        // The label is either "Issuer:username" or just "username"
        val label = uri.path.orEmpty().trimStart('/')
        val labelIssuer = label.substringBefore(':', missingDelimiterValue = "").trim()
        val username = label.substringAfter(':').trim()
        val issuer = params["issuer"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: labelIssuer.takeIf { it.isNotEmpty() }
            ?: username.takeIf { it.isNotEmpty() }
            ?: return OtpAuthResult.Invalid

        return OtpAuthResult.Valid(Account(issuer = issuer, username = username, secret = secret))
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', missingDelimiterValue = "")
            if (key.isEmpty()) null
            else URLDecoder.decode(key, Charsets.UTF_8).lowercase() to URLDecoder.decode(value, Charsets.UTF_8)
        }.toMap()
    }
}
