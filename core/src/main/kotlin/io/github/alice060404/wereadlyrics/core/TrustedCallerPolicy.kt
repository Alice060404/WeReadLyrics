package io.github.alice060404.wereadlyrics.core

class TrustedCallerPolicy(
    private val expectedPackage: String,
    expectedCertificateSha256: String,
) {
    private val expectedCertificateSha256 = expectedCertificateSha256.normalizedDigest()

    fun isTrusted(
        packagesForUid: Set<String>,
        certificateDigestsByPackage: Map<String, Set<String>>,
    ): Boolean {
        if (expectedPackage !in packagesForUid) return false
        return certificateDigestsByPackage[expectedPackage]
            .orEmpty()
            .any { it.normalizedDigest() == expectedCertificateSha256 }
    }

    private fun String.normalizedDigest(): String =
        filterNot { it == ':' }.lowercase()
}
