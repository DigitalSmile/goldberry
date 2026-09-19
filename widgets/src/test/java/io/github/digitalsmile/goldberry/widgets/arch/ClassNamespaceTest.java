package io.github.digitalsmile.goldberry.widgets.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.digitalsmile.goldberry.css.StyleRule;
import io.github.digitalsmile.goldberry.stats.FrameStats;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.CatalogMarkup;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.text.TextRank;

/// The two sets of class names, and that they stay apart ([ADR-0414]).
///
/// §1.4's type ranks are styled by a class selector with **no type on it**,
/// because a rank applies to anything: `.heading` is 15px/600 on whatever carries
/// it. A widget's own classes are the opposite — `selected` means nothing until
/// something says `tree-row.selected`. So the two sets live in one namespace with
/// one of them unqualified, and a widget that mints a rank's name picks the rank
/// up whatever else its own rule says.
///
/// That happened twice. A `hud` reading called `display` drew at 28px, was
/// renamed, and nothing stopped the next one; the next one was `tree-row.heading`,
/// which set a colour while `.heading` set the font behind it, and every branch of
/// a leaf-only tree drew at 15px/600 in a row with a fixed height. Neither was
/// visible in the sheet, in a test, or in a review.
///
/// ## Why the reserved set is read and not written down
///
/// Every set below is **derived** — the ranks from [TextRank], the unqualified
/// rules from the base sheet parsed by the real parser, the widgets from the
/// inflater's registry. A hand-kept list of seven names is a list that is right
/// until §1.4 gains an eighth, and the failure of a stale list is the check
/// passing.
class ClassNamespaceTest {

    /// The names §1.4 reserves, as CSS spells them.
    private static Set<String> ranks() {
        var names = new TreeSet<String>();
        for (var rank : TextRank.values()) {
            names.add(rank.cssClass());
        }
        return names;
    }

    private static List<StyleRule> baseRules() {
        return Controls.baseStylesheet().rules();
    }

    /// Every class in a selector that also names a type, anywhere in the chain —
    /// `tree-row.selected` and `checkbox:hover check-indicator.filled` alike.
    ///
    /// Per compound rather than per selector: a rule's rightmost compound may
    /// name a type while an ancestor compound carries a bare class, and it is the
    /// compound that decides whether a class is qualified.
    private static Set<String> classesQualifiedByAType() {
        var found = new TreeSet<String>();
        for (var rule : baseRules()) {
            for (var selector : rule.selectors()) {
                for (var part : selector.parts()) {
                    if (part.compound().type() != null) {
                        found.addAll(part.compound().classes());
                    }
                }
            }
        }
        return found;
    }

    /// Every class the base sheet styles with **nothing** qualifying it: one
    /// compound, one class, no type, no id, no pseudo-class.
    ///
    /// This is what "applies to anything" means operationally, and it is the
    /// definition the collision turns on rather than a proxy for it.
    private static Set<String> unqualifiedClasses() {
        var found = new TreeSet<String>();
        for (var rule : baseRules()) {
            for (var selector : rule.selectors()) {
                if (selector.parts().size() != 1) {
                    continue;
                }
                var compound = selector.parts().getFirst().compound();
                if (compound.type() == null
                        && compound.id() == null
                        && compound.pseudoClasses().isEmpty()
                        && compound.classes().size() == 1) {
                    found.add(compound.classes().getFirst());
                }
            }
        }
        return found;
    }

    @Nested
    @DisplayName("the reserved set, which is derived rather than listed")
    class TheReservedSet {

        @Test
        @DisplayName("the base sheet's unqualified class rules are exactly §1.4's ranks")
        void theUnqualifiedRulesAreTheRanks() {
            // The convention stated as an assertion. A rule with a bare class
            // selector in the base layer reaches every element in every
            // application, so the base layer gets to write exactly seven of them
            // and they are the ones §1.4 names. An eighth is either a rank — and
            // belongs in `TextRank` and in the table — or a widget class that
            // forgot its type.
            assertEquals(
                    ranks(),
                    unqualifiedClasses(),
                    "the base sheet styles a class with no type on it that is not one of §1.4's"
                            + " ranks, so it reaches every element in every application that happens to"
                            + " carry the name. Either it is a rank and belongs in TextRank, or it wants"
                            + " the type it is about in front of it.");
        }

        @Test
        @DisplayName("TextRank and the design system's table agree on the count")
        void thereAreSevenRanks() {
            // Guards the sweep rather than the code: a `TextRank` that lost its
            // constants would make every assertion here vacuously true, and
            // §1.4's table has seven rows.
            assertEquals(7, ranks().size(), () -> "§1.4 has seven ranks; TextRank has " + ranks());
        }
    }

    @Nested
    @DisplayName("nothing a widget mints is a reserved name")
    class NoWidgetMintsARank {

        /// The check the sheet can answer on its own, and the one that was failing
        /// when this was written: `tree-row.heading` set a colour while `.heading`
        /// set the font-size, line-height and weight behind it.
        ///
        /// It is the cheapest of the three and the only one that reaches a widget
        /// **part** — `tree-row` is not a registered node name and no inflater
        /// builds one, so the sweep below cannot see it and this can.
        @Test
        @DisplayName("no class is written both unqualified and beside a type")
        void noClassIsWrittenBothWays() {
            var collisions = new TreeSet<>(unqualifiedClasses());
            collisions.retainAll(classesQualifiedByAType());

            assertTrue(
                    collisions.isEmpty(),
                    () -> "these class names are styled both as a bare class and beside a type: "
                            + collisions + ". The bare rule reaches the typed one's element whatever the"
                            + " typed rule says, so the widget is wearing a design-system rank it did not"
                            + " ask for. Rename the widget's class.");
        }

        /// The sweep the CSS cannot do: a widget class that collides with a rank
        /// and that **nobody styled**. There is no rule to read, the widget simply
        /// draws at the rank's size, and the only place the name exists is the
        /// widget.
        @ParameterizedTest(name = "{0}")
        @MethodSource("io.github.digitalsmile.goldberry.widgets.CatalogMarkup#types")
        @DisplayName("no registered widget's classes() is a reserved name")
        void noRegisteredWidgetCarriesARank(String type) {
            if (!(CatalogMarkup.inflate(type, "") instanceof Styled styled)) {
                return;
            }
            var carried = new LinkedHashSet<>(styled.classes());
            // `classes(FrameStats)` too, which is the other half of a node's set
            // and the half a `hud` reading's level comes through -- so a rank
            // minted from the frame would otherwise be invisible here.
            carried.addAll(styled.classes(FrameStats.none()));
            carried.retainAll(ranks());

            assertTrue(
                    carried.isEmpty(),
                    () -> "<" + type + "> carries " + carried + ", which is one of §1.4's type ranks, so it"
                            + " renders at that rank's size and weight whatever its own rule says");
        }
    }

    /// The shape the `hud`'s `display` actually had, and the one both sweeps above
    /// would still miss.
    ///
    /// The reading's name was not a literal in a `classes()` body and the widget
    /// was not a registered node — it was an enum constant on a **part**, read
    /// through `cssClass()`. So the set of names the catalog can mint is not the
    /// set of names anything instantiates, and the enums are where to look.
    @Nested
    @DisplayName("and nothing an enum mints is either")
    class NoEnumMintsARank {

        /// Every enum in this module that publishes a `cssClass()`.
        ///
        /// From the source tree rather than a classpath scan, for
        /// [SemanticsSweepTest]'s reason: the question is about *this module's*
        /// catalog, and the failure message should name a file somebody can open.
        private static List<Class<?>> cssClassEnums() throws IOException {
            var found = new ArrayList<Class<?>>();
            for (var name : SourceTree.binaryNames(Path.of("src/main/java"))) {
                Class<?> type;
                try {
                    type = Class.forName(name, false, ClassNamespaceTest.class.getClassLoader());
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    // A file whose top-level type is named differently, or one the
                    // module system does not open to this test. Neither can be an
                    // enum this rule reaches.
                    continue;
                }
                collectEnums(type, found);
            }
            return found;
        }

        private static void collectEnums(Class<?> type, List<Class<?>> into) {
            if (type.isEnum() && declaresCssClass(type)) {
                into.add(type);
            }
            // Nested, because `Reading.Level` is where the `ok`/`near`/`over`
            // classes live and a top-level sweep would stop one short of it.
            for (var nested : type.getDeclaredClasses()) {
                collectEnums(nested, into);
            }
        }

        private static boolean declaresCssClass(Class<?> type) {
            for (var method : type.getDeclaredMethods()) {
                if (method.getName().equals("cssClass") && method.getParameterCount() == 0) {
                    return true;
                }
            }
            return false;
        }

        /// `constant.cssClass()`, looked up on the **enum type** rather than on
        /// the constant's own class.
        ///
        /// A `hud` reading's constants each have a body, so each is an
        /// anonymous subclass — `Reading$1` — which declares no `cssClass` of its
        /// own and made this throw. The enums with bodies are exactly the ones
        /// worth sweeping, so getting this wrong loses the interesting half.
        private static String cssClassOf(Class<?> type, Object constant) throws ReflectiveOperationException {
            var method = type.getDeclaredMethod("cssClass");
            method.setAccessible(true);
            return (String) method.invoke(constant);
        }

        @Test
        @DisplayName("no enum that names a CSS class names a reserved one, except the ranks themselves")
        void onlyTextRankMintsARank() throws Exception {
            var offenders = new TreeSet<String>();
            var swept = 0;
            for (var type : cssClassEnums()) {
                // **The one exception, and it is the definition rather than a
                // weakening.** `TextRank` *is* §1.4's ranks: `text style="title"`
                // is how an application asks for one, so an enum whose whole job
                // is to spell them has to spell them.
                if (type == TextRank.class) {
                    continue;
                }
                swept++;
                for (var constant : type.getEnumConstants()) {
                    var name = cssClassOf(type, constant);
                    if (ranks().contains(name)) {
                        offenders.add(type.getSimpleName() + "." + constant + " → \"" + name + "\"");
                    }
                }
            }

            final var found = swept;
            assertTrue(
                    found >= 4,
                    () -> "only " + found + " enums mint a CSS class; the sweep is probably"
                            + " reading the wrong source tree");
            assertTrue(
                    offenders.isEmpty(),
                    () -> "these enum constants mint one of §1.4's type ranks as a class name: " + offenders
                            + ". This is the `hud` reading called `display` that drew at 28px — the name"
                            + " never appears in a classes() body, so nothing else here would catch it.");
        }
    }
}
