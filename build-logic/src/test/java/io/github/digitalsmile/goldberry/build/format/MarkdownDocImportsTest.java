package io.github.digitalsmile.goldberry.build.format;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("imports a /// comment uses")
class MarkdownDocImportsTest {

    @Nested
    @DisplayName("a reference")
    class References {

        /** One {@code ///} line, and a name it must be read as referring to. */
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(delimiter = '|', textBlock = """
                See [ActionRegistry].                         | ActionRegistry
                See [ActionRegistry#strict()].                | ActionRegistry
                See [the registry][ActionRegistry].           | ActionRegistry
                See [ActionRegistry][].                       | ActionRegistry
                See [Outer.Inner].                            | Outer
                See [Paint#fill(Brush, Rect)].                | Brush
                See [Paint#fill(Brush, Rect)].                | Rect
                See [#fill(Brush)].                           | Brush
                See {@link Registry#get(Key) the registry}.   | Registry
                See {@link Registry#get(Key) the registry}.   | Key
                See {@linkplain Registry the registry}.       | Registry
                The default is {@value Limits#MAX}.           | Limits
                @throws BlendException if the call fails      | BlendException
                @see ScrollState                              | ScrollState
                """)
        void isFound(String comment, String name) {
            assertTrue(MarkdownDocImports.referencedNames("/// " + comment + "\nclass A {}\n").contains(name));
        }

        @Test
        @DisplayName("is not a hyperlink")
        void hyperlinkIsNot() {
            var names = MarkdownDocImports.referencedNames("/// [Registry](https://example.com)\n");
            assertFalse(names.contains("Registry"), names::toString);
        }

        @Test
        @DisplayName("is not inside an inline code span")
        void codeSpanIsNot() {
            var names = MarkdownDocImports.referencedNames("/// Write `[Registry]` to link it.\n");
            assertFalse(names.contains("Registry"), names::toString);
        }

        @Test
        @DisplayName("is not inside a fenced code block")
        void fenceIsNot() {
            var names = MarkdownDocImports.referencedNames("""
                    /// ```
                    /// var r = registries[Registry];
                    /// ```
                    /// After the fence, [Scope].
                    class A {}
                    """);
            assertEquals(Set.of("Scope"), names);
        }

        @Test
        @DisplayName("is not in a line comment or in code")
        void codeIsNot() {
            var names = MarkdownDocImports.referencedNames("""
                    // See [Registry].
                    int[] a = values[Index];
                    /** {@link Other} is Javadoc's, which the remover already reads. */
                    """);
            assertTrue(names.isEmpty(), names::toString);
        }

        @Test
        @DisplayName("does not carry a fence past the end of its comment")
        void unclosedFenceEnds() {
            var names = MarkdownDocImports.referencedNames("""
                    /// ```
                    /// never closed
                    class A {}
                    /// See [Registry].
                    class B {}
                    """);
            assertEquals(Set.of("Registry"), names);
        }
    }

    @Nested
    @DisplayName("restoring")
    class Restoring {

        private static final String BEFORE = """
                package p;

                import java.util.List;

                import a.ActionRegistry;
                import a.Unused;
                import b.Text;

                /// Shaped like [ActionRegistry], and [Text] is what it shows.
                final class Icons {
                    List<String> names;
                }
                """;

        /**
         * What palantir's remover produces from {@link #BEFORE}: every import only
         * a {@code ///} comment uses is gone, along with the one nothing uses.
         */
        private static final String AFTER = """
                package p;

                import java.util.List;

                /// Shaped like [ActionRegistry], and [Text] is what it shows.
                final class Icons {
                    List<String> names;
                }
                """;

        @Test
        @DisplayName("puts back what a /// comment refers to, and nothing else")
        void putsBackReferenced() {
            assertEquals("""
                    package p;

                    import java.util.List;
                    import a.ActionRegistry;
                    import b.Text;

                    /// Shaped like [ActionRegistry], and [Text] is what it shows.
                    final class Icons {
                        List<String> names;
                    }
                    """, MarkdownDocImports.restore(BEFORE, AFTER));
        }

        @Test
        @DisplayName("leaves the output alone when nothing was lost")
        void nothingLost() {
            assertSame(AFTER, MarkdownDocImports.restore(AFTER, AFTER));
        }

        @Test
        @DisplayName("lets a truly unused import go")
        void unusedStaysGone() {
            var before = """
                    package p;

                    import a.Unused;

                    /// Nothing here links anywhere.
                    final class A {}
                    """;
            var after = """
                    package p;

                    /// Nothing here links anywhere.
                    final class A {}
                    """;
            assertSame(after, MarkdownDocImports.restore(before, after));
        }

        @Test
        @DisplayName("goes after the package when no import survived")
        void afterPackage() {
            var before = """
                    package p;

                    import a.Registry;

                    /// See [Registry].
                    final class A {}
                    """;
            var after = """
                    package p;

                    /// See [Registry].
                    final class A {}
                    """;
            assertEquals("""
                    package p;

                    import a.Registry;

                    /// See [Registry].
                    final class A {}
                    """, MarkdownDocImports.restore(before, after));
        }

        @Test
        @DisplayName("does not restore a name a kept import already answers")
        void noClash() {
            var before = """
                    package p;

                    import a.Registry;
                    import b.Registry;

                    /// See [Registry].
                    final class A { Registry r; }
                    """;
            var after = """
                    package p;

                    import b.Registry;

                    /// See [Registry].
                    final class A { Registry r; }
                    """;
            assertSame(after, MarkdownDocImports.restore(before, after));
        }

        @Test
        @DisplayName("ignores static and wildcard imports")
        void staticAndWildcard() {
            var before = """
                    package p;

                    import static a.Limits.MAX;
                    import b.*;

                    /// See [MAX] and [b].
                    final class A {}
                    """;
            var after = """
                    package p;

                    /// See [MAX] and [b].
                    final class A {}
                    """;
            assertSame(after, MarkdownDocImports.restore(before, after));
        }
    }
}
