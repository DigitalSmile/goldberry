package io.github.digitalsmile.goldberry.widgets.controls.spinner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.widget.attr.Attributes;

/// A spinner's size — [ADR-0447].
@DisplayName("a spinner's size")
class SpinnerSizeTest {

    @Nested
    @DisplayName("changes the ring's stroke")
    class Stroke {

        /// The whole reason this is a value and not only a class: §8's CSS
        /// subset has no property for the weight of a mark the painter draws, so
        /// a 32px ring drawn with a 16px ring's 2px stroke would be a thin hoop
        /// and no stylesheet could have said otherwise.
        @Test
        @DisplayName("in proportion, so a large one is a big picture of a small one")
        void scalesInProportion() {
            var small = SpinnerSize.thicknessFor(SpinnerSize.SMALL.diameter());
            var medium = SpinnerSize.thicknessFor(SpinnerSize.MEDIUM.diameter());
            var large = SpinnerSize.thicknessFor(SpinnerSize.LARGE.diameter());

            assertTrue(small < medium && medium < large, small + " " + medium + " " + large);
            assertEquals(
                    large / SpinnerSize.LARGE.diameter(),
                    medium / SpinnerSize.MEDIUM.diameter(),
                    1e-9,
                    "every size is the same ring at a different scale");
        }

        /// The ratio is exactly today's 2px at today's 16px, which is what makes
        /// `medium` render identically to every spinner drawn before sizes
        /// existed — goldens included. A change here moves every existing
        /// picture.
        @Test
        @DisplayName("leaving the default size exactly where it was")
        void mediumIsUnchanged() {
            assertEquals(2.0, SpinnerSize.thicknessFor(SpinnerSize.MEDIUM.diameter()), 1e-9);
        }

        /// The diameter is the stylesheet's, so an application that overrides
        /// the width gets a stroke weighted for what it asked for.
        @Test
        @DisplayName("from whatever diameter it is actually drawn at")
        void followsTheResolvedWidth() {
            assertEquals(6.0, SpinnerSize.thicknessFor(48), 1e-9);
        }

        /// A sub-pixel stroke is a ring that is not there.
        @Test
        @DisplayName("and never thinner than a pixel")
        void neverVanishes() {
            assertEquals(1.0, SpinnerSize.thicknessFor(1), 1e-9);
            assertEquals(1.0, SpinnerSize.thicknessFor(0), 1e-9);
        }
    }

    @Nested
    @DisplayName("puts a class on the node")
    class Classes {

        @Test
        @DisplayName("so `spinner.large` selects without anybody writing it")
        void selectsWithoutBeingWritten() {
            assertTrue(new Spinner(SpinnerSize.LARGE).classes().contains("large"));
        }

        /// Including the default, so an application can select a size without
        /// knowing which one the toolkit happens to call the default.
        @Test
        @DisplayName("including the default one")
        void theDefaultHasAClassToo() {
            assertTrue(new Spinner().classes().contains("medium"));
        }

        @Test
        @DisplayName("beside the application's own, never instead of them")
        void keepsTheApplicationsClasses() {
            var spinner = new Spinner(SpinnerSize.SMALL).withAttributes(Attributes.NONE.classes("busy"));

            assertTrue(spinner.classes().contains("busy"));
            assertTrue(spinner.classes().contains("small"));
        }
    }

    @Nested
    @DisplayName("is read from markup")
    class Parsing {

        @Test
        @DisplayName("by name")
        void byName() {
            assertEquals(SpinnerSize.LARGE, SpinnerSize.of("large"));
            assertEquals(SpinnerSize.SMALL, SpinnerSize.of(" Small "));
        }

        /// A spinner with no size is still a spinner; refusing to build one
        /// would take a window down over a missing word.
        @Test
        @DisplayName("defaulting when a document does not say")
        void absentIsTheDefault() {
            assertEquals(SpinnerSize.MEDIUM, SpinnerSize.of(null));
            assertEquals(SpinnerSize.MEDIUM, SpinnerSize.of("  "));
        }

        /// A misspelt size is a document saying something it does not mean, and
        /// silently drawing the default would hide it for ever.
        @Test
        @DisplayName("and refusing one that is misspelt")
        void misspeltIsRefused() {
            var thrown = assertThrows(IllegalArgumentException.class, () -> SpinnerSize.of("enormous"));

            assertTrue(thrown.getMessage().contains("enormous"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("leaves what was there alone")
    class Compatibility {

        /// Every `new Spinner(...)` written before sizes existed still means the
        /// same spinner. Adding a component to a record is the change most
        /// likely to silently alter what existing callers get.
        @Test
        @DisplayName("so a spinner built the old way is still the default size")
        void theOldConstructorsAreUnchanged() {
            assertEquals(SpinnerSize.MEDIUM, new Spinner().size());
            assertEquals(SpinnerSize.MEDIUM, new Spinner(Attributes.NONE.id("a")).size());
        }

        @Test
        @DisplayName("and changing attributes keeps the size")
        void withAttributesKeepsTheSize() {
            var resized = new Spinner(SpinnerSize.LARGE).withAttributes(Attributes.NONE.id("a"));

            assertEquals(SpinnerSize.LARGE, resized.size());
        }
    }
}
