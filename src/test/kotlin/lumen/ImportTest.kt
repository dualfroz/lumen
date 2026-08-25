package lumen

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportTest {
    private fun runModules(files: Map<String, String>, main: String): String {
        val dir = createTempDirectory("lumen-import").toFile()
        try {
            for ((name, source) in files) {
                val file = File(dir, name)
                file.parentFile.mkdirs()
                file.writeText(source)
            }
            val buffer = StringBuilder()
            Lumen.runFile(File(dir, main), buffer)
            return buffer.toString()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun importBringsDefinitionsIntoScope() {
        val output = runModules(
            mapOf(
                "lib/util.lumen" to "fn double(x) { return x * 2 }\nlet unit = 1",
                "main.lumen" to "import \"lib/util.lumen\"\nprintln(double(21))\nprintln(unit)"
            ),
            "main.lumen"
        )
        assertEquals("42\n1\n", output)
    }

    @Test
    fun diamondImportLoadsSharedModuleOnce() {
        val output = runModules(
            mapOf(
                "base.lumen" to "println(\"loading base\")\nfn origin() { return \"base\" }",
                "left.lumen" to "import \"base.lumen\"\nfn left() { return origin() }",
                "right.lumen" to "import \"base.lumen\"\nfn right() { return origin() }",
                "main.lumen" to "import \"left.lumen\"\nimport \"right.lumen\"\nprintln(left() + right())"
            ),
            "main.lumen"
        )
        val baseLoads = output.split("\n").count { it == "loading base" }
        assertEquals(1, baseLoads, "shared module must run exactly once")
        assertEquals("loading base\nbasebase\n", output)
    }

    @Test
    fun mutualImportsDoNotLoopForever() {
        val output = runModules(
            mapOf(
                "a.lumen" to "import \"b.lumen\"\nfn fromA() { return \"A\" }",
                "b.lumen" to "import \"a.lumen\"\nfn fromB() { return \"B\" }",
                "main.lumen" to "import \"a.lumen\"\nprintln(fromA() + fromB())"
            ),
            "main.lumen"
        )
        assertEquals("AB\n", output)
    }
}
