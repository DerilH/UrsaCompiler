package org.derilh.lexer

data class SourceLocation(val line: Int, val column: Int, val length: Int, val file: String? = null) {
    companion object {
        val EXPORTED = SourceLocation(0, 0, 0, )
    }
    override fun toString(): String = (if (file != null) "$file:" else "") + "$line:$column"
}
