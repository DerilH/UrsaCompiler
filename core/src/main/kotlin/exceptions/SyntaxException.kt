package org.derilh.exceptions

class SyntaxProblem(val msg: String, val level: ProblemLevel, val location: org.derilh.core.SourceLocation, var trace: Array<StackTraceElement>? = null)