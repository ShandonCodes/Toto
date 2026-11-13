package codes.shandon.toto

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

// Thin wrapper over the platform BiometricPrompt (minSdk 35, so no androidx.biometric needed).
// Strong biometrics with the device PIN/pattern/password as a fallback.
object AppLock {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val manager = context.getSystemService(BiometricManager::class.java)
        return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(activity: Activity, title: String, onResult: (success: Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                // Cancelled, locked out, etc. Individual failed attempts don't end the prompt.
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            }
        )
    }
}
