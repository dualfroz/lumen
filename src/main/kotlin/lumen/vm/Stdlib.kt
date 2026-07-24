package lumen.vm

import lumen.RuntimeError
import lumen.runtime.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

fun asString(value: Value, ctx: String): String =
    (value as? StringValue)?.value ?: throw RuntimeError("$ctx expects a string, got ${typeName(value)}", 0)

fun asList(value: Value, ctx: String): MutableList<Value> =
    (value as? ListValue)?.items ?: throw RuntimeError("$ctx expects a list, got ${typeName(value)}", 0)

fun asMap(value: Value, ctx: String): LinkedHashMap<Value, Value> =
    (value as? MapValue)?.entries ?: throw RuntimeError("$ctx expects a map, got ${typeName(value)}", 0)

fun asIntArg(value: Value, ctx: String): Int =
    (value as? IntValue)?.value?.toInt() ?: throw RuntimeError("$ctx expects an int, got ${typeName(value)}", 0)

fun compareValues(a: Value, b: Value): Int = when {
    a is IntValue && b is IntValue -> a.value.compareTo(b.value)
    a is StringValue && b is StringValue -> a.value.compareTo(b.value)
    isNumeric(a) && isNumeric(b) -> numberOf(a).compareTo(numberOf(b))
    else -> throw RuntimeError("cannot order ${typeName(a)} and ${typeName(b)}", 0)
}

private fun isNumeric(value: Value): Boolean = value is IntValue || value is DoubleValue

private fun numberOf(value: Value): Double = when (value) {
    is IntValue -> value.value.toDouble()
    is DoubleValue -> value.value
    else -> 0.0
}

fun stringUpper(receiver: Value): Value = StringValue(asString(receiver, "upper").uppercase())

fun stringLower(receiver: Value): Value = StringValue(asString(receiver, "lower").lowercase())

fun stringTrim(receiver: Value): Value = StringValue(asString(receiver, "trim").trim())

fun stringSplit(receiver: Value, separator: Value): Value {
    val text = asString(receiver, "split")
    val sep = asString(separator, "split")
    val parts = if (sep.isEmpty()) text.map { it.toString() } else text.split(sep)
    return ListValue(parts.mapTo(ArrayList(parts.size)) { StringValue(it) })
}

fun stringReplace(receiver: Value, old: Value, new: Value): Value =
    StringValue(asString(receiver, "replace").replace(asString(old, "replace"), asString(new, "replace")))

fun stringContains(receiver: Value, needle: Value): Value =
    BoolValue(asString(receiver, "contains").contains(asString(needle, "contains")))

fun stringIndexOf(receiver: Value, needle: Value): Value =
    IntValue(asString(receiver, "indexOf").indexOf(asString(needle, "indexOf")).toLong())

fun stringSubstring(receiver: Value, start: Value, end: Value): Value {
    val text = asString(receiver, "substring")
    val from = asIntArg(start, "substring")
    val to = asIntArg(end, "substring")
    if (from < 0 || to > text.length || from > to) {
        throw RuntimeError("substring range $from..$to out of bounds for length ${text.length}", 0)
    }
    return StringValue(text.substring(from, to))
}

fun stringChars(receiver: Value): Value {
    val text = asString(receiver, "chars")
    return ListValue(text.mapTo(ArrayList(text.length)) { StringValue(it.toString()) })
}

fun stringStartsWith(receiver: Value, prefix: Value): Value =
    BoolValue(asString(receiver, "startsWith").startsWith(asString(prefix, "startsWith")))

fun stringEndsWith(receiver: Value, suffix: Value): Value =
    BoolValue(asString(receiver, "endsWith").endsWith(asString(suffix, "endsWith")))

fun listSort(receiver: Value): Value {
    val items = asList(receiver, "sort")
    val sorted = ArrayList(items)
    sorted.sortWith { a, b -> compareValues(a, b) }
    return ListValue(sorted)
}

fun listReverse(receiver: Value): Value {
    val items = asList(receiver, "reverse")
    val reversed = ArrayList(items)
    reversed.reverse()
    return ListValue(reversed)
}

fun listSlice(receiver: Value, start: Value, end: Value): Value {
    val items = asList(receiver, "slice")
    val from = asIntArg(start, "slice")
    val to = asIntArg(end, "slice")
    if (from < 0 || to > items.size || from > to) {
        throw RuntimeError("slice range $from..$to out of bounds for length ${items.size}", 0)
    }
    return ListValue(ArrayList(items.subList(from, to)))
}

fun listIndexOf(receiver: Value, target: Value): Value {
    val items = asList(receiver, "indexOf")
    val index = items.indexOfFirst { valuesEqual(it, target) }
    return IntValue(index.toLong())
}

fun listContains(receiver: Value, target: Value): Value {
    val items = asList(receiver, "contains")
    return BoolValue(items.any { valuesEqual(it, target) })
}

fun listSum(receiver: Value): Value {
    val items = asList(receiver, "sum")
    var longSum = 0L
    var doubleSum = 0.0
    var isDouble = false
    for (element in items) {
        when (element) {
            is IntValue -> {
                longSum += element.value
                doubleSum += element.value
            }
            is DoubleValue -> {
                isDouble = true
                doubleSum += element.value
            }
            else -> throw RuntimeError("sum expects numbers, found ${typeName(element)}", 0)
        }
    }
    return if (isDouble) DoubleValue(doubleSum) else IntValue(longSum)
}

fun listMin(receiver: Value): Value {
    val items = asList(receiver, "min")
    if (items.isEmpty()) throw RuntimeError("min of empty list", 0)
    return items.reduce { a, b -> if (compareValues(b, a) < 0) b else a }
}

fun listMax(receiver: Value): Value {
    val items = asList(receiver, "max")
    if (items.isEmpty()) throw RuntimeError("max of empty list", 0)
    return items.reduce { a, b -> if (compareValues(b, a) > 0) b else a }
}

fun listPush(receiver: Value, element: Value): Value {
    asList(receiver, "push").add(element)
    return receiver
}

fun listPop(receiver: Value): Value {
    val items = asList(receiver, "pop")
    if (items.isEmpty()) throw RuntimeError("pop from empty list", 0)
    return items.removeAt(items.size - 1)
}

fun listJoin(receiver: Value, separator: Value): Value {
    val items = asList(receiver, "join")
    val sep = asString(separator, "join")
    return StringValue(items.joinToString(sep) { displayString(it) })
}

fun mapHas(receiver: Value, key: Value): Value =
    BoolValue(asMap(receiver, "has").containsKey(key))

fun mapRemove(receiver: Value, key: Value): Value =
    asMap(receiver, "remove").remove(key) ?: NilValue

fun mapKeys(receiver: Value): Value = ListValue(ArrayList(asMap(receiver, "keys").keys))

fun mapValues(receiver: Value): Value = ListValue(ArrayList(asMap(receiver, "values").values))

fun mapEntries(receiver: Value): Value {
    val entries = asMap(receiver, "entries")
    return ListValue(entries.entries.mapTo(ArrayList(entries.size)) {
        ListValue(mutableListOf(it.key, it.value))
    })
}

fun mathAbs(value: Value): Value = when (value) {
    is IntValue -> IntValue(abs(value.value))
    is DoubleValue -> DoubleValue(abs(value.value))
    else -> throw RuntimeError("abs expects a number, got ${typeName(value)}", 0)
}

fun mathSqrt(value: Value): Value = DoubleValue(sqrt(numberArg(value, "sqrt")))

fun mathPow(base: Value, exponent: Value): Value =
    DoubleValue(numberArg(base, "pow").pow(numberArg(exponent, "pow")))

fun mathFloor(value: Value): Value = when (value) {
    is IntValue -> value
    is DoubleValue -> IntValue(floor(value.value).toLong())
    else -> throw RuntimeError("floor expects a number, got ${typeName(value)}", 0)
}

fun mathCeil(value: Value): Value = when (value) {
    is IntValue -> value
    is DoubleValue -> IntValue(ceil(value.value).toLong())
    else -> throw RuntimeError("ceil expects a number, got ${typeName(value)}", 0)
}

fun mathRound(value: Value): Value = when (value) {
    is IntValue -> value
    is DoubleValue -> IntValue(value.value.roundToLong())
    else -> throw RuntimeError("round expects a number, got ${typeName(value)}", 0)
}

fun mathMin(a: Value, b: Value): Value = if (compareValues(b, a) < 0) b else a

fun mathMax(a: Value, b: Value): Value = if (compareValues(b, a) > 0) b else a

private fun numberArg(value: Value, ctx: String): Double = when (value) {
    is IntValue -> value.value.toDouble()
    is DoubleValue -> value.value
    else -> throw RuntimeError("$ctx expects a number, got ${typeName(value)}", 0)
}
