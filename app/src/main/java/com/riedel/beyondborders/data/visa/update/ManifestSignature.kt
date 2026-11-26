package com.riedel.beyondborders.data.visa.update

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** The detached signature published next to [MANIFEST_FILE]: base64 of a DER ECDSA signature over its exact bytes. */
const val SIGNATURE_FILE = "manifest.sig"
const val MAX_SIGNATURE_BYTES = 1024

/**
 * True only if [signatureBase64] is a valid ECDSA P-256 / SHA-256 signature over [manifest]
 * by at least one of [publicKeys] (base64 X.509 SubjectPublicKeyInfo). Anything wrong with
 * the signature or a key (bad base64, bad DER, wrong curve, no keys at all) is a plain
 * false; one unusable key never hides a usable one after it.
 */
internal fun verifyManifestSignature(manifest: ByteArray, signatureBase64: String, publicKeys: List<String>): Boolean {
    val sig = try {
        Base64.getDecoder().decode(signatureBase64.trim())
    } catch (e: IllegalArgumentException) {
        return false
    }
    if (sig.isEmpty()) return false
    return publicKeys.any { key ->
        try {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.trim())))
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(pub)
                update(manifest)
                verify(sig)
            }
        } catch (e: Exception) {
            false
        }
    }
}
