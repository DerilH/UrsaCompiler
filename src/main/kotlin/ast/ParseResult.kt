package org.derilh.ast

import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SyntaxProblem

data class ParseResult(val problems: Map<ProblemLevel, List<SyntaxProblem>>, val root: RootNode) {
    fun isSuccess() = problems[ProblemLevel.ERROR].isNullOrEmpty()
}
