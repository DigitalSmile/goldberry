package dev.goldberry.html.view;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// The stylesheet `html-view` needs, for an application to add beside the toolkit's
/// own.
///
/// ```java
/// var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK));
/// sheets.add(HtmlStyles.stylesheet());
/// ```
///
/// **This is what a browser's default stylesheet is, written in Goldberry's own
/// CSS.** A renderer with no engine still has to supply the default styling of `h1`,
/// `p`, `a`, `code` and `table`, and it has to follow the active theme so that a page
/// is Nord-native in either mode. That is exactly what `html.css` is — a file rather
/// than a generator, because the rules are written in `var(--gb-*)` and the cascade
/// resolves them against whichever theme is on. There is nothing to regenerate on a
/// theme switch.
///
/// Separate from [dev.goldberry.markdown.view.MarkdownStyles] and
/// not folded into it: an application that renders notes and never a page should
/// carry rules for one kind of document, which is the same argument that keeps both
/// of them out of `controls.css`.
///
/// [CascadeLayer#TOOLKIT_BASE], the layer `controls.css` is in, so that an
/// application's own sheet overrides a page's appearance without `!important` — which
/// is what a layer is for.
///
/// Read more: [HTML view](https://goldberry.dev/docs/components/content.html#html-view).
public final class HtmlStyles {

    /// Where the rules live, beside this class.
    private static final String RESOURCE = "html.css";

    private HtmlStyles() {}

    /// The rules, parsed.
    ///
    /// Parsed on each call rather than cached, for the reason `MarkdownStyles` gives:
    /// an application asks once, at start-up, and a cache would be a lifetime to
    /// explain for no measurable saving.
    ///
    /// **The package holding this file is `opens`-ed to `:core`**, and it has to be:
    /// JPMS encapsulates resources as well as classes, so on the module path a `.css`
    /// beside a class is invisible to the module that reads it unless the package is
    /// open. A class-path test cannot show that — there are no modules
    /// there — which is why `HtmlStylesTest` asserts it against the compiled
    /// descriptor instead.
    public static Stylesheet stylesheet() {
        return Stylesheet.resource(CascadeLayer.TOOLKIT_BASE, HtmlStyles.class, RESOURCE);
    }

    /// The rules' text, as it ships.
    ///
    /// Public for the same reason
    /// [dev.goldberry.widgets.Controls#baseSource()] is: somebody
    /// overriding how a page looks should be able to read what they are overriding
    /// rather than guess at it.
    public static String source() {
        try (var in = HtmlStyles.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("the HTML stylesheet is missing from the jar: " + RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + RESOURCE, e);
        }
    }
}
