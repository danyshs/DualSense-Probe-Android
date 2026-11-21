package dev.dsprobe.app

import java.io.File

/**
 * The pad module's hex fixtures, read from the shared test resources; fails loudly when one is
 * missing or empty.
 */
object TestFixtures {
    fun bytes(path: String): ByteArray {
        val url =
            checkNotNull(TestFixtures::class.java.getResource("/$path")) { "missing fixture $path" }
        val tokens = url.readText().trim().split(Regex("\\s+"))
        check(tokens.isNotEmpty() && tokens[0].isNotEmpty()) { "empty fixture $path" }
        return ByteArray(tokens.size) { tokens[it].toInt(16).toByte() }
    }

    fun names(dir: String): List<String> {
        val url =
            checkNotNull(TestFixtures::class.java.getResource("/$dir")) {
                "missing resource dir $dir"
            }
        val names = File(url.toURI()).listFiles { f ->
            f.name.endsWith(".hex")
        }!!.map { it.name.removeSuffix(".hex") }.sorted()
        check(names.isNotEmpty()) { "no fixtures in $dir" }
        return names
    }

    fun report(name: String): ByteArray = bytes("reports/$name.hex")

    val allOffGolden: ByteArray get() = bytes("golden/release-all-off.hex")
}
