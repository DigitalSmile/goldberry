package dev.goldberry.widgets.form.spelling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.image.Image;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.text.spell.SpellCheckers;
import dev.goldberry.text.spell.WordListChecker;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.form.richinput.RichInput;
import dev.goldberry.widgets.form.textarea.TextArea;
import dev.goldberry.widgets.form.textinput.TextInput;

/// The three fields drawing their misspelled words, counted in **red** ink:
/// white text on a blue field, and `::spelling-error` drawing a red wavy line,
/// so a red pixel is a misspelling's line and nothing else.
///
/// Off the UI thread the check answers at once, so the one frame an offscreen
/// render has is the frame with the lines.
class FieldSpellingTest {

    private static final int WIDTH = 360;
    private static final int HEIGHT = 140;

    private static final String SHEET = "text-input, text-area, rich-input { background: #2040c0; color: #ffffff }"
            + " ::spelling-error { text-decoration: wavy underline #ff0000 }";

    @BeforeEach
    void install() {
        RendererRequirement.enforce();
        SpellCheckers.install(WordListChecker.english("the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog"));
    }

    @AfterEach
    void clear() {
        SpellCheckers.clear();
    }

    @Nested
    @DisplayName("text-area")
    class Area {

        @Test
        @DisplayName("draws a line under a misspelled word when it says spellcheck=true")
        void flagged() {
            assertTrue(red(new TextArea("the quikc brown fox", value -> {}).spellcheck(true)) > 0);
        }

        @Test
        @DisplayName("draws nothing when every word is known")
        void clean() {
            assertEquals(0, red(new TextArea("the quick brown fox", value -> {}).spellcheck(true)));
        }

        @Test
        @DisplayName("draws nothing unless something says spellcheck=true")
        void offByDefault() {
            assertEquals(0, red(new TextArea("the quikc brown fox", value -> {})));
        }

        @Test
        @DisplayName("takes spellcheck from the node around it, and lets itself say otherwise")
        void inherits() {
            var around = new Column(new TextArea("the quikc brown fox", value -> {})).spellcheck(true);
            assertTrue(red(around) > 0, "a column saying spellcheck=true reaches the area inside it");

            var refused =
                    new Column(new TextArea("the quikc brown fox", value -> {}).spellcheck(false)).spellcheck(true);
            assertEquals(0, red(refused), "the area's own spellcheck=false wins");
        }

        @Test
        @DisplayName("draws the line at 2x as well, and no more of it than the word")
        void doubleScale() {
            var once = red(new TextArea("the quikc brown fox", value -> {}).spellcheck(true), 1);
            var twice = red(new TextArea("the quikc brown fox", value -> {}).spellcheck(true), 2);

            assertTrue(
                    twice > 2 * once,
                    () -> "a wave at 2x covers about four times the pixels: " + once + " and " + twice);
            assertTrue(twice < 8 * once, () -> "and not the whole line: " + once + " and " + twice);
        }

        @Test
        @DisplayName("checks a later line as well as the first")
        void everyLine() {
            assertTrue(red(new TextArea("the quick\nbrown fxo", value -> {}).spellcheck(true)) > 0);
        }

        @Test
        @DisplayName("checks nothing in a language that has no checker")
        void noChecker() {
            var german = new TextArea("the quikc brown fox", value -> {})
                    .spellcheck(true)
                    .lang(Locale.GERMAN);

            assertEquals(0, red(german));
        }

        @Test
        @DisplayName("a word added to the personal dictionary is not flagged")
        void personal() {
            SpellCheckers.personal(Locale.US).add("quikc");

            assertEquals(0, red(new TextArea("the quikc brown fox", value -> {}).spellcheck(true)));
        }
    }

    @Nested
    @DisplayName("text-input")
    class Input {

        @Test
        @DisplayName("draws a line under a misspelled word, and none under known ones")
        void flagged() {
            assertTrue(red(new TextInput("the lazzy dog", value -> {}).spellcheck(true)) > 0);
            assertEquals(0, red(new TextInput("the lazy dog", value -> {}).spellcheck(true)));
        }

        @Test
        @DisplayName("never checks a password")
        void password() {
            assertEquals(
                    0, red(new TextInput("hunter", value -> {}).password(true).spellcheck(true)));
        }
    }

    @Nested
    @DisplayName("rich-input")
    class Rich {

        @Test
        @DisplayName("draws a line under a misspelled word")
        void flagged() {
            assertTrue(red(new RichInput(MarkedText.of("the jumsp over"), value -> {}).spellcheck(true)) > 0);
        }

        @Test
        @DisplayName("draws nothing when every word is known")
        void clean() {
            assertEquals(0, red(new RichInput(MarkedText.of("the jumps over"), value -> {}).spellcheck(true)));
        }
    }

    // --- helpers --------------------------------------------------------------

    private static int red(Widget field) {
        return red(field, 1);
    }

    private static int red(Widget field, float scale) {
        var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.add(Stylesheet.parse(CascadeLayer.APPLICATION, SHEET));
        var image = Offscreen.of(WIDTH, HEIGHT).scale(scale).stylesheets(sheets).render(new Column(field));
        return countRed(image);
    }

    /// Pixels that are mostly red: more red than either of the other two by a
    /// wide margin, which white text on blue never is.
    private static int countRed(Image image) {
        var count = 0;
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                var argb = image.argb(x, y);
                var r = (argb >>> 16) & 0xFF;
                var g = (argb >>> 8) & 0xFF;
                var b = argb & 0xFF;
                if (r > 0x90 && r > g + 0x50 && r > b + 0x50) {
                    count++;
                }
            }
        }
        return count;
    }
}
