package io.github.digitalsmile.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.kdl.KdlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// An application's CSS and markup come from files beside its code
/// ([ADR-0093](../../../../../../book/src/adr/0093-an-application-is-a-root-widget.md)).
///
/// The toolkit read its own theme and control sheets from resources from the
/// start; an application had no supported way to do the same and wrote its CSS in
/// a Java text block, which is CSS no editor will highlight and no designer will
/// open.
class ResourceLoadingTest {

    @Test
    @DisplayName("a stylesheet loads from a resource beside a class")
    void stylesheetFromResource() {
        var sheet = Stylesheet.resource(
                CascadeLayer.APPLICATION, ResourceLoadingTest.class, "sample.css");

        assertEquals(CascadeLayer.APPLICATION, sheet.layer());
        assertFalse(sheet.rules().isEmpty(), "the file parsed to nothing");
    }

    @Test
    @DisplayName("markup loads from a resource the same way")
    void markupFromResource() {
        var nodes = KdlParser.resource(ResourceLoadingTest.class, "sample.kdl");

        assertEquals(1, nodes.size());
        assertEquals("row", nodes.getFirst().name());
        assertEquals(2, nodes.getFirst().children().size());
    }

    /// A missing resource is an error and not an empty document, and the message
    /// has to say which of the two things went wrong — because for a file that
    /// ships inside a jar, "not found" is a build problem, and for one inside a
    /// named module it is usually a missing `opens`.
    @Test
    @DisplayName("a missing stylesheet names the directory it belongs in")
    void missingStylesheetExplainsItself() {
        var thrown = assertThrows(IllegalStateException.class, () -> Stylesheet.resource(
                CascadeLayer.APPLICATION, ResourceLoadingTest.class, "nothing.css"));

        assertTrue(thrown.getMessage().contains("nothing.css"));
        assertTrue(thrown.getMessage().contains("io/github/digitalsmile/goldberry/css"),
                () -> "the message does not say where to put it: " + thrown.getMessage());
    }

    @Test
    @DisplayName("a missing document says the same")
    void missingMarkupExplainsItself() {
        var thrown = assertThrows(IllegalStateException.class,
                () -> KdlParser.resource(ResourceLoadingTest.class, "nothing.kdl"));

        assertTrue(thrown.getMessage().contains("nothing.kdl"));
    }

    /// UTF-8, unconditionally. A stylesheet read in the platform's charset is one
    /// that parses differently on a machine whose default is not UTF-8 — which is
    /// the class of bug §1.1's "deterministic" principle exists to prevent, and
    /// which no golden image would catch because the developer who wrote the file
    /// is on the machine where it works.
    @Test
    @DisplayName("resources are read as UTF-8 whatever the platform default is")
    void readsUtf8() {
        var nodes = KdlParser.resource(ResourceLoadingTest.class, "sample.kdl");

        assertEquals("é—ü", nodes.getFirst().children().get(1)
                .argument().orElseThrow().asString());
    }
}
