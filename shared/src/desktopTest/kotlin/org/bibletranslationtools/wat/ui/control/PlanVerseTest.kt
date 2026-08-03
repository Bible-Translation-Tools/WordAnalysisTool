package org.bibletranslationtools.wat.ui.control

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What [planVerse] decides for real verses, measured with a real text measurer.
 * The card shows [VERSE_MAX_LINES] lines, so a plan is good when the reviewed
 * word and the "view more" button both land inside them and as much of the verse
 * as possible is kept.
 */
class PlanVerseTest {

    private val density = Density(density = 1f, fontScale = 1f)

    private val measurer = TextMeasurer(
        defaultFontFamilyResolver = createFontFamilyResolver(),
        defaultDensity = density,
        defaultLayoutDirection = LayoutDirection.Ltr
    )

    private val style = TextStyle(
        fontSize = 24.sp,
        lineHeight = 46.sp,
        textAlign = TextAlign.Center,
        fontFamily = FontFamily.Default
    )

    private val viewMoreLabel = "view more"

    /** The button is inline content, sized like the card sizes it. */
    private val viewMorePlaceholder by lazy {
        placeholderFor(viewMoreLabel, style.copy(fontSize = 16.sp))
    }

    /** Card widths the review screen produces on the sizes people use it at. */
    private val cardWidths = listOf(380, 480, 600, 720, 900)

    private fun regexFor(word: String) = Regex(
        pattern = "(?<!\\p{L})${Regex.escape(word)}(?!\\p{L})",
        option = RegexOption.IGNORE_CASE
    )

    private fun placeholderFor(label: String, size: TextStyle) = Placeholder(
        width = with(density) {
            (measurer.measure(label, size).size.width + 16).toSp()
        },
        height = 40.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )

    /** The text the card ends up showing, and how it lays out. */
    private data class Rendered(
        val text: AnnotatedString,
        val lineCount: Int,
        val wordLine: Int,
        val buttonLine: Int
    )

    private fun render(
        plan: VersePlan,
        verse: String,
        reference: String,
        word: String,
        cardWidthPx: Int
    ): Rendered {
        val trimmed = if (plan.headTrim == 0) {
            verse
        } else "$ELLIPSIS${verse.substring(plan.headTrim)}"
        val match = regexFor(word).find(trimmed)
        val body = verseAnnotated(reference, trimmed, match, Color.Unspecified)

        val text = if (!plan.truncated) {
            body
        } else buildAnnotatedString {
            plan.cutAt?.let {
                append(body.subSequence(0, it))
            } ?: append(body)
            append(" ")
            append(viewMoreLabel)
        }

        val referenceLength = reference.length + REFERENCE_SEPARATOR.length
        val chipEnd = match?.let { referenceLength + it.range.last + 1 }

        val layout = measurer.measure(
            text = text,
            style = style,
            maxLines = Int.MAX_VALUE,
            placeholders = buildList {
                match?.let {
                    add(
                        AnnotatedString.Range(
                            item = placeholderFor(it.value, style),
                            start = referenceLength + it.range.first,
                            end = referenceLength + it.range.last + 1
                        )
                    )
                }
                if (plan.truncated) {
                    add(
                        AnnotatedString.Range(
                            item = viewMorePlaceholder,
                            start = text.length - viewMoreLabel.length,
                            end = text.length
                        )
                    )
                }
            },
            constraints = Constraints(maxWidth = cardWidthPx)
        )

        return Rendered(
            text = text,
            lineCount = layout.lineCount,
            wordLine = chipEnd?.let { layout.getLineForOffset(it - 1) } ?: 0,
            buttonLine = layout.getLineForOffset(text.length - 1)
        )
    }

    private fun plan(verse: String, reference: String, word: String, cardWidthPx: Int) = planVerse(
        fullVerse = verse,
        wordRegex = regexFor(word),
        reference = reference,
        maxLines = VERSE_MAX_LINES,
        style = style,
        chipPlaceholder = placeholderFor(word, style),
        viewMorePlaceholder = viewMorePlaceholder,
        widthPx = cardWidthPx,
        textMeasurer = measurer
    )

    @Test
    fun `short verse is left alone`() {
        val verse = "Kay mahal kaayo sa Dios an kalibutan."
        cardWidths.forEach { width ->
            val plan = plan(verse, "JHN 3:16", "mahal", width)

            assertEquals(0, plan.headTrim, "at width $width")
            assertNull(plan.cutAt, "at width $width")
            assertTrue(!plan.truncated, "a verse that fits needs no button, at width $width")
        }
    }

    @Test
    fun `word at the end of the verse keeps the head that fits`() {
        val verse = "Endulya nandi nali bwene, no kuboko kwakwe, no kuboko kwakwe " +
                "alikutmbalikishe kuli nebo; mukuboko kwakwe mwali akatabo akalembelwepo."
        assertUsesEveryLine(verse, "EZK 2:9", "akalembelwepo")
    }

    @Test
    fun `word in the middle of a long verse keeps every line`() {
        val verse = "Uyo uukokuputaula mutupimfya aita cilolo wakwe; bakoipuntaula " +
                "pabanabo ilyo bakoenda; bakobutukila mukusansa icibumba ca musumba. " +
                "Inkwela iikulu ilipekanishiwe kukubacingilila."
        assertUsesEveryLine(verse, "NAM 2:5", "mukusansa")
    }

    @Test
    fun `word at the end of a verse far longer than the card`() {
        val verse = "Uyo uukokuputaula mutupimfya aita cilolo wakwe; bakoipuntaula " +
                "pabanabo ilyo bakoenda; bakobutukila mukusansa icibumba ca musumba. " +
                "Inkwela iikulu ilipekanishiwe kukubacingilila aba abkubasansa."
        assertUsesEveryLine(verse, "NAM 2:5", "abkubasansa")
    }

    /**
     * At every card width the card either shows the whole verse, or leaves out as
     * little as it can: word and button on a visible line, no hidden lines, and
     * no room left for one more word of the head.
     */
    private fun assertUsesEveryLine(verse: String, reference: String, word: String) {
        cardWidths.forEach { width ->
            val plan = plan(verse, reference, word, width)
            val rendered = render(plan, verse, reference, word, width)
            val where = "at width $width: \"${rendered.text}\""

            if (!plan.truncated) {
                // Wide cards fit the whole verse; nothing may be left out then.
                assertEquals(0, plan.headTrim, "head trimmed untruncated, $where")
                assertNull(plan.cutAt, "tail cut untruncated, $where")
                assertTrue(
                    rendered.lineCount <= VERSE_MAX_LINES,
                    "kept a verse that does not fit, $where"
                )
                assertTrue(verse in rendered.text.text, "verse incomplete, $where")
                return@forEach
            }

            assertTrue(
                plan.headTrim < verse.indexOf(word),
                "head trimmed to the word itself, $where"
            )
            assertTrue(rendered.wordLine < VERSE_MAX_LINES, "word hidden, $where")
            assertTrue(rendered.buttonLine < VERSE_MAX_LINES, "button hidden, $where")
            assertTrue(
                rendered.lineCount <= VERSE_MAX_LINES,
                "lines hidden, $where"
            )

            if (rendered.lineCount < VERSE_MAX_LINES) {
                // Fewer lines than allowed is only fair if one more word of head
                // would push the word or the button out of sight.
                val more = verse.lastIndexOf(' ', plan.headTrim - 2) + 1
                val richer = render(
                    plan = VersePlan(headTrim = more, cutAt = null, truncated = true),
                    verse = verse,
                    reference = reference,
                    word = word,
                    cardWidthPx = width
                )
                assertTrue(
                    richer.lineCount > VERSE_MAX_LINES ||
                            richer.wordLine >= VERSE_MAX_LINES ||
                            richer.buttonLine >= VERSE_MAX_LINES,
                    "could have kept more head, $where"
                )
            }
        }
    }
}
