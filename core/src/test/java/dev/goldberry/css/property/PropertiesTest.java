package dev.goldberry.css.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Direction;

/// The property table against the engine it describes.
///
/// The table is a second account of what `ComputedStyle`'s switch already
/// says, so every test here holds one to the other: the same names, each
/// copier moving exactly what a declaration of its property sets, and each
/// shorthand writing no more than its longhands.
class PropertiesTest {

    /// One declaration per longhand, each with a value that differs from the
    /// initial style's. The sides' border styles are set through the side
    /// shorthands first, since nothing else can set them.
    private static final String EVERY_LONGHAND = """
            flex-direction: row-reverse; justify-content: space-between; align-items: center;
            align-self: center; align-content: space-around; flex-wrap: wrap;
            width: 10px; height: 11px; min-width: 1px; max-width: 100px; min-height: 2px; max-height: 101px;
            row-gap: 3px; column-gap: 4px; flex-grow: 2; flex-shrink: 3; flex-basis: 9px;
            position: absolute; overflow-x: hidden; overflow-y: scroll; display: contents; aspect-ratio: 16 / 9;
            order: 3; direction: rtl; unicode-bidi: plaintext; box-sizing: content-box; container-type: size; container-name: side;
            visibility: hidden; pointer-events: none; user-select: none; z-index: 4;
            clip-path: circle(); mix-blend-mode: multiply; isolation: isolate; filter: sepia(1);
            padding-top: 5px; padding-right: 6px; padding-bottom: 7px; padding-left: 8px;
            margin-top: 1px; margin-right: 2px; margin-bottom: 3px; margin-left: 4px;
            top: 1px; right: 2px; bottom: 3px; left: 4px;
            white-space: nowrap; overflow-wrap: anywhere; word-break: break-all; text-overflow: ellipsis; line-clamp: 3;
            text-align: center; text-decoration-line: underline; color: #abcdef; opacity: 0.5;
            background-color: #123456; background-image: linear-gradient(red, blue);
            background-position-x: 3px; background-position-y: 4px; background-size: 10px 20px;
            background-repeat: no-repeat; background-origin: content-box; background-clip: padding-box;
            border-top-left-radius: 1px; border-top-right-radius: 2px; border-bottom-right-radius: 3px;
            border-bottom-left-radius: 4px;
            border-top: 1px dashed red; border-right: 1px dotted red;
            border-bottom: 1px double red; border-left: 1px dashed red;
            border-top-style: dashed; border-right-style: dotted; border-bottom-style: double;
            border-left-style: dashed;
            border-top-width: 2px; border-right-width: 3px; border-bottom-width: 4px; border-left-width: 5px;
            border-top-color: #010203; border-right-color: #040506;
            border-bottom-color: #070809; border-left-color: #0a0b0c;
            outline-width: 2px; outline-color: red; outline-offset: 3px; outline-style: dashed; box-shadow: 0 1px 2px red;
            border-image-source: url("frame.png"); border-image-slice: 3; border-image-width: 2px;
            border-image-outset: 1px; border-image-repeat: round;
            font-family: Mono; font-size: 20px; font-weight: 700; font-style: italic; line-height: 30px;
            letter-spacing: 1px; word-spacing: 2px; text-transform: uppercase; text-shadow: 1px 1px red;
            text-justify: none; font-stretch: 75%; font-variation-settings: "GRAD" 1;
            animation-name: spin; animation-duration: 2s; animation-timing-function: linear;
            animation-delay: 1s; animation-iteration-count: 3; animation-direction: reverse;
            animation-fill-mode: forwards; animation-play-state: paused;
            transition-property: opacity, color; transition-duration: 1s; transition-timing-function: linear;
            transition-delay: 2ms; transform: rotate(10deg); transform-origin: 0 0; cursor: pointer;
            translate: 3px 4px; rotate: 30deg; scale: 2; transform-box: content-box
            """;

    /// One declaration per shorthand and alias, each changing everything it
    /// can, and each to values that differ from [#EVERY_LONGHAND]'s.
    private static final Map<String, String> SHORTHANDS = Map.ofEntries(
            Map.entry("padding", "11px 12px 13px 14px"),
            Map.entry("margin", "11px 12px 13px 14px"),
            Map.entry("inset", "11px 12px 13px 14px"),
            Map.entry("gap", "7px 8px"),
            Map.entry("font-width", "expanded"),
            Map.entry("-webkit-filter", "blur(1px)"),
            Map.entry("container", "main / inline-size"),
            Map.entry("flex", "4 5 6px"),
            Map.entry("text-decoration", "line-through"),
            Map.entry(
                    "background", "url(\"tile.png\") right 3px top 2px / 7px 8px space border-box content-box #654321"),
            Map.entry("border-width", "6px 7px 8px 9px"),
            Map.entry("border-color", "#111111 #222222 #333333 #444444"),
            Map.entry("border-top", "6px solid #abcdef"),
            Map.entry("border-right", "6px solid #abcdef"),
            Map.entry("border-bottom", "6px solid #abcdef"),
            Map.entry("border-left", "6px solid #abcdef"),
            Map.entry("border", "7px solid #fedcba"),
            Map.entry("outline", "5px solid #010203"),
            Map.entry("border-image", "url(\"other.png\") 5 / 3px / 2px stretch"),
            Map.entry("animation", "pulse 3s ease-exit 2s 4 alternate backwards running"),
            Map.entry("word-wrap", "normal"),
            Map.entry("-webkit-line-clamp", "5"),
            Map.entry("-webkit-user-select", "all"),
            Map.entry("transition", "background-color 3s ease-exit 1s"),
            Map.entry("font", "300 22px/28px Serif"),
            Map.entry("border-style", "groove ridge inset outset"),
            Map.entry("border-radius", "5px 6px 7px 8px"),
            Map.entry("overflow", "clip"),
            Map.entry("place-content", "center"),
            Map.entry("place-items", "flex-end"),
            Map.entry("place-self", "flex-end start"),
            Map.entry("background-position", "right 5px bottom 6px"),
            Map.entry("margin-inline", "21px 22px"),
            Map.entry("margin-block", "23px"),
            Map.entry("padding-inline", "21px 22px"),
            Map.entry("padding-block", "23px 24px"),
            Map.entry("inset-inline", "21px"),
            Map.entry("inset-block", "23px 24px"),
            Map.entry("margin-inline-start", "31px"),
            Map.entry("margin-inline-end", "32px"),
            Map.entry("margin-block-start", "33px"),
            Map.entry("margin-block-end", "34px"),
            Map.entry("padding-inline-start", "31px"),
            Map.entry("padding-inline-end", "32px"),
            Map.entry("padding-block-start", "33px"),
            Map.entry("padding-block-end", "34px"),
            Map.entry("inset-inline-start", "31px"),
            Map.entry("inset-inline-end", "32px"),
            Map.entry("inset-block-start", "33px"),
            Map.entry("inset-block-end", "34px"),
            Map.entry("inline-size", "41px"),
            Map.entry("block-size", "42px"),
            Map.entry("min-inline-size", "43px"),
            Map.entry("max-inline-size", "44px"),
            Map.entry("min-block-size", "45px"),
            Map.entry("max-block-size", "46px"),
            Map.entry("all", "initial"));

    private static final ComputedStyle EVERYTHING = style(EVERY_LONGHAND);

    @Nested
    @DisplayName("names")
    class Names {

        @Test
        @DisplayName("are exactly the switch's: every arm is in the table, and every readable entry is an arm")
        void sameAsTheSwitch() throws IOException {
            var readable = new LinkedHashSet<String>();
            for (var name : Properties.names()) {
                if (Properties.isReadable(name)) {
                    readable.add(name);
                }
            }
            assertEquals(switchArms(), readable);
        }

        @Test
        @DisplayName("are each a property the engine reports it has")
        void knownToTheEngine() {
            for (var name : Properties.names()) {
                assertEquals(Properties.isReadable(name), ComputedStyle.isProperty(name), name);
            }
        }

        @Test
        @DisplayName("are found in any case, and a custom property or an unknown name is not")
        void lookup() {
            assertTrue(Properties.find("Padding-Top").isPresent());
            assertTrue(Properties.find("--gb-accent").isEmpty());
            assertTrue(Properties.find("float").isEmpty());
            assertTrue(Properties.isReadable("border-top-style"));
            assertThrows(IllegalArgumentException.class, () -> Properties.expand("float"));
        }
    }

    @Nested
    @DisplayName("each longhand's copier")
    class Copiers {

        static Stream<Longhand> longhands() {
            return Properties.longhands().stream();
        }

        @Test
        @DisplayName("is given a source in which every longhand differs from the initial style")
        void theSourceIsComplete() {
            for (var longhand : Properties.longhands()) {
                assertNotEquals(
                        ComputedStyle.INITIAL,
                        longhand.copy(EVERYTHING, ComputedStyle.INITIAL),
                        longhand.name() + " has no value in EVERY_LONGHAND, or its copier moves nothing");
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("longhands")
        @DisplayName("moves a value no other copier writes")
        void disjoint(Longhand longhand) {
            var copied = longhand.copy(EVERYTHING, ComputedStyle.INITIAL);
            var style = copied;
            for (var other : Properties.longhands()) {
                if (other != longhand) {
                    style = other.copy(ComputedStyle.INITIAL, style);
                }
            }
            assertEquals(copied, style, () -> "another copier overwrites what " + longhand.name() + " copied");
        }

        @Test
        @DisplayName("together move the whole style, so no component is missing a longhand")
        void complete() {
            var style = ComputedStyle.INITIAL;
            for (var longhand : Properties.longhands()) {
                style = longhand.copy(EVERYTHING, style);
            }
            assertEquals(EVERYTHING, style);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("longhands")
        @DisplayName("copies what a declaration of the property sets")
        void matchesTheDeclaration(Longhand longhand) {
            if (!longhand.readable()) {
                return;
            }
            var declaration = declarations(EVERY_LONGHAND).get(longhand.name());
            var declared =
                    ComputedStyle.INITIAL.applied(Map.of(longhand.name(), declaration), CssLength.Context.DEFAULT);
            assertEquals(declared, longhand.copy(EVERYTHING, ComputedStyle.INITIAL), longhand.name());
        }
    }

    @Nested
    @DisplayName("inheritance")
    class Inheritance {

        @Test
        @DisplayName("the inherited longhands are exactly what a child takes from its parent")
        void sameAsTheCascade() {
            var style = ComputedStyle.INITIAL;
            for (var longhand : Properties.longhands()) {
                if (longhand.inherited()) {
                    style = longhand.copy(EVERYTHING, style);
                }
            }
            assertEquals(ComputedStyle.of(Map.of(), CssLength.Context.DEFAULT, EVERYTHING), style);
        }

        @Test
        @DisplayName("colour and the font inherit, and the box does not")
        void examples() {
            assertTrue(Properties.longhand("color").orElseThrow().inherited());
            assertTrue(Properties.longhand("font-size").orElseThrow().inherited());
            assertFalse(Properties.longhand("padding-top").orElseThrow().inherited());
            assertFalse(Properties.longhand("transition-duration").orElseThrow().inherited());
        }
    }

    @Nested
    @DisplayName("shorthands and aliases")
    class Shorthands {

        static Stream<String> shorthands() {
            return Properties.names().stream()
                    .filter(name -> switch (Properties.find(name).orElseThrow()) {
                        case Shorthand _, Alias _ -> true;
                        case Longhand _, Inert _ -> false;
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {"justify-items: center", "justify-self: auto", "justify-items: legacy left"})
        @DisplayName("an inert property is read, and changes nothing")
        void inert(String declaration) {
            assertEquals(EVERYTHING, EVERYTHING.applied(declarations(declaration), CssLength.Context.DEFAULT));
            assertTrue(Properties.expand(declaration.substring(0, declaration.indexOf(':')))
                    .isEmpty());
        }

        @Test
        @DisplayName("each has a sample here")
        void sampled() {
            assertEquals(Set.copyOf(shorthands().toList()), SHORTHANDS.keySet());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("shorthands")
        @DisplayName("write nothing outside the longhands they expand to")
        void writeOnlyTheirLonghands(String name) {
            // Left to right, which is the direction the table writes a logical
            // property's sides in; `OrderAndDirectionTest` holds the other.
            var everything = EVERYTHING.presence(EVERYTHING.presence().direction(Direction.LTR));
            var declared =
                    everything.applied(declarations(name + ": " + SHORTHANDS.get(name)), CssLength.Context.DEFAULT);
            assertNotEquals(everything, declared, name + "'s sample is not read");
            assertEquals(declared, Properties.copy(name, declared, everything), name);
        }

        @Test
        @DisplayName("expand through other shorthands and aliases to longhands, in order")
        void expansion() {
            assertEquals(
                    List.of("padding-top", "padding-right", "padding-bottom", "padding-left"),
                    Properties.expand("padding").stream().map(Longhand::name).toList());
            assertEquals(
                    List.of("overflow-wrap"),
                    Properties.expand("word-wrap").stream().map(Longhand::name).toList());
            assertEquals(12, Properties.expand("border").size());
            assertTrue(
                    Properties.expand("border").stream().anyMatch(l -> l.name().equals("border-left-style")));
        }

        @Test
        @DisplayName("copy a shorthand as each of its longhands, which is what padding: inherit means")
        void copy() {
            var copied = Properties.copy("padding", EVERYTHING, ComputedStyle.INITIAL);
            assertEquals(EVERYTHING.padding(), copied.padding());
            assertEquals(ComputedStyle.INITIAL.margin(), copied.margin());
        }
    }

    @Test
    @DisplayName("a shorthand that writes nothing is refused")
    void emptyShorthand() {
        assertThrows(IllegalArgumentException.class, () -> new Shorthand("nothing", List.of()));
    }

    // --- helpers ----------------------------------------------------------------

    /// The property names the switch in `ComputedStyle.with` has an arm for, read
    /// from its source. Between the method's first line and its `default` arm,
    /// every `case "…"` label names one or more.
    private static Set<String> switchArms() throws IOException {
        var source = Files.readString(Path.of("src/main/java/dev/goldberry/css/ComputedStyle.java"));
        var start = source.indexOf("private ComputedStyle with(String property, List<Token> value");
        var end = source.indexOf("            default ->", start);
        assertTrue(start > 0 && end > start, "the switch moved; update this test");
        var names = new LinkedHashSet<String>();
        var label = Pattern.compile("^ {12}case ((?:\"[a-z-]+\"(?:,\\s*)?)+)\\s*->", Pattern.MULTILINE)
                .matcher(source.substring(start, end));
        while (label.find()) {
            var name = Pattern.compile("\"([a-z-]+)\"").matcher(label.group(1));
            while (name.find()) {
                names.add(name.group(1));
            }
        }
        return names;
    }

    private static Map<String, List<Token>> declarations(String css) {
        var map = new LinkedHashMap<String, List<Token>>();
        var rule = CssParser.parseSheet("probe { " + css + " }", ParseMode.STRICT, "PropertiesTest")
                .rules()
                .getFirst();
        for (var declaration : rule.declarations()) {
            map.put(declaration.property(), declaration.value());
        }
        return map;
    }

    private static ComputedStyle style(String css) {
        return ComputedStyle.INITIAL.applied(declarations(css), CssLength.Context.DEFAULT);
    }
}
