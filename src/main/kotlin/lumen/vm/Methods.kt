package lumen.vm

import lumen.RuntimeError
import lumen.runtime.*

class Method(
    val name: String,
    val arity: IntRange,
    val call: (Vm, Value, List<Value>) -> Value
)

fun installMethods(vm: Vm) {
    fun define(name: String, arity: IntRange, call: (Vm, Value, List<Value>) -> Value) {
        vm.defineMethod(Method(name, arity, call))
    }

    define("upper", 0..0) { _, receiver, _ -> stringUpper(receiver) }
    define("lower", 0..0) { _, receiver, _ -> stringLower(receiver) }
    define("trim", 0..0) { _, receiver, _ -> stringTrim(receiver) }
    define("split", 1..1) { _, receiver, args -> stringSplit(receiver, args[0]) }
    define("replace", 2..2) { _, receiver, args -> stringReplace(receiver, args[0], args[1]) }
    define("substring", 2..2) { _, receiver, args -> stringSubstring(receiver, args[0], args[1]) }
    define("chars", 0..0) { _, receiver, _ -> stringChars(receiver) }
    define("startsWith", 1..1) { _, receiver, args -> stringStartsWith(receiver, args[0]) }
    define("endsWith", 1..1) { _, receiver, args -> stringEndsWith(receiver, args[0]) }

    define("sort", 0..0) { _, receiver, _ -> listSort(receiver) }
    define("reverse", 0..0) { _, receiver, _ -> listReverse(receiver) }
    define("slice", 2..2) { _, receiver, args -> listSlice(receiver, args[0], args[1]) }
    define("sum", 0..0) { _, receiver, _ -> listSum(receiver) }
    define("push", 1..1) { _, receiver, args -> listPush(receiver, args[0]) }
    define("pop", 0..0) { _, receiver, _ -> listPop(receiver) }
    define("join", 1..1) { _, receiver, args -> listJoin(receiver, args[0]) }

    define("has", 1..1) { _, receiver, args -> mapHas(receiver, args[0]) }
    define("remove", 1..1) { _, receiver, args -> mapRemove(receiver, args[0]) }
    define("entries", 0..0) { _, receiver, _ -> mapEntries(receiver) }
    define("keys", 0..0) { _, receiver, _ -> mapKeys(receiver) }
    define("values", 0..0) { _, receiver, _ -> mapValues(receiver) }

    define("contains", 1..1) { _, receiver, args ->
        when (receiver) {
            is StringValue -> stringContains(receiver, args[0])
            is ListValue -> listContains(receiver, args[0])
            is MapValue -> mapHas(receiver, args[0])
            else -> throw RuntimeError("value of type ${typeName(receiver)} has no method 'contains'", 0)
        }
    }
    define("indexOf", 1..1) { _, receiver, args ->
        when (receiver) {
            is StringValue -> stringIndexOf(receiver, args[0])
            is ListValue -> listIndexOf(receiver, args[0])
            else -> throw RuntimeError("value of type ${typeName(receiver)} has no method 'indexOf'", 0)
        }
    }
    define("min", 0..0) { _, receiver, _ -> listMin(receiver) }
    define("max", 0..0) { _, receiver, _ -> listMax(receiver) }
    define("len", 0..0) { _, receiver, _ -> IntValue(methodLength(receiver)) }

    define("map", 1..1) { runner, receiver, args ->
        val items = asList(receiver, "map")
        ListValue(items.mapTo(ArrayList(items.size)) { runner.invoke(args[0], listOf(it)) })
    }
    define("filter", 1..1) { runner, receiver, args ->
        val items = asList(receiver, "filter")
        ListValue(items.filterTo(ArrayList()) { isTruthy(runner.invoke(args[0], listOf(it))) })
    }
    define("reduce", 2..2) { runner, receiver, args ->
        val items = asList(receiver, "reduce")
        var accumulator = args[1]
        for (element in items) accumulator = runner.invoke(args[0], listOf(accumulator, element))
        accumulator
    }
}

private fun methodLength(receiver: Value): Long = when (receiver) {
    is StringValue -> receiver.value.length.toLong()
    is ListValue -> receiver.items.size.toLong()
    is MapValue -> receiver.entries.size.toLong()
    is RangeValue -> receiver.length()
    else -> throw RuntimeError("value of type ${typeName(receiver)} has no method 'len'", 0)
}
