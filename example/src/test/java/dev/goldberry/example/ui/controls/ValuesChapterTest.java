package dev.goldberry.example.ui.controls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.key.Key;
import dev.goldberry.widgets.controls.knob.Knob;
import dev.goldberry.widgets.controls.progressbar.Progress;
import dev.goldberry.widgets.controls.slider.Slider;

/// The Values screen, driven from the keyboard: one value read by a slider, two
/// knobs and a bar, and a fader that says when its gesture ended.
@DisplayName("the Values screen")
class ValuesChapterTest {

    private ChapterSession chapter;

    @BeforeEach
    void open() {
        chapter = ChapterSession.open("values");
    }

    @AfterEach
    void close() {
        if (chapter != null) {
            chapter.close();
        }
    }

    @Test
    @DisplayName("has a card per section")
    void cards() {
        assertEquals(
                Set.of("value-card", "values-decibels", "values-knob", "report-card", "values-spinner"),
                chapter.cardIds("values"));
    }

    @Test
    @DisplayName("moves the knobs and the bar when the slider steps")
    void oneValue() {
        assertTrue(chapter.session().focus("gain"));

        chapter.session().key(Key.RIGHT);

        assertEquals(45.0, ((Number) chapter.value("app.gain")).doubleValue());
        var slider = chapter.find(
                        chapter.element("value-card"),
                        Slider.class,
                        s -> "gain".equals(s.attributes().id()))
                .orElseThrow();
        assertEquals(45.0, ((Slider) slider.widget()).resolved());
        assertEquals(45.0, chapter.widget("gain-knob", Knob.class).resolved());
        assertEquals(0.45, chapter.widget("known", Progress.class).resolved(), 1e-9, "the bar is 45 out of 100");
    }

    @Test
    @DisplayName("commits the decibel fader's value when a key step ends")
    void commit() {
        assertEquals("Settled: 0.50", chapter.text("decibel-settled"));
        assertTrue(chapter.session().focus("decibel-fader"));

        chapter.session().key(Key.UP);

        assertNotEquals("Settled: 0.50", chapter.text("decibel-settled"));
        assertEquals(chapter.text("decibel-moving").replace("Moving", "Settled"), chapter.text("decibel-settled"));
    }
}
