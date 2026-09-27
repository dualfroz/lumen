# Lumen

Lumen is a small dynamically typed scripting language implemented in Kotlin. Source
text is scanned into tokens, parsed into an abstract syntax tree, compiled to bytecode,
and executed on a stack-based virtual machine with closures, first-class functions,
lists, maps, lazy ranges, a method-call standard library and file-based modules. It
ships as a command-line tool with a REPL, a file runner and a bytecode disassembler,
and as a small Kotlin API for embedding.

## Features

- Values: `nil`, `bool`, 64-bit `int`, `double`, `string`, `list`, `map`, `range` and
  `function`; `type(value)` returns one of these names
- `let` (immutable) and `var` (mutable) bindings with block scoping; assigning to a
  `let` binding, redeclaring a name in the same scope, and reading a variable inside
  its own initializer are compile errors
- Named function declarations and anonymous `fn (...) { ... }` expressions, closures
  that capture variables by reference, recursion, arity checked on every call
- Control flow: `if` / `else if` / `else`, `while`, `for (x in ...)` over lists, map
  keys, string characters and ranges, `return`
- Operators: `+ - * / %`, `== != < <= > >=`, and short-circuit `and`, `or`, `not`
  that return their operands; only `nil` and `false` are falsy
- Literals: integers, decimals, strings with `\n \t \r \" \\ \0` escapes (strings may span
  lines), list literals `[1, 2]` and map literals `{"key": value}` with any value as a
  key and optional trailing commas
- Indexing for lists, strings and ranges by int and for maps by key (a missing key
  yields `nil`); index assignment on lists and maps
- Statements end at a newline or `;`; newlines inside `(...)` and `[...]` are ignored,
  except inside a `{ ... }` body nested in them (such as a callback passed to `map`),
  where they separate statements as at the top level
- `//` line comments and nestable `/* ... */` block comments
- Built-in functions: `print`, `println`, `len`, `type`, `str`, `int`, `double`,
  `push`, `pop`, `keys`, `values`, `range`, `map`, `filter`, `reduce`, `assert`, `abs`,
  `sqrt`, `pow`, `floor`, `ceil`, `round`, `min`, `max`, and the constant `PI`
- Methods on strings: `upper`, `lower`, `trim`, `split`, `replace`, `substring`,
  `chars`, `startsWith`, `endsWith`, `contains`, `indexOf`, `len`
- Methods on lists: `push`, `pop`, `sort`, `reverse`, `slice`, `sum`, `min`, `max`,
  `join`, `contains`, `indexOf`, `len`, `map`, `filter`, `reduce` (`sort`, `reverse`
  and `slice` return new lists, `push` and `pop` mutate)
- Methods on maps: `has`, `contains`, `remove`, `keys`, `values`, `entries`, `len`;
  ranges support `len`
- Modules: `import "path.lumen"` resolved relative to the importing file, each file
  executed once, import cycles tolerated; `import` inside a function or block is a
  compile error
- Error reports with the line number, the source line and, for lexer, parser and
  runtime errors, the column with a caret under the offending token
- CLI: interactive REPL that prints the value of the last expression, file runner,
  and `--dump` to disassemble the compiled bytecode
- Embedding API: `lumen.Lumen` exposes `tokenize`, `parse`, `compile`, `run`,
  `runToString`, `runFile` and `runFileToString`

## Design

Lumen has the overall shape of clox from *Crafting Interpreters* (a bytecode compiler
feeding a stack VM with upvalue-based closures), but it makes different choices than
Lox and Wren in several places.

### A real AST between the parser and the compiler

clox compiles in a single pass straight from tokens. Lumen keeps the stages apart:
`parser/` builds a typed AST (`ast/Ast.kt`, sealed `Expr` and `Stmt` classes) and
`compiler/` walks it to emit bytecode. Every AST node and every emitted instruction
carries a line and a column, which is what makes the error carets possible, and the
REPL reuses the same compiler with a flag that turns the last expression into the
return value.

### Immutable bindings, enforced by the compiler

Lox and Wren only have `var`. Lumen has `let` for bindings that never change and `var`
for the ones that do, and the compiler rejects assignments to `let` bindings before
anything runs. The mutability table is shared across all modules of a program and
across REPL inputs, so a `let` defined in an imported file cannot be reassigned by its
importer either.

```lumen
let limit = 10
var used = 0
used = used + 1
limit = 11
```

```text
error: cannot assign to immutable 'limit'
  --> line 4
  |
4 | limit = 11
  |
```

### Two number types

Lox and Wren have a single double type. Lumen keeps 64-bit integers and doubles
apart: arithmetic on two ints stays an int (so `/` truncates and dividing by zero is an
error), mixing in a double promotes the result, and equality and ordering compare the
numeric value across both types.

```lumen
println(7 / 2, 7 % 2, 7 / 2.0)
println(type(7), type(7 / 2.0), 1 == 1.0)
```

```text
3 1 3.5
int double true
```

### Methods without classes

There are no classes, instances or traits. Instead `value.name(args)` compiles to a
single `Invoke` instruction that looks the method up by name in a table owned by the
VM (`vm/Methods.kt`), and each method checks the receiver type it supports. This gives
built-in types fluent method syntax without an object model, and new methods can be
registered from Kotlin with `Vm.defineMethod`.

```lumen
let words = "  the quick brown fox ".trim().split(" ")
println(words.map(fn(w) { return w.upper() }).join("-"))
println([3, 1, 2].sort().reverse(), "lumen".contains("me"))
```

```text
THE-QUICK-BROWN-FOX
[3, 2, 1] true
```

### Lazy ranges and one iteration protocol

`range(start, end, step)` produces a range value that is never materialized: `len`,
indexing and iteration are computed from its bounds. `for ... in` compiles to a pair of
`GetIter` / `ForIter` instructions that work the same way for lists, map keys, string
characters and ranges. Lists and maps are iterated over a snapshot, so the loop body may
modify the collection it is walking.

```lumen
let evens = range(0, 1000000000000, 2)
println(len(evens), evens[21])
for (i in range(10, 0, -3)) {
    print(i, "")
}
println()
let queue = [1, 2]
for (n in queue) {
    queue.push(n * 10)
}
println(queue)
```

```text
500000000000 42
10 7 4 1 
[1, 2, 10, 20]
```

### Closures over shared upvalues

Captured variables become upvalues that point at the live stack slot while the
enclosing function runs and are moved to the heap when it returns. Closures that
capture the same variable share one upvalue, so they see each other's writes.

```lumen
fn makeAccount() {
    var balance = 0
    let deposit = fn(amount) {
        balance = balance + amount
        return balance
    }
    let current = fn() { return balance }
    return {"deposit": deposit, "current": current}
}

let account = makeAccount()
account["deposit"](50)
account["deposit"](25)
println(account["current"]())
```

```text
75
```

### Native higher-order functions call back into the VM

`map`, `filter` and `reduce` are written in Kotlin but take Lumen closures. The VM is
re-entrant: a native function can call `Vm.invoke`, which pushes a new frame and runs
the dispatch loop until that frame returns, so closures passed to natives keep their
captured state.

```lumen
let scale = 3
println(reduce(fn(total, n) { return total + n * scale }, range(1, 5), 0))
```

```text
30
```

### Modules are files

`import` takes a path relative to the importing file. Before a file is compiled, the
loader runs all of its imports; each file is identified by its canonical path, runs at
most once, and a file that is already being loaded is skipped, which makes import cycles
harmless. Modules share one global namespace: whatever a module defines at the top
level is visible to its importer.

```lumen
// lib/geometry.lumen
fn area(width, height) {
    return width * height
}
let unit = "cm2"
```

```lumen
// main.lumen
import "lib/geometry.lumen"

println(str(area(3, 4)) + " " + unit)
```

```text
12 cm2
```

## Building

Requires a JDK 17 or newer to run Gradle. The build compiles for a Java 17 toolchain
and lets Gradle download one if it is not installed. The Gradle wrapper is included:

```sh
./gradlew build installDist
```

This produces a launcher in `build/install/lumen/bin/lumen`.

## Running

Start the REPL:

```sh
build/install/lumen/bin/lumen
```

Run a program:

```sh
build/install/lumen/bin/lumen examples/fib.lumen
```

Disassemble the compiled bytecode of a file:

```sh
build/install/lumen/bin/lumen --dump examples/fib.lumen
```

The same commands work through Gradle, for example
`./gradlew run --args="examples/fib.lumen"`.

Embedding from Kotlin:

```kotlin
val output = Lumen.runToString("println(1 + 2)")
```

The [`examples/`](examples/) directory contains a reverse-polish calculator, closures,
quicksort, FizzBuzz, Fibonacci, a CSV parser, a tour of the standard library, and two
library modules under `examples/lib/` used by the calculator and the CSV parser.

## Tests

```sh
./gradlew test
```

The suite covers the lexer, parser, compiler, VM, methods, standard library, imports,
error reports, and runs the programs in `examples/` end to end.

## Project layout

```
src/main/kotlin/lumen/
  Pipeline.kt              embedding API (tokenize, parse, compile, run)
  LumenError.kt            error types and source-annotated error formatting
  lexer/                   token definitions and the tokenizer
  ast/                     abstract syntax tree nodes
  parser/                  recursive descent parser
  bytecode/                opcodes, chunk container and disassembler
  compiler/                AST to bytecode compiler, scopes, locals and upvalues
  runtime/                 value model, equality, truthiness and display
  vm/                      virtual machine, built-in functions, methods, stdlib
  module/                  module loader and import resolution
  cli/                     REPL and command-line entry point
src/test/kotlin/lumen/     test suite
examples/                  sample programs and library modules
```

## Limitations

- No classes, user-defined methods, string interpolation, pattern matching, `break` or
  `continue`, exceptions, or a file and network library.
- Integer arithmetic wraps on 64-bit overflow.
- Map keys use type-exact equality: `1` and `1.0` are different keys, and lists, maps
  and functions are keys by identity.
- Module names are not namespaced.
- The REPL evaluates one line at a time, so a multi-line block has to be written on a
  single line.
- `--dump` disassembles only the given file, not its imports.

## License

MIT, see [LICENSE](LICENSE).
