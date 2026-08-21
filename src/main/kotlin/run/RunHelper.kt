package org.derilh.run

import org.derilh.PreProcessor
import org.derilh.ast.Parser
import org.derilh.lexer.Lexer
import org.derilh.semantic.AnalyzeResult
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.target.X86_64LinuxTargetInfo
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class RunHelper {
    companion object{

        fun analyze(code: String, path:Path, target: String): AnalyzeResult {
            val preProcessor = PreProcessor();
            val code = preProcessor.preProcess(code, path)

            val lexer = Lexer()
            val tokens = lexer.tokenize(code)

            val parser = Parser(tokens)
            val ast = parser.parse();


            val targetInfo = when(target) {
                "x86_64Linux" -> X86_64LinuxTargetInfo();
                else -> throw IllegalArgumentException("Unknown target: $target")
            }

            val analyzer = SemanticAnalyzer(ast, targetInfo);

            return analyzer.analyze();
        }
    }
}