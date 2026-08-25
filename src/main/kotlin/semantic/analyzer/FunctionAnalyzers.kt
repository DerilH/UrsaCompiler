package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.ClassScope
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.FunctionBodyNode
import org.derilh.ast.FunctionDeclaratorNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.FunctionTypeNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.core.FunctionQualifiers
import org.derilh.core.RefQualifier
import org.derilh.core.getOrElse
import org.derilh.semantic.SemanticType

private fun checkMethodQualifiers(decl: DeclSymbol.FunctionDecl, qual: FunctionQualifiers, ctx: AnalyzeContext) {
    if (!decl.isMethod) {
        if (qual.isConst || qual.isVolatile || qual.refQualifier != RefQualifier.NONE) {
            ctx.error("Function declaration cannot have cv-qualifiers and ref-qualifiers")
        }
    }
}

class FunctionDeclAnalyzer : NodeAnalyzer<FunctionDeclaratorNode> {
    override fun analyze(node: FunctionDeclaratorNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need to check is node.id.name is unqualified-id for declaration only
        val signatureType = ctx.resolveType(node.type, ctx.scope).getOrElse{ ctx.error(it); return node; }
        if(signatureType.hasUndeducedAuto) {
            ctx.error("Cannot deduce return type from function declaration", node)
            return node;
        }

        node.functionDecl.signatureType = signatureType as SemanticType.Function
        node.functionDecl.processed = true;

        if (ctx.scope is ClassScope) {
            if(node.id.name == (ctx.scope.ownerSymbol as DeclSymbol.ClassDecl).name) {
                ctx.error("Constructor cannot have a return type", node)
                return node;
            }
        }

        val type = node.type as FunctionTypeNode
        return ctx.withScope(node.functionDecl.scope) {
            var hasDefault = false;
            for (param in type.params) {
                ctx.analyze(param, ctx.scope)
                if(hasDefault && !param.hasDefaultValue) {
                    ctx.error("Missing default value on parameter ${param.name}", node = param)
                }
                else hasDefault = hasDefault || param.hasDefaultValue;
            }
            checkMethodQualifiers(node.functionDecl, type.qualifiers, ctx)
            node;
        }
    }
}

class FunctionDefAnalyzer : NodeAnalyzer<FunctionDefinitionNode> {

    override fun analyze(node: FunctionDefinitionNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need check for redefinitions
        ctx.analyze(node.declarator, ctx.scope)
        if (ctx.scope is ClassScope) {
            if(node.name.name == (ctx.scope.ownerSymbol as DeclSymbol.ClassDecl).name) {
                ctx.error("Constructor cannot have a return type", node)
                return node;
            }
        }

        node.functionDecl.processed = true;

        return ctx.withScope(node.functionDecl.scope) {
            var hasDefault = false;
            for (param in node.type.params) {
                ctx.analyze(param, ctx.scope)
                if(hasDefault && !param.hasDefaultValue) {
                    ctx.error("Missing default value on parameter ${param.name}", node = param)
                }
                else hasDefault = hasDefault || param.hasDefaultValue;
            }
            checkMethodQualifiers(node.functionDecl, node.type.qualifiers, ctx)

            node.body = ctx.findAnalyzer(node.body).analyze(node.body, ctx) as FunctionBodyNode;
            val bodyRets = node.body.returnStatements
            var funcType = ctx.resolveType(node.type, ctx.scope, null).getOrElse { ctx.error(it); return@withScope node; }
            funcType as SemanticType.Function
            if (bodyRets != null) {
                val bodyReturnType = analyzeReturns(bodyRets, node.functionDecl, ctx) ?: ctx.types.void;

                if(funcType.returnType.hasUndeducedAuto) {
                    funcType = ctx.resolveType(node.type, ctx.scope, ctx.types.getFunction(bodyReturnType, funcType.params, funcType.qualifiers)).getOrElse { ctx.error(it); return@withScope  node; }
                }
            }
            node.functionDecl.signatureType = funcType as SemanticType.Function;
            node;
        }
    }

    fun analyzeReturns(
        returns: List<ReturnStatementNode>,
        funcDecl: DeclSymbol.FunctionDecl,
        ctx: AnalyzeContext
    ): SemanticType? {
        val needsDeduce = funcDecl.returnType.hasUndeducedAuto;
        var returnType: SemanticType? = null;

        if (returns.isEmpty()) return ctx.types.void;

        for (ret in returns) {
            val retType = ret.expression?.resolvedType ?: ctx.types.void;
            if (returnType != null && ctx.types.removeRef(returnType) !== ctx.types.removeRef(retType)) {
                if (needsDeduce) {
                    ctx.error(
                        "'auto' in return type deduced as ${returnType} earlier but here deduced as ${retType}",
                        ret
                    )
                    returnType = retType;
                } else {
                    ctx.error("Return type ${returnType} is not compatible with ${retType}", ret)
                }
            } else {
                returnType = retType;
            }
        }
        return returnType;
    }
}

//class FunctionBodyAnalyzer : NodeAnalyzer<FunctionBodyNode> {
//    override fun analyze(node: FunctionBodyNode, ctx: AnalyzeContext): ASTNode {
//
//    }
//}
