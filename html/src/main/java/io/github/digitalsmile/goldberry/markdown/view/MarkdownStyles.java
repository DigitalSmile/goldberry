package io.github.digitalsmile.goldberry.markdown.view;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;

/// The stylesheet `markdown-view` needs, for an application to add beside the
/// toolkit's own.
///
/// ```java
/// var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
/// sheets.add(MarkdownStyles.stylesheet());
/// ```
///
/// Not added automatically, and not folded into `controls.css`, for the reason this
/// module exists at all: `:widgets` does not know Markdown exists, and an
/// application that never renders a document should not carry rules for one. An
/// optional module brings its own stylesheet exactly as it brings its own natives
/// (ADR-0190).
///
/// [CascadeLayer#TOOLKIT_BASE], the layer `controls.css` is in, so that an
/// application's own sheet overrides a document's appearance without `!important` —
/// which is what a layer is for.
public final class MarkdownStyles {

    /// Where the rules live, beside this class.
    private static final String RESOURCE = "markdown.css";

    private MarkdownStyles() {}

    /// The rules, parsed.
    ///
    /// Parsed on each call rather than cached: an application asks once, at start-up,
    /// beside the four sheets it already asks `Controls` for, and a cache would be a
    /// lifetime to explain for no measurable saving.
    ///
    /// **The package holding this file is `opens`-ed to `:core`**, and it has to be:
    /// JPMS encapsulates resources as well as classes, so on the module path a `.css`
    /// beside a class is invisible to the module that reads it unless the package is
    /// open (ADR-0093). A class-path test cannot show that — there are no modules
    /// there — which is why `MarkdownStylesTest` asserts it against the compiled
    /// descriptor instead.
    public static Stylesheet stylesheet() {
        return Stylesheet.resource(CascadeLayer.TOOLKIT_BASE, MarkdownStyles.class, RESOURCE);
    }

    /// The rules' text, as it ships.
    ///
    /// Public for the same reason [io.github.digitalsmile.goldberry.widgets.Controls#baseSource()]
    /// is: someone overriding how a document looks should be able to read what they
    /// are overriding rather than guess at it.
    public static String source() {
        try (var in = MarkdownStyles.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("the Markdown stylesheet is missing from the jar: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + RESOURCE, e);
        }
    }
}
