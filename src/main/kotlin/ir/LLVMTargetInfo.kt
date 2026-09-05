package org.derilh.ir

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.llvm.LLVM.LLVMTargetDataRef
import org.bytedeco.llvm.LLVM.LLVMTargetMachineRef

data class LLVMTargetInfo (
    val target: LLVMTargetMachineRef,
    val dataLayout: LLVMTargetDataRef,
    val targetTriple: BytePointer,
)