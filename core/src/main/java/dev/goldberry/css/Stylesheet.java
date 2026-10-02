package dev.goldberry.css;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.DroppedRule;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.log.Logs;

/// A parsed stylesheet and the layer of the cascade it belongs to.
///
/// ```java
/// Stylesheet.resource(CascadeLayer.APPLICATION, MyApp.class, "app.css")
/// ```
///
/// An application's sheets are returned from `Application.stylesheets()`, in
/// cascade order; the toolkit's own and the theme's sit in the layers below. A
/// sheet is an immutable value: hot reload replaces it rather than editing it.
///
/// ## Strict and lenient
///
/// A sheet is parsed under a [ParseMode]. The toolkit's own are strict: anything
/// outside the CSS subset refuses the sheet, because a rule in them that matched
/// nothing would be a control drawn wrong in every application. An
/// application's are lenient: a rule asking for something the subset lacks is
/// dropped with one warning naming its selector, this sheet and the line, and
/// recorded in [#dropped()]. The forms without a mode choose by layer:
/// [CascadeLayer#TOOLKIT_BASE] is strict, every other layer lenient, and the
/// toolkit's own theme sheets ask for strict by name.
///
/// Either way, a property the engine does not have at all is warned about once
/// per property when the sheet is read, naming the sheet and the first line.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#strict-and-lenient-sheets).
///
/// @param layer     where its rules sit in the cascade
/// @param rules     in source order
/// @param keyframes the `@keyframes` blocks it declares, in source order
/// @param origin    what the sheet is called in a warning: its resource name, or
///                  `a stylesheet` for text with no name
/// @param dropped   the rules a lenient parse left out, in source order; empty
///                  for a strict sheet, which would have thrown instead
public record Stylesheet(
        CascadeLayer layer,
        List<StyleRule> rules,
        List<Keyframes> keyframes,
        String origin,
        List<DroppedRule> dropped) {

    private static final Logger LOG = Logs.of(Stylesheet.class);

    /// What a sheet with no name of its own is called in a warning.
    private static final String UNNAMED = "a stylesheet";

    public Stylesheet {
        Objects.requireNonNull(layer, "layer");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        keyframes = List.copyOf(Objects.requireNonNull(keyframes, "keyframes"));
        Objects.requireNonNull(origin, "origin");
        dropped = List.copyOf(Objects.requireNonNull(dropped, "dropped"));
    }

    /// A stylesheet of rules and keyframes that dropped nothing.
    public Stylesheet(CascadeLayer layer, List<StyleRule> rules, List<Keyframes> keyframes) {
        this(layer, rules, keyframes, UNNAMED, List.of());
    }

    /// A stylesheet of rules and no keyframes.
    public Stylesheet(CascadeLayer layer, List<StyleRule> rules) {
        this(layer, rules, List.of());
    }

    /// The mode a sheet in `layer` is parsed under when nobody says: strict for
    /// [CascadeLayer#TOOLKIT_BASE], which only the toolkit writes, and lenient
    /// for the rest.
    public static ParseMode defaultMode(CascadeLayer layer) {
        return Objects.requireNonNull(layer, "layer") == CascadeLayer.TOOLKIT_BASE
                ? ParseMode.STRICT
                : ParseMode.LENIENT;
    }

    /// Parses `css` into a stylesheet in `layer`, under [#defaultMode].
    ///
    /// @throws CssSyntaxException if the text is malformed, or is strict and asks
    ///         for something outside the supported subset
    public static Stylesheet parse(CascadeLayer layer, String css) {
        return parse(layer, css, defaultMode(layer));
    }

    /// Parses `css` into a stylesheet in `layer`, under `mode`.
    ///
    /// @throws CssSyntaxException if the text is malformed, or under
    ///         [ParseMode#STRICT] if it asks for something outside the subset
    public static Stylesheet parse(CascadeLayer layer, String css, ParseMode mode) {
        return parse(layer, css, mode, UNNAMED);
    }

    /// Parses `css`, naming it `origin` in what it warns about.
    ///
    /// @throws CssSyntaxException if the text is malformed, or under
    ///         [ParseMode#STRICT] if it asks for something outside the subset
    public static Stylesheet parse(CascadeLayer layer, String css, ParseMode mode, String origin) {
        Objects.requireNonNull(layer, "layer");
        var parsed = CssParser.parseSheet(css, mode, origin);
        var sheet = new Stylesheet(layer, parsed.rules(), parsed.keyframes(), origin, parsed.dropped());
        sheet.warnUnknownProperties();
        return sheet;
    }

    /// Parses a stylesheet from a resource beside `owner`, under [#defaultMode].
    ///
    /// What an application's own CSS should be: a `.css` file next to the class
    /// that uses it, rather than a text block in the middle of Java. The toolkit
    /// loads its own theme and control sheets exactly this way.
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
        return resource(layer, owner, name, defaultMode(layer));
    }

    /// The same, under `mode`.
    public static Stylesheet resource(CascadeLayer layer, Class<?> owner, String name, ParseMode mode) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        try (var in = owner.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("no stylesheet resource \"" + name + "\" beside " + owner.getName()
                        + ". Either it is missing from src/main/resources/"
                        + owner.getPackageName().replace('.', '/') + "/, or "
                        + (owner.getModule().isNamed() && !owner.getModule().isOpen(owner.getPackageName())
                                ? "module " + owner.getModule().getName()
                                        + " does not open the package: JPMS encapsulates"
                                        + " resources, so add `opens "
                                        + owner.getPackageName() + ";` to its module-info"
                                : "it is not on the module path"));
            }
            return parse(layer, new String(in.readAllBytes(), StandardCharsets.UTF_8), mode, name);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + name, e);
        }
    }

    /// An empty stylesheet — what a hot reload falls back to before the first
    /// good parse.
    public static Stylesheet empty(CascadeLayer layer) {
        return new Stylesheet(layer, List.of());
    }

    /// Warns once for each property in this sheet that the engine does not have.
    ///
    /// Here, once per sheet, rather than where a declaration is applied, which
    /// runs per node per restyle: there it was a debug line nobody saw, and at
    /// warn it would be a stream. The count says how many declarations do
    /// nothing, and the line says where the first one is.
    private void warnUnknownProperties() {
        var unknown = new LinkedHashMap<String, int[]>();
        for (var rule : rules) {
            for (var declaration : rule.declarations()) {
                note(declaration, unknown);
            }
        }
        for (var block : keyframes) {
            for (var frame : block.frames()) {
                for (var declaration : frame.declarations()) {
                    note(declaration, unknown);
                }
            }
        }
        for (var entry : unknown.entrySet()) {
            var seen = entry.getValue();
            LOG.warn(
                    "{}, line {}: \"{}\" is not a property this toolkit has, so its {} declaration(s) do nothing",
                    origin,
                    seen[0],
                    entry.getKey(),
                    seen[1]);
        }
    }

    private static void note(Declaration declaration, LinkedHashMap<String, int[]> unknown) {
        if (declaration.isCustomProperty() || ComputedStyle.isProperty(declaration.property())) {
            return;
        }
        unknown.computeIfAbsent(declaration.property(), _ -> new int[] {declaration.line(), 0})[1]++;
    }
}
