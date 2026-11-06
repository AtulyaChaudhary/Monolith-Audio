package com.example.lyrics

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import kotlin.math.max

/**
 * Multi-format Lyrics Parser:
 * 1. `.ttml` (W3C Timed Text Markup Language — Syllable, Word, or Line-timed with Main, Background `x-bg`, and Duo `v2` vocals).
 * 2. `.lrc` (Standard `[mm:ss.xx]` Line-synced LRC & Enhanced `<mm:ss.xx>` Word-synced LRC).
 * 3. `.txt` / Plain Text (Unsynced multi-line lyrics).
 */
object TtmlParser {

    private data class RawSpan(
        val text: String,
        val startMs: Double,
        val endMs: Double,
        val isBg: Boolean
    )

    private data class RawLine(
        val startMs: Double,
        val endMs: Double,
        val spans: List<RawSpan>,
        val isBg: Boolean,
        val isDuo: Boolean,
        val isLineSyncedOnly: Boolean = false
    )

    private val LRC_LINE_REGEX = Regex("""\[(\d{1,3}):(\d{1,2}(?:\.\d{1,3})?)]""")
    private val LRC_WORD_TAG_REGEX = Regex("""<(\d{1,3}):(\d{1,2}(?:\.\d{1,3})?)>\s*([^<]+)""")
    private val LRC_META_REGEX = Regex("""^\s*\[(ti|ar|al|au|by|length|offset|re|ve):.*]\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Automatically detects whether `rawContent` is `.ttml` XML, `.lrc` (Line/Enhanced LRC),
     * or Plain Text (`.txt`) and parses it into `List<SyllableLineModel>` + `LyricsSyncPriority`.
     */
    fun parseAnyLyricsFormat(
        rawContent: String,
        durationMs: Long = 180_000L
    ): Pair<List<SyllableLineModel>, LyricsSyncPriority> {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return emptyList<SyllableLineModel>() to LyricsSyncPriority.NO_SYNC

        // 1. Check TTML XML
        if (trimmed.startsWith("<") && (trimmed.contains("<tt", ignoreCase = true) || trimmed.contains("<p", ignoreCase = true))) {
            val ttmlLines = parseTtml(trimmed)
            if (ttmlLines.isNotEmpty()) {
                val hasLetters = ttmlLines.any { l -> l.words.any { it.letterGroup } }
                val isLineOnly = ttmlLines.all { it.isLineSyncedOnly }
                val priority = when {
                    isLineOnly -> LyricsSyncPriority.LINE
                    hasLetters -> LyricsSyncPriority.SYLLABLE
                    else -> LyricsSyncPriority.WORD
                }
                return ttmlLines to priority
            }
        }

        // 2. Check LRC format (`[mm:ss.xx] ...`)
        if (LRC_LINE_REGEX.containsMatchIn(trimmed)) {
            val lrcResult = parseLrc(trimmed, durationMs)
            if (lrcResult.first.isNotEmpty()) {
                return lrcResult
            }
        }

        // 3. Fallback: Plain Text (Unsynced multi-line lyrics)
        val plainLines = parsePlainText(trimmed, durationMs)
        return plainLines to LyricsSyncPriority.NO_SYNC
    }

    /**
     * Parses standard `[mm:ss.xx]` Line-Synced LRC and Enhanced `<mm:ss.xx>` Word-Synced LRC.
     */
    fun parseLrc(
        lrcContent: String,
        trackDurationMs: Long = 180_000L
    ): Pair<List<SyllableLineModel>, LyricsSyncPriority> {
        data class TimedLrcEntry(
            val startMs: Double,
            val text: String
        )

        val entries = mutableListOf<TimedLrcEntry>()
        var globalOffsetMs = 0.0
        val offsetRegex = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

        for (rawLine in lrcContent.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val offsetMatch = offsetRegex.find(line)
            if (offsetMatch != null) {
                globalOffsetMs = offsetMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                continue
            }
            if (LRC_META_REGEX.matches(line)) continue

            val matches = LRC_LINE_REGEX.findAll(line).toList()
            if (matches.isEmpty()) continue

            val lyricBody = line.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                val mins = m.groupValues[1].toDoubleOrNull() ?: 0.0
                val secs = m.groupValues[2].toDoubleOrNull() ?: 0.0
                val timeMs = max(0.0, (mins * 60.0 + secs) * 1000.0 + globalOffsetMs)
                entries.add(TimedLrcEntry(timeMs, lyricBody))
            }
        }

        if (entries.isEmpty()) return emptyList<SyllableLineModel>() to LyricsSyncPriority.NO_SYNC
        val sorted = entries.sortedBy { it.startMs }

        val models = mutableListOf<SyllableLineModel>()
        var lineIdx = 0
        var hasWordTags = false

        for (i in sorted.indices) {
            val entry = sorted[i]
            if (entry.text.isBlank()) continue
            val nextStart = if (i + 1 < sorted.size) {
                sorted[i + 1].startMs
            } else {
                max(entry.startMs + 4000.0, trackDurationMs.toDouble())
            }
            val endMs = max(entry.startMs + 300.0, nextStart)

            val wordMatches = LRC_WORD_TAG_REGEX.findAll(entry.text).toList()
            if (wordMatches.isNotEmpty()) {
                hasWordTags = true
                val words = wordMatches.mapIndexedNotNull { wIdx, wm ->
                    val wMins = wm.groupValues[1].toDoubleOrNull() ?: 0.0
                    val wSecs = wm.groupValues[2].toDoubleOrNull() ?: 0.0
                    val wStart = max(0.0, (wMins * 60.0 + wSecs) * 1000.0 + globalOffsetMs)
                    val wText = wm.groupValues[3].trim()
                    if (wText.isEmpty()) return@mapIndexedNotNull null
                    val wNext = if (wIdx + 1 < wordMatches.size) {
                        val nm = wordMatches[wIdx + 1]
                        val nMins = nm.groupValues[1].toDoubleOrNull() ?: 0.0
                        val nSecs = nm.groupValues[2].toDoubleOrNull() ?: 0.0
                        max(wStart + 50.0, (nMins * 60.0 + nSecs) * 1000.0 + globalOffsetMs)
                    } else {
                        max(wStart + 150.0, endMs)
                    }
                    SyllableWordModel(
                        text = wText,
                        startTimeMs = wStart,
                        endTimeMs = wNext,
                        isLastWordInLine = wIdx == wordMatches.lastIndex
                    )
                }
                if (words.isNotEmpty()) {
                    models.add(
                        SyllableLineModel(
                            index = lineIdx++,
                            startTimeMs = entry.startMs,
                            endTimeMs = endMs,
                            words = words,
                            isLineSyncedOnly = false
                        )
                    )
                }
            } else {
                val tokens = entry.text.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (tokens.isNotEmpty()) {
                    val words = tokens.mapIndexed { wIdx, token ->
                        SyllableWordModel(
                            text = token,
                            startTimeMs = entry.startMs,
                            endTimeMs = endMs,
                            isLastWordInLine = wIdx == tokens.lastIndex
                        )
                    }
                    models.add(
                        SyllableLineModel(
                            index = lineIdx++,
                            startTimeMs = entry.startMs,
                            endTimeMs = endMs,
                            words = words,
                            isLineSyncedOnly = true
                        )
                    )
                }
            }
        }

        val priority = if (hasWordTags) LyricsSyncPriority.WORD else LyricsSyncPriority.LINE
        return models to priority
    }

    /**
     * Parses plain unsynced text lyrics (`.txt` or static lyrics) into readable `SyllableLineModel` rows.
     */
    fun parsePlainText(
        plainText: String,
        trackDurationMs: Long = 180_000L
    ): List<SyllableLineModel> {
        val rawLines = plainText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !LRC_META_REGEX.matches(it) }
        if (rawLines.isEmpty()) return emptyList()

        val stepMs = max(2000.0, trackDurationMs.toDouble() / rawLines.size.coerceAtLeast(1))
        return rawLines.mapIndexed { idx, lineText ->
            val lStart = idx * stepMs
            val lEnd = lStart + stepMs
            val tokens = lineText.split(Regex("\\s+")).filter { it.isNotBlank() }
            val words = tokens.mapIndexed { wIdx, token ->
                SyllableWordModel(
                    text = token,
                    startTimeMs = lStart,
                    endTimeMs = lEnd,
                    isLastWordInLine = wIdx == tokens.lastIndex,
                    state = ElementVocalState.Active,
                    fillFraction = 1f
                )
            }
            SyllableLineModel(
                index = idx,
                startTimeMs = lStart,
                endTimeMs = lEnd,
                words = words,
                isLineSyncedOnly = true,
                isPlainTextUnsynced = true,
                state = ElementVocalState.Active
            )
        }
    }

    fun parseTtml(ttmlContent: String): List<SyllableLineModel> {
        if (ttmlContent.isBlank()) return emptyList()
        val rawLines = mutableListOf<RawLine>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(ttmlContent))

            var eventType = parser.eventType
            var insideP = false
            var pStartMs = 0.0
            var pEndMs = 0.0
            var pIsBg = false
            var pIsDuo = false
            var primaryAgentId: String? = null
            val currentSpans = mutableListOf<RawSpan>()
            val bgInlineSpans = mutableListOf<RawSpan>()
            var pDirectText = StringBuilder()

            var insideSpan = false
            var spanStartMs = 0.0
            var spanEndMs = 0.0
            var spanIsBg = false
            var spanText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "ttm:agent" || tag == "agent") {
                            val id = getAttr(parser, "xml:id", "id")
                            val type = getAttr(parser, "type")
                            if (primaryAgentId == null && id != null && (type == null || type == "person" || type == "character")) {
                                primaryAgentId = id
                            }
                        } else if (tag == "p") {
                            insideP = true
                            pStartMs = parseTimeAttr(getAttr(parser, "begin")) ?: 0.0
                            pEndMs = parseTimeAttr(getAttr(parser, "end")) ?: (pStartMs + 3000.0)
                            val role = getAttr(parser, "ttm:role", "role")
                            val agent = getAttr(parser, "ttm:agent", "agent")
                            val itunesBg = getAttr(parser, "itunes:bg")
                            pIsBg = role?.contains("x-bg", ignoreCase = true) == true || itunesBg == "true"
                            pIsDuo = isSecondaryVocalAgent(agent, primaryAgentId)
                            currentSpans.clear()
                            bgInlineSpans.clear()
                            pDirectText = StringBuilder()
                        } else if (insideP && tag == "span") {
                            val role = getAttr(parser, "ttm:role", "role")
                            val isRoleBg = role?.contains("x-bg", ignoreCase = true) == true
                            val bAttr = parseTimeAttr(getAttr(parser, "begin"))
                            val eAttr = parseTimeAttr(getAttr(parser, "end"))

                            if (insideSpan && spanIsBg && bAttr != null && eAttr != null) {
                                val prevTxt = spanText.toString().trim()
                                if (prevTxt.isNotEmpty()) {
                                    bgInlineSpans.add(RawSpan(prevTxt, spanStartMs, spanEndMs, true))
                                }
                                spanStartMs = bAttr
                                spanEndMs = eAttr
                                spanText = StringBuilder()
                            } else {
                                insideSpan = true
                                spanStartMs = bAttr ?: pStartMs
                                spanEndMs = eAttr ?: pEndMs
                                spanIsBg = pIsBg || isRoleBg
                                spanText = StringBuilder()
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val txt = parser.text ?: ""
                        if (insideSpan) {
                            spanText.append(txt)
                        } else if (insideP) {
                            pDirectText.append(txt)
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "span" && insideSpan) {
                            val cleaned = spanText.toString().trim()
                                .removePrefix("(")
                                .removeSuffix(")")
                                .trim()
                            if (cleaned.isNotEmpty()) {
                                val spanObj = RawSpan(
                                    text = if (spanIsBg) spanText.toString().trim().removePrefix("(").removeSuffix(")").trim() else spanText.toString().trim(),
                                    startMs = spanStartMs,
                                    endMs = max(spanStartMs + 60.0, spanEndMs),
                                    isBg = spanIsBg
                                )
                                if (spanIsBg && !pIsBg) {
                                    bgInlineSpans.add(spanObj)
                                } else {
                                    currentSpans.add(spanObj)
                                }
                            }
                            insideSpan = false
                        } else if (tag == "p" && insideP) {
                            var wasLineSyncedOnly = false
                            if (currentSpans.isEmpty()) {
                                val direct = pDirectText.toString().trim()
                                if (direct.isNotEmpty()) {
                                    wasLineSyncedOnly = true
                                    val words = direct.split(Regex("\\s+")).filter { it.isNotBlank() }
                                    words.forEach { w ->
                                        currentSpans.add(
                                            RawSpan(
                                                text = w,
                                                startMs = pStartMs,
                                                endMs = pEndMs,
                                                isBg = pIsBg
                                            )
                                        )
                                    }
                                }
                            }
                            if (currentSpans.isNotEmpty()) {
                                val lStart = minOf(pStartMs, currentSpans.first().startMs)
                                val lEnd = maxOf(pEndMs, currentSpans.last().endMs)
                                rawLines.add(
                                    RawLine(
                                        startMs = lStart,
                                        endMs = lEnd,
                                        spans = currentSpans.toList(),
                                        isBg = pIsBg,
                                        isDuo = pIsDuo,
                                        isLineSyncedOnly = wasLineSyncedOnly
                                    )
                                )
                            }
                            if (bgInlineSpans.isNotEmpty()) {
                                val bgStart = bgInlineSpans.first().startMs
                                val bgEnd = bgInlineSpans.last().endMs
                                rawLines.add(
                                    RawLine(
                                        startMs = bgStart,
                                        endMs = bgEnd,
                                        spans = bgInlineSpans.toList(),
                                        isBg = true,
                                        isDuo = pIsDuo,
                                        isLineSyncedOnly = false
                                    )
                                )
                            }
                            insideP = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        if (rawLines.isEmpty()) return emptyList()

        val result = mutableListOf<SyllableLineModel>()
        var lineIndexCounter = 0

        val firstVocalStart = rawLines.first().startMs
        if (firstVocalStart >= 2500.0) {
            result.add(createDotLine(lineIndexCounter++, 0.0, firstVocalStart))
        }

        for (i in rawLines.indices) {
            val raw = rawLines[i]
            if (i > 0 && !raw.isBg) {
                val prevEnd = rawLines[i - 1].endMs
                val gap = raw.startMs - prevEnd
                if (gap >= 3500.0) {
                    result.add(createDotLine(lineIndexCounter++, prevEnd + 150.0, raw.startMs))
                }
            }

            val wordModels = raw.spans.mapIndexed { wIdx, span ->
                val isLast = wIdx == raw.spans.lastIndex
                val duration = span.endMs - span.startMs
                val shouldSplitLetters = !raw.isLineSyncedOnly && duration >= 750.0 && span.text.length >= 3
                val letters = if (shouldSplitLetters) {
                    val chars = span.text.map { it.toString() }
                    val charDuration = duration / chars.size
                    chars.mapIndexed { cIdx, ch ->
                        val ls = span.startMs + cIdx * charDuration
                        val le = if (cIdx == chars.lastIndex) span.endMs else ls + charDuration
                        SplitLetterModel(
                            charText = ch,
                            startTimeMs = ls,
                            endTimeMs = le
                        )
                    }
                } else {
                    emptyList()
                }

                SyllableWordModel(
                    text = span.text,
                    startTimeMs = span.startMs,
                    endTimeMs = span.endMs,
                    isDot = false,
                    isBgWord = span.isBg,
                    isLastWordInLine = isLast,
                    letterGroup = shouldSplitLetters,
                    letters = letters
                )
            }

            result.add(
                SyllableLineModel(
                    index = lineIndexCounter++,
                    startTimeMs = raw.startMs,
                    endTimeMs = raw.endMs,
                    words = wordModels,
                    isDotLine = false,
                    isBgLine = raw.isBg,
                    isDuoLine = raw.isDuo,
                    isLineSyncedOnly = raw.isLineSyncedOnly
                )
            )
        }

        return result
    }

    /**
     * Extracts songwriter names and provider attribution from `.ttml` XML headers (`<songwriter>`, `<ttm:copyright>`).
     */
    fun extractAttributionFromTtml(ttmlContent: String): LyricsAttributionInfo? {
        if (ttmlContent.isBlank()) return null
        val writers = mutableListOf<String>()
        var isSpotify = false

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(ttmlContent))

            var eventType = parser.eventType
            var insideSongwriter = false
            val writerBuf = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "songwriter" || tag.endsWith(":songwriter")) {
                            insideSongwriter = true
                            writerBuf.clear()
                        } else if (tag.contains("spotify")) {
                            isSpotify = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideSongwriter) {
                            writerBuf.append(parser.text ?: "")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "songwriter" || tag.endsWith(":songwriter")) {
                            insideSongwriter = false
                            val w = writerBuf.toString().trim()
                            if (w.isNotEmpty()) writers.add(w)
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        if (writers.isEmpty()) {
            val regex = Regex("<(?:[a-zA-Z0-9_]+:)?songwriter[^>]*>([^<]+)</(?:[a-zA-Z0-9_]+:)?songwriter>", RegexOption.IGNORE_CASE)
            regex.findAll(ttmlContent).forEach { match ->
                val w = match.groupValues[1].trim()
                if (w.isNotEmpty()) writers.add(w)
            }
            if (ttmlContent.contains("spotify", ignoreCase = true)) {
                isSpotify = true
            }
        }

        return LyricsAttributionInfo(
            songWriters = writers,
            source = if (isSpotify) "spotify" else "apple_music",
            syncPriority = LyricsSyncPriority.SYLLABLE
        )
    }

    private fun isSecondaryVocalAgent(agent: String?, primaryAgent: String?): Boolean {
        if (agent.isNullOrBlank()) return false
        val lower = agent.lowercase()
        if (lower == "v2" || lower == "v3" || lower == "m2" || lower == "f2" || lower.contains("person2") || lower.contains("duet") || lower.contains("right")) {
            return true
        }
        return primaryAgent != null && !agent.equals(primaryAgent, ignoreCase = true)
    }

    private fun createDotLine(index: Int, startMs: Double, endMs: Double): SyllableLineModel {
        val total = max(600.0, endMs - startMs)
        val third = total / 3.0
        val dots = (0 until 3).map { d ->
            SyllableWordModel(
                text = "●",
                startTimeMs = startMs + d * third,
                endTimeMs = startMs + (d + 1) * third,
                isDot = true
            )
        }
        return SyllableLineModel(
            index = index,
            startTimeMs = startMs,
            endTimeMs = endMs,
            words = dots,
            isDotLine = true
        )
    }

    private fun getAttr(parser: XmlPullParser, vararg names: String): String? {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (names.any { it.equals(attrName, ignoreCase = true) }) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    fun parseTimeAttr(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        try {
            if (s.endsWith("ms")) {
                return s.removeSuffix("ms").trim().toDouble()
            }
            if (s.endsWith("s")) {
                return s.removeSuffix("s").trim().toDouble() * 1000.0
            }
            val parts = s.split(":")
            return when (parts.size) {
                3 -> {
                    val h = parts[0].toDouble()
                    val m = parts[1].toDouble()
                    val sec = parts[2].toDouble()
                    (h * 3600.0 + m * 60.0 + sec) * 1000.0
                }
                2 -> {
                    val m = parts[0].toDouble()
                    val sec = parts[1].toDouble()
                    (m * 60.0 + sec) * 1000.0
                }
                1 -> parts[0].toDouble() * 1000.0
                else -> null
            }
        } catch (_: Exception) {
            return null
        }
    }
}
