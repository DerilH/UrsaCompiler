package org.derilh.ir

import com.sun.tools.javac.tree.TreeInfo.args
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*
import org.derilh.analyzer.DeclSymbol
import org.derilh.ast.*
import org.derilh.core.ConversionKind
import org.derilh.core.Operator
import org.derilh.core.Options
import org.derilh.core.PrimitiveTypeKind
import org.derilh.semantic.SemanticType
import org.derilh.semantic.isDeclared
import org.derilh.semantic.isPrimitive
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.math.exp


class LLVMIRBuilder(val options: Options) : IIRBuilder {
    val context: LLVMContextRef;
    val module: LLVMModuleRef;
    val builder: LLVMBuilderRef;
    val types = IdentityHashMap<SemanticType, LLVMTypeRef>();
    val functions = IdentityHashMap<DeclSymbol.FunctionDecl, LLVMValueRef>();
    val variables = IdentityHashMap<DeclSymbol.VariableDecl, LLVMValueRef>();

    var nameIdCounter: Int = 0;
    var currentFn: LLVMValueRef? = null;

    init {
        // Инициализируем LLVM
        LLVMInitializeX86TargetInfo();
        LLVMInitializeX86Target();
        LLVMInitializeX86TargetMC();
        LLVMInitializeX86AsmPrinter();
        LLVMInitializeX86AsmParser()
        this.context = LLVMContextCreate();
        this.module = LLVMModuleCreateWithNameInContext("main", context);
        this.builder = LLVMCreateBuilderInContext(context);
    }

    override fun generate(ast: ASTNode) {
        ast as RootNode;
        ast.declarations.forEach {
            generateIR(it)
        }

        emitX86ObjectFile(module, "./out.o")
    }


    fun generateIR(ast: ASTNode): LLVMValueRef? {
        return when (ast) {
            is FunctionDefinitionNode -> getFunctionRef(ast.functionDecl)
            is BinaryExpressionNode -> visitBinaryExpr(ast)
            is ReturnStatementNode -> visitReturn(ast)
            is CallExpressionNode -> visitCallExpr(ast)
            is IntLiteralNode -> visitIntLit(ast)
            is ImplicitCastExpressionNode -> visitImplicitCast(ast);
            is IdExpressionNode -> visitIdExpr(ast);
            is VariableDeclaratorNode -> getVarDecl(ast.varDecl);
            is DeclarationSequenceNode -> {
                ast.declarations.forEach { generateIR(it) };
                null
            }

            is IfStatementNode -> visitIf(ast);
            is WhileStatementNode -> visitWhile(ast)
            is StringLiteralNode -> visitStringLit(ast);
            is UnaryExpressionNode -> visitUnaryExpr(ast);
            is ArrayAccessNode -> visitArrayAccess(ast)
            is CharLiteralNode -> visitCharLit(ast)
            is AsmStatementNode -> visitAsm(ast);
            is CompoundStatementNode -> {
                ast.statements.forEach { generateIR(it) }
                null
            }

            else -> throw IllegalArgumentException("Unsupported node: " + ast)
        }
    }

    fun visitAsm(node: AsmStatementNode): LLVMValueRef {
        val asmTemplate = fixAsmTemplateForLLVM((node.asmExr as StringLiteralNode).evaluated as String)
        val constraintItems = mutableListOf<String>()

        for (outNode in node.outList) {
            val constraintStr = (outNode.constraint as StringLiteralNode).evaluated as String
            constraintItems.add(constraintStr)
        }

        for (inNode in node.inList) {
            val constraintStr = (inNode.constraint as StringLiteralNode).evaluated as String
            constraintItems.add(constraintStr)
        }

        for (clobber in node.clobberList) {
            val clobberStr = (clobber as StringLiteralNode).evaluated as String
            val formattedClobber = if (clobberStr.startsWith("~")) clobberStr else "~{$clobberStr}"
            constraintItems.add(formattedClobber)
        }

        val fullConstraints = constraintItems.joinToString(",")

        // 3. Вычисляем входные аргументы (Inputs)
        val inputArgs = mutableListOf<LLVMValueRef>()
        val inputLlvmTypes = mutableListOf<LLVMTypeRef>()

        for (inNode in node.inList) {
            val constraintStr = (inNode.constraint as StringLiteralNode).evaluated as String
            val argVal = generateIR(inNode.expr)!!
            inputArgs.add(argVal)
            inputLlvmTypes.add(LLVMTypeOf(argVal))
        }

        val returnLlvmType: LLVMTypeRef = when (node.outList.size) {
            0 -> LLVMVoidTypeInContext(context)
            1 -> convertType(node.outList[0].expr.resolvedType!!)
            else -> {
                val outTypes = node.outList.map { convertType(it.expr.resolvedType!!) }.toTypedArray()
                val typesPointer = PointerPointer(*outTypes)
                LLVMStructTypeInContext(context, typesPointer, outTypes.size, 0)
            }
        }

        val paramTypesPointer = PointerPointer(*inputLlvmTypes.toTypedArray())
        val asmFnType = LLVMFunctionType(
            returnLlvmType,
            paramTypesPointer,
            inputLlvmTypes.size,
            0 // isVarArg = false
        )

        val asmValue = LLVMConstInlineAsm(
            asmFnType,
            asmTemplate,
            fullConstraints,
            if (node.isVolatile) 1 else 0,
            0 // isAlignStack = false
        )

        val argsPointer = PointerPointer(*inputArgs.toTypedArray())
        val callResult = LLVMBuildCall2(
            builder,
            asmFnType,
            asmValue,
            argsPointer,
            inputArgs.size,
            if (node.outList.isEmpty()) "" else "asm_res"
        )

        when (node.outList.size) {
            0 -> { }
            1 -> {
                val outAddr = generateIR(node.outList[0].expr)
                LLVMBuildStore(builder, callResult, outAddr)
            }
            else -> {
                for ((index, outNode) in node.outList.withIndex()) {
                    val extractedValue = LLVMBuildExtractValue(builder, callResult, index, "asm_out_$index")
                    val outAddr = generateIR(outNode.expr)
                    LLVMBuildStore(builder, extractedValue, outAddr)
                }
            }
        }

        return callResult
    }

    fun fixAsmTemplateForLLVM(rawAsm: String): String {
        return rawAsm
            .replace(Regex("""\$(\d+)""")) { "\$\$${it.groupValues[1]}" }
            .replace(Regex("""%(\d+)""")) { "\$${it.groupValues[1]}" }.replace("\u0000", "").replace("%%", "%").trim().trim()
    }

    fun visitArrayAccess(node: ArrayAccessNode): LLVMValueRef? {
        val operand = generateIR(node.operand)!!;
        val index = generateIR(node.index)!!;
        val indices = PointerPointer<LLVMValueRef>(1L).apply { put(0, index) }
        return LLVMBuildGEP2(builder, convertType(node.resolvedType!!), operand, indices, 1, getUniqueName("geparr"));
    }

    fun visitWhile(node: WhileStatementNode): LLVMValueRef? {
        val condBB = LLVMAppendBasicBlock(currentFn, getUniqueName("while.cond"))
        val bodyBB = LLVMAppendBasicBlock(currentFn, getUniqueName("while.body"))
        val endBB = LLVMAppendBasicBlock(currentFn, getUniqueName("while.end"))

        LLVMBuildBr(builder, condBB)

        LLVMPositionBuilderAtEnd(builder, condBB)
        val condValue = generateIR(node.condition)
        LLVMBuildCondBr(builder, condValue, bodyBB, endBB)

        LLVMPositionBuilderAtEnd(builder, bodyBB)

        generateIR(node.body)

        val currentBlock = LLVMGetInsertBlock(builder)
        if (LLVMGetBasicBlockTerminator(currentBlock) == null) {
            LLVMBuildBr(builder, condBB)
        }

        LLVMPositionBuilderAtEnd(builder, endBB)

        return null
    }

    fun visitIf(node: IfStatementNode): LLVMValueRef? {
        val condIR = generateIR(node.condition) ?: return null

        val thenBB = LLVMAppendBasicBlock(currentFn, getUniqueName("then"))
        val mergeBB = LLVMAppendBasicBlock(currentFn, getUniqueName("merge"))
        val elseBB = if (node.elseBody != null) LLVMAppendBasicBlock(currentFn, getUniqueName("else")) else null

        LLVMBuildCondBr(builder, condIR, thenBB, elseBB ?: mergeBB)

        LLVMPositionBuilderAtEnd(builder, thenBB)
        generateIR(node.body)

        val currentThenBB = LLVMGetInsertBlock(builder)
        if (LLVMGetBasicBlockTerminator(currentThenBB) == null) {
            LLVMBuildBr(builder, mergeBB)
        }

        if (elseBB != null && node.elseBody != null) {
            LLVMPositionBuilderAtEnd(builder, elseBB)
            generateIR(node.elseBody!!)

            val currentElseBB = LLVMGetInsertBlock(builder)
            if (LLVMGetBasicBlockTerminator(currentElseBB) == null) {
                LLVMBuildBr(builder, mergeBB)
            }
        }

        LLVMPositionBuilderAtEnd(builder, mergeBB)

        return null
    }

    fun visitStringLit(node: StringLiteralNode): LLVMValueRef {
        return LLVMBuildGlobalString(builder, node.evaluated as String, getUniqueName("str"))
    }

    fun visitIntLit(node: IntLiteralNode): LLVMValueRef {
        return LLVMConstInt(convertType(node.resolvedType!!), (node.evaluated as BigInteger).toLong(), 0);
    }

    fun visitCharLit(node: CharLiteralNode): LLVMValueRef {
        return LLVMConstInt(convertType(node.resolvedType!!), node.numericValue!!.toLong(), 0);
    }


    fun getVarDecl(varDecl: DeclSymbol.VariableDecl): LLVMValueRef {
        return variables.getOrPut(varDecl) {
            val node = varDecl.astNode as VariableDeclaratorNode
            val varType = convertType(varDecl.type)

            val alloca = LLVMBuildAlloca(builder, varType, getUniqueName(varDecl.name))

            val init = node.initializer
            if (init != null) {
                val initVal = generateIR(init)!!
                LLVMBuildStore(builder, initVal, alloca)
            }
            alloca
        }
    }

    fun visitIdExpr(ast: IdExpressionNode): LLVMValueRef {
        val decl = ast.decl
        if (decl is DeclSymbol.VariableDecl) {
            return getVarDecl(decl)
        } else throw IllegalArgumentException("Unsupported node: " + ast)
    }

    fun visitImplicitCast(node: ImplicitCastExpressionNode): LLVMValueRef {
        when (node.kind) {
            ConversionKind.LVALUE_TO_RVALUE -> {
                val lvaluePtr: LLVMValueRef = generateIR(node.operand)!!
                val valueType: LLVMTypeRef = convertType(node.resolvedType!!)
                return LLVMBuildLoad2(builder, valueType, lvaluePtr, "rvalue_tmp")
            }

            ConversionKind.ARRAY_TO_POINTER -> {
                return generateIR(node.operand)!!
            }

            ConversionKind.POINTER_TO_BOOLEAN -> {
                val ptr = generateIR(node.operand)
                val ptrType = LLVMTypeOf(ptr)
                val nullPtr = LLVMConstNull(ptrType)
                return LLVMBuildICmp(builder, LLVMIntNE, ptr, nullPtr, getUniqueName("ptrtob"))
            }

            ConversionKind.INTEGRAL_PROMOTION -> {
                val operand = generateIR(node.operand)!!
                val intType = convertType(node.resolvedType!!);

                return LLVMBuildSExt(builder, operand, intType, getUniqueName("iprom"))
            }

            ConversionKind.INTEGRAL_CONVERSION -> {
                val operand = generateIR(node.operand)!!
                val operandType = node.operand.resolvedType!! as SemanticType.Primitive
                val nodeType = node.resolvedType!! as SemanticType.Primitive
                return if (nodeType.kind.intRank > operandType.kind.intRank) {
                    LLVMBuildSExt(builder, operand, convertType(nodeType), getUniqueName("iprom"))
                } else {
                    LLVMBuildTrunc(builder, operand, convertType(nodeType), getUniqueName("itrunc"))
                }
            }

            else -> throw IllegalArgumentException("Unsupported implicit cast kind: ${node.kind}")
        }
    }

    fun emitSyscallPrint(buf: LLVMValueRef?, len: LLVMValueRef?): LLVMValueRef {
        val i64 = LLVMInt64TypeInContext(context)
        val ptr = LLVMPointerType(LLVMInt8TypeInContext(context), 0)

        val paramTypes = arrayOf<LLVMTypeRef?>(i64, i64, ptr, i64)
        val fp = PointerPointer(*paramTypes);
        val asmType = LLVMFunctionType(i64, fp, 4, 0)

        val asmString = BytePointer("syscall")
        val constraints = BytePointer("={rax},{rax},{rdi},{rsi},{rdx},~{rcx},~{r11},~{memory}")
        val len = if (LLVMTypeOf(len) != i64) {
            LLVMBuildZExt(builder, len, i64, "len_i64_cast")
        } else {
            len
        }

        val inlineAsm = LLVMGetInlineAsm(
            asmType,
            asmString,
            asmString.capacity() - 1, // без null-terminator
            constraints,
            constraints.capacity() - 1,
            1, 0, LLVMInlineAsmDialectATT, 0
        )
        // 4. Аргументы syscall: write (1), stdout (1), buf, len
        val args = arrayOf<LLVMValueRef?>(
            LLVMConstInt(i64, 1, 0),  // rax = 1 (sys_write)
            LLVMConstInt(i64, 1, 0),  // rdi = 1 (stdout)
            buf,  // rsi = pointer
            len // rdx = length
        )
        val p = PointerPointer<LLVMValueRef>(*args)
        return LLVMBuildCall2(builder, asmType, inlineAsm, p, args.size, "sys_write_res")
    }

    fun visitCallExpr(node: CallExpressionNode): LLVMValueRef {
//        if (node.functionDecl?.name == "print_syscall_stdout") {
//            return emitSyscallPrint(generateIR(node.arguments.arguments[0]), generateIR(node.arguments.arguments[1]))
//        }
        val funcDecl = node.functionDecl as DeclSymbol.FunctionDecl
        val func = getFunctionRef(funcDecl)
        val args = node.arguments.arguments.map { generateIR(it) }.toTypedArray()

        val p = PointerPointer<LLVMValueRef>(*args)
        val name = if(node.resolvedType!!.isPrimitive(PrimitiveTypeKind.VOID)){
            ""
        } else getUniqueName("call")
        return LLVMBuildCall2(builder, convertType(funcDecl.signatureType), func, p, args.size, name)
    }

    fun getFunctionRef(decl: DeclSymbol.FunctionDecl): LLVMValueRef {
        return functions.getOrPut(decl) {
//            val paramArr = decl.params.map { convertType(it) }.toTypedArray();
//            val p = PointerPointer(*paramArr)
//            val funcType = LLVMFunctionType(LLVMInt32TypeInContext(context), p, paramArr.size, 0)

            val funcType = convertType(decl.signatureType);

            val func = LLVMAddFunction(module, decl.name, funcType)

            val entryBlock = LLVMAppendBasicBlockInContext(context, func, getUniqueName("entry"))
            LLVMPositionBuilderAtEnd(builder, entryBlock)
            currentFn = func;

            for (i in decl.params.indices) {
                val paramAST = ((decl.astNode as FunctionDeclaratorNode).type as FunctionTypeNode).params[i].declarator as VariableDeclaratorNode
                val llvmParam = LLVMGetParam(func, i)
                val alloca = generateIR(paramAST)
                LLVMBuildStore(builder, llvmParam, alloca)
            }


            (decl.definitionNode as FunctionDefinitionNode).body.statements.forEach { generateIR(it) }
            val currentBlock = LLVMGetInsertBlock(builder)

            val hasTerminator = LLVMGetBasicBlockTerminator(currentBlock) != null

            if (!hasTerminator) {
                if (decl.returnType.isPrimitive(PrimitiveTypeKind.VOID)) {
                    LLVMBuildRetVoid(builder)
                } else if (decl.name == "main") {
                    val zero = LLVMConstInt(LLVMInt32TypeInContext(context), 0L, 0)
                    LLVMBuildRet(builder, zero)
                } else {
                    LLVMBuildUnreachable(builder)
                }
            }
            currentFn = null
            func
        }
    }

    fun convertType(type: SemanticType): LLVMTypeRef {
        return types.getOrPut(type) {
            when (type) {
                is SemanticType.Array -> {
                    val refType = convertType(type.elementType);
                    LLVMArrayType(refType, type.size!!.toInt())
                }

                is SemanticType.Declared -> {
                    LLVMStructCreateNamed(context, type.decl.name).also {
                        val params = type.decl.layout!!.fields.map { f -> convertType(f.type) }
                        LLVMStructSetBody(it, params[0], 2, 0 /* isPacked = false */)
                    }
                }

                is SemanticType.Pointer -> {
                    val refType = convertType(type.pointee);
                    LLVMPointerType(refType, 0);
                }

                is SemanticType.Primitive -> {
                    when (type.kind) {
                        PrimitiveTypeKind.BOOL -> LLVMInt1TypeInContext(context)
                        PrimitiveTypeKind.VOID -> LLVMVoidTypeInContext(context)
                        PrimitiveTypeKind.NULLPTR -> {
                            val intType = LLVMInt32TypeInContext(context)
                            LLVMPointerType(intType, 0)
                        }

                        PrimitiveTypeKind.CHAR -> LLVMInt8TypeInContext(context)
                        PrimitiveTypeKind.SIGNED_CHAR -> LLVMInt8TypeInContext(context)
                        PrimitiveTypeKind.UNSIGNED_CHAR -> LLVMInt8TypeInContext(context)
                        PrimitiveTypeKind.CHAR8_T -> LLVMInt8TypeInContext(context)
                        PrimitiveTypeKind.CHAR16_T -> LLVMInt16TypeInContext(context)
                        PrimitiveTypeKind.CHAR32_T -> LLVMInt32TypeInContext(context)
                        PrimitiveTypeKind.WCHAR_T -> LLVMInt32TypeInContext(context)
                        PrimitiveTypeKind.SHORT -> LLVMInt16TypeInContext(context)
                        PrimitiveTypeKind.UNSIGNED_SHORT -> LLVMInt16TypeInContext(context)
                        PrimitiveTypeKind.INT -> LLVMInt32TypeInContext(context)
                        PrimitiveTypeKind.UNSIGNED_INT -> LLVMInt32TypeInContext(context)
                        PrimitiveTypeKind.LONG -> LLVMInt64TypeInContext(context)
                        PrimitiveTypeKind.UNSIGNED_LONG -> LLVMInt64TypeInContext(context)
                        PrimitiveTypeKind.LONG_LONG -> LLVMInt64TypeInContext(context)
                        PrimitiveTypeKind.UNSIGNED_LONG_LONG -> LLVMInt64TypeInContext(context)
                        PrimitiveTypeKind.FLOAT -> LLVMFloatTypeInContext(context)
                        PrimitiveTypeKind.DOUBLE -> LLVMDoubleTypeInContext(context)
                        PrimitiveTypeKind.LONG_DOUBLE -> LLVMX86FP80TypeInContext(context)
                    }
                }

                is SemanticType.RValueReference -> {
                    val refType = convertType(type.pointee);
                    LLVMPointerType(refType, 0);
                }

                is SemanticType.Reference -> {
                    val refType = convertType(type.pointee);
                    LLVMPointerType(refType, 0);
                }

                is SemanticType.Function -> {
                    val paramArr = type.params.map { convertType(it) }.toTypedArray();
                    val p = PointerPointer(*paramArr)
                    LLVMFunctionType(convertType(type.returnType), p, paramArr.size, 0)
                }

                else -> throw IllegalArgumentException("Unsupported type: " + type)
            }
        }
    }

    fun visitUnaryExpr(expr: UnaryExpressionNode): LLVMValueRef {
        val operand = generateIR(expr.operand)!!
        val opType = expr.operand.resolvedType!!;
        val name = getUniqueName("unop")
        if (!opType.isPrimitive()) TODO("Only primitive unary ops are supported")
        val isFloat = opType.kind.isFloat;

        return when (expr.operator) {
            Operator.PLUS -> operand
            Operator.MINUS -> {
                if (isFloat) LLVMBuildFNeg(builder, operand, name)
                else LLVMBuildNeg(builder, operand, name)
            }

            Operator.NOT -> LLVMBuildNot(builder, operand, name)
            Operator.INCREMENT -> {
                val one = if (isFloat) LLVMConstReal(convertType(opType), 1.0) else LLVMConstInt(convertType(opType), 1L, 0)
                val loaded = LLVMBuildLoad2(builder, convertType(opType), operand, name)
                val incrementedValue = if (isFloat) LLVMBuildFAdd(builder, loaded, one, name) else LLVMBuildAdd(builder, loaded, one, name)
                LLVMBuildStore(builder, incrementedValue, operand)
                if (expr.isPrefix) operand
                else incrementedValue
            }

            else -> throw IllegalArgumentException("Unsupported operator: " + expr.operator)
        }
    }

    fun visitBinaryExpr(binaryExpr: BinaryExpressionNode): LLVMValueRef {
        val leftType = binaryExpr.left.resolvedType!!;
        val rightType = binaryExpr.right.resolvedType!!;
        val leftIR = generateIR(binaryExpr.left)!!
        val rightIR = generateIR(binaryExpr.right)!!
        val name = getUniqueName("binop")

        val isFloat = leftType.isPrimitive() && leftType.kind.isFloat;
        if (leftType.isDeclared() || rightType.isDeclared()) {
            TODO("Operator overload not supported yet on IR level")
        }

        return when (binaryExpr.operator) {
            Operator.PLUS -> {
                if (isFloat) LLVMBuildFAdd(builder, leftIR, rightIR, name)
                else LLVMBuildAdd(builder, leftIR, rightIR, name)
            }

            Operator.MINUS -> {
                if (isFloat) LLVMBuildFSub(builder, leftIR, rightIR, name)
                else LLVMBuildSub(builder, leftIR, rightIR, name)
            }

            Operator.POINTER -> {
                if (isFloat) LLVMBuildFMul(builder, leftIR, rightIR, name)
                else LLVMBuildMul(builder, leftIR, rightIR, name)
            }

            Operator.DIVIDE -> {
                if (isFloat) LLVMBuildFDiv(builder, leftIR, rightIR, name)
                else if (leftType.isPrimitive() && leftType.kind.isUnsigned) {
                    LLVMBuildUDiv(builder, leftIR, rightIR, name)
                } else {
                    LLVMBuildSDiv(builder, leftIR, rightIR, name)
                }
            }

            Operator.EQUAL -> {
                if (leftType.isPrimitive() && rightType.isPrimitive()) {
                    if (leftType.kind.isFloat) LLVMBuildFCmp(builder, LLVMRealOEQ, leftIR, rightIR, name)
                    else LLVMBuildICmp(builder, LLVMIntEQ, leftIR, rightIR, name)
                } else throw IllegalArgumentException("Unsupported operator: " + binaryExpr.operator)
            }

            Operator.NOT_EQ -> {
                if (leftType.isPrimitive() && rightType.isPrimitive()) {
                    if (leftType.kind.isFloat) LLVMBuildFCmp(builder, LLVMRealONE, leftIR, rightIR, name)
                    else LLVMBuildICmp(builder, LLVMIntNE, leftIR, rightIR, name)
                } else throw IllegalArgumentException("Unsupported operator: " + binaryExpr.operator)
            }

            else -> throw IllegalArgumentException("Unsupported operator: " + binaryExpr.operator)
        }
    }

    // Возврат значения из функции
    fun visitReturn(node: ReturnStatementNode): LLVMValueRef {
        if (node.expression == null) {
            return LLVMBuildRetVoid(builder)
        }
        return LLVMBuildRet(builder, generateIR(node.expression!!))
    }

    fun emitX86ObjectFile(module: LLVMModuleRef, outputFileName: String) {
        // 1. Инициализация таргетов
        // 2. Использование системного Triple (чтобы избежать конфликтов с ОС)
        val triplePtr = LLVMGetDefaultTargetTriple()
        val targetTriple = triplePtr.string

        val targetPtr = PointerPointer<LLVMTargetRef>(1)
        val errorPtr = BytePointer()

        // 3. Получение Target
        if (LLVMGetTargetFromTriple(targetTriple, targetPtr, errorPtr) != 0) {
//            val msg = errorPtr.get(BytePointer::class.java).string
//            LLVMDisposeMessage(errorPtr.get(BytePointer::class.java))
            throw RuntimeException("Error getting target")
        }

        val target = LLVMTargetRef(targetPtr.get())

        // 4. Создание TargetMachine
        val targetMachine = LLVMCreateTargetMachine(
            target,
            targetTriple,
            "generic",
            "",
            LLVMCodeGenLevelDefault,
            LLVMRelocPIC,
            LLVMCodeModelDefault
        ) ?: throw RuntimeException("Failed to create TargetMachine")

        // 5. УСТАНОВКА TRIPLE И DATA LAYOUT В МОДУЛЬ (Убирает segfault!)
        val dataLayout = LLVMCreateTargetDataLayout(targetMachine)
        LLVMSetTarget(module, targetTriple)
        LLVMSetModuleDataLayout(module, dataLayout)
        LLVMDisposeTargetData(dataLayout)

        // 6. ПРОВЕРКА МОДУЛЯ (Распечатает ошибку IR, если она есть)
        val verifyErr = PointerPointer<BytePointer>(1)
        LLVMDumpModule(module)

        if (LLVMVerifyModule(module, LLVMPrintMessageAction, verifyErr) != 0) {
            throw RuntimeException("LLVM IR is invalid! Check console output.")
        }

        // 7. Запись Object File (.o)
        val outputFile = BytePointer(outputFileName)
        val emitErrorPtr = PointerPointer<BytePointer>(1)


        val result = LLVMTargetMachineEmitToFile(
            targetMachine,
            module,
            outputFile,
            LLVMObjectFile,
            emitErrorPtr
        )

        if (result != 0) {
            val msgPtr = emitErrorPtr.get(BytePointer::class.java)
            val msg = msgPtr.string
            LLVMDisposeMessage(msgPtr)
            throw RuntimeException("Error emitting object file: $msg")
        }

        println("Файл $outputFileName успешно сгенерирован!")

        // Очистка
        LLVMDisposeTargetMachine(targetMachine)
        LLVMDisposeMessage(triplePtr)
    }

    fun String.toDirectByteBuffer(): ByteBuffer {
        val bytes = this.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }

    fun getUniqueName(prefix: String? = null): String {
        return "${prefix ?: "var"}_${nameIdCounter++}"
    }
}