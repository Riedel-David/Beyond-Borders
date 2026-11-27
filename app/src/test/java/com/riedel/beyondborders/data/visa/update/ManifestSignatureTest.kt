package com.riedel.beyondborders.data.visa.update

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestSignatureTest {
    // Throwaway keys generated at test time; no real key lives in the repository.
    private fun newKey(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private fun pub(k: KeyPair) = Base64.getEncoder().encodeToString(k.public.encoded)
    private fun sign(k: KeyPair, bytes: ByteArray) = Base64.getEncoder().encodeToString(
        Signature.getInstance("SHA256withECDSA").run { initSign(k.private); update(bytes); sign() },
    )

    private val manifest = """{"manifestVersion":1}""".toByteArray()

    @Test
    fun `a valid signature is accepted`() {
        val k = newKey()
        assertTrue(verifyManifestSignature(manifest, sign(k, manifest), listOf(pub(k))))
    }

    @Test
    fun `a trailing newline in the signature file is accepted`() {
        val k = newKey()
        assertTrue(verifyManifestSignature(manifest, sign(k, manifest) + "\n", listOf(pub(k))))
    }

    @Test
    fun `a flipped byte in the manifest is rejected`() {
        val k = newKey()
        val sig = sign(k, manifest)
        val flipped = manifest.clone().also { it[3] = (it[3].toInt() xor 1).toByte() }
        assertFalse(verifyManifestSignature(flipped, sig, listOf(pub(k))))
    }

    @Test
    fun `a signature by another key is rejected`() {
        assertFalse(verifyManifestSignature(manifest, sign(newKey(), manifest), listOf(pub(newKey()))))
    }

    @Test
    fun `malformed base64 or an empty signature is rejected`() {
        val k = newKey()
        assertFalse(verifyManifestSignature(manifest, "%%% not base64", listOf(pub(k))))
        assertFalse(verifyManifestSignature(manifest, "", listOf(pub(k))))
        assertFalse(verifyManifestSignature(manifest, Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)), listOf(pub(k))))
    }

    @Test
    fun `an empty key list rejects everything`() {
        val k = newKey()
        assertFalse(verifyManifestSignature(manifest, sign(k, manifest), emptyList()))
    }

    @Test
    fun `a second key in the list is accepted for rotation and a broken key does not hide it`() {
        val old = newKey()
        val next = newKey()
        val sig = sign(next, manifest)
        assertTrue(verifyManifestSignature(manifest, sig, listOf(pub(old), pub(next))))
        assertTrue(verifyManifestSignature(manifest, sig, listOf("garbage", pub(next))))
    }

    @Test
    fun `the shipped key list holds only well-formed keys`() {
        MANIFEST_PUBLIC_KEYS.forEach { assertTrue(java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(it))) != null) }
    }
}
