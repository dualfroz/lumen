package lumen.bytecode

enum class Opcode {
    Constant,
    Nil,
    True,
    False,
    Pop,

    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Negate,

    Equal,
    NotEqual,
    Less,
    LessEqual,
    Greater,
    GreaterEqual,
    Not,

    GetLocal,
    SetLocal,
    GetGlobal,
    SetGlobal,
    DefineGlobal,
    GetUpvalue,
    SetUpvalue,

    Jump,
    JumpIfFalse,
    Loop,

    Call,
    Invoke,
    Closure,
    CloseUpvalue,
    Return,

    BuildList,
    BuildMap,
    IndexGet,
    IndexSet,

    GetIter,
    ForIter;

    companion object {
        private val values = entries.toTypedArray()
        fun from(ordinal: Int): Opcode = values[ordinal]
    }
}
