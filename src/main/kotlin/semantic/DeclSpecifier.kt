package semantic

enum class StorageClassSpecifier {
    NONE,
    STATIC,
    EXTERN,
    MUTABLE
}

data class DeclSpecifier(val stageSpec: StorageClassSpecifier, ) {



}