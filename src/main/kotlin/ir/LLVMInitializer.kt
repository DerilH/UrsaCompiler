package org.derilh.ir

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.LLVMTargetRef
import org.bytedeco.llvm.global.LLVM.LLVMCodeGenLevelDefault
import org.bytedeco.llvm.global.LLVM.LLVMCodeModelDefault
import org.bytedeco.llvm.global.LLVM.LLVMCreateTargetDataLayout
import org.bytedeco.llvm.global.LLVM.LLVMCreateTargetMachine
import org.bytedeco.llvm.global.LLVM.LLVMDisposeMessage
import org.bytedeco.llvm.global.LLVM.LLVMGetDefaultTargetTriple
import org.bytedeco.llvm.global.LLVM.LLVMGetTargetFromTriple
import org.bytedeco.llvm.global.LLVM.LLVMInitializeX86AsmParser
import org.bytedeco.llvm.global.LLVM.LLVMInitializeX86AsmPrinter
import org.bytedeco.llvm.global.LLVM.LLVMInitializeX86Target
import org.bytedeco.llvm.global.LLVM.LLVMInitializeX86TargetInfo
import org.bytedeco.llvm.global.LLVM.LLVMInitializeX86TargetMC
import org.bytedeco.llvm.global.LLVM.LLVMRelocPIC
import org.derilh.core.target.TargetArchitecture
import org.derilh.core.target.TargetInfo

class LLVMInitializer {
    companion object {
        fun init(targetInfo: TargetInfo): LLVMTargetInfo {
            when (targetInfo.architecture) {
                TargetArchitecture.X86_64 -> {
                    LLVMInitializeX86TargetInfo();
                    LLVMInitializeX86Target();
                    LLVMInitializeX86TargetMC();
                    LLVMInitializeX86AsmPrinter();
                    LLVMInitializeX86AsmParser()
                }

                else -> throw IllegalArgumentException("Unsupported architecture: ${targetInfo.architecture}")
            }

            val triplePtr = LLVMGetDefaultTargetTriple()
            val targetTriple = triplePtr.string

            val targetPtr = PointerPointer<LLVMTargetRef>(1)
            val errorPtr = BytePointer()

            if (LLVMGetTargetFromTriple(targetTriple, targetPtr, errorPtr) != 0) {
                throw RuntimeException("Error getting target")
            }

            val target = LLVMTargetRef(targetPtr.get())

            val targetMachine = LLVMCreateTargetMachine(
                target,
                targetTriple,
                "generic",
                "",
                LLVMCodeGenLevelDefault,
                LLVMRelocPIC,
                LLVMCodeModelDefault
            ) ?: throw RuntimeException("Failed to create TargetMachine")

            val dataLayout = LLVMCreateTargetDataLayout(targetMachine)

            LLVMDisposeMessage(triplePtr)
            return LLVMTargetInfo(targetMachine, dataLayout, triplePtr)
        }
    }
}