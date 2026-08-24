package org.derilh.analyzer

import org.derilh.ast.ASTNode
import org.derilh.ast.ClassDeclarationNode
import org.derilh.ast.ClassDefinitionNode
import org.derilh.ast.ConstructorDeclarationNode
import org.derilh.ast.ConstructorDefinitionNode
import org.derilh.ast.DeclaratorNode
import org.derilh.ast.EmptyStatementNode
import org.derilh.ast.FunctionDeclaratorNode
import org.derilh.ast.FunctionDefinitionNode
import org.derilh.ast.NamespaceDeclarationNode
import org.derilh.core.ClassType
import org.derilh.core.FunctionQualifiers
import org.derilh.core.AccessSpecifier
import org.derilh.semantic.SemanticType

sealed class DeclSymbol(
    val name: String,
    val parentSymbol: DeclSymbol?,
) {
    var astNode: ASTNode? = null

    val qualifiedName: String
        get() {
            if (parentSymbol == null || parentSymbol.name.isEmpty()) return name
            return "${parentSymbol.qualifiedName}::$name"
        }

    class NamespaceDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        val isAnonymous: Boolean = false
    ) : DeclSymbol(name, parentSymbol) {
        lateinit var scope: Scope
        val declarations: MutableList<ASTNode> = mutableListOf()
    }

    class ClassDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        astNode: ASTNode,
        val type: ClassType,
        var hasDefinition: Boolean
//        val baseClasses: MutableList<ClassDecl> = mutableListOf()
    ) : DeclSymbol(name, parentSymbol) {
        lateinit var scope: ClassScope

        fun getDefaultVisibility(): AccessSpecifier = when(type) {
            ClassType.STRUCT,ClassType.UNION -> AccessSpecifier.PUBLIC
            ClassType.CLASS -> AccessSpecifier.PRIVATE
        }
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
        val qualifiers: FunctionQualifiers,
        val isMethod: Boolean,
        val isBuiltin: Boolean = false,
        val defaultParamsCount: Int,
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
        qualifiers: FunctionQualifiers,
        isMethod: Boolean,
        isBuiltin: Boolean = false
    ) : FunctionDecl(name, parentSymbol, astNode, signatureType, returnType, params, qualifiers, isMethod, isBuiltin, 0)

    class ConstructorDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        astNode: ASTNode,
        signatureType: SemanticType.Function,
        returnType: SemanticType,
        params: List<SemanticType>,
        qualifiers: FunctionQualifiers,
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

        fun constructorDecl(name: String, declarator: ConstructorDeclarationNode, parentSymbol: DeclSymbol?, defaultParamCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as SemanticType.Function
            return ConstructorDecl(name, parentSymbol, declarator, signatureType = funcType, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, isExplicit = declarator.isExplicit, isBuiltin = false, defaultParamsCount = defaultParamCount)
        }

        fun constructorDef(name: String, declarator: ConstructorDefinitionNode, parentSymbol: DeclSymbol?, defaultParamCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as SemanticType.Function
            return ConstructorDecl(name, parentSymbol, declarator, signatureType = funcType, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, isExplicit = declarator.isExplicit, isBuiltin = false, defaultParamsCount = defaultParamCount)
        }

        fun builtinOpFunction(name: String, funcType: SemanticType.Function): OperatorFunctionDecl {
            //TODO add proper signature type for compatibility
            return OperatorFunctionDecl(name, null, EmptyStatementNode, signatureType = funcType, isMethod = false, returnType = funcType.returnType, params = funcType.params, qualifiers = FunctionQualifiers(), isBuiltin = true)
        }

        fun methodDecl(name: String, declarator: FunctionDeclaratorNode, parentSymbol: DeclSymbol?, defaultCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as SemanticType.Function
            return FunctionDecl(name, parentSymbol, declarator, signatureType = funcType, isMethod = true, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultCount)
        }

        fun methodDef(name: String, declarator: FunctionDefinitionNode, parentSymbol: DeclSymbol?, defaultCount: Int): FunctionDecl {
            val funcType = declarator.type.resolvedType as SemanticType.Function
            return FunctionDecl(name, parentSymbol, declarator, signatureType = funcType, isMethod = true, returnType = funcType.returnType, params = funcType.params, qualifiers = funcType.qualifiers, defaultParamsCount = defaultCount)
        }

        fun classDecl(name: String, classType: ClassType, parentSymbol: DeclSymbol?): ClassDecl {
            return ClassDecl(name, parentSymbol, classType, hasDefinition = false)
        }
        fun classDef(name: String, declarator: ClassDefinitionNode, classType: ClassType, parentSymbol: DeclSymbol?): ClassDecl {
            return ClassDecl(name, parentSymbol, declarator, classType, hasDefinition = true)
        }

        fun namespace(name: String, declarator: NamespaceDeclarationNode, parentSymbol: DeclSymbol?, isAnonymous: Boolean): NamespaceDecl {
            return NamespaceDecl(name, parentSymbol, declarator, isAnonymous)
        }
    }
}