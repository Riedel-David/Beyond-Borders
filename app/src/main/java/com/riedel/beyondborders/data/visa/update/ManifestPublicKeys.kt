package com.riedel.beyondborders.data.visa.update

/**
 * The public keys a published manifest.json may be signed with: ECDSA P-256, each one the
 * X.509 SubjectPublicKeyInfo in DER, base64 (one line, no PEM header).
 *
 * Generated 2026-11-26; the private half is the MANIFEST_SIGNING_KEY repository secret
 * (docs/DATA.md, "Signing the manifest"). An app holding a key rejects any manifest that is not
 * signed by it, so do not build a release from main until the live manifest.sig has been verified
 * against this key (rollout order in docs/DATA.md). An empty list would accept no signature at all.
 *
 * Rotation: ship the new key here in an app release (old and new side by side), switch the
 * MANIFEST_SIGNING_KEY secret, and drop the old key in a later release.
 *
 * tools/visa/check_published.py reads the quoted strings out of this list, so keep one key
 * per line, in double quotes, and nothing else in the parentheses but comments.
 */
internal val MANIFEST_PUBLIC_KEYS: List<String> = listOf(
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXObN9ybmHod5XCug3Jip6AOX2a/ud0t4xtzCdq4737TXQrC8ChqYi+xBmQdAOtGahMq1nAAdaho1DKjozCkD9A==",
)
