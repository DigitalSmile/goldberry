package dev.goldberry.example.book;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import dev.goldberry.kdl.KdlParser;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Widgets;

/// Every `kdl` sample in the guide, parsed and inflated against the catalogue
/// the showcase runs with.
///
/// A sample in documentation is read more often than any test, and a node name
/// that was renamed after the chapter was written is a reader's first failure.
/// `docs/book.md` promises that every fenced `kdl` block is a document the
/// inflater accepts, so this is where that promise is kept. It runs here rather
/// than in `:widgets` because the showcase is the one module with every widget
/// catalogue on its path: `markdown-view`, `video-view` and `canvas3d` are
/// names the guide writes and `:widgets` alone cannot inflate.
///
/// Nothing is bound. [Widgets#inflater()] resolves every `bind=`, `press=` and
/// `icon=` to nothing, which is what a preview does, so a sample is free to name
/// an application's values without an application behind it. What still fails
/// is what a reader would hit first: an unknown node, an attribute whose value
/// has the wrong type, a `format=` that does not match its value.
class BookMarkupTest {

    @Test
    @DisplayName("the guide has samples to check, so a chapter that lost its fences is noticed")
    void thereAreSamples() {
        var samples = BookSamples.kdl();
        assertFalse(samples.isEmpty(), "no ```kdl blocks found under book/src");
        assertTrue(
                samples.size() > 40, () -> "only " + samples.size() + " kdl samples; the guide used to have far more");
    }

    @TestFactory
    @DisplayName("a kdl sample inflates")
    Stream<DynamicTest> everySampleInflates() {
        var inflater = Widgets.inflater();
        return BookSamples.kdl().stream()
                .map(sample -> DynamicTest.dynamicTest(sample.chapter() + ":" + sample.line(), () -> {
                    List<Widget> widgets = inflater.inflateAll(KdlParser.parse(sample.text()));
                    assertAll(
                            () -> assertFalse(
                                    widgets.isEmpty(),
                                    () -> sample.chapter() + ":" + sample.line()
                                            + " is a kdl block that builds no widget; fence it as kdl,ignore if it is a fragment"),
                            () -> assertTrue(widgets.stream().allMatch(widget -> widget != null), "a null widget"));
                }));
    }
}
