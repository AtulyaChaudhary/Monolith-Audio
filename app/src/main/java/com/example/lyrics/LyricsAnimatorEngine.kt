package com.example.lyrics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class AnimationPoint(
    val Time: Double,
    val Value: Double
)

fun clamp(value: Double, minVal: Double, maxVal: Double): Double {
    return max(minVal, min(maxVal, value))
}

class CubicSpline(
    private val xs: DoubleArray,
    private val ys: DoubleArray
) {
    private val ks: DoubleArray = DoubleArray(xs.size)

    init {
        if (xs.size >= 2) {
            computeNaturalKs()
        }
    }

    private fun computeNaturalKs() {
        val n = xs.size - 1
        val a = Array(n + 1) { DoubleArray(n + 2) }

        for (i in 1 until n) {
            a[i][i - 1] = 1.0 / (xs[i] - xs[i - 1])
            a[i][i] = 2.0 * (1.0 / (xs[i] - xs[i - 1]) + 1.0 / (xs[i + 1] - xs[i]))
            a[i][i + 1] = 1.0 / (xs[i + 1] - xs[i])
            a[i][n + 1] = 3.0 * (
                (ys[i] - ys[i - 1]) / ((xs[i] - xs[i - 1]) * (xs[i] - xs[i - 1])) +
                    (ys[i + 1] - ys[i]) / ((xs[i + 1] - xs[i]) * (xs[i + 1] - xs[i]))
                )
        }

        a[0][0] = 2.0 / (xs[1] - xs[0])
        a[0][1] = 1.0 / (xs[1] - xs[0])
        a[0][n + 1] = (3.0 * (ys[1] - ys[0])) / ((xs[1] - xs[0]) * (xs[1] - xs[0]))

        a[n][n - 1] = 1.0 / (xs[n] - xs[n - 1])
        a[n][n] = 2.0 / (xs[n] - xs[n - 1])
        a[n][n + 1] = (3.0 * (ys[n] - ys[n - 1])) / ((xs[n] - xs[n - 1]) * (xs[n] - xs[n - 1]))

        solveGaussian(a, ks)
    }

    private fun solveGaussian(a: Array<DoubleArray>, x: DoubleArray) {
        val m = a.size
        for (k in 0 until m) {
            var iMax = k
            var vMax = abs(a[k][k])
            for (i in k + 1 until m) {
                if (abs(a[i][k]) > vMax) {
                    iMax = i
                    vMax = abs(a[i][k])
                }
            }
            if (iMax != k) {
                val tmp = a[k]
                a[k] = a[iMax]
                a[iMax] = tmp
            }
            val pivot = a[k][k]
            if (abs(pivot) < 1e-12) continue
            for (i in k + 1 until m) {
                val f = a[i][k] / pivot
                for (j in k until m + 1) {
                    a[i][j] -= a[k][j] * f
                }
            }
        }
        for (i in m - 1 downTo 0) {
            var sum = a[i][m]
            for (j in i + 1 until m) {
                sum -= a[i][j] * x[j]
            }
            val diag = a[i][i]
            x[i] = if (abs(diag) > 1e-12) sum / diag else 0.0
        }
    }

    fun at(x: Double): Double {
        if (xs.isEmpty()) return 0.0
        if (xs.size == 1) return ys[0]
        val clampedX = x.coerceIn(xs.first(), xs.last())
        var i = 1
        while (i < xs.size - 1 && xs[i] < clampedX) {
            i++
        }
        val dx = xs[i] - xs[i - 1]
        if (abs(dx) < 1e-9) return ys[i]
        val t = (clampedX - xs[i - 1]) / dx
        val a = ks[i - 1] * dx - (ys[i] - ys[i - 1])
        val b = -ks[i] * dx + (ys[i] - ys[i - 1])
        return (1 - t) * ys[i - 1] + t * ys[i] + t * (1 - t) * (a * (1 - t) + b * t)
    }
}

fun getSpline(range: List<AnimationPoint>): CubicSpline {
    return CubicSpline(
        range.map { it.Time }.toDoubleArray(),
        range.map { it.Value }.toDoubleArray()
    )
}

class LyricsSpring(
    initialValue: Double,
    private val frequency: Double,
    private val damping: Double
) {
    var position: Double = initialValue
        private set
    private var velocity: Double = 0.0
    var goal: Double = initialValue
        private set

    fun setGoal(target: Double, instant: Boolean = false) {
        goal = target
        if (instant) {
            position = target
            velocity = 0.0
        }
    }

    fun step(dtSeconds: Double): Double {
        val dt = dtSeconds.coerceIn(0.0, 0.064)
        if (dt <= 0.0) return position
        val angularFreq = frequency * 2.0 * PI
        if (angularFreq <= 1e-6) {
            position = goal
            velocity = 0.0
            return position
        }
        val offset = position - goal
        if (abs(offset) < 1e-5 && abs(velocity) < 1e-5) {
            position = goal
            velocity = 0.0
            return position
        }
        val f = 1.0 + 2.0 * dt * damping * angularFreq
        val oo = angularFreq * angularFreq
        val hoo = dt * oo
        val hhoo = dt * hoo
        val detInv = 1.0 / (f + hhoo)
        val detX = f * position + dt * velocity + hhoo * goal
        val detV = velocity + hoo * (goal - position)
        position = detX * detInv
        velocity = detV * detInv
        return position
    }

    fun canSleep(): Boolean {
        return abs(position - goal) < 0.0005 && abs(velocity) < 0.0005
    }
}

enum class ElementVocalState {
    NotSung,
    Active,
    Sung
}

data class SplitLetterModel(
    val charText: String,
    val startTimeMs: Double,
    val endTimeMs: Double,
    var scaleSpring: LyricsSpring? = null,
    var yOffsetSpring: LyricsSpring? = null,
    var glowSpring: LyricsSpring? = null,
    var state: ElementVocalState = ElementVocalState.NotSung,
    var fillFraction: Float = 0f, // Strictly 0f when NotSung, (0..1) when Active, 1f when Sung
    var currentScale: Float = 1f,
    var currentTranslateXEm: Float = 0f,
    var currentTranslateYEm: Float = 0f,
    var currentGlowRadiusPx: Float = 0f,
    var currentGlowOpacity: Float = 0f,
    var currentGradientPercent: Float = -50f
)

data class SyllableWordModel(
    val text: String,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val isDot: Boolean = false,
    val isBgWord: Boolean = false,
    val isLastWordInLine: Boolean = false,
    val letterGroup: Boolean = false,
    val letters: List<SplitLetterModel> = emptyList(),
    var scaleSpring: LyricsSpring? = null,
    var yOffsetSpring: LyricsSpring? = null,
    var glowSpring: LyricsSpring? = null,
    var opacitySpring: LyricsSpring? = null,
    var state: ElementVocalState = ElementVocalState.NotSung,
    var fillFraction: Float = 0f, // Strictly 0f when NotSung, (0..1) when Active, 1f when Sung
    var currentScale: Float = 1f,
    var currentYOffsetEm: Float = 0f,
    var currentGlowRadiusPx: Float = 0f,
    var currentGlowOpacity: Float = 0f,
    var currentGradientPercent: Float = -50f,
    var currentDotOpacity: Float = 0.35f
) {
    val totalTimeMs: Double get() = max(1.0, endTimeMs - startTimeMs)
}

data class SyllableLineModel(
    val index: Int,
    val startTimeMs: Double,
    val endTimeMs: Double,
    val words: List<SyllableWordModel>,
    val isDotLine: Boolean = false,
    val isBgLine: Boolean = false,
    val isDuoLine: Boolean = false, // True for secondary/duo vocalist (right-aligned)
    var state: ElementVocalState = ElementVocalState.NotSung,
    var animatedState: ElementVocalState? = null,
    var settling: Boolean = false,
    var preHidden: Boolean = false,
    var groupActive: Boolean = true,
    var sungAfterActive: Boolean = false,
    var blurAmountPx: Float = 0f,
    var dotGroupBreathScale: Float = 1f
)

class LyricsAnimatorEngine {
    companion object {
        const val LetterGlowMultiplier_Opacity = 185.0
        const val YOffsetDamping = 0.4
        const val YOffsetFrequency = 1.45
        const val ScaleDamping = 0.64
        const val ScaleFrequency = 0.88
        const val GlowDamping = 0.56
        const val GlowFrequency = 1.18
        const val SungLetterGlow = 0.2
        const val PreHiddenDotLineMs = 450.0
        const val DefaultBlurMultiplier = 1.8f

        val ScaleRange = listOf(
            AnimationPoint(0.0, 0.95),
            AnimationPoint(0.7, 1.0505),
            AnimationPoint(1.0, 1.0)
        )
        val LetterScaleRange = listOf(
            AnimationPoint(0.0, 0.95),
            AnimationPoint(0.7, 1.175),
            AnimationPoint(1.0, 1.0)
        )
        val SimpleLetterScaleRange = listOf(
            AnimationPoint(0.0, 0.95),
            AnimationPoint(0.7, 1.07),
            AnimationPoint(1.0, 1.0)
        )
        val YOffsetRange = listOf(
            AnimationPoint(0.0, 1.0 / 100.0),
            AnimationPoint(0.9, -(1.0 / 60.0)),
            AnimationPoint(1.0, 0.0)
        )
        val SimpleYOffsetRange = listOf(
            AnimationPoint(0.0, 1.0 / 100.0),
            AnimationPoint(1.0, -0.033)
        )
        val LetterYOffsetRange = listOf(
            AnimationPoint(0.0, 1.0 / 100.0),
            AnimationPoint(0.9, -(1.0 / 56.0)),
            AnimationPoint(1.0, 0.0)
        )
        val SimpleLetterYOffsetRange = listOf(
            AnimationPoint(0.0, 1.0 / 100.0),
            AnimationPoint(0.9, -(1.0 / 62.0)),
            AnimationPoint(1.0, 0.0)
        )
        val GlowRange = listOf(
            AnimationPoint(0.0, 0.0),
            AnimationPoint(0.15, 1.0),
            AnimationPoint(0.6, 1.0),
            AnimationPoint(1.0, 0.0)
        )
        val DotScaleRange = listOf(
            AnimationPoint(0.0, 0.75),
            AnimationPoint(0.7, 1.05),
            AnimationPoint(1.0, 1.0)
        )
        val DotYOffsetRange = listOf(
            AnimationPoint(0.0, 0.0),
            AnimationPoint(0.9, -0.12),
            AnimationPoint(1.0, 0.0)
        )
        val DotGlowRange = listOf(
            AnimationPoint(0.0, 0.0),
            AnimationPoint(0.6, 1.0),
            AnimationPoint(1.0, 1.0)
        )

        fun getDotOpacityRange(simpleLyricsMode: Boolean) = listOf(
            AnimationPoint(0.0, if (simpleLyricsMode) 0.27 else 0.35),
            AnimationPoint(0.6, 1.0),
            AnimationPoint(1.0, 1.0)
        )

        fun cubicBezier(x1: Double, y1: Double, x2: Double, y2: Double): (Double) -> Double {
            val cx = 3.0 * x1
            val bx = 3.0 * (x2 - x1) - cx
            val ax = 1.0 - cx - bx
            val cy = 3.0 * y1
            val by = 3.0 * (y2 - y1) - cy
            val ay = 1.0 - cy - by
            val curveX = { t: Double -> ((ax * t + bx) * t + cx) * t }
            val curveY = { t: Double -> ((ay * t + by) * t + cy) * t }
            val slopeX = { t: Double -> (3.0 * ax * t + 2.0 * bx) * t + cx }
            return { x: Double ->
                if (x <= 0.0) 0.0
                else if (x >= 1.0) 1.0
                else {
                    var t = x
                    var converged = false
                    for (i in 0 until 8) {
                        val err = curveX(t) - x
                        if (abs(err) < 1e-6) {
                            converged = true
                            break
                        }
                        val slope = slopeX(t)
                        if (abs(slope) < 1e-6) break
                        t = clamp(t - err / slope, 0.0, 1.0)
                    }
                    if (!converged) {
                        var lo = 0.0
                        var hi = 1.0
                        t = x
                        while (hi - lo > 1e-6) {
                            if (curveX(t) < x) lo = t else hi = t
                            t = (lo + hi) / 2.0
                        }
                    }
                    curveY(t)
                }
            }
        }

        private val emphasisIn = cubicBezier(0.2, 0.4, 0.58, 1.0)
        private val emphasisOut = cubicBezier(0.3, 0.0, 0.58, 1.0)

        fun getEmphasis(x: Double): Double {
            if (x <= 0.0 || x >= 1.0) return 0.0
            return if (x < 0.5) emphasisIn(x / 0.5) else 1.0 - emphasisOut((x - 0.5) / 0.5)
        }

        fun easeSinOut(x: Double): Double {
            return sin((clamp(x, 0.0, 1.0) * PI) / 2.0)
        }

        fun smoothstep(x: Double): Double {
            val t = clamp(x, 0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }

        fun easeInOutCos(x: Double): Double {
            return 0.5 - 0.5 * cos(PI * clamp(x, 0.0, 1.0))
        }
    }

    private val scaleSpline = getSpline(ScaleRange)
    private val glowSpline = getSpline(GlowRange)
    private val dotScaleSpline = getSpline(DotScaleRange)
    private val dotYOffsetSpline = getSpline(DotYOffsetRange)
    private val dotGlowSpline = getSpline(DotGlowRange)

    private var lastFrameNanos: Long = 0L

    private fun getElementState(currentTime: Double, startTime: Double, endTime: Double): ElementVocalState {
        if (currentTime < startTime) return ElementVocalState.NotSung
        if (currentTime >= endTime) return ElementVocalState.Sung
        return ElementVocalState.Active
    }

    private fun getProgressPercentage(currentTime: Double, startTime: Double, endTime: Double): Double {
        if (currentTime <= startTime) return 0.0
        if (currentTime >= endTime) return 1.0
        return (currentTime - startTime) / (endTime - startTime)
    }

    private data class RefreshedWaveStrength(
        val amount: Double,
        val blur: Double,
        val duration: Double
    )

    private fun getRefreshedWaveStrength(durationMs: Double, isLastWord: Boolean): RefreshedWaveStrength {
        val grow = { x: Double -> if (x > 1.0) sqrt(x) else x.pow(3.0) }
        var du = max(1000.0, durationMs)
        var amount = grow(du / 2000.0) * 0.6
        var blur = grow(du / 3000.0) * 0.5
        if (isLastWord) {
            amount *= 1.6
            blur *= 1.5
            du *= 1.2
        }
        return RefreshedWaveStrength(
            amount = min(1.2, amount),
            blur = min(0.8, blur),
            duration = du
        )
    }

    private fun getRefreshedFloat(startTime: Double, endTime: Double, position: Double): Double {
        val x = clamp((position - startTime) / max(1000.0, endTime - startTime), 0.0, 1.0)
        return 1.0 - (1.0 - x) * (1.0 - x)
    }

    fun animate(
        lines: List<SyllableLineModel>,
        rawPositionMs: Double,
        timeOffsetMs: Double,
        simpleLyricsMode: Boolean,
        refreshedSimpleLyrics: Boolean,
        glowMultiplierUser: Float = 1.0f,
        frameNanos: Long = System.nanoTime()
    ): Int {
        if (lines.isEmpty()) return -1

        val dtSeconds = if (lastFrameNanos == 0L) {
            1.0 / 60.0
        } else {
            ((frameNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.001, 0.064)
        }
        lastFrameNanos = frameNanos

        val processedPosition = rawPositionMs + timeOffsetMs - (if (simpleLyricsMode) 33.5 else 0.0)
        val refreshedSimple = simpleLyricsMode && refreshedSimpleLyrics
        val letterRest = if (refreshedSimple) 1.0 else 0.0

        val yOffsetSpline = getSpline(if (simpleLyricsMode) SimpleYOffsetRange else YOffsetRange)
        val letterScaleSpline = getSpline(if (simpleLyricsMode) SimpleLetterScaleRange else LetterScaleRange)
        val letterYOffsetSpline = getSpline(if (simpleLyricsMode) SimpleLetterYOffsetRange else LetterYOffsetRange)
        val dotOpacitySpline = getSpline(getDotOpacityRange(simpleLyricsMode))

        var activeLineIndex = -1
        for (i in lines.indices) {
            if (lines[i].isBgLine) continue
            val st = getElementState(processedPosition, lines[i].startTimeMs, lines[i].endTimeMs)
            if (st == ElementVocalState.Active) {
                activeLineIndex = i
                break
            }
        }
        if (activeLineIndex == -1) {
            for (i in lines.indices) {
                if (processedPosition < lines[i].startTimeMs) {
                    activeLineIndex = max(0, i - 1)
                    break
                }
            }
            if (activeLineIndex == -1) activeLineIndex = lines.lastIndex
        }

        // Background lines stay visible alongside their lead vocal line
        for (line in lines) {
            line.groupActive = true
        }

        val maxBlur = DefaultBlurMultiplier * 4.5f

        for (index in lines.indices) {
            val line = lines[index]
            val lineState = getElementState(processedPosition, line.startTimeMs, line.endTimeMs)
            if (line.animatedState != lineState) {
                line.settling = lineState == ElementVocalState.Sung
                line.animatedState = lineState
            }
            line.state = lineState

            val distance = abs(index - activeLineIndex)
            line.blurAmountPx = if (lineState == ElementVocalState.Active || distance <= 1) {
                0f
            } else {
                min(DefaultBlurMultiplier * (distance - 1), maxBlur)
            }

            if (line.isDotLine) {
                line.preHidden = when (lineState) {
                    ElementVocalState.Active -> processedPosition > line.endTimeMs - PreHiddenDotLineMs
                    ElementVocalState.NotSung -> true
                    ElementVocalState.Sung -> false
                }
            }

            val words = line.words
            var floatCap = 1.0
            for (wordIndex in words.indices) {
                val word = words[wordIndex]
                val wordState = getElementState(processedPosition, word.startTimeMs, word.endTimeMs)
                word.state = wordState
                val percentage = getProgressPercentage(processedPosition, word.startTimeMs, word.endTimeMs)

                if (!word.isDot) {
                    if (word.scaleSpring == null) {
                        word.scaleSpring = LyricsSpring(scaleSpline.at(0.0), ScaleFrequency, ScaleDamping)
                        word.yOffsetSpring = LyricsSpring(
                            yOffsetSpline.at(0.0),
                            YOffsetFrequency,
                            if (refreshedSimple) 1.0 else YOffsetDamping
                        )
                        word.glowSpring = LyricsSpring(glowSpline.at(0.0), GlowFrequency, GlowDamping)
                    }

                    val targetScale: Double
                    var targetYOffset: Double
                    val targetGlow: Double
                    val targetFill: Float

                    when (wordState) {
                        ElementVocalState.Active -> {
                            targetScale = if (simpleLyricsMode) 1.0 else scaleSpline.at(percentage)
                            targetYOffset = yOffsetSpline.at(percentage)
                            targetGlow = if (simpleLyricsMode) 0.0 else glowSpline.at(percentage)
                            targetFill = percentage.toFloat().coerceIn(0f, 1f)
                        }
                        ElementVocalState.NotSung -> {
                            targetScale = if (simpleLyricsMode) 1.0 else scaleSpline.at(0.0)
                            targetYOffset = yOffsetSpline.at(0.0)
                            targetGlow = 0.0
                            // Strictly 0.0f when NotSung so not even a single letter is pre-lit!
                            targetFill = 0f
                        }
                        ElementVocalState.Sung -> {
                            targetScale = 1.0
                            targetYOffset = yOffsetSpline.at(1.0)
                            targetGlow = glowSpline.at(1.0)
                            targetFill = 1f
                        }
                    }

                    if (refreshedSimple && (lineState == ElementVocalState.Active || lineState == ElementVocalState.Sung)) {
                        floatCap = min(
                            floatCap,
                            getRefreshedFloat(word.startTimeMs, word.endTimeMs, processedPosition)
                        )
                        targetYOffset = yOffsetSpline.at(floatCap)
                    }

                    word.scaleSpring!!.setGoal(targetScale)
                    word.yOffsetSpring!!.setGoal(targetYOffset, refreshedSimple)
                    word.glowSpring!!.setGoal(targetGlow)

                    word.currentScale = word.scaleSpring!!.step(dtSeconds).toFloat()
                    word.currentYOffsetEm = word.yOffsetSpring!!.step(dtSeconds).toFloat()
                    val currentGlow = word.glowSpring!!.step(dtSeconds).toFloat() * glowMultiplierUser
                    word.fillFraction = targetFill
                    word.currentGradientPercent = -50f + 150f * targetFill
                    word.currentGlowRadiusPx = 4f + 6f * currentGlow
                    word.currentGlowOpacity = if (wordState == ElementVocalState.Active) {
                        (currentGlow * 0.45f).coerceIn(0f, 1f)
                    } else 0f

                    if (word.letterGroup && word.letters.isNotEmpty()) {
                        val waveStrength = if (refreshedSimple) {
                            getRefreshedWaveStrength(word.endTimeMs - word.startTimeMs, word.isLastWordInLine)
                        } else null

                        var activeLetterIndex = -1
                        var activeLetterPercentage = 0.0
                        if (wordState == ElementVocalState.Active) {
                            for (i in word.letters.indices) {
                                val ltr = word.letters[i]
                                if (getElementState(processedPosition, ltr.startTimeMs, ltr.endTimeMs) == ElementVocalState.Active) {
                                    activeLetterIndex = i
                                    activeLetterPercentage = getProgressPercentage(processedPosition, ltr.startTimeMs, ltr.endTimeMs)
                                    break
                                }
                            }
                        }

                        val count = max(1, word.letters.size)
                        val firstLetterStart = word.letters.first().startTimeMs
                        val floatAmp = 0.05 * (if (word.isBgWord) 2.0 else 1.0)

                        for (k in word.letters.indices) {
                            val letter = word.letters[k]
                            if (letter.scaleSpring == null) {
                                letter.scaleSpring = LyricsSpring(letterScaleSpline.at(letterRest), ScaleFrequency, ScaleDamping)
                                letter.yOffsetSpring = LyricsSpring(letterYOffsetSpline.at(letterRest), YOffsetFrequency, YOffsetDamping)
                                letter.glowSpring = LyricsSpring(glowSpline.at(0.0), GlowFrequency, GlowDamping)
                            }

                            val letterState = getElementState(processedPosition, letter.startTimeMs, letter.endTimeMs)
                            letter.state = letterState

                            // Strictly 0f when NotSung, 0..1 when Active, 1f when Sung
                            val letterFill: Float = when (letterState) {
                                ElementVocalState.NotSung -> 0f
                                ElementVocalState.Sung -> 1f
                                ElementVocalState.Active -> easeSinOut(
                                    getProgressPercentage(processedPosition, letter.startTimeMs, letter.endTimeMs)
                                ).toFloat().coerceIn(0f, 1f)
                            }
                            letter.fillFraction = letterFill
                            letter.currentGradientPercent = -50f + 150f * letterFill

                            if (waveStrength != null && wordState != ElementVocalState.NotSung) {
                                val du = waveStrength.duration
                                val at = processedPosition - firstLetterStart - ((du * (1.0 / 2.5)) / count) * k
                                val swell = getEmphasis(at / du)
                                val floatAt = (at + 400.0) / (du * 1.4)
                                val floatVal = if (floatAt > 0.0 && floatAt < 1.0) sin(floatAt * PI) * floatAmp else 0.0
                                val xEm = -swell * 0.03 * waveStrength.amount * (count / 2.0 - k)
                                val yEm = -swell * 0.025 * waveStrength.amount - floatVal
                                letter.currentTranslateXEm = xEm.toFloat()
                                letter.currentTranslateYEm = yEm.toFloat()
                                letter.currentScale = (1.0 + swell * 0.1 * waveStrength.amount).toFloat()
                                val alpha = swell * waveStrength.blur * (0.4 + 0.6 * letterFill)
                                letter.currentGlowRadiusPx = (min(0.3, waveStrength.blur * 0.3) * 28.0).toFloat()
                                letter.currentGlowOpacity = (alpha * glowMultiplierUser).toFloat().coerceIn(0f, 1f)
                            } else {
                                var lTargetScale = letterScaleSpline.at(if (wordState == ElementVocalState.Sung) 1.0 else letterRest)
                                var lTargetY = letterYOffsetSpline.at(if (wordState == ElementVocalState.Sung) 1.0 else letterRest)
                                var lTargetGlow = glowSpline.at(if (wordState == ElementVocalState.Sung) 1.0 else 0.0)

                                if (wordState == ElementVocalState.Active && activeLetterIndex != -1) {
                                    val pctCount = if (simpleLyricsMode) percentage else activeLetterPercentage
                                    val baseScale = letterScaleSpline.at(pctCount)
                                    val baseY = letterYOffsetSpline.at(pctCount)
                                    val baseGlow = glowSpline.at(pctCount)
                                    val restScale = letterScaleSpline.at(0.0)
                                    val restY = letterYOffsetSpline.at(0.0)
                                    val restGlow = glowSpline.at(0.0)
                                    val dist = abs(k - activeLetterIndex).toDouble()
                                    val falloff = max(0.0, 1.0 / (1.0 + dist.pow(2.8)))
                                    val glowFalloff = max(0.0, 1.0 / (1.0 + dist * 0.9))
                                    lTargetScale = restScale + (baseScale - restScale) * falloff
                                    lTargetY = restY + (baseY - restY) * falloff
                                    lTargetGlow = restGlow + (baseGlow - restGlow) * glowFalloff
                                } else if (letterState == ElementVocalState.Sung && activeLetterIndex == -1) {
                                    lTargetGlow = glowSpline.at(SungLetterGlow)
                                }

                                letter.scaleSpring!!.setGoal(lTargetScale)
                                letter.yOffsetSpring!!.setGoal(lTargetY)
                                letter.glowSpring!!.setGoal(lTargetGlow)

                                letter.currentScale = letter.scaleSpring!!.step(dtSeconds).toFloat()
                                letter.currentTranslateXEm = 0f
                                letter.currentTranslateYEm = (letter.yOffsetSpring!!.step(dtSeconds) * 2.0).toFloat()
                                val lGlow = letter.glowSpring!!.step(dtSeconds).toFloat() * glowMultiplierUser
                                letter.currentGlowRadiusPx = 4f + 12f * lGlow
                                letter.currentGlowOpacity = if (letterState == ElementVocalState.Active) {
                                    ((lGlow * LetterGlowMultiplier_Opacity) / 100.0).toFloat().coerceIn(0f, 1f)
                                } else 0f
                            }
                        }
                    }
                } else {
                    if (refreshedSimple) {
                        val elapsed = processedPosition - line.startTimeMs
                        val fill = easeInOutCos(percentage)
                        val enter = smoothstep((elapsed - wordIndex * 120.0) / 650.0)
                        val opacity = (0.22 + (0.92 - 0.22) * fill) * enter
                        word.currentDotOpacity = opacity.toFloat()
                        word.currentScale = 1f
                        word.currentYOffsetEm = 0f
                        word.currentGlowOpacity = 0f

                        if (wordIndex == 0) {
                            val room = line.endTimeMs - line.startTimeMs - PreHiddenDotLineMs
                            var breathScale = 1.0
                            if (room >= 2600.0) {
                                val cycles = max(1.0, (room / 4200.0).roundToInt().toDouble())
                                val phase = clamp(elapsed / (room / cycles), 0.0, cycles)
                                breathScale = 1.0 + 0.16 * (0.5 - 0.5 * cos(2.0 * PI * phase))
                            }
                            line.dotGroupBreathScale = breathScale.toFloat()
                        }
                    } else {
                        line.dotGroupBreathScale = 1f
                        if (word.scaleSpring == null) {
                            word.scaleSpring = LyricsSpring(dotScaleSpline.at(0.0), 0.7, 0.6)
                            word.yOffsetSpring = LyricsSpring(dotYOffsetSpline.at(0.0), 1.25, 0.4)
                            word.glowSpring = LyricsSpring(dotGlowSpline.at(0.0), 1.0, 0.5)
                            word.opacitySpring = LyricsSpring(dotOpacitySpline.at(0.0), 1.0, 0.5)
                        }
                        val tAt = when (wordState) {
                            ElementVocalState.Active -> percentage
                            ElementVocalState.NotSung -> 0.0
                            ElementVocalState.Sung -> 1.0
                        }
                        word.scaleSpring!!.setGoal(if (simpleLyricsMode) 1.0 else dotScaleSpline.at(tAt))
                        word.yOffsetSpring!!.setGoal(if (simpleLyricsMode) 0.0 else dotYOffsetSpline.at(tAt))
                        word.glowSpring!!.setGoal(if (simpleLyricsMode) 0.0 else dotGlowSpline.at(tAt))
                        word.opacitySpring!!.setGoal(dotOpacitySpline.at(tAt))

                        word.currentScale = word.scaleSpring!!.step(dtSeconds).toFloat()
                        word.currentYOffsetEm = word.yOffsetSpring!!.step(dtSeconds).toFloat()
                        val dGlow = word.glowSpring!!.step(dtSeconds).toFloat() * glowMultiplierUser
                        word.currentDotOpacity = word.opacitySpring!!.step(dtSeconds).toFloat()
                        word.currentGlowRadiusPx = 4f + 6f * dGlow
                        word.currentGlowOpacity = (dGlow * 0.9f).coerceIn(0f, 1f)
                    }
                }
            }
        }

        val firstActive = lines.indexOfFirst { it.state == ElementVocalState.Active }
        for (i in lines.indices) {
            lines[i].sungAfterActive = firstActive != -1 && i > firstActive && lines[i].state == ElementVocalState.Sung
        }

        return activeLineIndex
    }
}
