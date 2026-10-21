package io.github.alice060404.wereadlyrics

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RootAccessCheckerTest {
    @Test
    fun grantsOnlyWhenCommandSucceedsWithUidZero() {
        checker("printf '0\\n'").use { assertEquals(RootAccess.GRANTED, it.check()) }
    }

    @Test
    fun rejectsNonRootUidEvenWhenCommandSucceeds() {
        checker("printf '10000\\n'").use { assertEquals(RootAccess.NOT_GRANTED, it.check()) }
    }

    @Test
    fun rejectsUidZeroWhenCommandFails() {
        checker("printf '0\\n'; exit 1").use { assertEquals(RootAccess.NOT_GRANTED, it.check()) }
    }

    @Test
    fun missingSuIsUnavailableRatherThanGranted() {
        RootAccessChecker(startProcess = { throw IOException("missing su") }).use {
            assertEquals(RootAccess.UNAVAILABLE, it.check())
        }
    }

    @Test
    fun timedOutAuthorizationTerminatesTheProcess() {
        val process = ProcessBuilder("sh", "-c", "exec sleep 5").start()
        RootAccessChecker(timeoutMillis = 20, startProcess = { process }).use {
            assertEquals(RootAccess.TIMED_OUT, it.check())
        }
        process.waitFor()
        assertFalse(process.isAlive)
    }

    @Test
    fun closedCheckerDoesNotRequestAuthorization() {
        var started = false
        val checker = RootAccessChecker(startProcess = {
            started = true
            error("must not start")
        })
        checker.close()
        assertEquals(RootAccess.UNAVAILABLE, checker.check())
        assertFalse(started)
    }

    private fun checker(script: String) = RootAccessChecker(startProcess = {
        // These fixtures simulate su responses; tests never request real root.
        ProcessBuilder("sh", "-c", script).redirectErrorStream(true).start()
    })
}
