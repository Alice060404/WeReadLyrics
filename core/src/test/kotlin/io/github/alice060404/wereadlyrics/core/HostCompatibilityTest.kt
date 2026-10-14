package io.github.alice060404.wereadlyrics.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostCompatibilityTest {
    @Test
    fun acceptsVerifiedWeReadVersion() {
        assertTrue(
            HostCompatibility.isSupported(
                HostCompatibility.SUPPORTED_PACKAGE,
                HostCompatibility.SUPPORTED_VERSION_CODE,
            ),
        )
    }

    @Test
    fun rejectsUnknownWeReadVersion() {
        assertFalse(
            HostCompatibility.isSupported(
                HostCompatibility.SUPPORTED_PACKAGE,
                HostCompatibility.SUPPORTED_VERSION_CODE + 1,
            ),
        )
    }

    @Test
    fun rejectsDifferentPackageWithMatchingVersion() {
        assertFalse(
            HostCompatibility.isSupported(
                "com.example.reader",
                HostCompatibility.SUPPORTED_VERSION_CODE,
            ),
        )
    }
}
