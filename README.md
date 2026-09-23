# UrsaCompiler

UrsaCompiler is an ambitious project to develop a fully independent C++ compiler from scratch, including its own frontend and eventually its own backend with custom code generation. 

> [!WARNING]
> The project is currently a **Work in Progress**. While the goal is total independence, the custom code generation backend is still under development. Currently, the compiler leverages **LLVM** for stable IR generation and machine code output.

## Project Vision

The primary objective of UrsaCompiler is to explore and implement the complexities of the C++ language without relying on existing compiler frontends. It aims to provide a clear, modular architecture that handles everything from lexical analysis to semantic verification and final binary emission.

## Features & Implementation Highlights

During development, several complex C++ features have been implemented or are in progress:

### 1. Robust Semantic Analysis
The semantic analyzer is the heart of UrsaCompiler. It handles:
*   **Overload Resolution:** Implementation of C++ rules for finding the best function match, including support for operators and constructors.
*   **Argument Dependent Lookup (ADL):** Correctly resolving functions based on the types of their arguments.
*   **Standard Conversion Sequences (SCS):** A sophisticated system to rank and apply implicit type conversions (Lvalue-to-rvalue, Qualification conversions, etc.), mimicking the C++ standard's ranking system.

### 2. Modern Frontend Architecture
*   **Recursive Descent Parser:** A handwritten parser designed to handle the notoriously difficult C++ grammar, including ambiguous constructs and recovery from syntax errors.
*   **Namespace & Scope Management:** Full support for nested namespaces, class scopes, and complex identifier resolution (qualified and unqualified).

### 3. Low-Level Integration
*   **Inline Assembly:** Support for `asm` blocks (including `volatile` and operand constraints), allowing direct hardware interaction.
*   **Linkage Specifications:** Support for `extern "C"` and other linkage types to facilitate interoperability.

### 4. Target Awareness
The compiler is designed with target-specific information in mind, supporting different ABIs and platform-specific configurations (e.g., `x86_64LinuxTargetInfo`).

## Current Architecture

The compiler is divided into several logical stages:

1.  **Lexer:** Tokenizes source code, handling macros and C++ specific literals.
2.  **Parser:** Builds an Abstract Syntax Tree (AST) from the token stream.
3.  **Semantic Analyzer:** Performs type checking, symbol resolution, and transforms the AST into a semantically verified form (including implicit casts).
4.  **IR Generation:** 
    *   **LLVM Backend:** Currently the primary backend, translating verified AST nodes into LLVM IR.
    *   **Ursa IR (UIR):** The future independent backend (in development), intended to replace LLVM for true independence.
5.  **Optimization & Emission:** Leverages LLVM's optimization passes and generates Assembly or Binary files.

## Project Structure

*   `core/`: Contains base interfaces for AST, Target info, and common utilities.
*   `src/main/kotlin/lexer/`: Tokenization logic.
*   `src/main/kotlin/ast/`: Parser implementation and AST node definitions.
*   `src/main/kotlin/semantic/`: The complex logic for type checking and name resolution.
*   `src/main/kotlin/ir/`: IR builders for both LLVM and the future internal IR.
*   `src/main/kotlin/run/`: CLI implementation using Clikt.

## Getting Started

### Prerequisites
*   JDK 17 or higher
*   Maven
*   LLVM libraries (used via JavaCPP)

### Building
```bash
mvn clean install
```
Add `-Djavacpp.platform` to specify the target platform (e.g., linux-x86_64) 

### Usage
You can run the compiler via the command line:
```bash
java -jar UrsaCompiler.jar -i inFilePath -o outFilePath compile
```

*   `--emit-IR`: Dumps the generated LLVM IR.
*   `--emit-ASM`: Generates assembly code.
*   `--emit-BIN`: (Default) Generates an executable binary.
*   `-O0` to `-O3`: Sets the optimization level.

## Contributing

As this is a personal project intended for learning and exploration, it is currently in an early state. However, feedback and discussions on compiler architecture are always welcome.
