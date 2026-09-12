package io.github.digitalsmile.goldberry.natives.sdl.dialog;

import java.util.Objects;

/// One row of a file dialog's type dropdown.
///
/// ```c
/// typedef struct SDL_DialogFileFilter { const char *name; const char *pattern; } SDL_DialogFileFilter;
/// ```
///
/// A plain value with no foreign memory in it, which is why it is here rather
/// than beside the wrapper that copies it into an arena.
///
/// **The pattern is SDL's own dialect and not a glob.** Extensions are listed
/// without their dot and separated by semicolons — `png;jpg;jpeg` — and the
/// single character `*` means "every file". A pattern containing a dot, a space,
/// a slash or a `*` anywhere but alone is rejected here rather than handed to a
/// platform that would silently show nothing: SDL's own documentation calls those
/// undefined behaviour, and a dialog that lists no files is the hardest kind of
/// bug to read backwards.
///
/// @param name    what the dropdown says — "Images", "Markdown"
/// @param pattern the extensions, `;`-separated and dotless, or `*`
public record SdlFileFilter(String name, String pattern) {

    /// The pattern that matches everything.
    public static final String ALL = "*";

    public SdlFileFilter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(pattern, "pattern");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a file filter needs a name to put in the dropdown");
        }
        if (!ALL.equals(pattern)) {
            requireExtensions(pattern);
        }
    }

    /// A filter over one or more extensions, given without their dots.
    public static SdlFileFilter of(String name, String... extensions) {
        return new SdlFileFilter(name, String.join(";", extensions));
    }

    /// The "All files" row.
    public static SdlFileFilter all(String name) {
        return new SdlFileFilter(name, ALL);
    }

    private static void requireExtensions(String pattern) {
        if (pattern.isBlank()) {
            throw new IllegalArgumentException(
                    "an empty pattern matches nothing; use SdlFileFilter.ALL for every file");
        }
        for (var extension : pattern.split(";", -1)) {
            if (extension.isEmpty()) {
                throw new IllegalArgumentException("empty extension in pattern '" + pattern + "'");
            }
            for (var i = 0; i < extension.length(); i++) {
                var c = extension.charAt(i);
                var alphanumeric = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
                if (!alphanumeric && c != '_' && c != '-') {
                    throw new IllegalArgumentException(
                            "'" + extension + "' is not a bare extension: SDL's patterns are "
                                    + "dotless, ';'-separated and alphanumeric, so write 'png;jpg' rather than '*.png, *.jpg'");
                }
            }
        }
    }
}
