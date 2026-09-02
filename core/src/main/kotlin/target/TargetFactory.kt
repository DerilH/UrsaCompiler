package org.derilh.core.target

import org.derilh.core.Options
import org.derilh.core.target.TargetInfo
import org.derilh.core.target.X86_64LinuxTargetInfo

class TargetFactory {
    companion object {
        fun createTarget(name: String): TargetInfo {
            return when (name) {
                "x86_64Linux" -> X86_64LinuxTargetInfo;
                else -> throw IllegalArgumentException("Unknown target: ${name}")
            }
        }

        fun getSupportedTargets(): Array<String> {
            return arrayOf("x86_64Linux")
        }
    }
}