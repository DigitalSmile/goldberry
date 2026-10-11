package dev.goldberry.css.container;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.TestElement;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.Viewport;

/// `@container`: a query, the container it is asked of, and the cascade that
/// asks it.
class ContainerQueryTest {

    private static ContainerQuery query(String prelude) {
        // Up to the block, as the parser hands it over: no end-of-input token.
        return ContainerQueries.parse(CssTokenizer.tokenize(prelude).stream()
                .filter(token -> !token.is(TokenType.EOF))
                .toList());
    }

    /// `page > card(container) > text`, the card laid out `width` by
    /// `height`.
    private static TestElement text(ContainerType type, double width, double height, String... names) {
        var text = element("text");
        element("page")
                .with(element("card").container(type, width, height, names).with(text));
        return text;
    }

    @Nested
    @DisplayName("a query")
    class Queries {

        @ParameterizedTest(name = "{0} at {1}x{2}: {3}")
        @CsvSource({
            "(min-width: 400px), 500, 100, true",
            "(min-width: 400px), 300, 100, false",
            "(width > 400px), 401, 100, true",
            "(300px <= inline-size <= 500px), 400, 100, true",
            "(orientation: portrait), 100, 300, true",
            "(min-block-size: 200px), 100, 250, true",
            "(min-width: 100px) and (max-width: 200px), 150, 10, true",
            "not (min-width: 400px), 300, 10, true",
        })
        @DisplayName("holds as the container's content box says")
        void holds(String prelude, double width, double height, boolean expected) {
            assertEquals(expected, query(prelude).holds(text(ContainerType.SIZE, width, height)), prelude);
        }

        @Test
        @DisplayName("a name narrows the container to the one called that")
        void named() {
            var inner = element("text");
            element("page")
                    .container(ContainerType.SIZE, 900, 900, "sidebar")
                    .with(element("card")
                            .container(ContainerType.SIZE, 200, 200)
                            .with(inner));
            assertTrue(query("sidebar (min-width: 800px)").holds(inner), "the named one, past the nearer card");
            assertFalse(query("(min-width: 800px)").holds(inner), "the nearest one, the card");
            assertEquals("sidebar", query("sidebar (min-width: 1px)").name());
        }

        @Test
        @DisplayName("an inline-size container answers the inline axis and no other")
        void inlineSize() {
            var text = text(ContainerType.INLINE_SIZE, 500, 500);
            assertTrue(query("(min-width: 400px)").holds(text));
            assertFalse(query("(min-height: 400px)").holds(text), "the block axis looks further up, and finds none");
            assertTrue(query("(min-height: 1px)").block());
        }

        @Test
        @DisplayName("with no container, or none laid out, it does not hold")
        void noContainer() {
            var orphan = element("text");
            element("page").with(orphan);
            assertFalse(query("(min-width: 0px)").holds(orphan));
            assertNull(ContainerQuery.nearest(orphan, null, false));
        }

        @Test
        @DisplayName("a style() query or a media feature cannot be answered")
        void foreign() {
            assertInstanceOf(
                    MediaCondition.Unsupported.class, query("style(--dark: 1)").condition());
            assertInstanceOf(
                    MediaCondition.Unsupported.class, query("(hover: hover)").condition());
        }
    }

    @Nested
    @DisplayName("a sheet")
    class Sheets {

        @Test
        @DisplayName("a rule inside @container carries its query, nested ones every one")
        void carried() {
            var rules = CssParser.parse(
                    "@container a (min-width: 10px) { @container (min-height: 5px) { x { color: red } } y { color: blue } }");
            assertEquals(2, rules.getFirst().containers().size());
            assertEquals(1, rules.getLast().containers().size());
            assertTrue(rules.getLast().isContained());
        }

        @Test
        @DisplayName("a strict sheet refuses a query it cannot answer, a lenient one keeps and never applies it")
        void modes() {
            var css = "@container (hover: hover) { x { color: red } }";
            var thrown = assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.STRICT, "t"));
            assertTrue(thrown.isUnsupportedFeature());
            assertEquals(
                    1, CssParser.parseSheet(css, ParseMode.LENIENT, "t").rules().size());
        }

        @Test
        @DisplayName("the cascade applies a contained rule where its query holds, and nowhere else")
        void cascade() {
            var resolver = new StyleResolver(List.of(Stylesheet.parse(
                    CascadeLayer.APPLICATION, "@container (min-width: 400px) { text { color: red } }")));
            assertTrue(resolver.readsContainers());
            Map<String, List<Token>> wide = resolver.resolve(text(ContainerType.SIZE, 500, 100));
            Map<String, List<Token>> narrow = resolver.resolve(text(ContainerType.SIZE, 300, 100));
            assertTrue(wide.containsKey("color"));
            assertFalse(narrow.containsKey("color"));
        }

        @Test
        @DisplayName("sheets with neither a query nor a container unit read no container")
        void notRead() {
            assertFalse(new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "x { width: 10vw }")))
                    .readsContainers());
            assertTrue(new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, "x { width: 10cqw }")))
                    .readsContainers());
        }
    }

    @Nested
    @DisplayName("the container units")
    class Units {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"10cqw, 40", "10cqh, 20", "10cqi, 40", "10cqb, 20", "10cqmin, 20", "10cqmax, 40"})
        @DisplayName("are a percentage of the container's content box")
        void ofTheContainer(String written, double expected) {
            var context = CssLength.Context.DEFAULT.withViewport(1000, 800).withContainer(new Viewport(400, 200));
            var token = CssTokenizer.tokenize(written).getFirst();
            assertEquals(expected, CssLength.pixels(token.numeric(), token.unit(), context), 1e-9);
        }

        @Test
        @DisplayName("fall back to the window's when there is no container")
        void ofTheWindow() {
            var context = CssLength.Context.DEFAULT.withViewport(1000, 800);
            assertEquals(100, CssLength.pixels(10, "cqw", context), 1e-9);
            assertEquals(new Viewport(1000, 800), ContainerQuery.unitsBasis(element("lone"), context.viewport()));
        }
    }
}
