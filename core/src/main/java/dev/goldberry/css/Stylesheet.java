package dev.goldberry.css;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.DroppedRule;
import dev.goldberry.css.parse.Import;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
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
/// @param fontFaces the faces its `@font-face` rules ship, which a renderer
///                  adds to its book of fonts
/// @param layers    its `@layer` names in the order it first mentions them,
///                  which is the order they rank in, nested ones as
///                  `outer.inner`
public record Stylesheet(
        CascadeLayer layer,
        List<StyleRule> rules,
        List<Keyframes> keyframes,
        String origin,
        List<DroppedRule> dropped,
        List<FontFaceRule> fontFaces,
        List<String> layers) {

    private static final Logger LOG = Logs.of(Stylesheet.class);

    /// What a sheet with no name of its own is called in a warning.
    private static final String UNNAMED = "a stylesheet";

    public Stylesheet {
        Objects.requireNonNull(layer, "layer");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        keyframes = List.copyOf(Objects.requireNonNull(keyframes, "keyframes"));
        Objects.requireNonNull(origin, "origin");
        dropped = List.copyOf(Objects.requireNonNull(dropped, "dropped"));
        fontFaces = List.copyOf(Objects.requireNonNull(fontFaces, "fontFaces"));
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
    }

    /// A stylesheet that ships no face and names no layer.
    public Stylesheet(
            CascadeLayer layer,
            List<StyleRule> rules,
            List<Keyframes> keyframes,
            String origin,
            List<DroppedRule> dropped) {
        this(layer, rules, keyframes, origin, dropped, List.of(), List.of());
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
        var dropped = new ArrayList<>(parsed.dropped());
        for (var skipped : parsed.imports()) {
            // Text has no place of its own to look beside.
            refuseImport(
                    skipped,
                    mode,
                    origin,
                    dropped,
                    "an @import is resolved only in a sheet read with"
                            + " Stylesheet.resource, beside which it is looked for");
        }
        var sheet = new Stylesheet(
                layer, parsed.rules(), parsed.keyframes(), origin, dropped, parsed.fontFaces(), parsed.layers());
        sheet.warnUnknownProperties();
        return sheet;
    }

    /// Refuses `skipped` in a strict sheet, and drops it with a warning in a
    /// lenient one.
    private static void refuseImport(
            Import skipped, ParseMode mode, String origin, List<DroppedRule> dropped, String reason) {
        if (mode == ParseMode.STRICT) {
            throw CssSyntaxException.unsupportedFeature(
                    "@import \"" + skipped.href() + "\": " + reason, skipped.line(), skipped.column());
        }
        dropped.add(new DroppedRule("@import " + skipped.href(), reason, skipped.line(), skipped.column()));
        LOG.warn("{}, line {}: dropping \"@import {}\": {}", origin, skipped.line(), skipped.href(), reason);
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
    /// The toolkit's module does the reading, so on the module path the package
    /// holding the file must be opened to `dev.goldberry.core`. [#stream] reads
    /// it with the application's own access and needs no `opens`.
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
        var text = read(owner, name);
        if (text == null) {
            throw new IllegalStateException(missing(owner, name));
        }
        var path = pathOf(owner, name);
        var visiting = new LinkedHashSet<String>();
        visiting.add(path);
        return load(layer, owner, path, name, text, mode, visiting);
    }

    /// The sheet `text`, read from `path`, with its imports loaded beside it
    /// and placed before its own rules.
    ///
    /// @param visiting the sheets being loaded on the way here, which an import
    ///                 may not name again
    private static Stylesheet load(
            CascadeLayer layer,
            Class<?> owner,
            String path,
            String origin,
            String text,
            ParseMode mode,
            Set<String> visiting) {
        var parsed = CssParser.parseSheet(text, mode, origin);
        var dropped = new ArrayList<>(parsed.dropped());
        var own = new Stylesheet(
                layer, parsed.rules(), parsed.keyframes(), origin, List.of(), parsed.fontFaces(), parsed.layers());
        own.warnUnknownProperties();
        own = own.anchoredAt(path);
        if (parsed.imports().isEmpty()) {
            return new Stylesheet(layer, own.rules, own.keyframes, origin, dropped, own.fontFaces, own.layers);
        }
        var directory = directoryOf(path);
        var rules = new ArrayList<StyleRule>();
        var keyframes = new ArrayList<Keyframes>();
        var faces = new ArrayList<FontFaceRule>();
        var names = new ArrayList<String>();
        for (var imported : parsed.imports()) {
            var href = imported.href();
            var target = href.startsWith("classpath:/")
                    ? normalize(href.substring("classpath:/".length()))
                    : href.startsWith("/") ? normalize(href.substring(1)) : normalize(directory + href);
            if (SCHEME.matcher(href).lookingAt() && !href.startsWith("classpath:/")) {
                refuseImport(
                        imported,
                        mode,
                        origin,
                        dropped,
                        "only a sheet beside this one, or a classpath: one," + " is imported");
                continue;
            }
            if (visiting.contains(target)) {
                refuseImport(imported, mode, origin, dropped, "it imports a sheet that is importing this one");
                continue;
            }
            var importedText = read(owner, "/" + target);
            if (importedText == null) {
                refuseImport(imported, mode, origin, dropped, "there is no " + target + " beside it");
                continue;
            }
            visiting.add(target);
            var sheet = load(layer, owner, target, target, importedText, mode, visiting);
            visiting.remove(target);
            var into = imported.layer();
            for (var rule : sheet.rules) {
                var under = rule.under(imported.media());
                rules.add(into == null ? under : under.inLayer(into));
            }
            for (var name : sheet.layers) {
                names.add(into == null ? name : into + "." + name);
            }
            for (var block : sheet.keyframes) {
                keyframes.add(block.under(imported.media()));
            }
            faces.addAll(sheet.fontFaces);
            dropped.addAll(sheet.dropped);
        }
        rules.addAll(own.rules);
        keyframes.addAll(own.keyframes);
        faces.addAll(own.fontFaces);
        names.addAll(own.layers);
        var ordered = new ArrayList<StyleRule>(rules.size());
        for (var i = 0; i < rules.size(); i++) {
            ordered.add(rules.get(i).ordered(i));
        }
        return new Stylesheet(
                layer, ordered, keyframes, origin, dropped, faces, List.copyOf(new LinkedHashSet<>(names)));
    }

    /// The text of the resource `name` beside `owner`, or null when there is
    /// none.
    private static @Nullable String read(Class<?> owner, String name) {
        try (var in = owner.getResourceAsStream(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + name, e);
        }
    }

    /// The resource path of `name` beside `owner`, from the root.
    private static String pathOf(Class<?> owner, String name) {
        return name.startsWith("/") ? name.substring(1) : owner.getPackageName().replace('.', '/') + "/" + name;
    }

    /// The directory a resource path is in, with its trailing slash.
    private static String directoryOf(String path) {
        var slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    /// This sheet with every relative `url("…")` in it made a resource beside
    /// the sheet: `url("ui/panel.png")` in `app/app.css` is
    /// `classpath:/app/ui/panel.png`, the way [Class#getResource] reads a name
    /// beside a class. A name starting with `/` is from the root, and an address
    /// with a scheme of its own, such as `classpath:`, is left as it is.
    private Stylesheet anchoredAt(String path) {
        var directory = directoryOf(path);
        var changed = new boolean[1];
        var anchoredRules = rules.stream()
                .map(rule -> {
                    var declarations = anchored(rule.declarations(), directory, changed);
                    return declarations == rule.declarations() ? rule : rule.declarations(declarations);
                })
                .toList();
        var anchoredKeyframes = keyframes.stream()
                .map(block -> new Keyframes(
                        block.name(),
                        block.frames().stream()
                                .map(frame -> new Keyframes.Frame(
                                        frame.offset(), anchored(frame.declarations(), directory, changed)))
                                .toList(),
                        block.media()))
                .toList();
        var anchoredFaces = fontFaces.stream()
                .map(face -> {
                    var src = anchoredHref(face.src(), directory);
                    if (!src.equals(face.src())) {
                        changed[0] = true;
                    }
                    return face.src(src);
                })
                .toList();
        return changed[0]
                ? new Stylesheet(layer, anchoredRules, anchoredKeyframes, origin, dropped, anchoredFaces, layers)
                : this;
    }

    /// `declarations` with their relative addresses anchored in `directory`, or
    /// the same list when none has one.
    private static List<Declaration> anchored(List<Declaration> declarations, String directory, boolean[] changed) {
        var result = new ArrayList<Declaration>(declarations.size());
        var any = false;
        for (var declaration : declarations) {
            var value = anchored(declaration.value(), directory);
            if (value != declaration.value()) {
                any = true;
                result.add(new Declaration(
                        declaration.property(),
                        value,
                        declaration.important(),
                        declaration.line(),
                        declaration.column()));
            } else {
                result.add(declaration);
            }
        }
        if (!any) {
            return declarations;
        }
        changed[0] = true;
        return List.copyOf(result);
    }

    /// `value` with the string of each relative `url("…")` made a `classpath:`
    /// name in `directory`, or the same list when it has none.
    private static List<Token> anchored(List<Token> value, String directory) {
        @Nullable List<Token> result = null;
        var inUrl = false;
        for (var i = 0; i < value.size(); i++) {
            var token = value.get(i);
            if (token.is(TokenType.FUNCTION) && token.text().equalsIgnoreCase("url")) {
                inUrl = true;
                continue;
            }
            if (inUrl && token.is(TokenType.STRING)) {
                inUrl = false;
                var href = token.text();
                if (!href.isEmpty() && !SCHEME.matcher(href).lookingAt()) {
                    var resolved = href.startsWith("/") ? href.substring(1) : normalize(directory + href);
                    if (result == null) {
                        result = new ArrayList<>(value);
                    }
                    result.set(
                            i,
                            new Token(
                                    TokenType.STRING,
                                    "classpath:/" + resolved,
                                    token.numeric(),
                                    token.unit(),
                                    token.line(),
                                    token.column()));
                }
                continue;
            }
            if (!token.is(TokenType.WHITESPACE)) {
                inUrl = false;
            }
        }
        return result == null ? value : List.copyOf(result);
    }

    /// `href` made a `classpath:` name in `directory` when it is relative, and
    /// as it is otherwise.
    private static String anchoredHref(String href, String directory) {
        if (href.isEmpty() || SCHEME.matcher(href).lookingAt()) {
            return href;
        }
        return "classpath:/" + (href.startsWith("/") ? href.substring(1) : normalize(directory + href));
    }

    /// A resource name with its `.` and `..` segments taken out.
    private static String normalize(String name) {
        var segments = new ArrayList<String>();
        for (var segment : name.split("/", -1)) {
            if (segment.equals("..")) {
                if (!segments.isEmpty()) {
                    segments.removeLast();
                }
            } else if (!segment.equals(".") && !segment.isEmpty()) {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }

    /// The start of an address with a scheme of its own, such as `classpath:`,
    /// or a drive letter, none of which is relative to a sheet.
    private static final Pattern SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:");

    /// Parses a stylesheet from a stream the application opens, under
    /// [#defaultMode].
    ///
    /// ```java
    /// Stylesheet.stream(CascadeLayer.APPLICATION, "app.css", () -> MyApp.class.getResourceAsStream("app.css"))
    /// ```
    ///
    /// The form that works in a modular application without opening anything:
    /// the supplier is the application's code, so the file is read with the
    /// application's own access. Read now, once, and closed.
    ///
    /// @param origin what the sheet is called in a warning, usually its file name
    /// @throws IllegalStateException if the supplier answers null, which is a
    ///         file that is not there
    public static Stylesheet stream(
            CascadeLayer layer, String origin, Supplier<? extends @Nullable InputStream> stream) {
        return stream(layer, origin, stream, defaultMode(layer));
    }

    /// The same, under `mode`.
    public static Stylesheet stream(
            CascadeLayer layer, String origin, Supplier<? extends @Nullable InputStream> stream, ParseMode mode) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(stream, "stream");
        try (var in = stream.get()) {
            if (in == null) {
                throw new IllegalStateException(
                        "no stylesheet " + origin + ": the stream supplier answered null, so the file is not there");
            }
            return parse(layer, new String(in.readAllBytes(), StandardCharsets.UTF_8), mode, origin);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + origin, e);
        }
    }

    /// Why `name` beside `owner` was not found, telling a missing file from an
    /// encapsulated one.
    ///
    /// The package is the **file's**, which is the owner's only when the name
    /// has no directory in it: `themes/dark.css` beside `app.App` is in
    /// `app.themes`, and that is what has to be opened.
    private static String missing(Class<?> owner, String name) {
        var module = owner.getModule();
        var reader = Stylesheet.class.getModule();
        var to = reader.isNamed() ? " to " + reader.getName() : "";
        var path = name.startsWith("/")
                ? name.substring(1)
                : owner.getPackageName().replace('.', '/') + "/" + name;
        var slash = path.lastIndexOf('/');
        var pkg = slash < 0 ? "" : path.substring(0, slash).replace('/', '.');
        var where = "no stylesheet resource \"" + name + "\" beside " + owner.getName() + ". ";
        if (module.isNamed() && module.getPackages().contains(pkg) && !module.isOpen(pkg, reader)) {
            return where + "Module " + module.getName() + " does not open " + pkg + to
                    + ", and JPMS encapsulates resources as well as classes. Add `opens " + pkg + to
                    + ";` to its module-info, or read the file with your own code:"
                    + " Stylesheet.stream(layer, \"" + name + "\", () -> " + owner.getSimpleName()
                    + ".class.getResourceAsStream(\"" + name + "\"))";
        }
        return where + "It is missing from src/main/resources/" + path + ", or it is not on the module path";
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
