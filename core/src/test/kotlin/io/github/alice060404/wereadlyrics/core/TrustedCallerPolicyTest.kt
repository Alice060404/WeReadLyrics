package io.github.alice060404.wereadlyrics.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedCallerPolicyTest {
    private val policy = TrustedCallerPolicy(
        expectedPackage = "com.tencent.weread",
        expectedCertificateSha256 = "AA:BB:CC",
    )

    @Test
    fun acceptsExpectedPackageAndCertificate() {
        assertTrue(
            policy.isTrusted(
                packagesForUid = setOf("com.tencent.weread"),
                certificateDigestsByPackage = mapOf("com.tencent.weread" to setOf("aabbcc")),
            ),
        )
    }

    @Test
    fun rejectsMatchingPackageWithDifferentCertificate() {
        assertFalse(
            policy.isTrusted(
                packagesForUid = setOf("com.tencent.weread"),
                certificateDigestsByPackage = mapOf("com.tencent.weread" to setOf("001122")),
            ),
        )
    }

    @Test
    fun rejectsMatchingCertificateFromDifferentPackage() {
        assertFalse(
            policy.isTrusted(
                packagesForUid = setOf("example.attacker"),
                certificateDigestsByPackage = mapOf("example.attacker" to setOf("aabbcc")),
            ),
        )
    }
}
