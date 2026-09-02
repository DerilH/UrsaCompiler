package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.AbstractDeclaratorNode
import org.derilh.ast.TypeDefDeclaratorNode
import org.derilh.ast.TypeDefStatementNode
import org.derilh.core.ifFailure
import org.derilh.semantic.SemanticType

class TypeDefStmtAnalyzer : NodeAnalyzer<TypeDefStatementNode> {
    override fun analyze(node: TypeDefStatementNode, ctx: AnalyzeContext): ASTNode {
        ctx.resolveType(node.typeSpecifier, ctx.scope).ifFailure(ctx::error);
        node.declarators.forEach { ctx.analyze(it, ctx.scope); }
        return node;
    }
}

class TypeDefDeclaratorAnalyzer : NodeAnalyzer<TypeDefDeclaratorNode> {
    override fun analyze(node: TypeDefDeclaratorNode, ctx: AnalyzeContext): ASTNode {
        ctx.resolveType(node.type, ctx.scope).ifFailure(ctx::error);
        node.decl.canonicalType = node.type.resolvedType!!
        node.decl.processed = true;
        return node;
    }
}