package dev.dsprobe.app.route

/** The public method names of one Android class, found by reflection. Names only: nothing here ever calls one. */
data class ClassMethods(val className: String, val methods: List<String>)

/** A method whose name has one of the probe's words in it. */
data class MethodMatch(val method: String, val keyword: String)

/** The words a method name is searched for: the amendment's list, matched whole (a plural counts, "enabled" does not count as "led"). */
val PROBE_KEYWORDS = listOf("trigger", "haptic", "feedback", "effect", "led", "light", "mute", "battery", "audio")

private val CAMEL_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")

/** "setTriggerEffect" gives "set", "trigger", "effect". */
fun wordsOf(name: String): List<String> = name.split(CAMEL_BOUNDARY).map { it.lowercase() }

private fun keywordsIn(method: String): List<String> {
    val words = wordsOf(method)
    return PROBE_KEYWORDS.filter { k -> words.any { it == k || it == k + "s" } }
}

/** What reflection over Android's own controller classes found on this API level. */
data class ApiProbe(val apiLevel: Int, val classes: List<ClassMethods>) {
    val methodCount: Int get() = classes.sumOf { it.methods.size }

    /** Every method with a probe word in its name, "Class.method", in class order. */
    fun matches(): List<MethodMatch> = classes.flatMap { c ->
        c.methods.flatMap { m -> keywordsIn(m).map { MethodMatch(c.className + "." + m, it) } }
    }

    fun matchesFor(keyword: String): List<MethodMatch> = matches().filter { it.keyword == keyword }
}
