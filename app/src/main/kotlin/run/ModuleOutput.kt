package run

import org.derilh.ast.ParseResult
import org.derilh.ast.Parser
import org.derilh.core.Options
import org.derilh.lexer.Lexer
import org.derilh.lexer.Token
import org.derilh.semantic.AnalyzeResult
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.util.Printer

class ModuleOutput(val sourceCode: String, val options: Options) {
    var lexer: Lexer? = null;
    var tokens: List<Token>? = null;
    var parser: Parser? = null;
    var sema: SemanticAnalyzer? = null;
    var analyzeResult: AnalyzeResult? = null;
    var parseResult: ParseResult? = null;

    fun createPrinter(): Printer {
        return Printer(sourceCode);
    }

    fun hasErrors(): Boolean {
        if(analyzeResult != null && !analyzeResult!!.isSuccess()) return true;
        if(parseResult != null && !parseResult!!.isSuccess()) return true;
        return false;
    }

    fun isValidForCodegen(): Boolean {
        if(analyzeResult == null || parseResult == null) return false;
        return analyzeResult!!.isSuccess() && parseResult!!.isSuccess();
    }
}