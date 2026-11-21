package dev.dsprobe.pad

import java.io.File

/** Loads hex fixtures from the test resources; fails loudly when one is missing or empty. */
object Fixtures {
    private fun resourceDir(dir: String): File {
        val url =
            checkNotNull(Fixtures::class.java.getResource("/$dir")) { "missing resource dir $dir" }
        return File(url.toURI())
    }

    fun bytes(path: String): ByteArray {
        val url =
            checkNotNull(Fixtures::class.java.getResource("/$path")) { "missing fixture $path" }
        val tokens = url.readText().trim().split(Regex("\\s+"))
        check(tokens.isNotEmpty() && tokens[0].isNotEmpty()) { "empty fixture $path" }
        return ByteArray(tokens.size) { tokens[it].toInt(16).toByte() }
    }

    fun names(dir: String): List<String> = resourceDir(dir).listFiles { f ->
        f.name.endsWith(".hex")
    }!!.map { it.name.removeSuffix(".hex") }.sorted()

    fun report(name: String): ByteArray = bytes("reports/$name.hex")

    fun parsed(name: String): UsbInput = (parseUsbInput(report(name)) as ParseResult.Ok).input
}
