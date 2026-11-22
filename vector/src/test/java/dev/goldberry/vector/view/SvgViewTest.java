package dev.goldberry.vector.view;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.vector.VectorNativesRequirement;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.markup.Wiring;

@DisplayName("svg-view")
class SvgViewTest {

    /// A square drawn entirely in `currentColor`.
    static final String SQUARE = """
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 10 10">
              <rect width="10" height="10" fill="currentColor"/>
            </svg>
            """;

    /// Red over blue, colours of its own.
    static final String HALVES = """
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 10 10">
              <rect width="10" height="5" fill="#ff0000"/>
              <rect y="5" width="10" height="5" fill="#0000ff"/>
            </svg>
            """;

    private static final int WHITE = 0xFFFFFFFF;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @BeforeEach
    void fresh() {
        SvgDocument.forget();
        SvgViewState.forgetReported();
    }

    private static Element part(ElementTree tree) {
        return tree.root().children().getFirst();
    }

    private static Stylesheet sheet(String css) {
        return Stylesheet.parse(CascadeLayer.APPLICATION, css);
    }

    @Nested
    @DisplayName("as a tree")
    class AsATree {

        @Test
        @DisplayName("reads its document, and is a figure named by its alt text")
        void figure() {
            var tree = new ElementTree(new SvgView(SvgSource.text(SQUARE), "A square"));
            var node = assertInstanceOf(Semantics.class, part(tree).widget());
            assertAll(
                    () -> assertEquals(Set.of(), part(tree).classes()),
                    () -> assertEquals(Role.FIGURE, node.role()),
                    () -> assertEquals("A square", node.accessibleName()));
        }

        @Test
        @DisplayName("is nothing to a reader when it is decoration")
        void decorative() {
            var tree = new ElementTree(SvgView.decorative(SvgSource.text(SQUARE)));
            assertFalse(part(tree).widget() instanceof Semantics);
        }

        @Test
        @DisplayName("says error, with its icon and alt text, for a file that is missing")
        void missing(@TempDir Path directory) {
            var tree = new ElementTree(new SvgView(SvgSource.file(directory.resolve("gone.svg")), "A missing logo"));
            assertAll(
                    () -> assertEquals(Set.of("error"), part(tree).classes()),
                    () -> assertEquals(2, part(tree).children().size()));
        }

        @Test
        @DisplayName("says error for bytes that are not an SVG document")
        void notADocument() {
            var tree = new ElementTree(SvgView.decorative(SvgSource.text("<html><svg/></html>")));
            assertEquals(Set.of("error"), part(tree).classes());
        }

        @Test
        @DisplayName("is refused without alt text unless it is decoration")
        void altRequired() {
            assertThrows(IllegalArgumentException.class, () -> new SvgView(SvgSource.text(SQUARE), " "));
        }

        @Test
        @DisplayName("keeps one drawing per document and colour, however many views show it")
        void shared() {
            var source = SvgSource.text(SQUARE);
            var _ = new ElementTree(new Column(
                    List.of(SvgView.decorative(source), SvgView.decorative(source), SvgView.decorative(source)),
                    Attributes.NONE));
            assertEquals(1, SvgDocument.remembered());
        }
    }

    @Nested
    @DisplayName("in markup")
    class InMarkup {

        @Test
        @DisplayName("is found by name, with its document as src= or written as its argument")
        void inflates(@TempDir Path directory) throws IOException {
            var file = directory.resolve("square.svg");
            Files.writeString(file, SQUARE);
            var fromFile = Widgets.inflater()
                    .inflate(KdlParser.parse("svg-view src=\"" + file.toString().replace("\\", "/")
                                    + "\" alt=\"Logo\" fit=\"cover\"")
                            .getFirst());
            var inline = Widgets.inflater()
                    .inflate(KdlParser.parse("svg-view decorative=#true \"<svg viewBox='0 0 1 1'/>\"")
                            .getFirst());
            var first = assertInstanceOf(SvgView.class, fromFile);
            var second = assertInstanceOf(SvgView.class, inline);
            assertAll(
                    () -> assertEquals(SvgSource.file(file), first.source()),
                    () -> assertEquals(Fit.COVER, first.fit()),
                    () -> assertEquals("Logo", first.alt()),
                    () -> assertEquals(SvgSource.text("<svg viewBox='0 0 1 1'/>"), second.source()),
                    () -> assertTrue(second.decorative()));
        }

        @Test
        @DisplayName("names a classpath resource with classpath:")
        void resource() {
            var view = (SvgView) SvgView.inflate(
                    KdlParser.parse("svg-view src=\"classpath:/icons/logo.svg\" decorative=#true")
                            .getFirst(),
                    List.of(),
                    Wiring.none());
            assertEquals("classpath:icons/logo.svg", view.source().key());
        }

        @Test
        @DisplayName("refuses both a src= and an argument, neither, children, and a fit it does not know")
        void refuses() {
            assertAll(
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> inflate("svg-view src=\"a.svg\" decorative=#true \"<svg/>\"")),
                    () -> assertThrows(IllegalArgumentException.class, () -> inflate("svg-view decorative=#true")),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> inflate("svg-view src=\"a.svg\" decorative=#true fit=\"stretch\"")),
                    () -> assertThrows(IllegalArgumentException.class, () -> inflate("svg-view src=\"a.svg\"")),
                    () -> assertThrows(
                            IllegalArgumentException.class,
                            () -> SvgView.inflate(
                                    KdlParser.parse("svg-view src=\"a.svg\" decorative=#true")
                                            .getFirst(),
                                    List.of(SvgView.decorative(SvgSource.text(SQUARE))),
                                    Wiring.none())));
        }

        private static void inflate(String kdl) {
            var _ = SvgView.inflate(KdlParser.parse(kdl).getFirst(), List.of(), Wiring.none());
        }
    }

    @Nested
    @DisplayName("drawn")
    class Drawn {

        @Test
        @DisplayName("in its CSS color, and again in another when the color changes on :hover")
        void hover() {
            var view =
                    SvgView.decorative(SvgSource.text(SQUARE)).withAttributes(new Attributes("icon", Set.of(), null));
            try (var session = Offscreen.of(20, 20)
                    .background(WHITE)
                    .stylesheets(List.of(sheet("""
                            #icon { width: 20px; height: 20px; color: #ff0000 }
                            #icon:hover { color: #0000ff }
                            """)))
                    .session(view)) {
                assertEquals(0xFFFF0000, session.frame().argb(10, 10));
                session.hover(10, 10);
                assertEquals(0xFF0000FF, session.frame().argb(10, 10));
            }
        }

        @Test
        @DisplayName("inheriting the color of the text around it")
        void inherits() {
            var column = new Column(
                    List.of(SvgView.decorative(SvgSource.text(SQUARE))), new Attributes("around", Set.of(), null));
            var frame = Offscreen.of(10, 10)
                    .background(WHITE)
                    .stylesheets(List.of(sheet("#around { color: #00ff00 }")))
                    .render(column);
            assertEquals(0xFF00FF00, frame.argb(5, 5));
        }

        @Test
        @DisplayName("contained, covering or filling a box of another shape")
        void fits() {
            var sheet = sheet("svg-view { width: 20px; height: 10px }");
            var source = SvgSource.text(HALVES);
            var contained = Offscreen.of(20, 10)
                    .background(WHITE)
                    .stylesheets(List.of(sheet))
                    .render(SvgView.decorative(source).fit(Fit.CONTAIN));
            var covered = Offscreen.of(20, 10)
                    .background(WHITE)
                    .stylesheets(List.of(sheet))
                    .render(SvgView.decorative(source).fit(Fit.COVER));
            assertAll(
                    () -> assertEquals(WHITE, contained.argb(2, 5), "contain leaves the sides"),
                    () -> assertEquals(0xFFFF0000, contained.argb(10, 2)),
                    () -> assertEquals(0xFFFF0000, covered.argb(1, 1), "cover fills the box"),
                    () -> assertEquals(0xFF0000FF, covered.argb(18, 8)));
        }

        @Test
        @DisplayName("at its natural size when nothing sizes it, sharp at 2x")
        void natural() {
            var frame = Offscreen.of(40, 40)
                    .scale(2f)
                    .background(WHITE)
                    .stylesheets(List.of(sheet("svg-view { color: #000000 }")))
                    .render(SvgView.decorative(SvgSource.bytes(SQUARE.getBytes(StandardCharsets.UTF_8))));
            assertAll(
                    () -> assertEquals(0xFF000000, frame.argb(19, 19), "10 units at 2x end on device pixel 19"),
                    () -> assertEquals(WHITE, frame.argb(20, 19)));
        }
    }
}
