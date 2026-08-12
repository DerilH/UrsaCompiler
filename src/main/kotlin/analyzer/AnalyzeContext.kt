package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.target.TargetInfo

interface AnalyzeContext {

    val target: TargetInfo
    val scope: Scope
    fun enterScope(scopeType: ScopeType)
    fun leaveScope()
    fun <T : ASTNode> findAnalyzer(node: T): NodeAnalyzer<T>
    fun warn(message: String, node: ASTNode? = null)
    fun error(message: String, node: ASTNode? = null)
}