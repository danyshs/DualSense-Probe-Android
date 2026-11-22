package dev.dsprobe.app.debug

import dev.dsprobe.app.AppLimits
import dev.dsprobe.app.output.ledMaskText
import dev.dsprobe.app.route.Route
import dev.dsprobe.app.route.androidSourceOf

/** How sure FACTS.md is about a card's meaning, strongest first. */
enum class Confidence { PROVEN, DOCUMENTED, UNKNOWN }

/** Which family of endpoints a card belongs to. */
enum class CardKind { INPUT_FIELD, INPUT_RANGE, OUTPUT, FEATURE, ANDROID, AUDIO }

/**
 * One thing a card covers: an INPUT_MAP id, an `out.<field>` id, or the card's own id. [label] is
 * the short name its location and guess lines start with.
 */
data class CardField(val id: String, val label: String)

/**
 * One stop of the guided test: one physical control, not one field. [title] is the plain name,
 * [where] one location line per field, [guess] the paragraph (what it is, one line per field, how
 * to move it), [factsRow] the FACTS.md rows it comes from, comma-separated (null: no row, so
 * [confidence] is UNKNOWN). [rawByte], [rawByteCount] and [rawMask] say which live bytes of report
 * 0x01 a card shows; -1 means none. [tryIt] is what the big button fires, or the reason there is
 * nothing to fire.
 */
data class EndpointCard(
    val id: String,
    val kind: CardKind,
    val title: String,
    val where: List<String>,
    val guess: String,
    val factsRow: String?,
    val confidence: Confidence,
    val tryIt: TryIt,
    val fields: List<CardField> = listOf(CardField(id, title)),
    val rawByte: Int = -1,
    val rawByteCount: Int = 0,
    val rawMask: Int = BYTE_ALL,
    /** Control IDs (or prefixes) whose trace rows this card listens to while it is open. */
    val listens: List<String> = emptyList(),
    /** The guess in Android mode's units, when they differ from Direct USB's. */
    val androidGuess: String? = null,
) {
    init {
        require(where.isNotEmpty() && fields.isNotEmpty()) { "card $id has no location or field" }
    }
}

const val BYTE_ALL = 0xff

const val HAND_REASON =
    "Nothing to fire: this one is yours to move. Use the control and watch the value."
const val AUDIO_STEP_REASON = "Direct USB audio needs the audio step, not built yet."

/**
 * FACTS "Confirmed" text to a tag: only a plain yes is Proven; a row with a "not yet", a "no" or a
 * mix is Documented.
 */
fun confidenceFromConfirmed(confirmed: String): Confidence {
    val text = confirmed.trim().lowercase()
    val plainYes = text.startsWith("yes") && "not yet" !in text && "no (" !in text
    return if (plainYes) Confidence.PROVEN else Confidence.DOCUMENTED
}

/**
 * The FACTS rows the guided test cites, with the tag their Confirmed column gives. The test reads
 * FACTS.md and compares.
 */
val ROW_CONFIDENCE: Map<String, Confidence> = mapOf(
    "P10" to Confidence.PROVEN, "P11" to Confidence.PROVEN, "P12" to Confidence.PROVEN,
    "P13" to Confidence.PROVEN,
    "P14" to Confidence.PROVEN, "P15" to Confidence.PROVEN, "P16" to Confidence.DOCUMENTED,
    "P17" to Confidence.PROVEN,
    "P18" to Confidence.DOCUMENTED, "P19" to Confidence.DOCUMENTED,
    "P31" to Confidence.PROVEN, "P35" to Confidence.PROVEN, "P36" to Confidence.PROVEN,
    "P37" to Confidence.PROVEN,
    "P39" to Confidence.PROVEN, "P42" to Confidence.PROVEN, "P43" to Confidence.PROVEN,
    "P44" to Confidence.PROVEN,
    "P45" to Confidence.PROVEN, "P51" to Confidence.PROVEN, "P61" to Confidence.PROVEN,
    "P62" to Confidence.PROVEN,
    "P71" to Confidence.PROVEN, "P72" to Confidence.DOCUMENTED, "P73" to Confidence.DOCUMENTED,
    "P74" to Confidence.DOCUMENTED,
    "P75" to Confidence.DOCUMENTED, "P76" to Confidence.PROVEN, "P77" to Confidence.DOCUMENTED,
    "P78" to Confidence.DOCUMENTED,
    "P80" to Confidence.PROVEN,
)

/** A card citing several FACTS rows is only as sure as its least sure row. */
fun confidenceOfRows(rows: List<String>): Confidence {
    require(rows.isNotEmpty()) { "a card cites no FACTS row" }
    val tags = rows.map { checkNotNull(ROW_CONFIDENCE[it]) { "no confidence for $it" } }
    return tags.maxBy { it.ordinal }
}

/** The rows as a card shows them, each once, in order of first use. */
fun factsRowText(rows: List<String>): String = rows.distinct().joinToString(", ")

/** The paragraph under a card's title: what it is, one line per field, how to move it. */
fun cardText(what: String, lines: List<String>, move: String): String =
    (listOf(what) + lines + move).joinToString("\n")

/** The card's guess in the live route's units. */
fun guessText(card: EndpointCard, route: Route): String =
    if (route == Route.ANDROID) card.androidGuess ?: card.guess else card.guess

/** The cards whose raw byte is the player LED mask: their last location line shows its value. */
private val PLAYER_LED_CARDS = setOf("out.player_leds", "android.light.player")

private val INPUT_KINDS = setOf(CardKind.INPUT_FIELD, CardKind.INPUT_RANGE)

/**
 * The location lines, one per field, with the live player LED byte (binary, hex, which LED) added
 * on the cards that drive it. On the Android route an input card names its Android source instead
 * of the report byte; [noAndroidEvent] is the text for a field Android has no event for.
 */
fun locationLines(
    card: EndpointCard,
    playerLeds: Int,
    route: Route,
    noAndroidEvent: String,
): List<String> {
    if (route == Route.ANDROID && card.kind == CardKind.INPUT_RANGE) {
        return listOf(androidSourceOf(card.id) ?: noAndroidEvent)
    }
    if (route == Route.ANDROID && card.kind in INPUT_KINDS) {
        return card.fields.map { "${it.label}: ${androidSourceOf(it.id) ?: noAndroidEvent}" }
    }
    if (card.id in PLAYER_LED_CARDS) {
        return card.where.dropLast(1) + (card.where.last() + " · now " + ledMaskText(playerLeds))
    }
    return card.where
}

/**
 * Every endpoint the guided test visits, in order: input controls, free report bytes, outputs,
 * audio, firmware, then Android's APIs. Bounded by [AppLimits.TOUR_CARDS_MAX].
 */
fun buildEndpointCards(): List<EndpointCard> {
    val cards = ArrayList<EndpointCard>()
    cards.addAll(inputGroupCards())
    cards.addAll(rangeCards())
    cards.addAll(outputCards())
    cards.addAll(audioCards())
    cards.add(FIRMWARE_CARD)
    cards.addAll(androidCards())
    check(cards.size <= AppLimits.TOUR_CARDS_MAX) { "more cards than the limit: ${cards.size}" }
    check(cards.map { it.id }.toSet().size == cards.size) { "a card id repeats" }
    return cards
}
