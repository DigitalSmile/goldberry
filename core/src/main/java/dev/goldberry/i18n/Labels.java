package dev.goldberry.i18n;

import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/// The words the toolkit shows, in the application's language.
///
/// ```java
/// Labels.use(ResourceBundle.getBundle("app.messages", locale));
/// // app/messages_ru.properties: goldberry.copy=Копировать
/// ```
///
/// One set for the process, as there is one language for an application's
/// windows; set it in `Application.start`, or again when the user switches
/// language, and restyle so open menus are rebuilt. A label the translation
/// does not have stays English.
///
/// Read more: [Text](https://goldberry.dev/docs/guide/text.html).
public final class Labels {

    private static volatile Function<ToolkitLabel, @Nullable String> translations = label -> null;

    private Labels() {}

    /// What the toolkit shows for `label` now.
    public static String text(ToolkitLabel label) {
        Objects.requireNonNull(label, "label");
        var translated = translations.apply(label);
        return translated == null || translated.isBlank() ? label.english() : translated;
    }

    /// Takes the toolkit's words from `translate`, which answers null for one
    /// it has not got.
    public static void use(Function<ToolkitLabel, @Nullable String> translate) {
        translations = Objects.requireNonNull(translate, "translate");
    }

    /// Takes them from `bundle`, by each label's [ToolkitLabel#key()].
    public static void use(ResourceBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        use(label -> {
            try {
                return bundle.getString(label.key());
            } catch (MissingResourceException e) {
                return null;
            }
        });
    }

    /// Back to English.
    public static void reset() {
        translations = label -> null;
    }
}
