package dev.dsprobe.app.debug

/**
 * One field's line on a group card: a short [label] and one sentence on what its value means. Every
 * word comes from a FACTS.md row; where the row says the meaning is untested, the line says so and
 * says that the user's answer settles it. [androidLine] is the same line in Android mode's units,
 * for fields whose number differs by route.
 */
data class FieldGuess(val label: String, val line: String, val androidLine: String? = null) {
    init {
        require(label.isNotBlank() && line.isNotBlank()) { "empty part in guess '$label'" }
    }
}
