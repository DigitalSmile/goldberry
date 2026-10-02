package dev.goldberry.widgets;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;

/// Which scrollbars a window draws: an overlay thumb that fades, or a gutter
/// that is always there. One of the accessibility switches.
///
/// ```java
/// var sheets = Controls.stylesheets(theme, density, Scrollbars.ALWAYS);
/// ```
///
/// An application setting in the same shape as [Density]: a token stylesheet
/// added after the theme, in the [CascadeLayer#THEME] slot, which the controls
/// already read. There is no settings mechanism in the toolkit, so an application
/// passes this to [Controls#stylesheets(dev.goldberry.css.Theme, Density, Scrollbars)]
/// from its own preferences, exactly as it passes a density.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#scrollbars).
public enum Scrollbars {

    /// The default: a thin thumb over the content that widens on hover and
    /// fades when idle. The toolkit's own tokens, so applying it applies nothing.
    OVERLAY,

    /// "Always show scroll bars": a 12px gutter reserved beside the content,
    /// with a track that is always there.
    ALWAYS;

    /// The resource that puts [#ALWAYS] in force.
    static final String ALWAYS_RESOURCE = "scrollbars-always.css";

    /// The stylesheets that put this setting in force, to be added after the
    /// theme and the density. Empty for [#OVERLAY].
    public List<Stylesheet> stylesheets() {
        return this == OVERLAY ? List.of() : List.of(Stylesheet.parse(CascadeLayer.THEME, source()));
    }

    /// This setting's stylesheet text, as it ships — empty for [#OVERLAY].
    public String source() {
        if (this == OVERLAY) {
            return "";
        }
        try (InputStream in = Scrollbars.class.getResourceAsStream(ALWAYS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("the scrollbar gutter is missing from the jar: " + ALWAYS_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + ALWAYS_RESOURCE, e);
        }
    }
}
