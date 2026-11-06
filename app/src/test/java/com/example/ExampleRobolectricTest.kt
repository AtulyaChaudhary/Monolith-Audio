package com.example

import com.example.audio.MusicAudioEngine
import com.example.data.QueueItem
import com.example.data.TrackEntity
import com.example.lyrics.ElementVocalState
import com.example.lyrics.LyricsAnimatorEngine
import com.example.lyrics.LyricsSyncPriority
import com.example.lyrics.SpicyLyricsClient
import com.example.lyrics.SpicyServiceIds
import com.example.lyrics.SpicyTrackSearchResult
import com.example.lyrics.TtmlParser
import com.example.ui.theme.MonoCarbon
import com.example.ui.theme.MonoPureBlack
import com.example.ui.theme.MonoPureWhite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        val queue = mutableListOf(
            QueueItem(UUID.randomUUID().toString(), tomato),
            QueueItem(UUID.randomUUID().toString(), potato)
        )
        val currentIndex = 0
        assertEquals("Tomato", queue[0].track.title)
        assertEquals("Potato", queue[1].track.title)

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
        assertEquals(0.1f, MusicAudioEngine.stepOneDecimal(0.1f, -1), 0.0001f)
        assertEquals(5.0f, MusicAudioEngine.stepOneDecimal(5.0f, 1), 0.0001f)
    }

    @Test
    fun `verify TTML parser and LyricsAnimatorEngine spline and spring step`() {
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

    @Test
    fun `verify SpicyLyricsClient secret key substitution, candidate ID ordering, attribution and Syllable priority`() {
        val defaultSnippet = SpicyLyricsClient.buildNodeJsFetchSnippet(SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER)
        assertTrue(defaultSnippet.contains("Bearer \$SPICY_LYRICS_SECRET_KEY"))
        assertFalse(SpicyLyricsClient.isSecretKeyConfigured(SpicyLyricsClient.DEFAULT_SECRET_KEY_PLACEHOLDER))

        val activeSnippet = SpicyLyricsClient.buildNodeJsFetchSnippet("sl_sk_test_12345", "1QV6tiMFM6fSOKOGLMHYYg")
        assertTrue(activeSnippet.contains("Bearer sl_sk_test_12345"))
        assertTrue(activeSnippet.contains("/v1/lyrics/1QV6tiMFM6fSOKOGLMHYYg"))
        assertTrue(SpicyLyricsClient.isSecretKeyConfigured("sl_sk_test_12345"))

        val trackResult = SpicyTrackSearchResult(
            id = "4cOdK2wGLETKBW3PvgPWqT",
            ids = SpicyServiceIds(
                spotify = "4cOdK2wGLETKBW3PvgPWqT",
                isrc = "GBARL9300135",
                appleMusic = "1438556832",
                tidal = "491206012",
                youtube = "7FwDP17XPlk"
            ),
            title = "Never Gonna Give You Up",
            artists = listOf("Rick Astley"),
            album = "Whenever You Need Somebody",
            durationSeconds = 213.573,
            explicit = false,
            artworkUrl = "https://image-cdn-ak.spotifycdn.com/image/ab67616d0000b273baf89eb11ec7c657805d2da0",
            tier = "exact",
            variants = listOf(
                com.example.lyrics.SpicyTrackVariant(
                    id = "4PTG3Z6ehGkBFwjybzWkR8",
                    album = "Whenever You Need Somebody",
                    durationSeconds = 213.573
                )
            )
        )
        assertEquals("Never Gonna Give You Up", trackResult.title)
        assertEquals("Rick Astley", trackResult.artistDisplay)
        assertEquals("exact", trackResult.tier)
        assertEquals(
            listOf("4cOdK2wGLETKBW3PvgPWqT", "1438556832", "GBARL9300135", "4PTG3Z6ehGkBFwjybzWkR8"),
            trackResult.allCandidateIds()
        )

        val sampleTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head>
                <metadata>
                  <songwriters>
                    <songwriter>Michael Fiscella</songwriter>
                    <songwriter>Noah Burke</songwriter>
                  </songwriters>
                </metadata>
              </head>
              <body><div><p begin="00:01.000" end="00:03.000">The pain doesn't hurt</p></div></body>
            </tt>
        """.trimIndent()
        val ttmlAttribution = TtmlParser.extractAttributionFromTtml(sampleTtml)
        assertNotNull(ttmlAttribution)
        assertEquals(listOf("Michael Fiscella", "Noah Burke"), ttmlAttribution!!.songWriters)
        assertEquals("apple_music", ttmlAttribution.source)

        assertTrue(LyricsSyncPriority.SYLLABLE.rank > LyricsSyncPriority.WORD.rank)
        assertTrue(LyricsSyncPriority.WORD.rank > LyricsSyncPriority.LINE.rank)
        assertTrue(LyricsSyncPriority.LINE.rank > LyricsSyncPriority.NO_SYNC.rank)
    }

    @Test
    fun `verify LRC line-synced, Plain Text unsynced, strict same-song matching, and pitch-black monochrome`() {
        // 1. Parse standard LRC line-synced lyrics
        val sampleLrc = """
            [00:10.00]Hardwood floors, high heels on the table
            [00:14.50]That's my part time angel
            [00:18.00]Said, "Do you get down?"
        """.trimIndent()
        val (lrcLines, lrcPriority) = TtmlParser.parseAnyLyricsFormat(sampleLrc, 180000L)
        assertEquals(LyricsSyncPriority.LINE, lrcPriority)
        assertEquals(3, lrcLines.size)
        assertTrue(lrcLines[0].isLineSyncedOnly)
        assertEquals(10000.0, lrcLines[0].startTimeMs, 1.0)
        assertEquals(14500.0, lrcLines[0].endTimeMs, 1.0)
        assertEquals("Hardwood floors, high heels on the table", lrcLines[0].words.joinToString(" ") { it.text })

        // 2. Parse Plain Text (.txt) unsynced lyrics
        val sampleTxt = """
            First line of plain text lyrics
            Second line of plain text lyrics
            Third line of plain text lyrics
        """.trimIndent()
        val (txtLines, txtPriority) = TtmlParser.parseAnyLyricsFormat(sampleTxt, 180000L)
        assertEquals(LyricsSyncPriority.NO_SYNC, txtPriority)
        assertEquals(3, txtLines.size)
        assertTrue(txtLines[0].isPlainTextUnsynced)
        assertEquals("First line of plain text lyrics", txtLines[0].words.joinToString(" ") { it.text })

        // 3. Strict Same-Song Verification: another song's TTML must be rejected
        val exactCandidate = SpicyTrackSearchResult(
            id = "id_exact",
            ids = SpicyServiceIds(spotify = "id_exact"),
            title = "round and round",
            artists = listOf("bbno$"),
            album = "bag or die",
            durationSeconds = 168.0,
            explicit = true,
            artworkUrl = null,
            tier = "exact",
            variants = emptyList()
        )
        val wrongSongCandidate = SpicyTrackSearchResult(
            id = "id_wrong",
            ids = SpicyServiceIds(spotify = "id_wrong"),
            title = "Round Here",
            artists = listOf("Counting Crows"),
            album = "August and Everything After",
            durationSeconds = 332.0,
            explicit = false,
            artworkUrl = null,
            tier = "fuzzy",
            variants = emptyList()
        )
        assertTrue(SpicyLyricsClient.isSameSongMatch("round and round", "bbno$", exactCandidate))
        assertFalse(SpicyLyricsClient.isSameSongMatch("round and round", "bbno$", wrongSongCandidate))

        // 4. Verify Pure Monochrome pitch-black (#000000) & pure white (#FFFFFF) with zero blue tint
        assertEquals(0f, MonoPureBlack.red, 0.0001f)
        assertEquals(0f, MonoPureBlack.green, 0.0001f)
        assertEquals(0f, MonoPureBlack.blue, 0.0001f)
        assertEquals(MonoCarbon.red, MonoCarbon.blue, 0.0001f)
        assertEquals(1f, MonoPureWhite.red, 0.0001f)
        assertEquals(1f, MonoPureWhite.green, 0.0001f)
        assertEquals(1f, MonoPureWhite.blue, 0.0001f)
    }
}
