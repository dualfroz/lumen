package lumen.vm

import lumen.RuntimeError
import lumen.bytecode.Chunk
import lumen.bytecode.Opcode
import lumen.runtime.*

private class CallFrame(val closure: ClosureValue, var ip: Int, val slotBase: Int)

class Vm(val out: Appendable = System.out) {
    private val stack = ArrayList<Value>(256)
    private val frames = ArrayList<CallFrame>()
    private val openUpvalues = ArrayList<Upvalue>()
    val globals = HashMap<String, Value>()
    val methods = HashMap<String, Method>()

    init {
        installBuiltins(this)
        installMethods(this)
    }

    fun defineMethod(method: Method) {
        methods[method.name] = method
    }

    fun defineNative(name: String, native: NativeFunctionValue) {
        globals[name] = native
    }

    fun interpret(script: FunctionValue): Value {
        stack.clear()
        frames.clear()
        openUpvalues.clear()
        val closure = ClosureValue(script, emptyArray())
        push(closure)
        frames.add(CallFrame(closure, 0, 0))
        return run(0)
    }

    fun invoke(callable: Value, args: List<Value>): Value {
        when (callable) {
            is NativeFunctionValue -> {
                checkArity(callable, args.size)
                return callable.call(args)
            }
            is ClosureValue -> {
                if (callable.function.arity != args.size) {
                    throw RuntimeError(
                        "function '${callable.function.name}' expects ${callable.function.arity} arguments but got ${args.size}",
                        0
                    )
                }
                val floor = frames.size
                push(callable)
                args.forEach { push(it) }
                frames.add(CallFrame(callable, 0, stack.size - args.size - 1))
                run(floor)
                return pop()
            }
            else -> throw RuntimeError("value of type ${typeName(callable)} is not callable", 0)
        }
    }

    private fun run(exitFrameFloor: Int): Value {
        while (true) {
            val frame = frames[frames.size - 1]
            val chunk = frame.closure.function.chunk
            when (Opcode.from(readByte(frame, chunk))) {
                Opcode.Constant -> push(chunk.constants[readByte(frame, chunk)])
                Opcode.Nil -> push(NilValue)
                Opcode.True -> push(BoolValue(true))
                Opcode.False -> push(BoolValue(false))
                Opcode.Pop -> pop()

                Opcode.Add -> binaryAdd(frame, chunk)
                Opcode.Sub -> arithmetic(frame, chunk, Arith.Sub)
                Opcode.Mul -> arithmetic(frame, chunk, Arith.Mul)
                Opcode.Div -> arithmetic(frame, chunk, Arith.Div)
                Opcode.Mod -> arithmetic(frame, chunk, Arith.Mod)
                Opcode.Negate -> negate(frame, chunk)

                Opcode.Equal -> {
                    val b = pop()
                    val a = pop()
                    push(BoolValue(valuesEqual(a, b)))
                }
                Opcode.NotEqual -> {
                    val b = pop()
                    val a = pop()
                    push(BoolValue(!valuesEqual(a, b)))
                }
                Opcode.Less -> compare(frame, chunk, Compare.Less)
                Opcode.LessEqual -> compare(frame, chunk, Compare.LessEqual)
                Opcode.Greater -> compare(frame, chunk, Compare.Greater)
                Opcode.GreaterEqual -> compare(frame, chunk, Compare.GreaterEqual)
                Opcode.Not -> push(BoolValue(!isTruthy(pop())))

                Opcode.GetLocal -> {
                    val slot = readByte(frame, chunk)
                    push(stack[frame.slotBase + slot])
                }
                Opcode.SetLocal -> {
                    val slot = readByte(frame, chunk)
                    stack[frame.slotBase + slot] = peek(0)
                }
                Opcode.GetGlobal -> {
                    val name = constantName(chunk, readByte(frame, chunk))
                    val value = globals[name] ?: throw RuntimeError("undefined variable '$name'", lineOf(frame, chunk), columnOf(frame, chunk))
                    push(value)
                }
                Opcode.SetGlobal -> {
                    val name = constantName(chunk, readByte(frame, chunk))
                    if (!globals.containsKey(name)) {
                        throw RuntimeError("undefined variable '$name'", lineOf(frame, chunk), columnOf(frame, chunk))
                    }
                    globals[name] = peek(0)
                }
                Opcode.DefineGlobal -> {
                    val name = constantName(chunk, readByte(frame, chunk))
                    globals[name] = pop()
                }
                Opcode.GetUpvalue -> {
                    val index = readByte(frame, chunk)
                    push(readUpvalue(frame.closure.upvalues[index]))
                }
                Opcode.SetUpvalue -> {
                    val index = readByte(frame, chunk)
                    writeUpvalue(frame.closure.upvalues[index], peek(0))
                }

                Opcode.Jump -> {
                    val offset = readByte(frame, chunk)
                    frame.ip += offset
                }
                Opcode.JumpIfFalse -> {
                    val offset = readByte(frame, chunk)
                    if (!isTruthy(peek(0))) frame.ip += offset
                }
                Opcode.Loop -> {
                    val offset = readByte(frame, chunk)
                    frame.ip -= offset
                }

                Opcode.Call -> {
                    val argCount = readByte(frame, chunk)
                    callValue(peek(argCount), argCount, frame, chunk)
                }
                Opcode.Invoke -> invoke(frame, chunk)
                Opcode.Closure -> makeClosure(frame, chunk)
                Opcode.CloseUpvalue -> {
                    closeUpvalues(stack.size - 1)
                    pop()
                }
                Opcode.Return -> {
                    val result = pop()
                    closeUpvalues(frame.slotBase)
                    frames.removeAt(frames.size - 1)
                    truncate(frame.slotBase)
                    push(result)
                    if (frames.size == exitFrameFloor) return result
                }

                Opcode.BuildList -> buildList(frame, chunk)
                Opcode.BuildMap -> buildMap(frame, chunk)
                Opcode.IndexGet -> indexGet(frame, chunk)
                Opcode.IndexSet -> indexSet(frame, chunk)

                Opcode.GetIter -> push(IteratorValue(makeIterator(pop(), frame, chunk)))
                Opcode.ForIter -> {
                    val iterSlot = readByte(frame, chunk)
                    val offset = readByte(frame, chunk)
                    val iterator = stack[frame.slotBase + iterSlot] as IteratorValue
                    if (iterator.iterator.hasNext()) push(iterator.iterator.next())
                    else frame.ip += offset
                }
            }
        }
    }

    private fun callValue(callee: Value, argCount: Int, frame: CallFrame, chunk: Chunk) {
        when (callee) {
            is ClosureValue -> {
                if (callee.function.arity != argCount) {
                    throw RuntimeError(
                        "function '${callee.function.name}' expects ${callee.function.arity} arguments but got $argCount",
                        lineOf(frame, chunk),
                        columnOf(frame, chunk)
                    )
                }
                frames.add(CallFrame(callee, 0, stack.size - argCount - 1))
            }
            is NativeFunctionValue -> {
                val args = ArrayList<Value>(argCount)
                for (i in 0 until argCount) args.add(stack[stack.size - argCount + i])
                if (!callee.arity.contains(argCount)) {
                    throw RuntimeError(arityMessage(callee, argCount), lineOf(frame, chunk), columnOf(frame, chunk))
                }
                truncate(stack.size - argCount - 1)
                val produced = try {
                    callee.call(args)
                } catch (error: RuntimeError) {
                    if (error.line == 0) {
                        throw RuntimeError(error.rawMessage, lineOf(frame, chunk), columnOf(frame, chunk))
                    }
                    throw error
                }
                push(produced)
            }
            else -> throw RuntimeError("value of type ${typeName(callee)} is not callable", lineOf(frame, chunk), columnOf(frame, chunk))
        }
    }

    private fun invoke(frame: CallFrame, chunk: Chunk) {
        val name = constantName(chunk, readByte(frame, chunk))
        val argCount = readByte(frame, chunk)
        val receiver = peek(argCount)
        val method = methods[name]
            ?: throw RuntimeError("unknown method '$name' on value of type ${typeName(receiver)}", lineOf(frame, chunk), columnOf(frame, chunk))
        if (!method.arity.contains(argCount)) {
            throw RuntimeError(
                "method '$name' expects ${arityText(method.arity)} arguments but got $argCount",
                lineOf(frame, chunk),
                columnOf(frame, chunk)
            )
        }
        val args = ArrayList<Value>(argCount)
        for (i in 0 until argCount) args.add(stack[stack.size - argCount + i])
        val result = try {
            method.call(this, receiver, args)
        } catch (error: RuntimeError) {
            if (error.line == 0) {
                throw RuntimeError(error.rawMessage, lineOf(frame, chunk), columnOf(frame, chunk))
            }
            throw error
        }
        truncate(stack.size - argCount - 1)
        push(result)
    }

    private fun makeClosure(frame: CallFrame, chunk: Chunk) {
        val function = chunk.constants[readByte(frame, chunk)] as FunctionValue
        val upvalues = Array(function.upvalueCount) {
            val isLocal = readByte(frame, chunk) == 1
            val index = readByte(frame, chunk)
            if (isLocal) captureUpvalue(frame.slotBase + index) else frame.closure.upvalues[index]
        }
        push(ClosureValue(function, upvalues))
    }

    private fun buildList(frame: CallFrame, chunk: Chunk) {
        val count = readByte(frame, chunk)
        val items = ArrayList<Value>(count)
        for (i in 0 until count) items.add(NilValue)
        for (i in count - 1 downTo 0) items[i] = pop()
        push(ListValue(items))
    }

    private fun buildMap(frame: CallFrame, chunk: Chunk) {
        val count = readByte(frame, chunk)
        val pairs = ArrayList<Pair<Value, Value>>(count)
        for (i in 0 until count) {
            val value = pop()
            val key = pop()
            pairs.add(key to value)
        }
        pairs.reverse()
        val entries = LinkedHashMap<Value, Value>()
        for ((key, value) in pairs) entries[key] = value
        push(MapValue(entries))
    }

    private fun indexGet(frame: CallFrame, chunk: Chunk) {
        val index = pop()
        val target = pop()
        push(readIndex(target, index, frame, chunk))
    }

    private fun indexSet(frame: CallFrame, chunk: Chunk) {
        val value = pop()
        val index = pop()
        val target = pop()
        writeIndex(target, index, value, frame, chunk)
        push(value)
    }

    private fun readIndex(target: Value, index: Value, frame: CallFrame, chunk: Chunk): Value = when (target) {
        is ListValue -> {
            val i = intIndex(index, frame, chunk)
            if (i < 0 || i >= target.items.size) {
                throw RuntimeError("list index $i out of range 0..${target.items.size - 1}", lineOf(frame, chunk), columnOf(frame, chunk))
            }
            target.items[i]
        }
        is MapValue -> target.entries[index] ?: NilValue
        is StringValue -> {
            val i = intIndex(index, frame, chunk)
            if (i < 0 || i >= target.value.length) {
                throw RuntimeError("string index $i out of range", lineOf(frame, chunk), columnOf(frame, chunk))
            }
            StringValue(target.value[i].toString())
        }
        is RangeValue -> {
            val i = intIndex(index, frame, chunk).toLong()
            if (i < 0 || i >= target.length()) {
                throw RuntimeError("range index $i out of range", lineOf(frame, chunk), columnOf(frame, chunk))
            }
            IntValue(target.elementAt(i))
        }
        else -> throw RuntimeError("value of type ${typeName(target)} is not indexable", lineOf(frame, chunk), columnOf(frame, chunk))
    }

    private fun writeIndex(target: Value, index: Value, value: Value, frame: CallFrame, chunk: Chunk) {
        when (target) {
            is ListValue -> {
                val i = intIndex(index, frame, chunk)
                if (i < 0 || i >= target.items.size) {
                    throw RuntimeError("list index $i out of range 0..${target.items.size - 1}", lineOf(frame, chunk), columnOf(frame, chunk))
                }
                target.items[i] = value
            }
            is MapValue -> target.entries[index] = value
            else -> throw RuntimeError("value of type ${typeName(target)} does not support index assignment", lineOf(frame, chunk), columnOf(frame, chunk))
        }
    }

    private fun intIndex(index: Value, frame: CallFrame, chunk: Chunk): Int {
        if (index !is IntValue) {
            throw RuntimeError("index must be an int, got ${typeName(index)}", lineOf(frame, chunk), columnOf(frame, chunk))
        }
        return index.value.toInt()
    }

    private fun makeIterator(iterable: Value, frame: CallFrame, chunk: Chunk): Iterator<Value> = when (iterable) {
        is ListValue -> ArrayList(iterable.items).iterator()
        is MapValue -> ArrayList(iterable.entries.keys).iterator()
        is StringValue -> iterable.value.map { StringValue(it.toString()) as Value }.iterator()
        is RangeValue -> rangeIterator(iterable)
        else -> throw RuntimeError("value of type ${typeName(iterable)} is not iterable", lineOf(frame, chunk), columnOf(frame, chunk))
    }

    private fun rangeIterator(range: RangeValue): Iterator<Value> = iterator {
        var value = range.start
        if (range.step > 0) {
            while (value < range.end) {
                yield(IntValue(value))
                value += range.step
            }
        } else {
            while (value > range.end) {
                yield(IntValue(value))
                value += range.step
            }
        }
    }

    private enum class Arith { Sub, Mul, Div, Mod }
    private enum class Compare { Less, LessEqual, Greater, GreaterEqual }

    private fun binaryAdd(frame: CallFrame, chunk: Chunk) {
        val b = pop()
        val a = pop()
        val result = when {
            a is IntValue && b is IntValue -> IntValue(a.value + b.value)
            a is StringValue && b is StringValue -> StringValue(a.value + b.value)
            isNumber(a) && isNumber(b) -> DoubleValue(asDouble(a) + asDouble(b))
            else -> throw RuntimeError("cannot add ${typeName(a)} and ${typeName(b)}", lineOf(frame, chunk), columnOf(frame, chunk))
        }
        push(result)
    }

    private fun arithmetic(frame: CallFrame, chunk: Chunk, op: Arith) {
        val b = pop()
        val a = pop()
        if (a is IntValue && b is IntValue) {
            val x = a.value
            val y = b.value
            val result = when (op) {
                Arith.Sub -> x - y
                Arith.Mul -> x * y
                Arith.Div -> {
                    if (y == 0L) throw RuntimeError("division by zero", lineOf(frame, chunk), columnOf(frame, chunk))
                    x / y
                }
                Arith.Mod -> {
                    if (y == 0L) throw RuntimeError("modulo by zero", lineOf(frame, chunk), columnOf(frame, chunk))
                    x % y
                }
            }
            push(IntValue(result))
            return
        }
        if (isNumber(a) && isNumber(b)) {
            val x = asDouble(a)
            val y = asDouble(b)
            val result = when (op) {
                Arith.Sub -> x - y
                Arith.Mul -> x * y
                Arith.Div -> x / y
                Arith.Mod -> x % y
            }
            push(DoubleValue(result))
            return
        }
        throw RuntimeError("cannot apply arithmetic to ${typeName(a)} and ${typeName(b)}", lineOf(frame, chunk), columnOf(frame, chunk))
    }

    private fun negate(frame: CallFrame, chunk: Chunk) {
        when (val operand = pop()) {
            is IntValue -> push(IntValue(-operand.value))
            is DoubleValue -> push(DoubleValue(-operand.value))
            else -> throw RuntimeError("cannot negate ${typeName(operand)}", lineOf(frame, chunk), columnOf(frame, chunk))
        }
    }

    private fun compare(frame: CallFrame, chunk: Chunk, op: Compare) {
        val b = pop()
        val a = pop()
        val ordering = when {
            isNumber(a) && isNumber(b) -> asDouble(a).compareTo(asDouble(b))
            a is StringValue && b is StringValue -> a.value.compareTo(b.value)
            else -> throw RuntimeError("cannot compare ${typeName(a)} and ${typeName(b)}", lineOf(frame, chunk), columnOf(frame, chunk))
        }
        val result = when (op) {
            Compare.Less -> ordering < 0
            Compare.LessEqual -> ordering <= 0
            Compare.Greater -> ordering > 0
            Compare.GreaterEqual -> ordering >= 0
        }
        push(BoolValue(result))
    }

    private fun captureUpvalue(location: Int): Upvalue {
        for (existing in openUpvalues) {
            if (existing.location == location) return existing
        }
        val created = Upvalue(location)
        openUpvalues.add(created)
        return created
    }

    private fun closeUpvalues(fromLocation: Int) {
        val iterator = openUpvalues.iterator()
        while (iterator.hasNext()) {
            val upvalue = iterator.next()
            if (upvalue.location >= fromLocation) {
                upvalue.closed = stack[upvalue.location]
                upvalue.location = -1
                iterator.remove()
            }
        }
    }

    private fun readUpvalue(upvalue: Upvalue): Value =
        if (upvalue.isClosed) upvalue.closed else stack[upvalue.location]

    private fun writeUpvalue(upvalue: Upvalue, value: Value) {
        if (upvalue.isClosed) upvalue.closed = value else stack[upvalue.location] = value
    }

    private fun checkArity(native: NativeFunctionValue, argCount: Int) {
        if (!native.arity.contains(argCount)) {
            throw RuntimeError(arityMessage(native, argCount), 0)
        }
    }

    private fun arityMessage(native: NativeFunctionValue, argCount: Int): String {
        val expected = if (native.arity.first == native.arity.last) "${native.arity.first}"
        else "${native.arity.first}..${native.arity.last}"
        return "builtin '${native.name}' expects $expected arguments but got $argCount"
    }

    private fun constantName(chunk: Chunk, index: Int): String = (chunk.constants[index] as StringValue).value

    private fun readByte(frame: CallFrame, chunk: Chunk): Int = chunk.code[frame.ip++]

    private fun lineOf(frame: CallFrame, chunk: Chunk): Int {
        val index = (frame.ip - 1).coerceIn(0, chunk.lines.size - 1)
        return if (chunk.lines.isEmpty()) 0 else chunk.lines[index]
    }

    private fun columnOf(frame: CallFrame, chunk: Chunk): Int {
        val index = (frame.ip - 1).coerceIn(0, chunk.columns.size - 1)
        return if (chunk.columns.isEmpty()) 0 else chunk.columns[index]
    }

    private fun arityText(arity: IntRange): String =
        if (arity.first == arity.last) "${arity.first}" else "${arity.first}..${arity.last}"

    private fun push(value: Value) {
        stack.add(value)
    }

    private fun pop(): Value = stack.removeAt(stack.size - 1)

    private fun peek(distance: Int): Value = stack[stack.size - 1 - distance]

    private fun truncate(size: Int) {
        while (stack.size > size) stack.removeAt(stack.size - 1)
    }

    private fun isNumber(value: Value): Boolean = value is IntValue || value is DoubleValue

    private fun asDouble(value: Value): Double = when (value) {
        is IntValue -> value.value.toDouble()
        is DoubleValue -> value.value
        else -> 0.0
    }
}
