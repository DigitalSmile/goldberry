package io.github.digitalsmile.goldberry.css;

import java.util.List;
import java.util.Objects;

/// A parsed stylesheet and the layer it belongs to.
///
/// @param layer where its rules sit in the cascade
/// @param rules in source order
public record Stylesheet(CascadeLayer layer, List<StyleRule> rules) {

    public Stylesheet {
        Objects.requireNonNull(layer, "layer");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    /// Parses `css` into a stylesheet in `layer`.
    ///
    /// @throws CssSyntaxException if the text is not in the supported subset
    public static Stylesheet parse(CascadeLayer layer, String css) {
        return new Stylesheet(layer, CssParser.parse(css));
    }

    /// An empty stylesheet — what a hot reload falls back to before the first
    /// good parse.
    public static Stylesheet empty(CascadeLayer layer) {
        return new Stylesheet(layer, List.of());
    }
}
