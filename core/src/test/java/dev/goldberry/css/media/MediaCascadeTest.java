package dev.goldberry.css.media;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.render.desktop.SystemTheme;

/// `@media` in the cascade: a rule applies while its condition holds, and the
/// resolver is replaced only when an answer changes.
class MediaCascadeTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int BLACK = 0xFF000000;

    private static final String SHEET = """
            panel { color: red }
            @media (max-width: 600px) { panel { color: blue } }
            @media (prefers-color-scheme: dark) { panel { background: black } }
            @media (prefers-reduced-motion: reduce) { panel { transition: none } }
            """;

    private static StyleResolver resolver(String css) {
        return new StyleResolver(List.of(Stylesheet.parse(CascadeLayer.APPLICATION, css)));
    }

    private static ComputedStyle style(StyleResolver resolver) {
        return ComputedStyle.of(resolver.resolve(element("panel")), CssLength.Context.DEFAULT);
    }

    @Test
    @DisplayName("a rule inside @media applies only while its condition holds")
    void appliesWhileItHolds() {
        var resolver = resolver(SHEET);
        var wide = resolver.under(MediaContext.UNKNOWN.size(1280, 800));
        var narrow = resolver.under(MediaContext.UNKNOWN.size(480, 800));

        assertEquals(RED, style(wide).color());
        assertEquals(BLUE, style(narrow).color());
        assertEquals(
                BLACK,
                style(wide.under(wide.media().colorScheme(SystemTheme.DARK))).background());
    }

    @Test
    @DisplayName("before any window has said its size, a width condition does not hold")
    void unknownWindow() {
        assertEquals(RED, style(resolver(SHEET)).color());
    }

    @Test
    @DisplayName("a new context that changes no answer keeps the very same resolver, and so every cached style")
    void identityKeptAcrossAResizeWithinABreakpoint() {
        var wide = resolver(SHEET).under(MediaContext.UNKNOWN.size(1280, 800));

        assertSame(wide, wide.under(wide.media().size(1000, 700)), "no breakpoint crossed");
        assertNotSame(wide, wide.under(wide.media().size(500, 700)), "the 600px breakpoint crossed");
        assertNotSame(wide, wide.under(wide.media().reducedMotion(true)), "motion reduced");
    }

    @Test
    @DisplayName("sheets with no @media at all never replace the resolver")
    void noMediaNoChange() {
        var resolver = resolver("panel { color: red }");
        assertSame(resolver, resolver.under(MediaContext.UNKNOWN.size(10, 10).colorScheme(SystemTheme.DARK)));
    }

    @Test
    @DisplayName("nested blocks apply when both conditions hold")
    void nested() {
        var resolver =
                resolver("@media (min-width: 600px) { @media (prefers-color-scheme: dark) { panel { color: blue } } }");
        var context = MediaContext.UNKNOWN.size(800, 600);
        assertEquals(
                ComputedStyle.INITIAL.color(), style(resolver.under(context)).color());
        assertEquals(
                BLUE,
                style(resolver.under(context.colorScheme(SystemTheme.DARK))).color());
    }

    @Test
    @DisplayName("an unsupported feature never applies in an application's sheet, and refuses the toolkit's")
    void unsupportedFeature() {
        var css = "@media (hover: hover) { panel { color: blue } }";
        var lenient = CssParser.parseSheet(css, ParseMode.LENIENT, "app.css");
        assertEquals(1, lenient.dropped().size(), "reported once, as a value");
        assertFalse(lenient.rules().getFirst().media().matches(MediaContext.UNKNOWN.size(800, 600)));

        var thrown = assertThrows(CssSyntaxException.class, () -> CssParser.parse(css));
        assertTrue(thrown.isUnsupportedFeature());
    }

    @Test
    @DisplayName("@keyframes inside @media is outside the subset")
    void keyframesInsideMedia() {
        var parsed = CssParser.parseSheet(
                "@media (min-width: 1px) { @keyframes pulse { from { opacity: 0 } } }", ParseMode.LENIENT, "app.css");
        assertTrue(parsed.keyframes().isEmpty());
        assertEquals(1, parsed.dropped().size());
    }
}
