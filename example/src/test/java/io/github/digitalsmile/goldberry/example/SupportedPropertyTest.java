package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Density;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.css.parse.Token;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Every property the toolkit's own stylesheets write is one the engine
/// implements.
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
/// declaration. That one checks the values resolve; this one checks the
/// properties exist. Between them, a rule the toolkit writes either does
/// something or fails a test.
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
        return line.contains("ignoring unsupported property")
                && !line.contains("\"--");
    }

    /// Resolves every rule in `sheets` against the engine and returns what it
    /// complained about.
    ///
    /// **Every rule**, because a property is only reached when its declaration is
    /// applied: a sheet that is merely parsed says nothing about whether the
    /// engine knows `border-bottom`.
    private static List<String> complaintsFrom(List<Stylesheet> sheets) {
        var logger = (Logger) org.slf4j.LoggerFactory.getLogger(ComputedStyle.class);
        var captured = new Captured();
        captured.start();
        var previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(captured);
        try {
            for (var sheet : sheets) {
                for (var rule : sheet.rules()) {
                    // One style per rule, built the way the cascade builds one:
                    // a declaration is only *reached* when it is applied, so a
                    // sheet that is merely parsed says nothing about whether the
                    // engine knows `border-bottom`.
                    var declarations = new java.util.LinkedHashMap<String, List<Token>>();
                    for (var declaration : rule.declarations()) {
                        declarations.put(declaration.property(), declaration.value());
                    }
                    ComputedStyle.of(declarations, CONTEXT);
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
    @DisplayName("no rule the catalog ships names a property the engine does not implement")
    void theCatalogWritesOnlySupportedProperties() {
        var sheets = Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR);

        var ignored = new TreeSet<>(complaintsFrom(List.copyOf(sheets)).stream()
                .filter(SupportedPropertyTest::isUnsupportedProperty)
                .toList());

        assertTrue(ignored.isEmpty(),
                () -> "the toolkit's stylesheets write " + ignored.size()
                        + " property/properties the engine drops on the floor, so the rule"
                        + " does nothing and nothing says so: " + ignored);
    }

    @Test
    @DisplayName("and the showcase's own stylesheet does not either")
    void theShowcaseWritesOnlySupportedProperties() {
        // The gallery is the visual regression corpus (§14), so a dead
        // declaration in it is a screen that has been photographed wrong.
        var sheet = Stylesheet.resource(CascadeLayer.APPLICATION,
                Showcase.class, "showcase.css");

        var ignored = new TreeSet<>(complaintsFrom(List.of(sheet)).stream()
                .filter(SupportedPropertyTest::isUnsupportedProperty)
                .toList());

        assertTrue(ignored.isEmpty(), () -> "the showcase writes " + ignored);
    }

    @Test
    @DisplayName("and the check itself works — a known-bad property is caught")
    void theCheckCatchesOne() {
        // Without this, a change to the log's wording or to the appender wiring
        // would make the two tests above pass by seeing nothing at all.
        var bad = Stylesheet.parse(CascadeLayer.APPLICATION,
                "table-head { border-bottom: 1px solid #fff }");

        assertTrue(complaintsFrom(List.of(bad)).stream()
                        .anyMatch(SupportedPropertyTest::isUnsupportedProperty),
                "the check saw nothing wrong with `border-bottom`, which the engine"
                        + " does not implement — so it would see nothing wrong with anything");
    }
}
