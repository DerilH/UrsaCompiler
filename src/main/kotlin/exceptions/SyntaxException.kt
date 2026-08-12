package org.derilh.exceptions

import org.derilh.lexer.Token

class SyntaxException(val msg: String, val token: Token, val tokenId: Int) : Exception("Syntax error at ${token.location}: $token -> $msg") {
}