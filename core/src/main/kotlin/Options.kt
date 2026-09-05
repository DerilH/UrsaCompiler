package org.derilh.core

import org.derilh.core.target.TargetInfo
import java.io.File
import java.nio.file.Path

data class IncludePaths(
    val quoteIncludePaths: List<Path> = emptyList(), // -iquote
    val systemIncludePaths: List<Path> = emptyList() // -I, -isystem, C_INCLUDE_PATH
)

data class Options(
    val target: TargetInfo,
    val traceErrors: Boolean,
    val inputFile: Path,
    val outputMethod: OutputMethod,
    val includes: IncludePaths
)


sealed class OutputMethod {
    object Terminal : OutputMethod()
    class File(val file: Path) : OutputMethod()
}
