package org.bibletranslationtools.wat.ui.control

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.bibletranslationtools.wat.data.ReviewWord
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.ui.theme.MainAppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders a card at the sizes the review screen gives it and writes the images
 * out, so the layout can be looked at instead of reasoned about.
 */
class WordCardRenderTest {

    private val outputDir = File(
        System.getProperty("wat.render.out")
            ?: "${System.getProperty("java.io.tmpdir")}/wat-card-render"
    )

    private val word = ReviewWord(
        word = "akalonganya",
        ref = Verse(
            book = "mrk",
            chapter = 13,
            verse = "27",
            text = "Kabili akatuma abengi bakwe elyo akalonganya bonse abasalwa " +
                    "ukufuma ku myela ine, ukufuma mumpela shapano nse ukufika " +
                    "kumpela shakwiulu."
        ),
        correct = true
    )

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(name: String, widthDp: Int, heightDp: Int) {
        val density = Density(1f)
        val scene = ImageComposeScene(
            width = widthDp + 40,
            height = heightDp + 40,
            density = density
        )

        try {
            scene.setContent {
                MainAppTheme {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(20.dp)
                    ) {
                        WordCard(
                            word = word,
                            footer = CardFooter.NEXT,
                            enabled = true,
                            modifier = Modifier
                                .width(widthDp.dp)
                                .height(heightDp.dp)
                        )
                    }
                }
            }

            // The verse plan needs the width the first layout reports, so let the
            // scene settle over a few frames before capturing.
            var image = scene.render()
            repeat(4) { frame ->
                image = scene.render(nanoTime = (frame + 1) * 16_000_000L)
            }

            outputDir.mkdirs()
            val file = File(outputDir, "$name.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)

            assertTrue(file.length() > 0, "no image written for $name")
        } finally {
            scene.close()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders the review slider`() {
        val scene = ImageComposeScene(width = 700, height = 60, density = Density(1f))
        try {
            scene.setContent {
                MainAppTheme {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)
                    ) {
                        ReviewSlider(
                            position = 120,
                            reachable = 300,
                            total = 400,
                            onSeek = {}
                        )
                    }
                }
            }
            scene.render()
            val image = scene.render(nanoTime = 16_000_000L)
            outputDir.mkdirs()
            val file = File(outputDir, "slider.png")
            image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let(file::writeBytes)
            assertTrue(file.length() > 0, "no slider image written")
        } finally {
            scene.close()
        }
    }

    @Test
    fun `renders at the sizes the carousel uses`() {
        render(name = "card-wide", widthDp = 620, heightDp = 500)
        render(name = "card-medium", widthDp = 460, heightDp = 420)
        render(name = "card-narrow", widthDp = 300, heightDp = 300)
    }
}
