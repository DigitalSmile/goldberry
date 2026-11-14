package dev.goldberry.i18n;

import java.util.Locale;
import java.util.Optional;

/// CLDR's six plural categories. A language uses some of them: English has
/// `ONE` and `OTHER`, Russian `ONE`, `FEW`, `MANY` and `OTHER`, Japanese
/// `OTHER` alone. `OTHER` is in every language, and is what anything falls
/// back to.
///
/// Read more: [Text](https://goldberry.dev/docs/guide/text.html).
public enum PluralCategory {
    ZERO,
    ONE,
    TWO,
    FEW,
    MANY,
    OTHER;

    /// The category CLDR and ICU messages spell `name`, in any case: `one`,
    /// `few`, `other`.
    public static Optional<PluralCategory> named(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "zero" -> Optional.of(ZERO);
            case "one" -> Optional.of(ONE);
            case "two" -> Optional.of(TWO);
            case "few" -> Optional.of(FEW);
            case "many" -> Optional.of(MANY);
            case "other" -> Optional.of(OTHER);
            default -> Optional.empty();
        };
    }

    /// The name CLDR spells it with: `one`.
    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }
}
