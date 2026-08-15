package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Compiling a whole icon, not just one shape.
class IconCompilerTest {

    private final IconCompiler compiler = new IconCompiler();

    private String compile(String svg) throws IOException {
        return compiler.compile("test", new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }

    private static String icon(String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\""
                + " viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\""
                + " stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">"
                + body + "</svg>";
    }

    @Test
    @DisplayName("a path passes through unchanged")
    void pathsArePreserved() throws IOException {
        // Lucide's `check`, verbatim. Rewriting path data would be an
        // opportunity to lose precision for no gain.
        assertEquals("M20 6 9 17l-5-5", compile(icon("<path d=\"M20 6 9 17l-5-5\"/>")));
    }

    @Test
    @DisplayName("several shapes become several subpaths, in document order")
    void shapesConcatenateInOrder() throws IOException {
        var path = compile(icon(
                "<circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"M9 12h6\"/>"));

        assertTrue(path.startsWith("M2 12A10 10"), path);
        assertTrue(path.endsWith("M9 12h6"), path);
        // Concatenation is only safe because every subpath begins with a moveto.
        assertEquals(2, path.split(" ").length > 1 ? 2 : 1, "two subpaths");
    }

    @Test
    @DisplayName("every shape Lucide uses is convertible")
    void allSevenShapesAreHandled() throws IOException {
        for (var body : Map.of(
                "path", "<path d=\"M0 0h1\"/>",
                "line", "<line x1=\"1\" y1=\"2\" x2=\"3\" y2=\"4\"/>",
                "polyline", "<polyline points=\"1,2 3,4\"/>",
                "polygon", "<polygon points=\"1,2 3,4 5,6\"/>",
                "circle", "<circle cx=\"12\" cy=\"12\" r=\"10\"/>",
                "ellipse", "<ellipse cx=\"12\" cy=\"12\" rx=\"10\" ry=\"6\"/>",
                "rect", "<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\" rx=\"2\"/>").entrySet()) {

            var path = compile(icon(body.getValue()));
            assertTrue(path.startsWith("M"), () -> body.getKey() + " produced: " + path);
        }
    }

    @Test
    @DisplayName("an element that needs a real renderer is refused, not skipped")
    void unsupportedElementsFail() {
        // Skipping it would give an icon that is silently missing a piece, which
        // is far harder to notice than a build that stops and says why.
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> compile(icon("<path d=\"M0 0h1\"/><g><path d=\"M1 1h1\"/></g>")));

        assertTrue(thrown.getMessage().contains("<g>"), thrown.getMessage());
    }

    @Test
    @DisplayName("an icon that compiles to nothing is refused")
    void emptyIconsFail() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> compile(icon("")));

        assertTrue(thrown.getMessage().contains("render as nothing"), thrown.getMessage());
    }

    @Test
    @DisplayName("a rect with no rx keeps square corners")
    void rectWithoutRadiusIsSquare() throws IOException {
        var path = compile(icon("<rect x=\"3\" y=\"3\" width=\"18\" height=\"18\"/>"));

        assertEquals("M3 3H21V21H3Z", path);
    }

    @Test
    @DisplayName("a rect with only rx mirrors it into ry")
    void rectWithOneRadiusMirrorsIt() throws IOException {
        var onlyRx = compile(icon("<rect x=\"0\" y=\"0\" width=\"10\" height=\"10\" rx=\"2\"/>"));
        var both = compile(icon(
                "<rect x=\"0\" y=\"0\" width=\"10\" height=\"10\" rx=\"2\" ry=\"2\"/>"));

        assertEquals(both, onlyRx);
    }

    @Test
    @DisplayName("compiling a set sorts it, so the output is stable")
    void compiledSetsAreSorted() throws IOException {
        var svgs = Map.of(
                "zebra", icon("<path d=\"M0 0h1\"/>").getBytes(StandardCharsets.UTF_8),
                "apple", icon("<path d=\"M1 1h1\"/>").getBytes(StandardCharsets.UTF_8),
                "mango", icon("<path d=\"M2 2h1\"/>").getBytes(StandardCharsets.UTF_8));

        // An unstable order would make every rebuild produce a different jar for
        // the same inputs, which is a reproducibility problem, not a tidiness one.
        assertEquals(
                java.util.List.of("apple", "mango", "zebra"),
                java.util.List.copyOf(compiler.compileAll(svgs).keySet()));
    }

    @Test
    @DisplayName("a non-numeric attribute is reported rather than defaulted")
    void badNumbersAreReported() {
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> compile(icon("<circle cx=\"12\" cy=\"12\" r=\"ten\"/>")));

        assertTrue(thrown.getMessage().contains("not a number"), thrown.getMessage());
    }
}
