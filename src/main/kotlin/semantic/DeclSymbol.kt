package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.ClassDefinitionNode
import org.derilh.ast.ConstructorDeclarationNode
import org.derilh.ast.DeclaratorNode
import org.derilh.ast.EmptyStatementNode
import org.derilh.ast.FunctionDeclaratorNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.NamespaceDeclarationNode
import org.derilh.ast.StatementNode
import org.derilh.core.MethodQualifiers
import org.derilh.semantic.SemanticType

sealed class DeclSymbol(
    val name: String,
    val parentSymbol: DeclSymbol?,
    val astNode: ASTNode
) {
    val qualifiedName: String
        get() {
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
        val signatureType: SemanticType.Function,
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
        signatureType: SemanticType.Function,
        returnType: SemanticType,
        params: List<SemanticType>,
        qualifiers: MethodQualifiers,
        isMethod: Boolean,
        isBuiltin: Boolean = false
    ) : FunctionDecl(name, parentSymbol, astNode, signatureType, returnType, params, qualifiers, isMethod, isBuiltin, 0)

    class ConstructorDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        astNode: ConstructorDeclarationNode,
        signatureType: SemanticType.Function,
        returnType: SemanticType,
        params: List<SemanticType>,
        qualifiers: MethodQualifiers,
        isBuiltin: Boolean,
        val isExplicit: Boolean,
        defaultParamsCount: Int
    ) : FunctionDecl(name, parentSymbol, astNode, signatureType, returnType, params, qualifiers, true, isBuiltin, defaultParamsCount)


    companion object {
        fun variable(name: String, declarator: DeclaratorNode, parentSymbol: DeclSymbol?): VariableDecl {
            val type = declarator.type.resolvedType
                ?: throw IllegalStateException("Variable type not resolved yet ${declarator.location}")
            return VariableDecl(name, type, parentSymbol, declarator)
        }

        fun param(name: String, declarator: DeclaratorNode, parentSymbol: DeclSymbol?): VariableDecl {
            val type = declarator.type.resolvedType ?: throw IllegalStateException("Variable type not resolved yet")
            return VariableDecl(name, type, parentSymbol, declarator, isParameter = true)
        }

        fun functionDecl(name: String, decl: FunctionDeclaratorNode, parentSymbol: DeclSymbol?, defaultParamCount: Int): FunctionDecl {
            val funcType = decl.type.resolvedType as SemanticType.Function
            return FunctionDecl(name, parentSymbol, decl, signatureType = funcType, isMethod = false, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultParamCount)
        }

        fun functionDef(name: String, def: FunctionDefinitionNode, parentSymbol: DeclSymbol?, defaultParamCount: Int): FunctionDecl {
            val funcType = def.type.resolvedType as SemanticType.Function
            return FunctionDecl(name, parentSymbol, def, signatureType = funcType, isMethod = false, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultParamCount)
        }

        fun constructor(name: String, declarator: ConstructorDeclarationNode, parentSymbol: DeclSymbol?, defaultParamCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as? SemanticType.Function
                ?: throw IllegalStateException("Function type not resolved yet");
            return ConstructorDecl(name, parentSymbol, declarator, signatureType = funcType, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, isExplicit = declarator.isExplicit, isBuiltin = false, defaultParamsCount = defaultParamCount)
        }

        fun builtinOpFunction(name: String, funcType: SemanticType.Function): OperatorFunctionDecl {
            //TODO add proper signature type for compatibility
            return OperatorFunctionDecl(name, null, EmptyStatementNode, signatureType = funcType, isMethod = false, returnType = funcType.returnType, params = funcType.params, qualifiers = MethodQualifiers(), isBuiltin = true)
        }

        fun methodDecl(name: String, declarator: FunctionDeclaratorNode, parentSymbol: DeclSymbol?, defaultCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as SemanticType.Function
            return FunctionDecl(name, parentSymbol, declarator, signatureType = funcType, isMethod = true, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultCount)
        }

        fun methodDef(name: String, declarator: FunctionDefinitionNode, parentSymbol: DeclSymbol?, defaultCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as SemanticType.Function
            return FunctionDecl(name, parentSymbol, declarator, signatureType = funcType, isMethod = true, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultCount)
        }

        fun classDecl(name: String, declarator: ClassDeclarationNode, parentSymbol: DeclSymbol?): ClassDecl {
            return ClassDecl(name, parentSymbol, declarator, hasDefinition = false)
        }
        fun classDef(name: String, declarator: ClassDefinitionNode, parentSymbol: DeclSymbol?): ClassDecl {
            return ClassDecl(name, parentSymbol, declarator, hasDefinition = true)
        }

        fun namespace(name: String, declarator: NamespaceDeclarationNode, parentSymbol: DeclSymbol?, isAnonymous: Boolean): NamespaceDecl {
            return NamespaceDecl(name, parentSymbol, declarator, isAnonymous)
        }
    }
}