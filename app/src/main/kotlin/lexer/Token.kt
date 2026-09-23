package org.derilh.lexer

import org.derilh.core.CharPrefix
import org.derilh.core.Keyword
import org.derilh.core.Operator
import org.derilh.core.Radix
import org.derilh.core.SourceLocation
import org.derilh.core.Symbol
import org.derilh.util.Util
import java.math.BigInteger

sealed class Token(val location: SourceLocation, val leadingWhitespace: Boolean) {
    abstract val stringValue: String
    open val isIdentifierLike: Boolean get() = false;

    open infix fun isA(other: Any): Boolean = false
    open infix fun oneOf(other: List<Any>): Boolean {
        for (a in other) {
            return if (isA(a)) true
            else continue
        }
        return false;
    }

    open infix fun notA(other: Any): Boolean = !isA(other)
    abstract fun copy(location: SourceLocation? = null, leadingWhitespace: Boolean? = null): Token;
}

class EofToken(location: SourceLocation) : Token(location, false) {
    override val stringValue: String get() = ""
    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return EofToken(location ?: this.location)
    }
}

class IdToken(val value: String, location: SourceLocation, leadingWhitespace: Boolean) : Token(location, leadingWhitespace) {
    override val stringValue: String get() = value

    override val isIdentifierLike: Boolean
        get() = true

    override fun toString(): String {
        return "Id('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if(other !is String) return false;
        return value == other
    }

    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return IdToken(value, location ?: this.location, leadingWhitespace?: this.leadingWhitespace)
    }
}

abstract class ValueToken<T>(val value: T, location: SourceLocation, leadingWhitespace: Boolean) : Token(location, leadingWhitespace)
class BooleanToken(value: Boolean, location: SourceLocation, leadingWhitespace: Boolean) : ValueToken<Boolean>(value, location, leadingWhitespace) {
    override val stringValue: String
        get() = value.toString()
    override val isIdentifierLike: Boolean
        get() = true
    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return BooleanToken(value, location ?: this.location, leadingWhitespace?: this.leadingWhitespace)
    }

    companion object {
        fun resolve(name: String, location: SourceLocation, leadingWhitespace: Boolean): BooleanToken? =
            when (name) {
                "true" -> BooleanToken(true, location, leadingWhitespace)
                "false" -> BooleanToken(false, location, leadingWhitespace)
                else -> null
            }
    }

    override fun toString(): String {
        return "BooleanVal('$value')"
    }
}

class IntToken(value: BigInteger, val radix: Radix, val isUnsigned: Boolean, val isLong: Boolean, val isLongLong: Boolean, val isSizeT: Boolean, location: SourceLocation, leadingWhitespace: Boolean) : ValueToken<BigInteger>(value, location, leadingWhitespace) {
    override val stringValue: String get() = value.toString()
    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return IntToken(value, radix, isUnsigned, isLong, isLongLong, isSizeT, location ?: this.location, leadingWhitespace?:this.leadingWhitespace)
    }

    override fun toString(): String {
        return "IntVal('$value')"
    }
}

class FloatToken(value: String, val isDouble: Boolean, val isLong: Boolean, location: SourceLocation, leadingWhitespace: Boolean) : ValueToken<String>(value, location, leadingWhitespace) {
    override val stringValue: String get() = value
    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return FloatToken(value, isDouble, isLong, location ?: this.location, leadingWhitespace?:this.leadingWhitespace)
    }

    override fun toString(): String {
        return "FloatVal('$value')"
    }
}

class CharToken(value: IntArray, val prefix: CharPrefix, location: SourceLocation, leadingWhitespace: Boolean) : ValueToken<IntArray>(value, location, leadingWhitespace) {
    override val stringValue: String get() = Util.codePointsToUtf16Filtered(value)
    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return CharToken(value, prefix, location ?: this.location, leadingWhitespace?:this.leadingWhitespace)
    }

    override fun toString(): String {
        return "CharVal('${Util.codePointsToUtf16Filtered(value)}')"
    }
}

class StringLiteralToken(value: IntArray, val prefix: CharPrefix, location: SourceLocation, leadingWhitespace: Boolean) : ValueToken<IntArray>(value, location, leadingWhitespace) {
    override val stringValue: String get() = Util.codePointsToUtf16Filtered(value)
    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return StringLiteralToken(value, prefix, location ?: this.location, leadingWhitespace?:this.leadingWhitespace)
    }

    override fun toString(): String {
        return "StringVal('${Util.codePointsToUtf16Filtered(value)}')"
    }
}

class KeywordToken(val value: Keyword, location: SourceLocation, leadingWhitespace: Boolean) : Token(location, leadingWhitespace) {
    override val stringValue: String get() = value.value
    override val isIdentifierLike: Boolean
        get() = true
    companion object {
        private val BY_NAME: Map<String, Keyword> =
            Keyword.entries.associateBy { it.value }

        fun resolve(name: String, location: SourceLocation, leadingWhitespace: Boolean) = BY_NAME[name]?.let { KeywordToken(it, location, leadingWhitespace) }
    }

    override fun toString(): String {
        return "Keyword('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if(other !is Keyword) return false;
        return value == other
    }

    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return KeywordToken(value, location ?: this.location, leadingWhitespace?:this.leadingWhitespace)
    }
}

class SymbolToken(val value: Symbol, location: SourceLocation, leadingWhitespace: Boolean) : Token(location, leadingWhitespace) {
    override val stringValue: String
        get() = value.value.toString()
    companion object {
    }

    override fun toString(): String {
        return "Symbol('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if(other !is Symbol) return false;
        return value == other
    }

    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return SymbolToken(value, location ?: this.location, leadingWhitespace?:this.leadingWhitespace)
    }
}

class OperatorToken(val value: Operator, location: SourceLocation, leadingWhitespace: Boolean) : Token(location, leadingWhitespace) {
    override val stringValue: String get() = value.value

    val size: Int
        get() = value.value.length

    override fun toString(): String {
        return "Operator('$value')"
    }

    override infix fun isA(other: Any): Boolean {
        if (other !is Operator) return false
        return value == other
    }

    override fun copy(location: SourceLocation?, leadingWhitespace: Boolean?): Token {
        return OperatorToken(value, location ?: this.location, leadingWhitespace?:this.leadingWhitespace)
    }
}