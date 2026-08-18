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

    /// Parses a stylesheet from a resource beside `owner`.
    ///
    /// What an application's own CSS should be: a `.css` file next to the class
    /// that uses it, rather than a text block in the middle of Java. The toolkit
    /// loads its own theme and control sheets exactly this way, and an
    /// application had no supported way to do the same
    /// ([ADR-0093](../../../../../../book/src/adr/0093-an-application-is-a-root-widget.md)).
    ///
    /// ```java
    /// Stylesheet.resource(CascadeLayer.APPLICATION, MyApp.class, "app.css")
    /// ```
    ///
    /// UTF-8, because every file in this toolkit is.
    ///
    /// @throws IllegalStateException if the resource is not on the module path,
    ///         which for a file that ships inside a jar is a build problem rather
    ///         than a runtime one — and a silent empty stylesheet would be a
    ///         window that renders unstyled with no error at all
    public static Stylesheet resource(CascadeLayer layer, Class<?> owner, String name) {
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(name, "name");
        try (var in = owner.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(
                        "no stylesheet resource \"" + name + "\" beside " + owner.getName()
                                + ". Either it is missing from src/main/resources/"
                                + owner.getPackageName().replace('.', '/') + "/, or "
                                + (owner.getModule().isNamed()
                                        && !owner.getModule().isOpen(owner.getPackageName())
                                    ? "module " + owner.getModule().getName()
                                            + " does not open the package: JPMS encapsulates"
                                            + " resources, so add `opens "
                                            + owner.getPackageName() + ";` to its module-info"
                                    : "it is not on the module path"));
            }
            return parse(layer, new String(
                    in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("could not read " + name, e);
        }
    }

    /// An empty stylesheet — what a hot reload falls back to before the first
    /// good parse.
    public static Stylesheet empty(CascadeLayer layer) {
        return new Stylesheet(layer, List.of());
    }
}
