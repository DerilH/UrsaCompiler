package org.derilh.semantic.analyzer

import org.derilh.analyzer.AnalyzeContext
import org.derilh.analyzer.DeclSymbol
import org.derilh.analyzer.NodeAnalyzer
import org.derilh.ast.ASTNode
import org.derilh.ast.ArgumentsNode
import org.derilh.ast.CallExpressionNode
import org.derilh.ast.ExpressionNode
import org.derilh.ast.IdExpressionNode
import org.derilh.ast.QualifiedIdentifierNode
import org.derilh.core.RefQualifier
import org.derilh.core.ValueCategory
import org.derilh.core.getAsOrElse
import org.derilh.core.getOrElse
import org.derilh.core.getOrNull
import org.derilh.semantic.ExpressionInfo
import org.derilh.semantic.SemanticType
import org.derilh.semantic.isDeclared
import org.derilh.semantic.isFunctionPointer
import org.derilh.semantic.isFunctionRef
import org.derilh.semantic.isPointer
import org.derilh.util.ErrorHelper

class CallExprAnalyzer : NodeAnalyzer<CallExpressionNode> {
    override fun analyze(node: CallExpressionNode, ctx: AnalyzeContext): ASTNode {
        ctx.idContext = IdContext.CALLEE
        try {

            lateinit var convArgs: List<ExpressionNode>;
            val callee = node.callee ?: return node;
            ctx.analyze(callee, ctx.scope);
            val calleeType = callee.resolvedType ?: return node;
            val canonCallee = calleeType.canonical

            val argsInfo = node.arguments.arguments.map {
                ctx.analyze(it, ctx.scope)
                if (it.resolvedType == null) return node;
                ExpressionInfo(it.resolvedType!!, it.valueCategory!!, ctx.isNullPointerConstant(it), it.location)
            }

            when {
                canonCallee.isFunctionPointer() || canonCallee.isFunctionRef() || canonCallee is SemanticType.Function -> {
                    val func = ((canonCallee as? SemanticType.Pointer)?.pointee?.canonical ?: (canonCallee as? SemanticType.Reference)?.pointee?.canonical ?: canonCallee) as SemanticType.Function;
                    val params = func.params;
                    convArgs = ctx.probeCallArgs(params, 0, argsInfo, node.arguments.location!!, breakOnMiss = false).mapIndexed { index, result ->
                        val conv = result.getOrElse { ctx.error(it); return@analyze node }
                        ctx.buildConversionSeq(node.arguments.arguments[index], conv)
                    }
                }

                canonCallee.isPointer() && canonCallee.pointee.canonical.isDeclared() || canonCallee.isDeclared() -> {
                    val canonPointee = (canonCallee as? SemanticType.Pointer)?.pointee?.canonical
                    val declared = (canonPointee as? SemanticType.Declared) ?: canonCallee as SemanticType.Declared
                    val found: DeclSymbol.FunctionOverloadSet = ctx.resolveSymbolsLocal("${SemanticAnalyzer.OPERATOR_FUN_PREFIX}()", declared.decl.scope).getAsOrElse { ctx.error(it); return node }

                    val viableByThis = found.overloads.filter { methodDecl ->
                        isThisCompatible(methodDecl, ExpressionInfo(calleeType, callee.valueCategory!!, false))
                    }

                    val matches = ctx.findBestMatch(viableByThis, argsInfo).distinctBy { System.identityHashCode(it.decl) }
                    val result = ErrorHelper.checkViableSet(matches, "${SemanticAnalyzer.OPERATOR_FUN_PREFIX}()", callee.location!!).getOrElse {
                        ctx.error(it);
                        node.resolvedType = ctx.types.getError();
                        node.valueCategory = ValueCategory.PRVALUE
                        return node;
                    }
                    convArgs = result.sequences.mapIndexed { index, conv ->
                        ctx.buildConversionSeq(node.arguments.arguments[index], conv)
                    }

                    node.functionDecl = result.decl;
                    node.resolvedType = result.decl.returnType;
                    node.valueCategory = ctx.getRefValueCategory(result.decl.returnType)
                }

                canonCallee is SemanticType.OverloadSet -> {
                    val overloads = mutableListOf<DeclSymbol.FunctionDecl>();
                    val enableADL = canonCallee.isUnqualified && node.callee is IdExpressionNode
                    if (enableADL) {
                        overloads += ctx.collectADLOverloads(canonCallee.name, *argsInfo.toTypedArray());
                    }
                    overloads += canonCallee.overloads;

                    val matches = ctx.findBestMatch(overloads, argsInfo).distinctBy { System.identityHashCode(it.decl) }
                    val result = ErrorHelper.checkViableSet(matches, canonCallee.name, callee.location!!).getOrElse {
                        ctx.error(it);
                        node.resolvedType = ctx.types.getError();
                        node.valueCategory = ValueCategory.PRVALUE
                        return node;
                    }

                    if (result.decl is DeclSymbol.ConstructorDecl) {
                        node.resolvedType = ctx.types.getDeclared(result.decl.parentSymbol as DeclSymbol.ClassDecl)
                        node.valueCategory = ValueCategory.PRVALUE
                        node.functionDecl = result.decl;
                    } else {
                        node.resolvedType = result.decl.returnType;
                        node.valueCategory = ctx.getRefValueCategory(result.decl.returnType)
                        node.functionDecl = result.decl;
                        callee.resolvedType = result.decl.signatureType;
                    }

                    convArgs = result.sequences.mapIndexed { index, conv ->
                        ctx.buildConversionSeq(node.arguments.arguments[index], conv)
                    }
                }

                canonCallee is SemanticType.BoundMethodSet -> {
                    val viableByThis = canonCallee.overloads.filter { methodDecl ->
                        isThisCompatible(methodDecl, ExpressionInfo(calleeType, callee.valueCategory!!, false))
                    }
                    val matches = ctx.findBestMatch(viableByThis, argsInfo).distinctBy { System.identityHashCode(it.decl) }
                    val result = ErrorHelper.checkViableSet(matches, canonCallee.name, callee.location!!).getOrElse {
                        ctx.error(it);
                        node.resolvedType = ctx.types.getError();
                        node.valueCategory = ValueCategory.PRVALUE
                        return node;
                    }
                    convArgs = result.sequences.mapIndexed { index, conv ->
                        ctx.buildConversionSeq(node.arguments.arguments[index], conv)
                    }

                    callee.resolvedType = result.decl.signatureType;
                    node.functionDecl = result.decl;
                    node.resolvedType = result.decl.returnType;
                    node.valueCategory = ctx.getRefValueCategory(result.decl.returnType)
                }

                else -> {
                    ctx.error("Callable is not valid type ${callee.resolvedType}", callee)
                    return node;
                }
            }
            node.arguments = ArgumentsNode(convArgs, node.arguments.location);
            return node;
        } finally {
            ctx.idContext = IdContext.EXPRESSION
        }
    }

    fun isThisCompatible(
        method: DeclSymbol.FunctionDecl,
        instanceInfo: ExpressionInfo
    ): Boolean {
        val instanceType = instanceInfo.type
        val category = instanceInfo.valueCategory

        if (instanceType.isConst && !method.qualifiers.isConst) {
            return false
        }

        if (instanceType.isVolatile && !method.qualifiers.isVolatile) {
            return false
        }
        when (method.qualifiers.refQualifier) {
            RefQualifier.LVALUE -> {
                val isLValue = category == ValueCategory.LVALUE
                if (!isLValue && !method.qualifiers.isConst) {
                    return false
                }
            }

            RefQualifier.RVALUE -> {
                val isRValue = category == ValueCategory.PRVALUE || category == ValueCategory.XVALUE
                if (!isRValue) {
                    return false
                }
            }

            RefQualifier.NONE -> {}
        }

        return true
    }
}
