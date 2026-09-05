package org.derilh.core.target

import org.derilh.core.OpResult
import org.derilh.core.asSuccess
import org.derilh.core.getOrElse
import org.derilh.core.ifFailure

class TargetFactory {
    companion object {
        /**
         * Creates a target based on the given name.
         * @param name the name of the target may be [null] for autodetection base on current system information
         * @throws IllegalArgumentException if the name is not recognized
         */
        fun createTarget(name: String?): TargetInfo {
            return when (name) {
                "x86_64Linux" -> X86_64LinuxTargetInfo;
                null -> {
                    val os = detectOs().getOrElse { throw IllegalStateException(it.message) };
                    val arch = detectArchitecture().getOrElse() { throw IllegalStateException(it.message) };
                    resolveTarget(arch,os).getOrElse { throw IllegalStateException(it.message) }
                }
                else -> throw IllegalArgumentException("Unknown target: ${name}")
            }
        }

        fun getSupportedTargets(): Array<String> {
            return arrayOf("x86_64Linux")
        }

        private fun resolveTarget(arch: TargetArchitecture, os: TargetOS): OpResult<TargetInfo> {
            if(arch == TargetArchitecture.X86_64 && os == TargetOS.LINUX) return X86_64LinuxTargetInfo.asSuccess
            return OpResult.failure("Unsupported target: ${arch.name} ${os.name}")
        }

        private fun detectOs(): OpResult<TargetOS> {
            val osName = System.getProperty("os.name").lowercase()
            return when(osName) {
                "linux" -> TargetOS.LINUX.asSuccess
                else -> OpResult.failure("Unsupported OS: $osName")
            }
        }

        private fun detectArchitecture(): OpResult<TargetArchitecture> {
            val osArch = System.getProperty("os.arch").lowercase()

            val is64Bit = osArch.contains("64") || osArch.contains("aarch64")

            return when {
                osArch.startsWith("x86") || osArch.startsWith("amd64") -> {
                    if (is64Bit) TargetArchitecture.X86_64.asSuccess else TargetArchitecture.X86_32.asSuccess
                }
//                osArch.startsWith("arm") || osArch.startsWith("aarch") -> {
//                    if (is64Bit) TargetArchitecture.ARM_64 else TargetArchitecture.ARM_32
//                }
                else -> OpResult.failure("Unsupported architecture: ${osArch}")
            }
        }
    }
}