package org.derilh.core.target

import org.derilh.core.PrimitiveTypeKind
import org.derilh.core.TypeInfo
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val t = TargetTypesInfo(
    isCharSigned = true,
    bool = TypeInfo(8, 8),
    char = TypeInfo(8, 8),
    char8_t = TypeInfo(8,8),
    char16_t = TypeInfo(16,16),
    char32_t = TypeInfo(32,32),
    wchar_t = TypeInfo(32,32),
    short = TypeInfo(16, 16),
    int = TypeInfo(32, 32),
    long = TypeInfo(64, 64),
    longLong = TypeInfo(64, 64),
    float = TypeInfo(32, 32),
    double = TypeInfo(64, 64),
    longDouble = TypeInfo(128, 128),
    pointer = TypeInfo(64, 64),

    sizeType = PrimitiveTypeKind.UNSIGNED_LONG,
    ptrDiffType = PrimitiveTypeKind.LONG,
    intMaxType = PrimitiveTypeKind.LONG_LONG,
    uIntMaxType = PrimitiveTypeKind.UNSIGNED_LONG_LONG,
    intPtrType = PrimitiveTypeKind.LONG,
    uIntPtrType = PrimitiveTypeKind.UNSIGNED_LONG,
    wCharType = PrimitiveTypeKind.INT,
    wIntType = PrimitiveTypeKind.UNSIGNED_INT,
    char16Type = PrimitiveTypeKind.UNSIGNED_SHORT,
    char32Type = PrimitiveTypeKind.UNSIGNED_INT,
    int8Type = PrimitiveTypeKind.SIGNED_CHAR,
    int16Type = PrimitiveTypeKind.SHORT,
    int32Type = PrimitiveTypeKind.INT,
    int64Type = PrimitiveTypeKind.LONG,
    sigAtomicType = PrimitiveTypeKind.INT,
    processIdType = PrimitiveTypeKind.INT,

    floatFormat = FloatFormat.IEEE_SINGLE,
    doubleFormat = FloatFormat.IEEE_DOUBLE,
    longDoubleFormat = FloatFormat.X87_80,

    maxFloat = BigDecimal("3.4028235E38"),
    maxDouble = BigDecimal("1.7976931348623157E308"),
    maxLongDouble = BigDecimal("1.189731495357231765085759326628007016196477e4932")
)

object X86_64LinuxTargetInfo : TargetInfo(architecture = TargetArchitecture.X86_64, types = t) {
    override fun detectIncludes(): List<Path> {
        val paths = mutableListOf<Path>()

        val cppBase = Paths.get("/usr/include/c++")
        if (Files.exists(cppBase)) {
            val latestVersion = Files.list(cppBase)
                .filter { Files.isDirectory(it) }
                .map { it.fileName.toString() }
                .sorted(Comparator.reverseOrder())
                .findFirst()
                .orElse(null)

            if (latestVersion != null) {
                val stlPath = cppBase.resolve(latestVersion)
                paths.add(stlPath)

                val archPath = Paths.get("/usr/include/x86_64-linux-gnu/c++", latestVersion)
                if (Files.exists(archPath)) {
                    paths.add(archPath)
                }
            }
        }

        val standardCPaths = listOf(
            "/usr/local/include",
            "/usr/include/x86_64-linux-gnu",
            "/usr/include",
        )

        for (p in standardCPaths) {
            val path = Paths.get(p)
            if (Files.exists(path)) {
                paths.add(path)
            }
        }

        return paths
    }
}