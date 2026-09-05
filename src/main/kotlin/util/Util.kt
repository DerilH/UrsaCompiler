package org.derilh.util
import kotlin.math.pow
import kotlin.reflect.KProperty

class Util {
    companion object {
        fun codePointsToUtf16Filtered(codePoints: IntArray): String {
            return buildString {
                for (cp in codePoints) {
                    if (Character.isValidCodePoint(cp)) {
                        when (cp) {
                            '\n'.code -> append("\\n")
                            '\r'.code -> append("\\r")
                            '\t'.code -> append("\\t")
                            '\b'.code -> append("\\b")
                            '\\'.code -> append("\\\\")
                            else -> {
                                // Для остальных непечатных/управляющих символов используем Unicode-формат \uXXXX
                                if (Character.isISOControl(cp)) {
                                    append(String.format("\\u%04X", cp))
                                } else {
                                    appendCodePoint(cp)
                                }
                            }
                        }
                    } else {
                        append('?')
                    }
                }
            }
        }


        fun codePointsToUtf16Unescaped(codePoints: IntArray): String {
            return buildString {
                for (cp in codePoints) {
                    if (Character.isValidCodePoint(cp)) {
                        if (cp == 0) continue

                        if (Character.isISOControl(cp)) {
                            when (cp) {
                                '\n'.code, '\t'.code, '\r'.code -> appendCodePoint(cp)
                                else -> append(String.format("\\u%04X", cp))
                            }
                        } else {
                            appendCodePoint(cp)
                        }
                    } else {
                        append('?')
                    }
                }
            }
        }

        fun maxValueForByTypeWidth(bitWidth: Long, signed: Boolean): ULong {

            require(bitWidth in 1..64) { "Bit width must be between 1 and 64." }
            val bitWidth = bitWidth.toInt()

            val targetBits = if (signed) bitWidth - 1 else bitWidth
            return if (targetBits == 64) ULong.MAX_VALUE else (1UL shl  targetBits) - 1UL
        }

        fun alignUp(offset: Long, align: Long): Long {
            val remainder = offset % align
            return if (remainder == 0L) offset else offset + (align - remainder)
        }
        fun utf16ToCodePoints(text: String): IntArray {
            val codePointCount = text.codePointCount(0, text.length)
            val result = IntArray(codePointCount)

            var charIndex = 0
            var codePointIndex = 0

            while (charIndex < text.length) {
                val codePoint = text.codePointAt(charIndex)
                result[codePointIndex++] = codePoint
                charIndex += Character.charCount(codePoint)
            }

            return result
        }
    }
}

class ResettableLazyUntilNonNull<T : Any>(private val initializer: () -> T?) {
    private var value: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        if (value == null) {
            value = initializer()
        }
        return value
    }
}

fun <T : Any> lazyUntilNonNull(initializer: () -> T?) = ResettableLazyUntilNonNull(initializer)
