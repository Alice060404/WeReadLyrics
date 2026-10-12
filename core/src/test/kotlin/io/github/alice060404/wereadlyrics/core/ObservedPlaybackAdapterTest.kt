package io.github.alice060404.wereadlyrics.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObservedPlaybackAdapterTest {
    @Test
    fun highlightStartsSessionAndDisplaysObservedText() {
        val adapter = adapter()
        val machine = PlaybackStateMachine()

        adapter.accept(highlight(0, "one", "first", 0, 4, "chapter-1"))
            .forEach(machine::accept)

        assertEquals("first", machine.snapshot().current?.text)
        assertEquals("chapter-1", machine.snapshot().chapterId)
        assertEquals(PlaybackStatus.PLAYING, machine.snapshot().status)
    }

    @Test
    fun forwardRangeBecomesRangeAdvance() {
        val adapter = adapter()
        adapter.accept(highlight(0, "one", "first", 0, 4))

        val events = adapter.accept(highlight(100, "one", "first", 5, 9))

        assertEquals(1, events.size)
        assertIs<RangeAdvanced>(events.single())
    }

    @Test
    fun backwardRangeCreatesSeekAndFreshPlayback() {
        val adapter = adapter()
        adapter.accept(highlight(0, "later", "later text", 100, 109))

        val events = adapter.accept(highlight(100, "earlier", "earlier text", 20, 31))

        assertEquals(3, events.size)
        assertIs<Seeked>(events[0])
        assertIs<TextPrepared>(events[1])
        assertIs<PlaybackStarted>(events[2])
    }

    @Test
    fun immediateStopStartPairIsNormalizedAsSeek() {
        val adapter = adapter()
        adapter.accept(highlight(0, "one", "first", 0, 4))
        assertTrue(adapter.accept(ControlObserved(100, ControlAction.STOP)).isEmpty())

        val events = adapter.accept(ControlObserved(200, ControlAction.START))

        assertEquals(1, events.size)
        assertIs<Seeked>(events.single())
    }

    @Test
    fun p3PauseAndDelayedRestartCreatesNewSession() {
        val adapter = adapter()
        val machine = PlaybackStateMachine()
        adapter.accept(highlight(0, "one", "first", 0, 4)).forEach(machine::accept)
        adapter.accept(ControlObserved(100, ControlAction.PAUSE)).forEach(machine::accept)
        adapter.accept(HighlightCleared(105)).forEach(machine::accept)

        adapter.accept(ControlObserved(4_000, ControlAction.STOP)).forEach(machine::accept)
        adapter.accept(ControlObserved(4_001, ControlAction.START)).forEach(machine::accept)
        adapter.accept(highlight(4_100, "two", "second", 5, 10)).forEach(machine::accept)

        assertEquals(2L, machine.snapshot().sessionId)
        assertEquals("second", machine.snapshot().current?.text)
        assertEquals(PlaybackStatus.PLAYING, machine.snapshot().status)
    }

    @Test
    fun pauseExpiresToStopAndClearsText() {
        val adapter = adapter()
        val machine = PlaybackStateMachine()
        adapter.accept(highlight(0, "one", "first", 0, 4)).forEach(machine::accept)
        adapter.accept(ControlObserved(100, ControlAction.PAUSE)).forEach(machine::accept)

        adapter.flush(3_100).forEach(machine::accept)

        assertEquals(PlaybackStatus.IDLE, machine.snapshot().status)
        assertNull(machine.snapshot().current)
    }

    @Test
    fun chapterChangeClearsOldTextBeforeNewPlayback() {
        val adapter = adapter()
        val events = buildList {
            addAll(adapter.accept(highlight(0, "one", "first", 100, 110, "chapter-1")))
            addAll(adapter.accept(highlight(100, "two", "second", 0, 5, "chapter-2")))
        }

        assertTrue(events.any { it is ChapterChanged && it.chapterId == "chapter-2" })
        assertTrue(events.any { it is PlaybackStarted && it.itemId == "two" })
    }

    @Test
    fun outOfOrderObservationIsIgnored() {
        val adapter = adapter()
        adapter.accept(highlight(100, "one", "first", 0, 4))

        val events = adapter.accept(highlight(99, "two", "stale", 5, 9))

        assertTrue(events.isEmpty())
    }

    @Test
    fun delayedHighlightDoesNotCancelPauseTimeout() {
        val adapter = adapter()
        val machine = PlaybackStateMachine()
        adapter.accept(highlight(0, "one", "first", 0, 4)).forEach(machine::accept)
        adapter.accept(ControlObserved(100, ControlAction.PAUSE)).forEach(machine::accept)

        adapter.accept(highlight(200, "one", "first", 5, 9)).forEach(machine::accept)
        adapter.flush(3_100).forEach(machine::accept)

        assertEquals(PlaybackStatus.IDLE, machine.snapshot().status)
        assertNull(machine.snapshot().current)
    }

    @Test
    fun adjacentStartResumeProducesSingleResumeEvent() {
        val adapter = adapter()
        adapter.accept(highlight(0, "one", "first", 0, 4))
        adapter.accept(ControlObserved(100, ControlAction.PAUSE))

        val events = buildList {
            addAll(adapter.accept(ControlObserved(200, ControlAction.START)))
            addAll(adapter.accept(ControlObserved(201, ControlAction.RESUME)))
        }

        assertEquals(1, events.count { it is Resumed })
    }

    @Test
    fun invalidHighlightIsIgnoredWithoutStartingSession() {
        val adapter = adapter()

        val events = adapter.accept(highlight(0, "one", "first", 10, 5))

        assertTrue(events.isEmpty())
    }

    @Test
    fun firstHighlightFillsChapterMissingFromControlStart() {
        val adapter = adapter()
        val machine = PlaybackStateMachine()
        adapter.accept(ControlObserved(0, ControlAction.START)).forEach(machine::accept)

        adapter.accept(highlight(100, "one", "first", 0, 4, "chapter-1"))
            .forEach(machine::accept)

        assertEquals("chapter-1", machine.snapshot().chapterId)
        assertEquals("first", machine.snapshot().current?.text)
    }

    private fun adapter() = ObservedPlaybackAdapter(
        restartWindowMs = 1_000,
        idleStopTimeoutMs = 3_000,
    )

    private fun highlight(
        timeMs: Long,
        itemId: String,
        text: String,
        start: Int,
        end: Int,
        chapterId: String? = null,
    ) = HighlightObserved(timeMs, itemId, text, start, end, chapterId)
}
