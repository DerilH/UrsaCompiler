package org.derilh.lexer

data class SourceLocation(val line: Int, val column: Int, val file: String? = null) {
    override fun toString(): String = (if (file != null) "$file:" else "") + "$line:$column"
}
