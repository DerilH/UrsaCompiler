package org.derilh.ir

interface IIRModule {
    fun compileTo(outputFileName: String): Boolean;
    fun emitIRTo(outputFileName: String): Boolean;
}