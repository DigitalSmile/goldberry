package io.github.digitalsmile.goldberry.render.dialog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// One row of a file dialog's type dropdown: a label and the extensions it
/// admits.
///
/// ```java
/// FileFilter.of("Images", "png", "jpg", "jpeg");
/// FileFilter.of("Markdown", ".md");           // the dot is optional and stripped
/// FileFilter.everything("All files");
/// ```
///
/// **Extensions, not patterns.** `*.png` and `image/png` are two other things
/// that could have gone here and neither is portable: every platform spells its
/// own filter differently — Windows wants `*.png;*.jpg`, GTK wants a glob per
/// entry, macOS wants uniform type identifiers — so the toolkit's vocabulary is
/// the part they agree on and the backend spells it out. A leading dot is
/// accepted because it is what a `Path` shows you, and removed because it is not
/// what any of them store.
///
/// Lower-cased, because no platform's matching is case-sensitive and two filters
/// differing only in case would be one row drawn twice.
///
/// **A filter is a suggestion.** Several platforms let the user turn filtering
/// off, and some ignore it altogether; a dialog is not a validator, so code that
/// must not open a `.exe` checks the path it was given.
///
/// @param label      what the dropdown says
/// @param extensions the extensions, dotless and lower-cased; empty means every file
public record FileFilter(String label, List<String> extensions) {

    public FileFilter {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(extensions, "extensions");
        if (label.isBlank()) {
            throw new IllegalArgumentException("a file filter needs a label to put in the dropdown");
        }
        extensions = extensions.stream().map(FileFilter::normalize).distinct().toList();
    }

    /// A filter over one or more extensions, with or without their dots.
    public static FileFilter of(String label, String... extensions) {
        return new FileFilter(label, List.of(extensions));
    }

    /// The row that admits everything, which most platforms show whether it is
    /// asked for or not.
    public static FileFilter everything(String label) {
        return new FileFilter(label, List.of());
    }

    /// Whether this filter admits every file.
    public boolean matchesEverything() {
        return extensions.isEmpty();
    }

    /// Whether `name` ends in one of these extensions. Used by callers that want
    /// to add the extension a save dialog did not, which several platforms leave
    /// to the application.
    public boolean matches(String name) {
        Objects.requireNonNull(name, "name");
        if (matchesEverything()) {
            return true;
        }
        var lower = name.toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(extension -> lower.endsWith("." + extension));
    }

    private static String normalize(String extension) {
        Objects.requireNonNull(extension, "extension");
        var trimmed = extension.strip();
        while (trimmed.startsWith("*")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    "'" + extension + "' is not an extension; use FileFilter.everything(label) for every file");
        }
        for (var i = 0; i < trimmed.length(); i++) {
            var c = trimmed.charAt(i);
            var alphanumeric = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!alphanumeric && c != '_' && c != '-') {
                throw new IllegalArgumentException("'" + extension + "' is not a bare extension: write \"png\" or "
                        + "\".png\", not a pattern or a MIME type");
            }
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
