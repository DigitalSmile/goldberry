package io.github.digitalsmile.goldberry.example.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.kdl.KdlParser;

/// The code the emoji dialog shows — which is only worth showing if it is right.
///
/// The KDL half is parsed with the toolkit's own parser, so a sample that
/// drifted from the syntax — a missing quote, an escape KDL does not have —
/// fails here rather than in a reader's document.
class SpecimensTest {

    private static final String FOX = "🦊";

    private static final int FOX_POINT = 0x1F98A;

    @Test
    @DisplayName("the KDL sample parses, and every text in it carries the emoji — typed or escaped")
    void kdlParses() {
        var markup = String.join("\n", Specimens.kdlSample(FOX, FOX_POINT));

        List<KdlNode> nodes = KdlParser.parse(markup);

        assertEquals(
                List.of("text", "button", "text"),
                nodes.stream().map(KdlNode::name).toList());
        for (var node : nodes) {
            var content = node.argument().orElseThrow().asString();
            assertTrue(content.endsWith(FOX), node.name() + " reads \"" + content + "\"");
        }
        assertEquals(
                nodes.getFirst().argument().orElseThrow().asString(),
                nodes.getLast().argument().orElseThrow().asString(),
                "the escape is the same text as the typed character");
        assertEquals("app.celebrate", nodes.get(1).stringProperty("press"));
    }

    @Test
    @DisplayName("the Java sample types the emoji and builds it from the code point the dialog names")
    void javaNamesTheCodePoint() {
        var lines = Specimens.javaSample(FOX, FOX_POINT);

        assertTrue(lines.stream().anyMatch(line -> line.contains("new Text(\"Shipping on Friday " + FOX + "\")")));
        assertTrue(lines.contains("var emoji = Character.toString(0x1F98A);"), lines.toString());
        // What that line evaluates to is the emoji the dialog is about.
        assertEquals(FOX, Character.toString(FOX_POINT));
    }

    @Test
    @DisplayName("neither sample names a font, because routing is the itemizer's")
    void noFontIsNamed() {
        var all = String.join("\n", Specimens.kdlSample(FOX, FOX_POINT))
                + String.join("\n", Specimens.javaSample(FOX, FOX_POINT));

        assertTrue(!all.contains("font"), all);
    }
}
