package dev.dsprobe.app.debug

/**
 * What one input field probably is, in three parts a card shows as one paragraph: what it is, what
 * the ends of its range mean, and how to make it move. Every word comes from a FACTS.md row; where
 * the row says the meaning is untested, the text says so and says that the user's answer settles
 * it.
 */
data class FieldGuess(
    val title: String,
    val what: String,
    val ends: String,
    val move: String,
    /** The ends in Android mode's units, for fields whose number differs by route. */
    val androidEnds: String? = null,
) {
    init {
        require(title.isNotBlank() && what.isNotBlank() && ends.isNotBlank() && move.isNotBlank()) {
            "empty part in guess '$title'"
        }
    }

    /** The paragraph under the card's title. */
    val text: String get() = "$what $ends $move"

    /** The same paragraph in Android mode's units; null when the route does not change it. */
    val androidText: String? get() = androidEnds?.let { "$what $it $move" }
}
