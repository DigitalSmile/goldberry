package io.github.digitalsmile.goldberry.css;

import static io.github.digitalsmile.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.AnnotatedType;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.select.Selector;

/// [StyleElement]'s three nullable members, and that they say so
/// ([ADR-0413]).
///
/// Two kinds of assertion, and both are needed.
///
/// The **annotation** assertions are unusual and deliberate: nothing else in this
/// repository tests for the presence of an annotation. They are here because the
/// thing that was wrong for three hundred ADRs was not a behaviour — every caller
/// already handled the null — but a *signature*, and a signature that lies is
/// invisible to every test that exercises the code. `css.lint` was left unmarked
/// to accommodate this one, which is a checker switched off to keep a comment and
/// a declaration disagreeing. An assertion that the annotation is on the method is
/// the only thing that catches somebody taking it off again to quieten NullAway.
///
/// The **behavioural** assertions cover the four sites the annotation turned up.
/// None of them was broken, and that is worth writing down rather than assuming:
/// the null path through the cascade is live on every frame the showcase draws,
/// because a composition wrapper has no CSS type.
class StyleElementNullnessTest {

    private static Stylesheet sheet(String css) {
        return Stylesheet.parse(CascadeLayer.APPLICATION, css);
    }

    /// Whether a method's return type carries JSpecify's `@Nullable`.
    ///
    /// [AnnotatedType] rather than [java.lang.reflect.Method#getAnnotations], and
    /// the difference is the whole reason this helper exists: JSpecify's
    /// `@Nullable` is a `TYPE_USE` annotation, so it annotates the *type* in the
    /// return position and never appears among the method's own annotations.
    private static boolean returnIsNullable(Class<?> owner, String method) throws NoSuchMethodException {
        return owner.getMethod(method).getAnnotatedReturnType().isAnnotationPresent(Nullable.class);
    }

    @Nested
    @DisplayName("the interface says what its javadoc says")
    class TheSignatures {

        @Test
        @DisplayName("type(), id() and parent() are each @Nullable")
        void theThreeMembersAreAnnotated() throws NoSuchMethodException {
            // Each of these documented "or null" and declared a plain `String` or
            // `StyleElement`, inside a package that is `@NullMarked` — so NullAway
            // read all three as non-null and an implementation written in a marked
            // package could not override them honestly.
            assertTrue(returnIsNullable(StyleElement.class, "type"), "StyleElement.type()");
            assertTrue(returnIsNullable(StyleElement.class, "id"), "StyleElement.id()");
            assertTrue(returnIsNullable(StyleElement.class, "parent"), "StyleElement.parent()");
        }

        @Test
        @DisplayName("Selector.Compound's type and id are @Nullable too, for the same reason")
        void theCompoundIsAnnotated() throws NoSuchMethodException {
            // The other half of the same lie, and the one that was easy to miss:
            // `css.select` is marked as well, the parser that writes the nulls is
            // not, so nothing on either side of the boundary could see it. The
            // `*` of `* > .a` is a compound with a null type, and `isUniversal`
            // is written in terms of it.
            assertTrue(returnIsNullable(Selector.Compound.class, "type"), "Compound.type()");
            assertTrue(returnIsNullable(Selector.Compound.class, "id"), "Compound.id()");
        }

        @Test
        @DisplayName("classes() is not, because empty is how it says nothing")
        void classesIsNotAnnotated() throws NoSuchMethodException {
            // Asserted so that a sweep does not annotate the fourth member by
            // symmetry. An empty set and a null set would be two spellings of one
            // state, and every caller iterates.
            assertFalse(returnIsNullable(StyleElement.class, "classes"), "StyleElement.classes()");
        }
    }

    @Nested
    @DisplayName("the null path the annotation exposed")
    class TheCascade {

        private static final String SHEET = """
                button { color: #ff0000 }
                .wrapper { padding: 4px }
                """;

        /// `StyleResolver.candidatesFor` took a non-null `String type` and was
        /// handed `element.type()` from three call sites. Its body opened with
        /// `if (type == null) return untyped`, so the behaviour was right and only
        /// the declaration was wrong — which is exactly the shape a signature lie
        /// takes: correct code that no checker can confirm.
        @Test
        @DisplayName("a node with no type is cascaded against the untyped rules")
        void aTypelessNodeGetsTheUntypedRules() {
            var resolver = new StyleResolver(List.of(sheet(SHEET)));
            var wrapper = element(".wrapper");
            assertNull(wrapper.type(), "a composition node has no CSS type");

            var resolved = resolver.resolve(wrapper);

            assertTrue(resolved.containsKey("padding"), () -> "untyped rules reach it: " + resolved);
            assertFalse(resolved.containsKey("color"), () -> "typed rules do not: " + resolved);
        }

        /// The second of the three, and the one a test would otherwise never
        /// reach: `resolveStarting` short-circuits on a sheet with no
        /// `@starting-style` at all, which is nearly every sheet.
        @Test
        @DisplayName("and against the untyped starting rules")
        void aTypelessNodeGetsTheUntypedStartingRules() {
            var resolver = new StyleResolver(List.of(sheet("""
                    .wrapper { opacity: 1 }
                    @starting-style { .wrapper { opacity: 0 } }
                    """)));

            var starting = resolver.resolveStarting(element(".wrapper"));

            assertNotNull(starting, "a starting rule with no type matches a node with no type");
            assertTrue(starting.containsKey("opacity"), () -> starting.toString());
        }

        /// `:root` is matched as `parent() == null` rather than by asking the
        /// element, which is what makes the third nullable member load-bearing
        /// rather than incidental: a theme is a `:root` layer and the only thing
        /// that identifies a root is a null here.
        @Test
        @DisplayName("a null parent is what makes an element the root, so a theme reaches it")
        void aNullParentIsTheRoot() {
            var resolver = new StyleResolver(List.of(sheet(":root { --gb-accent: #88c0d0 }")));
            var root = element(".wrapper");
            var child = element("button");
            root.with(child);

            assertNull(root.parent(), "the root's parent is the null this annotates");
            assertNotNull(resolver.customProperty(root, "--gb-accent"), "the root matches :root");
            assertNotNull(resolver.customProperty(child, "--gb-accent"), "and its child inherits");
        }
    }
}
