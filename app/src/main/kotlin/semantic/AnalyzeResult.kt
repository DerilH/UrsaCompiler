package org.derilh.semantic

import org.derilh.ast.ASTNode
import org.derilh.exceptions.ProblemLevel
import org.derilh.exceptions.SemanticProblem

data class AnalyzeResult(val problems: Map<ProblemLevel, List<SemanticProblem>>, val ast: ASTNode) {
    fun isSuccess() = problems[ProblemLevel.ERROR].isNullOrEmpty()
}