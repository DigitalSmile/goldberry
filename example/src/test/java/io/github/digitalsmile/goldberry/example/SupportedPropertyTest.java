package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.StyleElement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.select.Selector;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;

/// Every declaration the toolkit's own stylesheets write is one the engine
/// applies — the property exists, **and** the value parses.
///
/// ## Why this exists
///
/// §8's subset is deliberately small and **an unsupported declaration is not an
/// error**: a stylesheet naming `box-shadow` before it is implemented should not
/// stop a window opening. So the engine logs at debug and carries on, which is
/// the right behaviour for an *application's* stylesheet and the wrong signal
/// for the toolkit's own — `border-bottom` shipped in `table-head`, drew nothing,
/// and the only trace was one debug line among thousands.
///
/// It lives here rather than in `:widgets` for two reasons: this module already
/// ships logback, so capturing what the cascade said costs no new dependency;
/// and §14 makes the gallery the visual regression corpus, which is where a
/// declaration that draws nothing is a screen photographed wrong.
///
/// It is `TokenClosureTest`'s argument applied to the other half of a
/// declaration. That one checks the `var()`s resolve; this one checks the engine
/// then does something with what they resolved to. Between them, a rule the
/// toolkit writes either does something or fails a test.
///
/// ## Both halves of "nothing happened"
///
/// It began (ADR-0215) as a check on the property name alone, and half a year of
/// stylesheets later two rules were found doing nothing for the *other* reason:
/// `group-box-title` wrote `border-radius: 7px 7px 0 0` and `select text-input`
/// wrote `background: none`, both properties the engine implements and neither a
/// value it took. Those are logged at **warn** rather than debug, which is louder
/// and was still not read — a start-up stream nobody is watching is a stream
/// nobody is watching at either level. So the lint reads both, and ADR-0216 is
/// the entry that made the values legal and this test the reason they stay so.
///
/// ## It asserts the behaviour rather than a copy of it
///
/// There is no list of supported properties here to drift out of step with the
/// engine. The sheets are resolved through the **real** `ComputedStyle`, and what
/// is asserted is that it reported nothing ignored — so a property added to the
/// engine tomorrow needs no edit here, and one removed is caught the same day.
class SupportedPropertyTest {

    /// What `em` and `rem` resolve against. Any context will do: the question is
    /// whether the *property* is known, and a length that fails to resolve is a
    /// different complaint with different words.
    private static final CssLength.Context CONTEXT = CssLength.Context.DEFAULT;

    /// Collects what the cascade said while a sheet was being resolved.
    private static final class Captured extends AppenderBase<ILoggingEvent> {

        private final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            lines.add(event.getFormattedMessage());
        }
    }

    /// Whether a complaint is about a property the engine does not implement,
    /// rather than about a **custom** one.
    ///
    /// `--gb-accent: …` reaches the same branch and is logged the same way, and
    /// it is not a fault: custom properties are the resolver's, computed for
    /// `var()` substitution before `ComputedStyle` ever sees a declaration
    /// ([ADR-0049]). Every theme is nothing but those, so a check that counted
    /// them would report a hundred and fifty-eight failures on a healthy tree —
    /// which is how this filter came to be written.
    private static boolean isUnsupportedProperty(String line) {
        return line.contains("ignoring unsupported property") && !line.contains("\"--");
    }

    /// Whether a complaint is about a **value** the engine would not take.
    ///
    /// The other way a rule does nothing: `border-radius: 7px 7px 0 0` named a
    /// property the engine has and a value it did not parse, so the declaration
    /// was dropped whole and the header drew square corners for as long as
    /// nobody read the log.
    private static boolean isDroppedValue(String line) {
        return line.contains("is not a valid value");
    }

    /// Every line saying a declaration did nothing, whichever half was at fault.
    ///
    /// @param sheets what is in force — the theme included, because a value half
    ///               of this check has to see what `var(--gb-surface)` stood for
    /// @param linted which of them is under scrutiny
    private static java.util.SortedSet<String> deadDeclarations(List<Stylesheet> sheets, List<Stylesheet> linted) {

        return new TreeSet<>(complaintsFrom(sheets, linted).stream()
                .filter(line -> isUnsupportedProperty(line) || isDroppedValue(line))
                .toList());
    }

    /// An element that exists only to make one rule apply.
    ///
    /// The value half needs the **resolver** and not just [ComputedStyle]: every
    /// colour in the toolkit is `var(--gb-something)`, substitution is the
    /// resolver's, and a raw declaration handed straight to the engine is a
    /// `var()` it has never been asked to understand — 164 false failures on a
    /// healthy tree, which is how this class came to build elements.
    ///
    /// One probe per compound of the selector, chained by [#parent], so
    /// `select text-input` is a `text-input` inside a `select`. Both combinators
    /// are satisfied by a direct parent, and the leftmost probe has no parent —
    /// which is what makes it `:root` and is how the theme's custom properties
    /// reach the rest of the chain.
    private record Probe(Selector.Compound compound, StyleElement parent) implements StyleElement {

        @Override
        public String type() {
            return compound.type();
        }

        @Override
        public String id() {
            return compound.id();
        }

        @Override
        public java.util.Set<String> classes() {
            return java.util.Set.copyOf(compound.classes());
        }

        @Override
        public boolean hasState(Selector.PseudoClass state) {
            return compound.pseudoClasses().contains(state);
        }
    }

    /// The element `selector` was written for, as a chain of [Probe]s.
    ///
    /// One per selector rather than one per rule: `.a, .b { … }` is one rule with
    /// two selectors, and a declaration only reaches the engine through an
    /// element that matches — so a rule whose second selector is the live one
    /// would go unchecked if only the first were built.
    private static StyleElement probeFor(Selector selector) {
        StyleElement element = null;
        // `parts` is rightmost first, so walking it backwards builds the chain
        // from the root down and ends holding the element the rule is *about*.
        var parts = selector.parts();
        for (var i = parts.size() - 1; i >= 0; i--) {
            element = new Probe(parts.get(i).compound(), element);
        }
        return element;
    }

    /// Resolves every rule in `linted` the way the cascade would and returns what
    /// the engine complained about.
    ///
    /// **Every rule**, because a declaration is only reached when it is applied:
    /// a sheet that is merely parsed says nothing about whether the engine knows
    /// `border-bottom` or takes `7px 7px 0 0`.
    private static List<String> complaintsFrom(List<Stylesheet> sheets, List<Stylesheet> linted) {
        var logger = (Logger) org.slf4j.LoggerFactory.getLogger(ComputedStyle.class);
        // A dropped value is reported **once per JVM** so that a typo cannot
        // repeat itself sixty times a second (the dedup on `ComputedStyle`).
        // Another test that had already resolved the same sheet would otherwise
        // leave this one reading an empty log and passing on it.
        ComputedStyle.forgetReportedDrops();
        var resolver = new StyleResolver(sheets);
        var captured = new Captured();
        captured.start();
        var previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(captured);
        try {
            for (var sheet : linted) {
                for (var rule : sheet.rules()) {
                    for (var selector : rule.selectors()) {
                        ComputedStyle.of(resolver.resolve(probeFor(selector)), CONTEXT);
                    }
                }
            }
        } finally {
            logger.detachAppender(captured);
            logger.setLevel(previous);
            captured.stop();
        }
        return captured.lines;
    }

    @Test
    @DisplayName("no rule the catalog ships is one the engine drops, by name or by value")
    void theCatalogWritesOnlyDeclarationsTheEngineApplies() {
        var sheets = List.copyOf(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));

        var dead = deadDeclarations(sheets, sheets);

        assertTrue(
                dead.isEmpty(),
                () -> "the toolkit's stylesheets write " + dead.size()
                        + " declaration(s) the engine drops on the floor, so the rule"
                        + " does nothing and nothing reads the line that says so: " + dead);
    }

    @Test
    @DisplayName("and the showcase's own stylesheet does not either")
    void theShowcaseWritesOnlySupportedProperties() {
        // The gallery is the visual regression corpus (§14), so a dead
        // declaration in it is a screen that has been photographed wrong.
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css");
        // Under the toolkit's sheets, which is where the showcase runs: its own
        // rules read the theme's custom properties like everybody else's.
        var inForce = new java.util.ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        inForce.add(sheet);

        var dead = deadDeclarations(List.copyOf(inForce), List.of(sheet));

        assertTrue(dead.isEmpty(), () -> "the showcase writes " + dead);
    }

    @Test
    @DisplayName("and the check itself works — a known-bad property is caught")
    void theCheckCatchesOne() {
        // Without this, a change to the log's wording or to the appender wiring
        // would make the two tests above pass by seeing nothing at all.
        var bad = Stylesheet.parse(CascadeLayer.APPLICATION, "table-head { border-bottom: 1px solid #fff }");

        assertTrue(
                complaintsFrom(List.of(bad), List.of(bad)).stream()
                        .anyMatch(SupportedPropertyTest::isUnsupportedProperty),
                "the check saw nothing wrong with `border-bottom`, which the engine"
                        + " does not implement — so it would see nothing wrong with anything");
    }

    @Test
    @DisplayName("and so does the value half — a property that exists with a value that does not")
    void theCheckCatchesABadValue() {
        // The half added by ADR-0216, guarded the same way: `border-radius` is a
        // property the engine has, and `50%` is a value it refuses because the
        // box has no size until Yoga has run.
        var bad = Stylesheet.parse(CascadeLayer.APPLICATION, "group-box-title { border-radius: 50% }");

        assertTrue(
                complaintsFrom(List.of(bad), List.of(bad)).stream().anyMatch(SupportedPropertyTest::isDroppedValue),
                "the check saw nothing wrong with `border-radius: 50%`, which the engine"
                        + " drops — so a rule with a bad value would sail past it");
    }
}
