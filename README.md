# Lumen

Lumen is a small dynamically-typed scripting language implemented in Kotlin. Source
is scanned into tokens, parsed into an abstract syntax tree, compiled to a compact
bytecode representation, and executed on a stack-based virtual machine.

## Features

- First-class functions and closures that capture mutable state
- `let` / `var` bindings with lexical scoping
- Control flow: `if` / `else`, `while`, and `for ... in` loops
- Lists and maps with literal syntax and built-in methods
- String, list, and map methods plus a small numeric standard library
- A module system driven by `import` for splitting programs across files
- Built-ins such as `print`, `println`, `len`, `type`, `str`, `int`, `assert`,
  `map`, `filter`, and `reduce`
- An interactive REPL and a file runner

## Building

The project uses the Gradle wrapper, so no local Gradle install is required:

```sh
./gradlew build
```

## Running

Start the REPL:

```sh
./gradlew run
```

Run a program file:

```sh
./gradlew run --args="examples/fib.lumen"
```

Disassemble the compiled bytecode for a file:

```sh
./gradlew run --args="--dump examples/fib.lumen"
```

## Example

```lumen
fn makeCounter() {
    var count = 0
    fn increment() {
        count = count + 1
        return count
    }
    return increment
}

let counter = makeCounter()
println(counter(), counter(), counter())

let numbers = [1, 2, 3, 4, 5]
let doubled = map(fn(x) { return x * 2 }, numbers)
println(doubled)
```

More programs live in [`examples/`](examples/), including a reverse-polish
calculator, closures, sorting, CSV handling, and reusable library modules under
`examples/lib/`.

## Project layout

```
src/main/kotlin/lumen/
  lexer/      token definitions and the tokenizer
  ast/        abstract syntax tree nodes
  parser/     recursive descent parser
  bytecode/   opcodes, chunk container, and disassembler
  compiler/   AST to bytecode compiler
  runtime/    runtime value model
  vm/         virtual machine, builtins, methods, and standard library
  module/     module loader and import resolution
  cli/        REPL and command-line entry point
src/test/kotlin/lumen/   test suite
examples/                sample Lumen programs
```

## Tests

```sh
./gradlew test
```
