package io.github.alice060404.wereadlyrics.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterProgressTest {
    private val adapter = ObservedPlaybackAdapter(1_000, 15_000)
    private val machine = PlaybackStateMachine()
    private var now = 0L

    private fun accept(event: ObservedPlaybackEvent) = adapter.accept(event).onEach(machine::accept)

    private fun start(itemId: String = "sentence", sourceStart: Int = 1_200) {
        accept(HighlightObserved(now++, itemId, "完整的长句字幕不会省略结尾", sourceStart, sourceStart + 12))
    }

    private fun progress(offset: Int, itemId: String = "sentence") =
        accept(CharacterProgressObserved(now++, itemId, offset))

    @Test
    fun localCharacterOffsetIsIndependentOfChapterRange() {
        start()
        progress(8)

        assertEquals(8, machine.snapshot().current?.characterOffset)
        assertEquals(1_200, machine.snapshot().current?.sourceStart)
        assertEquals("完整的长句字幕不会省略结尾", machine.snapshot().current?.text)
    }

    @Test
    fun pauseFreezesPositionAndResumeAcceptsNewProgress() {
        start()
        progress(4)
        accept(ControlObserved(now++, ControlAction.PAUSE))
        assertTrue(progress(9).isEmpty())
        assertEquals(4, machine.snapshot().current?.characterOffset)

        accept(ControlObserved(now++, ControlAction.RESUME))
        progress(9)
        assertEquals(9, machine.snapshot().current?.characterOffset)
    }

    @Test
    fun staleSentenceAndInvalidOffsetsCannotMoveCurrentText() {
        start()
        progress(5)
        assertTrue(progress(8, "previous").isEmpty())
        assertTrue(progress(-1).isEmpty())
        progress(100)
        assertEquals(5, machine.snapshot().current?.characterOffset)
    }

    @Test
    fun seekDiscardsLateProgressAndNewSentenceStartsAtZero() {
        start()
        progress(9)
        accept(ControlObserved(now++, ControlAction.SEEK))
        assertTrue(progress(10).isEmpty())
        start("next", 1_300)
        assertEquals(0, machine.snapshot().current?.characterOffset)
        progress(7, "next")
        assertEquals(7, machine.snapshot().current?.characterOffset)
    }

    @Test
    fun progressBeforeTextOrAfterStopIsIgnored() {
        assertTrue(progress(3).isEmpty())
        start()
        accept(ControlObserved(now++, ControlAction.STOP))
        adapter.flush(now + 1_000).forEach(machine::accept)
        now += 1_001
        assertTrue(progress(6).isEmpty())
        assertEquals(PlaybackStatus.IDLE, machine.snapshot().status)
    }

    @Test
    fun outOfOrderPositionCannotRewindPlayback() {
        start()
        progress(8)
        assertTrue(accept(CharacterProgressObserved(0, "sentence", 2)).isEmpty())
        assertEquals(8, machine.snapshot().current?.characterOffset)
    }
}
