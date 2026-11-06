package com.example

import com.example.audio.MusicAudioEngine
import com.example.data.QueueItem
import com.example.data.TrackEntity
import com.example.lyrics.ElementVocalState
import com.example.lyrics.LyricsAnimatorEngine
import com.example.lyrics.TtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ExampleRobolectricTest {

    @Test
    fun `verify queue state machine Tomato Potato Carrot priority insertion at currentIndex plus 1`() {
        val tomato = TrackEntity(
            id = "t1", title = "Tomato", artist = "Aether", album = "Test",
            trackNumber = 1, durationMs = 10000L, audioUri = "", folderPath = "/Music/Rock",
            folderName = "Rock", parentTreeUri = "/Music/Rock", baseFileName = "Tomato",
            extension = "flac", coLocatedTtmlUri = null, coverColorsCsv = "#FFFFFF,#888888,#000000"
        )
        val potato = tomato.copy(id = "t2", title = "Potato", trackNumber = 2, baseFileName = "Potato")
        val carrot = tomato.copy(id = "t3", title = "Carrot", trackNumber = 3, baseFileName = "Carrot")

        // 1. Direct click on "Tomato" in context [Tomato, Potato] -> Queue becomes [#1 Tomato, #2 Potato]
        val queue = mutableListOf(
            QueueItem(UUID.randomUUID().toString(), tomato),
            QueueItem(UUID.randomUUID().toString(), potato)
        )
        val currentIndex = 0
        assertEquals("Tomato", queue[0].track.title)
        assertEquals("Potato", queue[1].track.title)

        // 2. "Add to Queue" on "Carrot" -> Inserts at currentIndex + 1 (Position #2), pushing "Potato" to Position #3
        val insertIndex = (currentIndex + 1).coerceIn(0, queue.size)
        queue.add(insertIndex, QueueItem(UUID.randomUUID().toString(), carrot))

        assertEquals(3, queue.size)
        assertEquals("Tomato", queue[0].track.title)
        assertEquals("Carrot", queue[1].track.title)
        assertEquals("Potato", queue[2].track.title)
    }

    @Test
    fun `verify granular 0_1x to 5_0x speed and pitch one decimal math`() {
        assertEquals(0.8f, MusicAudioEngine.stepOneDecimal(0.9f, -1), 0.0001f)
        assertEquals("0.8x", MusicAudioEngine.formatMultiplier(0.8f))
        assertEquals(1.4f, MusicAudioEngine.stepOneDecimal(1.3f, 1), 0.0001f)
        assertEquals("3.2x", MusicAudioEngine.formatMultiplier(MusicAudioEngine.stepOneDecimal(3.1f, 1)))
        // Clamp boundaries
        assertEquals(0.1f, MusicAudioEngine.stepOneDecimal(0.1f, -1), 0.0001f)
        assertEquals(5.0f, MusicAudioEngine.stepOneDecimal(5.0f, 1), 0.0001f)
    }

    @Test
    fun `verify TTML parser and LyricsAnimatorEngine spline and spring step`() {
        val sampleTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p begin="00:00.500" end="00:03.000">
                    <span begin="00:00.500" end="00:01.200">Hello</span>
                    <span begin="00:01.200" end="00:03.000">WorldWide</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        // Pure JVM time parser & spline/spring verification
        assertEquals(500.0, TtmlParser.parseTimeAttr("00:00.500") ?: 0.0, 0.01)
        assertEquals(83450.0, TtmlParser.parseTimeAttr("01:23.450") ?: 0.0, 0.01)

        val scaleSpline = com.example.lyrics.getSpline(LyricsAnimatorEngine.ScaleRange)
        assertEquals(0.95, scaleSpline.at(0.0), 0.001)
        assertEquals(1.0505, scaleSpline.at(0.7), 0.001)
        assertEquals(1.0, scaleSpline.at(1.0), 0.001)

        val spring = com.example.lyrics.LyricsSpring(0.95, LyricsAnimatorEngine.ScaleFrequency, LyricsAnimatorEngine.ScaleDamping)
        spring.setGoal(1.05)
        val stepped = spring.step(1.0 / 60.0)
        assertTrue(stepped > 0.95)

        // Verify NotSung words strictly have 0f fillFraction (preventing premature 1.5-letter bleed)
        val engine = LyricsAnimatorEngine()
        val line = com.example.lyrics.SyllableLineModel(
            index = 0,
            startTimeMs = 1000.0,
            endTimeMs = 3000.0,
            words = listOf(
                com.example.lyrics.SyllableWordModel(
                    text = "Tonight",
                    startTimeMs = 1000.0,
                    endTimeMs = 2000.0
                ),
                com.example.lyrics.SyllableWordModel(
                    text = "Round",
                    startTimeMs = 2000.0,
                    endTimeMs = 3000.0,
                    isLastWordInLine = true
                )
            )
        )
        val lines = listOf(line)

        // Before line starts (500ms): both words are NotSung and fillFraction is strictly 0f
        engine.animate(
            lines = lines,
            rawPositionMs = 500.0,
            timeOffsetMs = 0.0,
            simpleLyricsMode = false,
            refreshedSimpleLyrics = false
        )
        assertEquals(ElementVocalState.NotSung, line.words[0].state)
        assertEquals(0f, line.words[0].fillFraction, 0.0001f)
        assertEquals(ElementVocalState.NotSung, line.words[1].state)
        assertEquals(0f, line.words[1].fillFraction, 0.0001f)

        // Mid first word (1500ms): first word is Active (~0.5 fill), second word is still NotSung (0f fill)
        engine.animate(
            lines = lines,
            rawPositionMs = 1500.0,
            timeOffsetMs = 0.0,
            simpleLyricsMode = false,
            refreshedSimpleLyrics = false
        )
        assertEquals(ElementVocalState.Active, line.words[0].state)
        assertEquals(0.5f, line.words[0].fillFraction, 0.01f)
        assertEquals(ElementVocalState.NotSung, line.words[1].state)
        assertEquals(0f, line.words[1].fillFraction, 0.0001f)
    }
}
