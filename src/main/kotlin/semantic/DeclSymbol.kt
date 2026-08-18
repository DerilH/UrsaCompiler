package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.ConstructorDeclarationNode
import org.derilh.ast.DeclaratorNode
import org.derilh.ast.EmptyStatementNode
import org.derilh.ast.FunctionDeclarationNode
import org.derilh.ast.NamespaceDeclarationNode
import org.derilh.core.MethodQualifiers
import org.derilh.semantic.SemanticType

sealed class DeclSymbol(
    val name: String,
    val parentSymbol: DeclSymbol?,
    val astNode: ASTNode
) {
    val qualifiedName: String get() {
        if (parentSymbol == null || parentSymbol.name.isEmpty()) return name
        return "${parentSymbol.qualifiedName}::$name"
    }

    class NamespaceDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        firstNode: ASTNode,
        val isAnonymous: Boolean = false
    ) : DeclSymbol(name, parentSymbol, firstNode) {
        lateinit var scope: Scope
        val declarations: MutableList<ASTNode> = mutableListOf()
    }

    class ClassDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        astNode: ASTNode,
        var hasDefinition: Boolean
//        val baseClasses: MutableList<ClassDecl> = mutableListOf()
    ) : DeclSymbol(name, parentSymbol, astNode) {
        lateinit var scope: ClassScope
    }

    class VariableDecl(
        name: String,
        val type: SemanticType,
        parentSymbol: DeclSymbol?,
        astNode: ASTNode,
        val isParameter: Boolean = false,
        val defaultValue: ASTNode? = null
    ) : DeclSymbol(name, parentSymbol, astNode)

    open class FunctionDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        astNode: ASTNode,
        val returnType: SemanticType,
        val params: List<SemanticType>,
        val qualifiers: MethodQualifiers,
        val isMethod: Boolean,
        val isBuiltin: Boolean = false,
        val defaultParamsCount: Int
    ) : DeclSymbol(name, parentSymbol, astNode) {
        lateinit var scope: Scope
    }

    class OperatorFunctionDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        astNode: ASTNode,
        returnType: SemanticType,
        params: List<SemanticType>,
        qualifiers: MethodQualifiers,
        isMethod: Boolean,
        isBuiltin: Boolean = false
    ) : FunctionDecl(name, parentSymbol, astNode, returnType, params, qualifiers, isMethod, isBuiltin, 0)

    class ConstructorDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        astNode: ConstructorDeclarationNode,
        returnType: SemanticType,
        params: List<SemanticType>,
        qualifiers: MethodQualifiers,
        isBuiltin: Boolean,
        val isExplicit: Boolean,
        defaultParamsCount: Int
    ) : FunctionDecl(name, parentSymbol, astNode, returnType, params, qualifiers, true, isBuiltin, defaultParamsCount)


    companion object {
        fun variable(name:String, declarator: DeclaratorNode, parentSymbol: DeclSymbol?): VariableDecl {
            val type = declarator.type.resolvedType ?: throw IllegalStateException("Variable type not resolved yet")
            return VariableDecl(name, type, parentSymbol, declarator)
        }
        fun param(name:String, declarator: DeclaratorNode, parentSymbol: DeclSymbol?): VariableDecl {
            val type = declarator.type.resolvedType ?: throw IllegalStateException("Variable type not resolved yet")
            return VariableDecl(name, type, parentSymbol, declarator, isParameter = true)
        }
        fun function(name:String, declarator: FunctionDeclarationNode, parentSymbol: DeclSymbol?, defaultParamCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as? SemanticType.Function ?: throw IllegalStateException("Function type not resolved yet");
            return FunctionDecl(name, parentSymbol, declarator, isMethod = false, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultParamCount)
        }
        fun constructor(name:String, declarator: ConstructorDeclarationNode, parentSymbol: DeclSymbol?, defaultParamCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as? SemanticType.Function ?: throw IllegalStateException("Function type not resolved yet");
            return ConstructorDecl(name, parentSymbol, declarator, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, isExplicit = declarator.isExplicit, isBuiltin = false, defaultParamsCount = defaultParamCount)
        }
        fun builtinOpFunction(name:String, type: SemanticType, vararg params: SemanticType): OperatorFunctionDecl {
            return OperatorFunctionDecl(name, null, EmptyStatementNode, isMethod = false, returnType = type, params = params.toList(), qualifiers = MethodQualifiers(), isBuiltin = true)
        }
        fun method(name:String, declarator: FunctionDeclarationNode, parentSymbol: DeclSymbol?, defaultCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as? SemanticType.Function ?: throw IllegalStateException("Function type not resolved yet");

            return FunctionDecl(name, parentSymbol, declarator, isMethod = true, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultCount)
        }

        fun clazz(name:String, declarator: ClassDeclarationNode, parentSymbol: DeclSymbol?): ClassDecl {
            return ClassDecl(name, parentSymbol, declarator, hasDefinition = declarator.body != null)
        }
        fun namespace(name:String, declarator: NamespaceDeclarationNode, parentSymbol: DeclSymbol?, isAnonymous: Boolean): NamespaceDecl {
            return NamespaceDecl(name, parentSymbol, declarator, isAnonymous)
        }
    }
}