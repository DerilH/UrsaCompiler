package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.ClassScope
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.FunctionBodyNode
import org.derilh.ast.FunctionDeclarationNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.ReturnStatementNode
import org.derilh.core.FunctionQualifiers
import org.derilh.core.getAsOrElse
import org.derilh.semantic.SemanticType
import org.derilh.util.ErrorHelper
import javax.lang.model.type.DeclaredType

private fun checkMethodQualifiers(decl: DeclSymbol.FunctionDecl, qual: FunctionQualifiers, ctx: AnalyzeContext) {
    if (!decl.isMethod) {
        if (qual.isConst || qual.isVolatile || qual.refQualifier != null) {
            ctx.error("Function declaration cannot have cv-qualifiers and ref-qualifiers")
        }
    }
}

class FunctionDeclAnalyzer : NodeAnalyzer<FunctionDeclarationNode> {
    override fun analyze(node: FunctionDeclarationNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need to check is node.id.name is unqualified-id for declaration only
        node.functionDecl.signatureType = ctx.resolveType(node.declSpec,node.declarator, ctx.scope).getAsOrElse{ ctx.error(it, location = node.location); ctx.types.getErrorFunction() }
        node.resolvedType = node.functionDecl.signatureType;
        if(node.functionDecl.signatureType.hasUndeducedAuto) {
            ctx.error("Cannot deduce return type from function declaration", node)
            return node;
        }

        val original = combineOverloads(node.overloadSet ?: return node, node.functionDecl, ctx)
        if(original != null) {
            node.functionDecl = original
            return node;
        }

        node.functionDecl.processed = true;
        if (ctx.scope is ClassScope) {
            if(node.declarator.id!!.name == (ctx.scope.ownerSymbol as DeclSymbol.ClassDecl).name) {
                ctx.error("Constructor cannot have a return type", node)
                return node;
            }
        }
//        return ctx.withScope(node.functionDecl.scope) {
//            var hasDefault = false;
//            for (param in node.declarator.getFunctionDeclarator()!!.params) {
//                if(hasDefault && !param.hasDefaultValue) {
//                    ctx.error("Missing default value on parameter ${param.declarator?.id?.toDisplayString()}", node = param)
//                }
//                else hasDefault = hasDefault || param.hasDefaultValue;
//            }
//            checkMethodQualifiers(node.functionDecl, node.resolvedType.qualifiers, ctx)
//            node;
//        }.also {
//            val retType = node.functionDecl.returnType
//            if(retType is DeclaredType && !retType.isComplete) {
//                ctx.error("Function return type ${retType} is incomplete", location = node.location)
//            }
//        }
//        return node;
        return node;
    }
}

class FunctionDefAnalyzer : NodeAnalyzer<FunctionDefinitionNode> {

    override fun analyze(node: FunctionDefinitionNode, ctx: AnalyzeContext): ASTNode {
        //TODO: maybe need check for redefinitions
        ctx.analyze(node.declaration, ctx.scope)

        var funcType = node.declaration.functionDecl.signatureType;
        node.functionDecl.signatureType = funcType
        val original = combineOverloads(node.overloadSet ?: return node, node.functionDecl, ctx)
        if(original != null) {
            if(original.definitionNode == null) {
                original.definitionNode = node.functionDecl.definitionNode
                original.scope = node.functionDecl.scope
                node.functionDecl = original
            }
            else {
                node.functionDecl = original
//                return node;
            }
        }

        node.functionDecl.processed = true;

        return ctx.withScope(node.functionDecl.scope) {
            var hasDefault = false;
            for (param in node.declaration.declarator.getFunctionDeclarator()!!.params) {
                ctx.analyze(param, ctx.scope)
                if(hasDefault && !param.hasDefaultValue) {
                    ctx.error("Missing default value on parameter ${param.declarator?.id?.toDisplayString()}", node = param)
                }
                else hasDefault = hasDefault || param.hasDefaultValue;
            }
            checkMethodQualifiers(node.functionDecl, node.declaration.resolvedType.qualifiers, ctx)

            node.body = ctx.findAnalyzer(node.body).analyze(node.body, ctx) as FunctionBodyNode;
            val bodyRets = node.body.returnStatements

            if (bodyRets != null) {
                val bodyReturnType = analyzeReturns(bodyRets, node.functionDecl, ctx) ?: ctx.types.void;

                if(funcType.returnType.hasUndeducedAuto) {
                    funcType = ctx.resolveType(node.declaration.declSpec, node.declaration.declarator, ctx.scope, ctx.types.getFunction(bodyReturnType, funcType.params, funcType.qualifiers)).getAsOrElse { ctx.error(it,); return@withScope  node; }
                }
            }
            node.functionDecl.signatureType = funcType
            node.declaration.resolvedType = funcType
            node;
        }.also {
            val retType = node.functionDecl.returnType
            if(retType is SemanticType.Declared && !retType.isComplete) {
                ctx.error("Function return type ${retType} is incomplete", node.declaration)
            }
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
            if (returnType != null && !(ctx.types.removeRef(returnType) isSame ctx.types.removeRef(retType))) {
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

fun combineOverloads(overloadSet: DeclSymbol.FunctionOverloadSet,  functionDecl: DeclSymbol.FunctionDecl, ctx: AnalyzeContext): DeclSymbol.FunctionDecl? {
    var firstDecl: DeclSymbol.FunctionDecl? = null;

    for(overload in overloadSet.overloads) {
        if(!overload.processed) break;

        if(ctx.isSameOverloadFun(overload.signatureType, functionDecl.signatureType)) {
            firstDecl = overload;
            break
        }
    }

    if(firstDecl != null) {
        if(!(firstDecl.returnType isSame functionDecl.returnType) || firstDecl.qualifiers.noExceptSpec != functionDecl.qualifiers.noExceptSpec) {
            ctx.error(ErrorHelper.alreadyDefined(functionDecl, firstDecl))
        }

        if(firstDecl.definitionNode != null) {
            if(functionDecl.definitionNode != null) {
//                ctx.error(ErrorHelper.alreadyDefined(functionDecl, firstDecl))
            }
        }

        overloadSet.overloads.distinctBy { System.identityHashCode(it) }

        return firstDecl
    }
    return null
}

//class FunctionBodyAnalyzer : NodeAnalyzer<FunctionBodyNode> {
//    override fun analyze(node: FunctionBodyNode, ctx: AnalyzeContext): ASTNode {
//
//    }
//}
