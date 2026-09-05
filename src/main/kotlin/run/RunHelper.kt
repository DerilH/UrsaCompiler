package org.derilh.run

import com.sun.management.ThreadMXBean
import org.derilh.ast.ParseResult
import org.derilh.ast.Parser
import org.derilh.core.Options
import org.derilh.core.OutputMethod
import org.derilh.core.preprocessor.Preprocessor
import org.derilh.core.target.TargetFactory
import org.derilh.lexer.Lexer
import org.derilh.lexer.Macro
import org.derilh.semantic.AnalyzeResult
import org.derilh.semantic.analyzer.SemanticAnalyzer
import run.ModuleOutput
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class RunHelper {
    companion object {
        fun runLexer(code: String, options: Options, macros: Collection<Macro> = emptyList()): ModuleOutput {
            val lexer = Lexer(predefinedMacros = Preprocessor.buildTargetMacros(options.target) + macros, options = options);
            val tokens = lexer.tokenize(code, options.inputFile.toAbsolutePath().toString())
            return ModuleOutput(code, options).also {
                it.lexer = lexer;
                it.tokens = tokens;
            }
        }

        fun parse(moduleOutput: ModuleOutput): ModuleOutput {
            val sema = SemanticAnalyzer(moduleOutput.options);
            if (moduleOutput.tokens == null) throw IllegalStateException("Module must be tokenized before parsing")
            val parser = Parser(moduleOutput.tokens!!, sema, moduleOutput.options)
            val parseResult = parser.parse();
            return moduleOutput.also {
                moduleOutput.parser = parser;
                moduleOutput.sema = sema;
                moduleOutput.parseResult = parseResult;
            }
        }

        fun analyze(moduleOutput: ModuleOutput): ModuleOutput {
            moduleOutput.parseResult ?: throw IllegalStateException("Module must be parsed before analyzing")
            moduleOutput.sema ?: throw IllegalStateException("Module must be parsed before analyzing")

            val analyzeResult = moduleOutput.sema!!.analyze(moduleOutput.parseResult!!.ast);
            return moduleOutput.also {
                moduleOutput.analyzeResult = analyzeResult
            }
        }

        fun parse(code: String, path: Path, options: Options): ModuleOutput {
            withMemoryWatchdog(1024) {
                return parse(runLexer(code, options));
            }
        }

        fun analyze(code: String, path: Path, options: Options): ModuleOutput {
            withMemoryWatchdog(1024) {
                return analyze(parse(runLexer(code, options)));
            }
        }

        @OptIn(ExperimentalAtomicApi::class)
        inline fun <T> withMemoryWatchdog(
            maxAllowedMB: Long,
            checkIntervalMs: Long = 50,
            block: () -> T
        ): T {
            val maxAllowedMB = maxAllowedMB * 1024 * 1024;
            val targetThread = Thread.currentThread()
            val threadMXBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

            val isThreadMemorySupported = threadMXBean.isThreadAllocatedMemorySupported
            if (isThreadMemorySupported) {
                threadMXBean.isThreadAllocatedMemoryEnabled = true
            }

            val isExceeded = AtomicBoolean(false)
            val initialAllocatedBytes = if (isThreadMemorySupported) threadMXBean.getThreadAllocatedBytes(targetThread.id) else 0L

            val watchdog = Thread {
                val runtime = Runtime.getRuntime()

                while (!Thread.currentThread().isInterrupted) {
                    val isLimitViolated = if (isThreadMemorySupported) {
                        val currentAllocated = threadMXBean.getThreadAllocatedBytes(targetThread.id)
                        (currentAllocated - initialAllocatedBytes) > maxAllowedMB
                    } else {
                        val used = runtime.totalMemory() - runtime.freeMemory()
                        used > maxAllowedMB
                    }

                    if (isLimitViolated) {
                        isExceeded.store(true)
                        targetThread.interrupt()
                        break
                    }

                    try {
                        Thread.sleep(checkIntervalMs)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }

            watchdog.isDaemon = true
            watchdog.start()

            return try {
                val result = block()
                result
            } catch (e: InterruptedException) {
                if (isExceeded.load()) {
                    throw RuntimeException("Watchdog: Memory limit reached (${maxAllowedMB / 1024 / 1024} MB)!")
                } else {
                    throw e
                }
            } finally {
                watchdog.interrupt()
            }
        }
    }
}