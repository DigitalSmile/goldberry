package io.github.digitalsmile.goldberry.html.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// What was written inside a tag.
///
/// ```java
/// element.attributes().value("href");         // null when absent
/// element.attributes().has("disabled");       // present, value or not
/// element.attributes().classes();             // the class attribute, split
/// ```
///
/// **Names are lower-cased and the order is the author's.** Lower-cased because HTML
/// is case-insensitive about them and a fold matching `href` must not miss `HREF`;
/// ordered because a serializer should be able to write the tag back out as it was
/// found, which is the one thing a `HashMap` would have quietly made impossible.
///
/// **A valueless attribute is the empty string, not null.** `<input disabled>` is
/// present-with-no-value, which is what HTML means by it, and [#has] is how a fold
/// asks the question that has a yes-or-no answer. Null is reserved for *absent*, so
/// that `value("href")` can tell `<a href="">` from `<a>`.
///
/// @param byName the attributes, in the order they were written
public record HtmlAttributes(Map<String, String> byName) {

    /// A tag with nothing in it.
    public static final HtmlAttributes NONE = new HtmlAttributes(Map.of());

    public HtmlAttributes {
        Objects.requireNonNull(byName, "byName");
        // Copied into a LinkedHashMap rather than through `Map.copyOf`, which does not
        // keep the order this record promises.
        var lowered = new LinkedHashMap<String, String>(byName.size());
        byName.forEach((name, value) -> lowered.put(
                Objects.requireNonNull(name, "an attribute with no name").toLowerCase(Locale.ROOT),
                Objects.requireNonNull(value, () -> "the " + name + " attribute has a null value; use \"\"")));
        byName = Collections.unmodifiableMap(lowered);
    }

    /// The attributes in `byName`, whatever order the caller's map has them in.
    public static HtmlAttributes of(Map<String, String> byName) {
        return byName.isEmpty() ? NONE : new HtmlAttributes(byName);
    }

    /// One attribute, for the common case of a tag that has exactly one.
    public static HtmlAttributes of(String name, String value) {
        return new HtmlAttributes(Map.of(name, value));
    }

    /// What `name` was set to, or null when the tag did not set it.
    public @Nullable String value(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    /// Whether the tag mentioned `name` at all.
    public boolean has(String name) {
        return byName.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /// The `class` attribute, split on whitespace, in the order it was written.
    ///
    /// The one attribute with a structure of its own, and the one that reaches the
    /// cascade: `html-view` puts each of these on the widget it builds, prefixed into
    /// the document's own namespace so that a page's `class="card"` cannot be styled
    /// by the application's rule for a `card` (ADR-0298).
    public List<String> classes() {
        var value = value("class");
        if (value == null || value.isBlank()) {
            return List.of();
        }
        var names = new ArrayList<String>(4);
        // The two-argument `split` rather than the one-argument one, which Error Prone
        // refuses for a good reason: the single-argument form drops trailing empty
        // fields, and a rule that quietly behaves differently at the end of a string
        // is not a rule worth having in a parser.
        for (var name : value.strip().split("\\s+", -1)) {
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }
}
