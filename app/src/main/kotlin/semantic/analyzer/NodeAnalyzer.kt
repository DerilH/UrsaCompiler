package org.derilh.analyzer

import org.derilh.ast.ASTNode


interface NodeAnalyzer<T : ASTNode> {
    fun analyze(node: T, ctx: AnalyzeContext) : ASTNode;
}