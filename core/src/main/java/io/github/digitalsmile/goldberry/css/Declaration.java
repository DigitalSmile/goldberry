package io.github.digitalsmile.goldberry.css;

import java.util.List;
import java.util.Objects;

/// One `property: value` pair, with the value left as tokens.
///
/// The value is **not** parsed here. What `4px 8px` means depends entirely on
/// which property it belongs to — two lengths for `padding`, nonsense for
/// `color` — and a `var()` in it cannot be resolved until the cascade knows which
/// element the declaration landed on. Both of those are later; keeping the tokens
/// is what lets them happen later.
///
/// @param property  the property name, lowercased unless it is a custom property
/// @param value     the value's tokens, with leading and trailing whitespace
///                  removed and no `!important` on the end
/// @param important whether the declaration was marked `!important`
/// @param line      1-based line the property name was on, for error messages
/// @param column    1-based column
public record Declaration(
        String property, List<Token> value, boolean important, int line, int column) {

    public Declaration {
        Objects.requireNonNull(property, "property");
        value = List.copyOf(Objects.requireNonNull(value, "value"));
    }

    /// Whether this declares a custom property — `--gb-accent: #88c0d0`.
    ///
    /// Custom properties cascade like any other declaration but resolve
    /// differently: their value is kept as written and substituted into `var()`
    /// rather than interpreted (§8).
    public boolean isCustomProperty() {
        return property.startsWith("--");
    }

    @Override
    public String toString() {
        var text = new StringBuilder(property).append(": ");
        value.forEach(t -> text.append(t.type() == TokenType.WHITESPACE ? " " : t.text()));
        if (important) {
            text.append(" !important");
        }
        return text.toString();
    }
}
