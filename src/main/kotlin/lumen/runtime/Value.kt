package lumen.runtime

import lumen.bytecode.Chunk

sealed class Value

object NilValue : Value()

data class IntValue(val value: Long) : Value()

data class DoubleValue(val value: Double) : Value()

data class BoolValue(val value: Boolean) : Value()

data class StringValue(val value: String) : Value()

class ListValue(val items: MutableList<Value> = mutableListOf()) : Value()

class MapValue(val entries: LinkedHashMap<Value, Value> = LinkedHashMap()) : Value()

class RangeValue(val start: Long, val end: Long, val step: Long) : Value() {
    fun length(): Long {
        if (step > 0) {
            if (end <= start) return 0
            return (end - start + step - 1) / step
        }
        if (start <= end) return 0
        return (start - end + (-step) - 1) / (-step)
    }

    fun elementAt(index: Long): Long = start + index * step
}

class FunctionValue(
    val name: String,
    val arity: Int,
    val chunk: Chunk,
    val upvalueCount: Int
) : Value()

class Upvalue(var location: Int) : Value() {
    var closed: Value = NilValue
    val isClosed: Boolean get() = location < 0
}

class ClosureValue(val function: FunctionValue, val upvalues: Array<Upvalue>) : Value()

class NativeFunctionValue(
    val name: String,
    val arity: IntRange,
    val call: (List<Value>) -> Value
) : Value()

class IteratorValue(val iterator: Iterator<Value>) : Value()

fun isTruthy(value: Value): Boolean = when (value) {
    is NilValue -> false
    is BoolValue -> value.value
    else -> true
}

fun valuesEqual(a: Value, b: Value): Boolean = when {
    a is IntValue && b is IntValue -> a.value == b.value
    a is DoubleValue && b is DoubleValue -> a.value == b.value
    a is IntValue && b is DoubleValue -> a.value.toDouble() == b.value
    a is DoubleValue && b is IntValue -> a.value == b.value.toDouble()
    a is StringValue && b is StringValue -> a.value == b.value
    a is BoolValue && b is BoolValue -> a.value == b.value
    a is NilValue && b is NilValue -> true
    else -> a === b
}

fun typeName(value: Value): String = when (value) {
    is NilValue -> "nil"
    is IntValue -> "int"
    is DoubleValue -> "double"
    is BoolValue -> "bool"
    is StringValue -> "string"
    is ListValue -> "list"
    is MapValue -> "map"
    is RangeValue -> "range"
    is FunctionValue -> "function"
    is ClosureValue -> "function"
    is NativeFunctionValue -> "function"
    is Upvalue -> "upvalue"
    is IteratorValue -> "iterator"
}

fun displayString(value: Value): String = when (value) {
    is NilValue -> "nil"
    is IntValue -> value.value.toString()
    is DoubleValue -> formatDouble(value.value)
    is BoolValue -> value.value.toString()
    is StringValue -> value.value
    is ListValue -> value.items.joinToString(prefix = "[", postfix = "]") { reprString(it) }
    is MapValue -> value.entries.entries.joinToString(prefix = "{", postfix = "}") {
        "${reprString(it.key)}: ${reprString(it.value)}"
    }
    is RangeValue -> "range(${value.start}, ${value.end}, ${value.step})"
    is FunctionValue -> "<fn ${value.name}>"
    is ClosureValue -> "<fn ${value.function.name}>"
    is NativeFunctionValue -> "<native ${value.name}>"
    is Upvalue -> "<upvalue>"
    is IteratorValue -> "<iterator>"
}

private fun reprString(value: Value): String =
    if (value is StringValue) "\"${value.value}\"" else displayString(value)

private fun formatDouble(d: Double): String {
    if (d.isNaN()) return "nan"
    if (d.isInfinite()) return if (d > 0) "inf" else "-inf"
    if (d == d.toLong().toDouble()) return "${d.toLong()}.0"
    return d.toString()
}
