package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.ConstructorDeclarationNode
import org.derilh.ast.ConstructorDefinitionNode
import org.derilh.ast.StatementNode
import org.derilh.core.FunctionQualifiers
import org.derilh.core.getAsOrElse
import org.derilh.util.ErrorHelper

class ConstructorDeclAnalyzer : NodeAnalyzer<ConstructorDeclarationNode> {
    override fun analyze(node: ConstructorDeclarationNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need to check is node.id.name is unqualified-id for declaration only
//        node.functionDecl.signatureType = ctx.resolveType(node.type, ctx.scope).getAsOrElse { ctx.error(it); ctx.types.getErrorFunction() }

//        val classDecl = ctx.scope.findCurrentClass();
//        if (classDecl == null) {
//            ctx.error("Constructor declaration outside of class", node)
//            return node;
//        }
//
//        checkOverloads(node.overloadSet ?: return node, node.ctorDecl, ctx)
//
//        val type = node.type;
//        return ctx.withScope(node.ctorDecl.scope) {
//            var hasDefault = false;
//            for (param in node.type.params) {
//                ctx.analyze(param, ctx.scope)
//                if (hasDefault && !param.hasDefaultValue) {
//                    ctx.error("Missing default value on parameter ${param.name?.toDisplayString()}", node = param)
//                } else hasDefault = hasDefault || param.hasDefaultValue;
//            }
//            checkMethodQualifiers(node.ctorDecl, type.qualifiers, ctx)
//            node;
//        }
        return node;
    }
}

class ConstructorDefAnalyzer : NodeAnalyzer<ConstructorDefinitionNode> {
    override fun analyze(node: ConstructorDefinitionNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need to check is node.id.name is unqualified-id for declaration only
//        node.ctorDecl.signatureType = ctx.resolveType(node.type, ctx.scope).getAsOrElse { ctx.error(it); return node }
//
//        val classDecl = ctx.scope.findCurrentClass();
//        if (classDecl == null) {
//            ctx.error("Constructor declaration outside of class", node)
//            return node;
//        }
//        checkOverloads(node.overloadSet ?: return node, node.ctorDecl, ctx)
//
//
//        val type = node.type;
//        return ctx.withScope(node.ctorDecl.scope) {
//            var hasDefault = false;
//            for (param in node.type.params) {
//                ctx.analyze(param, ctx.scope)
//                if (hasDefault && !param.hasDefaultValue) {
//                    ctx.error("Missing default value on parameter ${param.name?.toDisplayString()}", node = param)
//                } else hasDefault = hasDefault || param.hasDefaultValue;
//            }
//
//            node.body = ctx.analyze(node.body, ctx.scope) as StatementNode
//            ctx.error("Implement constructor member init analyzer", node)
//            checkMethodQualifiers(node.ctorDecl, type.qualifiers, ctx)
//            node;
//        }
//    }
        return node;
    }
}

private fun checkMethodQualifiers(decl: DeclSymbol.FunctionDecl, qual: FunctionQualifiers, ctx: AnalyzeContext) {
    if (!decl.isMethod) {
//        if (qual.isConst || qual.isVolatile || qual.refQualifier != RefQualifier.NONE) {
//            ctx.error("Function declaration cannot have cv-qualifiers and ref-qualifiers")
//        }
    }
}

fun checkOverloads(overloadSet: DeclSymbol.FunctionOverloadSet, functionDecl: DeclSymbol.ConstructorDecl, ctx: AnalyzeContext): DeclSymbol.FunctionDecl? {
    var firstDecl: DeclSymbol.FunctionDecl? = null;

    for (overload in overloadSet.overloads) {
        if (!overload.processed) break;

        if (ctx.isSameOverloadFun(overload.signatureType, functionDecl.signatureType)) {
            firstDecl = overload;
            break
        }
    }

    if (firstDecl != null) {
        ctx.error(ErrorHelper.alreadyDefined(functionDecl, firstDecl))
        overloadSet.overloads.removeAll { it === functionDecl }
        return firstDecl
    }
    return null
}