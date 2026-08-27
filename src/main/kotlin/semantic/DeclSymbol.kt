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
    lateinit var astNode: ASTNode
    var accessSpecifier: AccessSpecifier = AccessSpecifier.PUBLIC
    var processed: Boolean = false;

    val qualifiedName: String
        get() {
            if (parentSymbol == null || parentSymbol.name.isEmpty()) return name
            return "${parentSymbol.qualifiedName}::$name"
        }

    class NamespaceDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        val isAnonymous: Boolean
    ) : DeclSymbol(name, parentSymbol) {
        lateinit var scope: Scope
        val declarations: MutableList<ASTNode> = mutableListOf()
    }

    class ClassDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        val type: ClassType,
//        val baseClasses: MutableList<ClassDecl> = mutableListOf()
    ) : DeclSymbol(name, parentSymbol) {
        lateinit var scope: ClassScope
        var definitionNode: ASTNode? = null;
        var hasDefinition: Boolean = false;
        fun getDefaultVisibility(): AccessSpecifier = when (type) {
            ClassType.STRUCT, ClassType.UNION -> AccessSpecifier.PUBLIC
            ClassType.CLASS -> AccessSpecifier.PRIVATE
        }
    }

    class VariableDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        val isParameter: Boolean = false,
    ) : DeclSymbol(name, parentSymbol) {
        lateinit var type: SemanticType
    }

    open class FunctionDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        val qualifiers: FunctionQualifiers,
        val isMethod: Boolean,
        val isBuiltin: Boolean,
        val defaultParamsCount: Int,
    ) : DeclSymbol(name, parentSymbol) {
        lateinit var scope: Scope
        lateinit var signatureType: SemanticType.Function
        var definitionNode: ASTNode? = null
        val returnType: SemanticType get() = signatureType.returnType
        val params: List<SemanticType> get() = signatureType.params
    }

    class FunctionOverloadSet(name: String, parentSymbol: DeclSymbol?) : DeclSymbol(name, parentSymbol){
        val overloads: MutableList<FunctionDecl> = mutableListOf()
        init {
            processed = true;
        }
    }

    class OperatorFunctionDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        qualifiers: FunctionQualifiers,
        isMethod: Boolean,
        isBuiltin: Boolean,
        defaultParamsCount: Int
    ) : FunctionDecl(name, parentSymbol, qualifiers, isMethod, isBuiltin, defaultParamsCount) {

    }

    class ConstructorDecl(
        name: String,
        parentSymbol: DeclSymbol?,
        qualifiers: FunctionQualifiers,
        isBuiltin: Boolean,
        val isExplicit: Boolean,
        defaultParamsCount: Int
    ) : FunctionDecl(name, parentSymbol, qualifiers, isMethod = true, isBuiltin, defaultParamsCount)


    companion object {
        fun variable(name: String, parentSymbol: DeclSymbol?): VariableDecl {
            return VariableDecl(name, parentSymbol)
        }

        fun functionDecl(name: String, parentSymbol: DeclSymbol?, qualifiers: FunctionQualifiers, defaultParamCount: Int): FunctionDecl {
            return FunctionDecl(name, parentSymbol, qualifiers, isMethod = false, isBuiltin = false, defaultParamCount)
        }

        fun constructorDecl(name: String, parentSymbol: DeclSymbol?, qualifiers: FunctionQualifiers, isExplicit: Boolean, defaultParamCount: Int): ConstructorDecl {
            return ConstructorDecl(name, parentSymbol, qualifiers, isBuiltin = false, isExplicit, defaultParamCount)
        }

        fun builtinOpFunction(name: String, funcType: SemanticType.Function): OperatorFunctionDecl {
            //TODO add proper signature type for compatibility
            val decl = OperatorFunctionDecl(name, null, FunctionQualifiers(), isMethod = false, isBuiltin = true, defaultParamsCount = 0)
            decl.signatureType = funcType
            decl.definitionNode = EmptyStatementNode
            decl.astNode = EmptyStatementNode;
            return decl
        }

        fun methodDecl(name: String, parentSymbol: DeclSymbol?, qualifiers: FunctionQualifiers, defaultCount: Int): FunctionDecl {
            return FunctionDecl(name, parentSymbol, qualifiers, isMethod = true, isBuiltin = false, defaultParamsCount = defaultCount)
        }

        fun classDecl(name: String, classType: ClassType, parentSymbol: DeclSymbol?): ClassDecl {
            return ClassDecl(name, parentSymbol, classType)
        }

        fun namespace(name: String, parentSymbol: DeclSymbol?, isAnonymous: Boolean): NamespaceDecl {
            return NamespaceDecl(name, parentSymbol, isAnonymous)
        }
    }
}