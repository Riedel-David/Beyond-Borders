package com.riedel.beyondborders.data.visa.update

/**
 * The public keys a published manifest.json may be signed with: ECDSA P-256, each one the
 * X.509 SubjectPublicKeyInfo in DER, base64 (one line, no PEM header).
 *
 * PLACEHOLDER: the list is empty until the owner generates the real key pair (docs/DATA.md,
 * "Signing the manifest"). An empty list means no key is configured, so every signature
 * fails and the updater downloads nothing: do not release an app with this list empty.
 *
 * Rotation: ship the new key here in an app release (old and new side by side), switch the
 * MANIFEST_SIGNING_KEY secret, and drop the old key in a later release.
 *
 * tools/visa/check_published.py reads the quoted strings out of this list, so keep one key
 * per line, in double quotes, and nothing else in the parentheses but comments.
 */
internal val MANIFEST_PUBLIC_KEYS: List<String> = listOf(
    // "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
)
