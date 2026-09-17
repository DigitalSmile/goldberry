package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.widgets.Controls;

/// The toolkit's own stylesheets stay **type-first** ([ADR-0249]).
///
/// ADR-0152's saving is that a rule for `button` is never even looked at for a
/// `text`: the cascade buckets each rule by the type its rightmost compound
/// names, and only the rules that name none are checked against every element of
/// every kind. That saving is worth exactly as much as the stylesheet lets it be
/// — a sheet written entirely in classes puts every rule in the bucket nothing
/// can skip — and until this, nothing said so.
///
/// It is a lint in `:example` for [SupportedPropertyTest]'s reason: it reads the
/// toolkit's own sheets through the machinery that consumes them, and a sheet's
/// *shape* is the same kind of fact as a dropped declaration (ADR-0215,
/// ADR-0216).
class RuleBucketTest {

    /// The rules that name no type, and the whole list of them.
    ///
    /// An **exact set** rather than a threshold, on `ContrastTest`'s terms: a
    /// number somebody can raise is a number somebody raises, and a count that
    /// fails without naming anything is a count nobody reads. Every entry here
    /// has a reason it cannot be qualified, and a new one has to be argued for in
    /// a diff.
    ///
    /// All eight are unqualifiable by nature:
    ///
    /// - **The typography scale** (§1.4). `.display`, `.title`, `.heading`,
    ///   `.body`, `.body-strong`, `.caption` and `.mono` are ranks an application
    ///   puts on whatever it likes, so there is no type to name — that is what
    ///   makes them a scale rather than a widget's parts.
    /// - **`:root`**, which is the theme's token layer and matches the one node
    ///   with no parent.
    private static final List<String> UNTYPED_BY_NATURE = List.of(
            ".body",
            ".body-strong",
            ".caption",
            ".display",
            ".heading",
            ".mono",
            ".title",
            // A disabled control inside a disabled container does not fade
            // twice, and neither half of that names a kind: it is true of every
            // control in the catalog and of every container that can hold one
            // ([ADR-0379]).
            ":disabled :disabled",
            ":root");

    private static StyleResolver toolkit() {
        return new StyleResolver(new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK)));
    }

    @Test
    @DisplayName("every rule that could name a type does")
    void everyRuleThatCouldNameATypeDoes() {
        assertEquals(
                UNTYPED_BY_NATURE,
                toolkit().untypedSelectors(),
                "a rule in the toolkit's own stylesheets names no type, so the cascade has to"
                        + " check it against every element of every kind (ADR-0152). Qualify it"
                        + " — `text.tour-title` rather than `.tour-title` — or add it here with"
                        + " the reason it cannot be.");
    }

    /// The other direction, and the one that says the check is measuring
    /// something: nearly every rule *is* bucketed. A sweep asserting a list of
    /// eight would pass just as well against a sheet of eight rules.
    @Test
    @DisplayName("and that is almost all of them")
    void almostEverythingIsBucketed() {
        var resolver = toolkit();

        assertTrue(resolver.ruleCount() > 300, () -> "only " + resolver.ruleCount() + " rules were indexed");
        assertTrue(
                resolver.untypedRuleCount() * 20 < resolver.ruleCount(),
                () -> resolver.untypedRuleCount() + " of " + resolver.ruleCount()
                        + " rules name no type, which is more than one in twenty");
    }
}
