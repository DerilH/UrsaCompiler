package org.derilh.ir

import run.EmitFormat

interface IIRModule {
    fun compileTo(outputFileName: String, emitFormat: EmitFormat): Boolean;
    fun optimize(optLevel: String = "O0");
}