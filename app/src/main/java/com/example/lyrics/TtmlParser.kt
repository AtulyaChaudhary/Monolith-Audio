package com.example.lyrics

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import kotlin.math.max

/**
 * Parses W3C / Apple Music / Spicy Lyrics `.ttml` XML documents into `SyllableLineModel` objects:
 * - Main Vocal Lines (`ttm:agent="v1"` or default): Left-aligned, full size.
 * - Background Vocal Lines (`ttm:role="x-bg"` on `<p>` or nested `<span ttm:role="x-bg">`):
 *   Extracted into a dedicated smaller `isBgLine = true` sub-line directly beneath the lead line.
 * - Duo / Secondary Vocalist Lines (`ttm:agent="v2"`, `person2`, or any agent distinct from the
 *   primary vocalist): Marked `isDuoLine = true` for right-side alignment!
 */
object TtmlParser {

    fun parseTtml(ttmlContent: String): List<SyllableLineModel> {
        if (ttmlContent.isBlank()) return emptyList()

        val rawLines = mutableListOf<ParsedRawLine>()
        var primaryAgent: String? = null

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(ttmlContent))

            var eventType = parser.eventType
            var currentLineBegin: Double? = null
            var currentLineEnd: Double? = null
            var currentLineIsBg = false
            var currentLineAgent: String? = null
            var currentSpans = mutableListOf<ParsedRawSpan>()
            var paragraphTextBuffer = StringBuilder()
            var insideP = false
            var insideSpan = false
            var spanBegin: Double? = null
            var spanEnd: Double? = null
            var spanIsBg = false
            var spanTextBuffer = StringBuilder()

            // Support nested <span ttm:role="x-bg"> containing inner word <span> tags
            var spanDepth = 0
            var bgContainerDepth = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "p") {
                            insideP = true
                            currentLineBegin = parseTimeAttr(parser.getAttributeValue(null, "begin"))
                            currentLineEnd = parseTimeAttr(parser.getAttributeValue(null, "end"))
                            val role = getAttrAnyNs(parser, "role")
                            currentLineIsBg = role?.contains("bg", ignoreCase = true) == true
                            val agent = getAttrAnyNs(parser, "agent")?.trim()
                            currentLineAgent = agent
                            if (agent != null && primaryAgent == null && !currentLineIsBg) {
                                primaryAgent = agent
                            }
                            currentSpans = mutableListOf()
                            paragraphTextBuffer = StringBuilder()
                            spanDepth = 0
                            bgContainerDepth = 0
                        } else if (tag == "span" && insideP) {
                            spanDepth++
                            val role = getAttrAnyNs(parser, "role")
                            val isThisSpanBg = role?.contains("bg", ignoreCase = true) == true
                            if (isThisSpanBg && bgContainerDepth == 0) {
                                bgContainerDepth = spanDepth
                            }
                            val bAttr = parseTimeAttr(parser.getAttributeValue(null, "begin"))
                            val eAttr = parseTimeAttr(parser.getAttributeValue(null, "end"))
                            if (bAttr != null || eAttr != null || spanDepth == 1) {
                                insideSpan = true
                                spanBegin = bAttr ?: spanBegin
                                spanEnd = eAttr ?: spanEnd
                                spanIsBg = isThisSpanBg || bgContainerDepth > 0 || currentLineIsBg
                                spanTextBuffer = StringBuilder()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        if (insideSpan) {
                            spanTextBuffer.append(text)
                        } else if (insideP) {
                            paragraphTextBuffer.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.lowercase()
                        if (tag == "span" && insideP) {
                            if (insideSpan) {
                                val rawText = spanTextBuffer.toString().trim()
                                if (rawText.isNotEmpty() && spanBegin != null && spanEnd != null) {
                                    currentSpans.add(
                                        ParsedRawSpan(
                                            text = rawText,
                                            startMs = spanBegin!!,
                                            endMs = max(spanBegin!! + 50.0, spanEnd!!),
                                            isBg = spanIsBg
                                        )
                                    )
                                }
                                spanTextBuffer = StringBuilder()
                            }
                            if (spanDepth == bgContainerDepth) {
                                bgContainerDepth = 0
                            }
                            spanDepth = (spanDepth - 1).coerceAtLeast(0)
                            if (spanDepth == 0) {
                                insideSpan = false
                            }
                        } else if (tag == "p" && insideP) {
                            insideP = false
                            if (currentSpans.isEmpty()) {
                                val fullLine = paragraphTextBuffer.toString().trim()
                                val b = currentLineBegin ?: 0.0
                                val e = currentLineEnd ?: (b + 3000.0)
                                val words = fullLine.split(Regex("\\s+")).filter { it.isNotBlank() }
                                if (words.isNotEmpty()) {
                                    val step = max(120.0, (e - b) / words.size)
                                    words.forEachIndexed { idx, w ->
                                        val ws = b + idx * step
                                        val we = if (idx == words.lastIndex) e else ws + step
                                        currentSpans.add(ParsedRawSpan(w, ws, we, currentLineIsBg))
                                    }
                                }
                            }

                            if (currentSpans.isNotEmpty()) {
                                val isDuo = isSecondaryVocalAgent(currentLineAgent, primaryAgent)
                                val mainSpans = currentSpans.filter { !it.isBg }
                                val bgSpans = currentSpans.filter { it.isBg }.map { sp ->
                                    // Clean outer parentheses on background vocal words like "(Angel," "angel)"
                                    sp.copy(text = sp.text.removePrefix("(").removeSuffix(")").trim().ifEmpty { sp.text })
                                }

                                if (mainSpans.isNotEmpty()) {
                                    val lStart = currentLineBegin ?: mainSpans.first().startMs
                                    val lEnd = currentLineEnd ?: mainSpans.last().endMs
                                    rawLines.add(
                                        ParsedRawLine(
                                            startMs = lStart,
                                            endMs = max(lStart + 200.0, lEnd),
                                            isBg = false,
                                            isDuo = isDuo,
                                            spans = mainSpans
                                        )
                                    )
                                }
                                if (bgSpans.isNotEmpty()) {
                                    val bgStart = bgSpans.first().startMs
                                    val bgEnd = bgSpans.last().endMs
                                    rawLines.add(
                                        ParsedRawLine(
                                            startMs = bgStart,
                                            endMs = max(bgStart + 200.0, bgEnd),
                                            isBg = true,
                                            isDuo = isDuo,
                                            spans = bgSpans
                                        )
                                    )
                                }
                            }
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
                val shouldSplitLetters = duration >= 750.0 && span.text.length >= 3
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
                    isDuoLine = raw.isDuo
                )
            )
        }

        return result
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
            isDotLine = true,
            isBgLine = false,
            isDuoLine = false
        )
    }

    private fun getAttrAnyNs(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            if (name.equals(localName, ignoreCase = true) ||
                name.endsWith(":$localName", ignoreCase = true)
            ) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    fun parseTimeAttr(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().lowercase()
        return try {
            when {
                s.endsWith("ms") -> s.removeSuffix("ms").trim().toDouble()
                s.endsWith("s") -> s.removeSuffix("s").trim().toDouble() * 1000.0
                s.contains(":") -> {
                    val parts = s.split(":")
                    when (parts.size) {
                        2 -> {
                            val mins = parts[0].toDouble()
                            val secs = parts[1].toDouble()
                            (mins * 60.0 + secs) * 1000.0
                        }
                        3 -> {
                            val hrs = parts[0].toDouble()
                            val mins = parts[1].toDouble()
                            val secs = parts[2].toDouble()
                            (hrs * 3600.0 + mins * 60.0 + secs) * 1000.0
                        }
                        else -> null
                    }
                }
                else -> s.toDouble() * 1000.0
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class ParsedRawSpan(
        val text: String,
        val startMs: Double,
        val endMs: Double,
        val isBg: Boolean
    )

    private data class ParsedRawLine(
        val startMs: Double,
        val endMs: Double,
        val isBg: Boolean,
        val isDuo: Boolean,
        val spans: List<ParsedRawSpan>
    )
}
