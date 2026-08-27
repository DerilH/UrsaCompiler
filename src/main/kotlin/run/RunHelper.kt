package org.derilh.run

import com.sun.management.ThreadMXBean
import org.derilh.PreProcessor
import org.derilh.ast.ParseResult
import org.derilh.ast.Parser
import org.derilh.core.Options
import org.derilh.lexer.Lexer
import org.derilh.semantic.AnalyzeResult
import org.derilh.semantic.analyzer.SemanticAnalyzer
import org.derilh.target.X86_64LinuxTargetInfo
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class RunHelper {
    companion object{

        fun analyze(code: String, path:Path, options: Options = Options("x86_64Linux", false, false)): Pair<ParseResult, AnalyzeResult> {
            withMemoryWatchdog(1024) {
                val preProcessor = PreProcessor();
                val code = preProcessor.preProcess(code, path)

                val lexer = Lexer()
                val tokens = lexer.tokenize(code, path.toString())

                val sema = SemanticAnalyzer(options);

                val parser = Parser(tokens, sema, options)
                val parseResult = parser.parse();

                return parseResult to sema.analyze(parseResult.root);
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