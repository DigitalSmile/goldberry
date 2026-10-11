package dev.goldberry.css.property;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;

/// The keywords every property takes: `inherit`, `initial`, `unset`, `revert`
/// and `revert-layer`.
///
/// `revert` and `revert-layer` are the cascade's: it replaces a declaration
/// that says one with the declaration it reverts to, so a computed style only
/// meets one that reverted to nothing, and treats it as `unset`. The other
/// three are applied here, through the property table, so that
/// `padding: inherit` copies the parent's four sides and nothing else.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#inheritance).
public enum GlobalKeyword {

    /// The parent's value.
    INHERIT,

    /// The initial style's value.
    INITIAL,

    /// `inherit` for a property that inherits, `initial` for one that does not.
    UNSET,

    /// The value the toolkit's own sheets give, as if the sheets above them had
    /// said nothing about this property.
    REVERT,

    /// The value the layers below this declaration's give.
    REVERT_LAYER;

    /// The name as CSS writes it — `revert-layer`.
    public String cssName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /// The keyword `value` is, alone, or null when it is anything else.
    public static @Nullable GlobalKeyword of(List<Token> value) {
        Token only = null;
        for (var token : value) {
            if (token.is(TokenType.WHITESPACE) || token.is(TokenType.EOF)) {
                continue;
            }
            if (only != null) {
                return null;
            }
            only = token;
        }
        if (only == null || !only.is(TokenType.IDENT)) {
            return null;
        }
        for (var keyword : values()) {
            if (keyword.cssName().equalsIgnoreCase(only.text())) {
                return keyword;
            }
        }
        return null;
    }

    /// `style` with `property` set by this keyword.
    ///
    /// @param parent where `inherit` reads from; null at the root, where it reads
    ///               the initial style, as CSS says
    /// @return `style` unchanged when `property` is not in the table
    public ComputedStyle apply(String property, ComputedStyle style, @Nullable ComputedStyle parent) {
        if (Properties.find(property).isEmpty()) {
            return style;
        }
        var inherited = parent == null ? ComputedStyle.INITIAL : parent;
        return switch (this) {
            case INHERIT -> Properties.copy(property, inherited, style);
            case INITIAL -> Properties.copy(property, ComputedStyle.INITIAL, style);
            case UNSET, REVERT, REVERT_LAYER -> {
                var result = style;
                for (var longhand : Properties.expand(property)) {
                    result = longhand.copy(longhand.inherited() ? inherited : ComputedStyle.INITIAL, result);
                }
                yield result;
            }
        };
    }
}
