package com.justpass.app.data.auth

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Wrapper around Firebase Phone Auth. Used by tournament-creation flow to
 * verify that the phone number a user enters is theirs — protects the
 * approval queue from bot-submitted requests.
 *
 * Firebase Console prerequisites (one-time, manual):
 *   1. Authentication > Sign-in method > Phone > Enable.
 *   2. Project settings > Your apps > Add SHA-1 of release keystore (also
 *      debug keystore for testing). Without this, OTP requests silently
 *      fail with "App not authorized".
 *   3. Spark plan: 10 SMS/day per phone, 100/day project-wide. If you hit
 *      either, requests start being rejected. Upgrade to Blaze for higher.
 */
class PhoneAuthHelper(private val activity: Activity) {

    private val auth = FirebaseAuth.getInstance()

    interface Callback {
        fun onCodeSent(verificationId: String)
        fun onAutoVerified(credential: PhoneAuthCredential)
        fun onError(message: String)
    }

    /**
     * Request an OTP to be sent to [phoneE164]. Must be E.164 format
     * (e.g. "+919876543210"). On success, [callback.onCodeSent] is called
     * with a verificationId — pass that + the user-entered code into
     * [verifyCode] when the user submits.
     *
     * Some devices auto-detect incoming SMS and complete verification
     * without user interaction — that path fires [callback.onAutoVerified].
     * Treat both paths as equivalently successful.
     */
    fun sendOtp(phoneE164: String, callback: Callback) {
        if (phoneE164.isBlank() || !phoneE164.startsWith("+")) {
            callback.onError("Phone must include country code, e.g. +919876543210")
            return
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                callback.onAutoVerified(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "verification failed", e)
                val msg = when (e) {
                    is FirebaseAuthInvalidCredentialsException ->
                        "Invalid phone number format"
                    else -> e.message ?: "Failed to send OTP"
                }
                callback.onError(msg)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                callback.onCodeSent(verificationId)
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneE164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /**
     * Confirm the user-entered code against the verificationId returned by
     * [sendOtp]. Calls [onResult] with true if the credential is valid.
     */
    fun verifyCode(verificationId: String, code: String, onResult: (Boolean, String?) -> Unit) {
        if (code.length < 4) {
            onResult(false, "OTP too short")
            return
        }
        val credential = try {
            PhoneAuthProvider.getCredential(verificationId, code)
        } catch (e: Exception) {
            onResult(false, "Invalid code")
            return
        }
        // We're not actually signing-in with the credential — just using
        // PhoneAuthProvider.getCredential as a structural check that the
        // verificationId/code pair is well-formed. Firebase won't reject a
        // bad code at this point; the real check is via signInWithCredential.
        // We do that next so a wrong code surfaces as a verification error.
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    val msg = task.exception?.message ?: "Verification failed"
                    onResult(false, msg)
                }
            }
    }

    companion object { private const val TAG = "PhoneAuthHelper" }
}
