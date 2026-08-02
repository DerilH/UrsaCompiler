package org.derilh

import org.derilh.ast.Parser
import org.derilh.lexer.Lexer
import org.derilh.util.printTree
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

fun main() {
    val fileName = "sample.h"
    var code = Files.readString(Path.of(fileName))

    val preProcessor = PreProcessor();
    code = preProcessor.preProcess(code, fileName)

    val lexer = Lexer()
    val tokens = lexer.tokenize(code)
    tokens.forEachIndexed { index, token ->  println("${token.location.line} : $token") }

    val parser = Parser()
    val root = parser.parseRoot(tokens)
    root.printTree()
}