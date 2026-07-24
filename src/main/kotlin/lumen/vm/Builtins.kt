package lumen.vm

import lumen.RuntimeError
import lumen.runtime.*

fun installBuiltins(vm: Vm) {
    vm.defineNative("print", NativeFunctionValue("print", 0..Int.MAX_VALUE) { args ->
        vm.out.append(args.joinToString(" ") { displayString(it) })
        NilValue
    })

    vm.defineNative("println", NativeFunctionValue("println", 0..Int.MAX_VALUE) { args ->
        vm.out.append(args.joinToString(" ") { displayString(it) })
        vm.out.append("\n")
        NilValue
    })

    vm.defineNative("len", NativeFunctionValue("len", 1..1) { args ->
        IntValue(lengthOf(args[0]))
    })

    vm.defineNative("type", NativeFunctionValue("type", 1..1) { args ->
        StringValue(typeName(args[0]))
    })

    vm.defineNative("str", NativeFunctionValue("str", 1..1) { args ->
        StringValue(displayString(args[0]))
    })

    vm.defineNative("int", NativeFunctionValue("int", 1..1) { args ->
        toInt(args[0])
    })

    vm.defineNative("double", NativeFunctionValue("double", 1..1) { args ->
        toDouble(args[0])
    })

    vm.defineNative("push", NativeFunctionValue("push", 2..2) { args ->
        val list = args[0] as? ListValue
            ?: throw RuntimeError("push expects a list, got ${typeName(args[0])}", 0)
        list.items.add(args[1])
        list
    })

    vm.defineNative("pop", NativeFunctionValue("pop", 1..1) { args ->
        val list = args[0] as? ListValue
            ?: throw RuntimeError("pop expects a list, got ${typeName(args[0])}", 0)
        if (list.items.isEmpty()) throw RuntimeError("pop from empty list", 0)
        list.items.removeAt(list.items.size - 1)
    })

    vm.defineNative("keys", NativeFunctionValue("keys", 1..1) { args ->
        val map = args[0] as? MapValue
            ?: throw RuntimeError("keys expects a map, got ${typeName(args[0])}", 0)
        ListValue(ArrayList(map.entries.keys))
    })

    vm.defineNative("values", NativeFunctionValue("values", 1..1) { args ->
        val map = args[0] as? MapValue
            ?: throw RuntimeError("values expects a map, got ${typeName(args[0])}", 0)
        ListValue(ArrayList(map.entries.values))
    })

    vm.defineNative("range", NativeFunctionValue("range", 1..3) { args ->
        makeRange(args)
    })

    vm.defineNative("map", NativeFunctionValue("map", 2..2) { args ->
        val elements = elementsOf(args[1])
        ListValue(elements.mapTo(ArrayList(elements.size)) { vm.invoke(args[0], listOf(it)) })
    })

    vm.defineNative("filter", NativeFunctionValue("filter", 2..2) { args ->
        val elements = elementsOf(args[1])
        ListValue(elements.filterTo(ArrayList()) { isTruthy(vm.invoke(args[0], listOf(it))) })
    })

    vm.defineNative("reduce", NativeFunctionValue("reduce", 3..3) { args ->
        var accumulator = args[2]
        for (element in elementsOf(args[1])) {
            accumulator = vm.invoke(args[0], listOf(accumulator, element))
        }
        accumulator
    })

    vm.defineNative("assert", NativeFunctionValue("assert", 1..2) { args ->
        if (!isTruthy(args[0])) {
            val message = if (args.size == 2) displayString(args[1]) else "assertion failed"
            throw RuntimeError(message, 0)
        }
        NilValue
    })

    vm.defineNative("abs", NativeFunctionValue("abs", 1..1) { args -> mathAbs(args[0]) })
    vm.defineNative("sqrt", NativeFunctionValue("sqrt", 1..1) { args -> mathSqrt(args[0]) })
    vm.defineNative("pow", NativeFunctionValue("pow", 2..2) { args -> mathPow(args[0], args[1]) })
    vm.defineNative("floor", NativeFunctionValue("floor", 1..1) { args -> mathFloor(args[0]) })
    vm.defineNative("ceil", NativeFunctionValue("ceil", 1..1) { args -> mathCeil(args[0]) })
    vm.defineNative("round", NativeFunctionValue("round", 1..1) { args -> mathRound(args[0]) })

    vm.defineNative("min", NativeFunctionValue("min", 1..2) { args ->
        if (args.size == 1) listMin(args[0]) else mathMin(args[0], args[1])
    })
    vm.defineNative("max", NativeFunctionValue("max", 1..2) { args ->
        if (args.size == 1) listMax(args[0]) else mathMax(args[0], args[1])
    })

    vm.globals["PI"] = DoubleValue(Math.PI)
}

private fun lengthOf(value: Value): Long = when (value) {
    is StringValue -> value.value.length.toLong()
    is ListValue -> value.items.size.toLong()
    is MapValue -> value.entries.size.toLong()
    is RangeValue -> value.length()
    else -> throw RuntimeError("value of type ${typeName(value)} has no length", 0)
}

private fun toInt(value: Value): Value = when (value) {
    is IntValue -> value
    is DoubleValue -> IntValue(value.value.toLong())
    is BoolValue -> IntValue(if (value.value) 1 else 0)
    is StringValue -> IntValue(
        value.value.trim().toLongOrNull()
            ?: throw RuntimeError("cannot convert '${value.value}' to int", 0)
    )
    else -> throw RuntimeError("cannot convert ${typeName(value)} to int", 0)
}

private fun toDouble(value: Value): Value = when (value) {
    is DoubleValue -> value
    is IntValue -> DoubleValue(value.value.toDouble())
    is BoolValue -> DoubleValue(if (value.value) 1.0 else 0.0)
    is StringValue -> DoubleValue(
        value.value.trim().toDoubleOrNull()
            ?: throw RuntimeError("cannot convert '${value.value}' to double", 0)
    )
    else -> throw RuntimeError("cannot convert ${typeName(value)} to double", 0)
}

private fun makeRange(args: List<Value>): Value {
    val numbers = args.map {
        (it as? IntValue)?.value ?: throw RuntimeError("range expects int arguments, got ${typeName(it)}", 0)
    }
    return when (numbers.size) {
        1 -> RangeValue(0, numbers[0], 1)
        2 -> RangeValue(numbers[0], numbers[1], 1)
        else -> {
            if (numbers[2] == 0L) throw RuntimeError("range step cannot be zero", 0)
            RangeValue(numbers[0], numbers[1], numbers[2])
        }
    }
}

private fun elementsOf(value: Value): List<Value> = when (value) {
    is ListValue -> ArrayList(value.items)
    is MapValue -> ArrayList(value.entries.keys)
    is StringValue -> value.value.map { StringValue(it.toString()) }
    is RangeValue -> {
        val result = ArrayList<Value>(value.length().toInt().coerceAtLeast(0))
        var current = value.start
        if (value.step > 0) {
            while (current < value.end) {
                result.add(IntValue(current))
                current += value.step
            }
        } else {
            while (current > value.end) {
                result.add(IntValue(current))
                current += value.step
            }
        }
        result
    }
    else -> throw RuntimeError("value of type ${typeName(value)} is not iterable", 0)
}
