package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.CompoundStatementNode
import org.derilh.ast.DeclaratorNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.FunctionDeclarationNode
import org.derilh.ast.RefQualifier
import org.derilh.ast.RootNode
import org.derilh.ast.TypeNode
import org.derilh.ast.VariableDeclarationNode
import org.derilh.exceptions.SemanticException
import org.derilh.target.TargetInfo

class SemanticAnalyzer(val ast: RootNode, override val target: TargetInfo) : AnalyzeContext {
    var innerScope: Scope? = null
    override val scope: Scope
        get() = innerScope ?: throw SemanticException("Not in any scope")

    private val analyzers = hashMapOf<Class<out ASTNode>, NodeAnalyzer<out ASTNode>>()
    fun analyze() {
        enterScope(ScopeType.ROOT)
        for (child in ast.declarations) {
            findAnalyzer(child).analyze(child, this)
        }
        leaveScope()
    }

    override fun enterScope(scopeType: ScopeType) {
        innerScope = Scope(scopeType, innerScope)
    }

    override fun leaveScope() {
        if (innerScope == null) throw SemanticException("Already outside of any scope")
        innerScope = innerScope!!.parent
    }

    override fun <T : ASTNode> findAnalyzer(astNode: T): NodeAnalyzer<T> =
        (analyzers[astNode::class.java]
            ?: throw Exception("No analyzer found for ${astNode::class.java}")) as NodeAnalyzer<T>

    override fun warn(message: String, node: ASTNode?) {
        //TODO: male better warnings
        println("message: $message, node: $node")
    }

    fun isEqualTypes(first: TypeNode, second: TypeNode): Boolean = first == second

    fun findImplicitPath(actualType: TypeNode, neededType: TypeNode) {
        //TODO add user define casts

    }


    private inner class BlockAnalyzer : NodeAnalyzer<CompoundStatementNode> {
        override fun analyze(node: CompoundStatementNode, ctx: AnalyzeContext): ASTNode {
            val createScope = ctx.scope.type != ScopeType.FUNCTION && ctx.scope.type != ScopeType.METHOD
            if (createScope)
                enterScope(ScopeType.BLOCK)

            for (child in node.statements) {
                findAnalyzer(child).analyze(child, ctx)
            }

            if (createScope)
                leaveScope()

            return node;
        }
    }

    private class FunctionAnalyzer : NodeAnalyzer<FunctionDeclarationNode> {
        private fun analyzeParams(funcDecl: FunctionDeclarationNode, ctx: AnalyzeContext) {
            for (param in funcDecl.type.params) {
                if(param.name != null)
                    ctx.scope.define(param.declarator)
            }
        }

        override fun analyze(node: FunctionDeclarationNode, ctx: AnalyzeContext): ASTNode {
            ctx.scope.define(node.declarator)

            when (ctx.scope.type) {
                ScopeType.ROOT -> ctx.enterScope(ScopeType.FUNCTION)
                ScopeType.CLASS -> ctx.enterScope(ScopeType.METHOD)
                ScopeType.STRUCT -> ctx.enterScope(ScopeType.METHOD)
                else -> throw SemanticException("Invalid scope for function declaration only root, class or struct as for now")
            }

            analyzeParams(node, ctx)

            if (ctx.scope.type == ScopeType.FUNCTION) {
                val qual = node.type.qualifiers;

                if (qual.isConst || qual.isVolatile || qual.refQualifier != RefQualifier.NONE)
                    throw SemanticException("Function declaration cannot have cv-qualifiers and ref-qualifiers")
            }

            ctx.findAnalyzer(node.body).analyze(node.body, ctx)
            ctx.leaveScope()
            return node;
        }
    }
}