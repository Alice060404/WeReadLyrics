package io.github.alice060404.wereadlyrics.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackStateMachineTest {
    @Test
    fun prefetchedTextDoesNotReplaceCurrentPlayback() {
        val machine = playingMachine()

        machine.accept(TextPrepared(SESSION, 4, "next", "next text"))

        assertEquals("current", machine.snapshot().current?.itemId)
        assertEquals("current text", machine.snapshot().current?.text)
        assertEquals(1, machine.snapshot().preparedCount)
    }

    @Test
    fun outOfOrderEventDoesNotMoveRangeBackward() {
        val machine = playingMachine()
        machine.accept(RangeAdvanced(SESSION, 5, "current", 20, 30))

        machine.accept(RangeAdvanced(SESSION, 4, "current", 10, 15))

        assertEquals(20, machine.snapshot().current?.sourceStart)
        assertEquals(5, machine.snapshot().lastSequence)
    }

    @Test
    fun pausedPlaybackIgnoresProgressUntilResume() {
        val machine = playingMachine()
        machine.accept(Paused(SESSION, 4))
        machine.accept(RangeAdvanced(SESSION, 5, "current", 20, 30))

        assertEquals(PlaybackStatus.PAUSED, machine.snapshot().status)
        assertEquals(0, machine.snapshot().current?.sourceStart)

        machine.accept(Resumed(SESSION, 6))
        machine.accept(RangeAdvanced(SESSION, 7, "current", 20, 30))

        assertEquals(PlaybackStatus.PLAYING, machine.snapshot().status)
        assertEquals(20, machine.snapshot().current?.sourceStart)
    }

    @Test
    fun seekClearsOldTextAndPrefetch() {
        val machine = playingMachine()
        machine.accept(TextPrepared(SESSION, 4, "next", "next text"))

        machine.accept(Seeked(SESSION, 5))

        assertEquals(PlaybackStatus.BUFFERING, machine.snapshot().status)
        assertNull(machine.snapshot().current)
        assertEquals(0, machine.snapshot().preparedCount)
    }

    @Test
    fun chapterChangeClearsOldChapterState() {
        val machine = playingMachine()

        machine.accept(ChapterChanged(SESSION, 4, "chapter-2"))

        assertEquals("chapter-2", machine.snapshot().chapterId)
        assertEquals(PlaybackStatus.BUFFERING, machine.snapshot().status)
        assertNull(machine.snapshot().current)
    }

    @Test
    fun stopClearsVisibleAndPreparedText() {
        val machine = playingMachine()
        machine.accept(TextPrepared(SESSION, 4, "next", "next text"))

        machine.accept(Stopped(SESSION, 5))

        assertEquals(PlaybackStatus.IDLE, machine.snapshot().status)
        assertNull(machine.snapshot().current)
        assertEquals(0, machine.snapshot().preparedCount)
    }

    @Test
    fun newerSessionRejectsDelayedEventsFromOlderSession() {
        val machine = playingMachine()
        machine.accept(SessionStarted(SESSION + 1, 1, "chapter-2"))

        machine.accept(TextPrepared(SESSION, 20, "stale", "stale text"))
        machine.accept(PlaybackStarted(SESSION, 21, "stale", 0, 9))

        assertEquals(SESSION + 1, machine.snapshot().sessionId)
        assertEquals("chapter-2", machine.snapshot().chapterId)
        assertNull(machine.snapshot().current)
    }

    @Test
    fun preparedQueueRemainsBounded() {
        val machine = PlaybackStateMachine(preparedLimit = 2)
        machine.accept(SessionStarted(SESSION, 1))

        machine.accept(TextPrepared(SESSION, 2, "one", "one"))
        machine.accept(TextPrepared(SESSION, 3, "two", "two"))
        machine.accept(TextPrepared(SESSION, 4, "three", "three"))
        machine.accept(PlaybackStarted(SESSION, 5, "one", 0, 2))

        assertNull(machine.snapshot().current)
        assertEquals(2, machine.snapshot().preparedCount)
    }

    @Test
    fun invalidRangeDoesNotDiscardPreparedText() {
        val machine = PlaybackStateMachine()
        machine.accept(SessionStarted(SESSION, 1))
        machine.accept(TextPrepared(SESSION, 2, "current", "current text"))

        machine.accept(PlaybackStarted(SESSION, 3, "current", 10, 5))
        machine.accept(PlaybackStarted(SESSION, 4, "current", 0, 11))

        assertEquals("current text", machine.snapshot().current?.text)
        assertEquals(PlaybackStatus.PLAYING, machine.snapshot().status)
    }

    @Test
    fun sameItemCanBePreparedAndPlayedAgain() {
        val machine = playingMachine()

        machine.accept(TextPrepared(SESSION, 4, "current", "replayed text"))
        machine.accept(PlaybackStarted(SESSION, 5, "current", 40, 52))

        assertEquals("replayed text", machine.snapshot().current?.text)
        assertEquals(40, machine.snapshot().current?.sourceStart)
    }

    private fun playingMachine(): PlaybackStateMachine = PlaybackStateMachine().apply {
        accept(SessionStarted(SESSION, 1, "chapter-1"))
        accept(TextPrepared(SESSION, 2, "current", "current text"))
        accept(PlaybackStarted(SESSION, 3, "current", 0, 11))
    }

    private companion object {
        const val SESSION = 10L
    }
}
