package dev.goldberry.html.style;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Declaration;
import dev.goldberry.css.StyleRule;
import dev.goldberry.css.cascade.ScopedRules;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.select.Selector;
import dev.goldberry.html.model.Comment;
import dev.goldberry.html.model.Element;
import dev.goldberry.html.model.HtmlDocument;
import dev.goldberry.html.model.HtmlNode;
import dev.goldberry.html.model.HtmlText;
import dev.goldberry.html.model.Tags;

/// What one document says about its own appearance, worked out: a class for each
/// element that says anything, and the rules those classes stand for.
///
/// ```java
/// var styles = DocumentStyles.of(document, Colours.ADAPT);
/// styles.classOf(element);    // "html-author-3", or null
/// styles.rules();             // `.html-author-3 { color: #1f2328 }` and the rest
/// ```
///
/// ## A class per element, and the rules in a scope
///
/// Each element's declarations are gathered the way a browser cascades them: its
/// presentational attributes first (`bgcolor`, a `font`'s `color`), then the
/// `<style>` rules that match it by specificity and order, then its own
/// `style=`, which wins. The result is minted as **one generated class**,
/// `html-author-N`, and one rule for it. Two elements that end up saying the same
/// thing share a class.
///
/// The fold puts the class on the widget the element became, and the view puts
/// the rules in a `StyleScope` round the page, so they reach that page's widgets
/// and nothing else: not the application round it, not another mail beside it.
/// The selectors are generated, so nothing a mail writes can name a widget
/// outside its own page. A document class that looks like a generated one,
/// `class="author-2"`, is not forwarded, so a mail cannot borrow another
/// element's rule either.
///
/// ## Inline elements
///
/// An inline element is not a widget: it is a class on each of its words. So an
/// inline element's class carries **its own declarations merged over those of
/// every inline element around it**, and a word carries the innermost one alone.
/// That keeps `<span style="color:red"><b style="color:blue">` blue without two
/// rules of equal weight on one word having to agree. What does not survive the
/// translation is a box: a `background` or a `padding` on a `span` is drawn round
/// each of its words rather than round the run.
///
/// ## Colours: [Colours#ADAPT]
///
/// A mail is designed on its own white; a reader may be in a dark theme. Under
/// [Colours#ADAPT] a colour is kept where it can be seen to read, and replaced or
/// dropped where it cannot:
///
/// - **A background is kept**, and becomes the ground everything inside it is
///   checked against. An element that paints one and does not say what its text
///   is gets a text colour that reads on it, dark or light, unless the colour it
///   inherits from the mail already does.
/// - **A text colour on a ground the mail painted** is kept when it reaches
///   4.5:1 against that ground, and replaced by the dark or light ink when it does
///   not.
/// - **A text colour on no painted ground is dropped.** It would sit on the
///   theme's surface, which is light or dark depending on the reader, and the
///   text is in the theme's colour instead.
/// - **A link on a ground the mail painted** gets a blue that reads on it when
///   the mail gave it none, because the theme's link colour is chosen for the
///   theme's surface.
/// - Borders keep their colours: they are not text.
public final class DocumentStyles {

    /// The prefix of every generated class.
    public static final String PREFIX = "html-author-";

    /// A document class that would come out of the fold as a generated one.
    private static final Pattern GENERATED = Pattern.compile("author-\\d+");

    /// Nothing: what a view that applies no styles holds.
    public static final DocumentStyles NONE = new DocumentStyles(new IdentityHashMap<>(), ScopedRules.NONE);

    private final IdentityHashMap<Element, String> classes;
    private final ScopedRules rules;

    private DocumentStyles(IdentityHashMap<Element, String> classes, ScopedRules rules) {
        this.classes = classes;
        this.rules = rules;
    }

    /// What `document` says about its appearance, with its colours treated as
    /// `colours` says.
    public static DocumentStyles of(HtmlDocument document, Colours colours) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(colours, "colours");
        var walk = new Walk(AuthorSheet.of(document), colours);
        walk.nodes(document.children(), StyleNode.document(), Ground.NONE, null);
        return walk.classes.isEmpty() ? NONE : new DocumentStyles(walk.classes, ScopedRules.of(walk.rules));
    }

    /// The generated class for `element`, or null when the document says nothing
    /// about it.
    public @Nullable String classOf(Element element) {
        return classes.get(element);
    }

    /// The rules the classes stand for, for the scope round the page.
    public ScopedRules rules() {
        return rules;
    }

    /// Whether a document's own class `name` may be forwarded as `html-<name>`:
    /// not when it would read as a generated class.
    public static boolean forwardable(String name) {
        return !GENERATED.matcher(name).matches();
    }

    // --- one element's declarations -------------------------------------------

    /// What an element says, normally and in each state its rules name.
    private record Said(
            LinkedHashMap<String, Declaration> normal,
            EnumMap<Selector.PseudoClass, LinkedHashMap<String, Declaration>> states) {

        static Said empty() {
            return new Said(new LinkedHashMap<>(), new EnumMap<>(Selector.PseudoClass.class));
        }

        boolean isEmpty() {
            return normal.isEmpty() && states.values().stream().allMatch(Map::isEmpty);
        }

        /// `inner` over this, each map merged property by property.
        Said under(Said inner) {
            var merged = empty();
            merged.normal.putAll(normal);
            inner.normal.values().forEach(declaration -> put(merged.normal, declaration));
            states.forEach((state, said) -> merged.states.put(state, new LinkedHashMap<>(said)));
            inner.states.forEach((state, said) -> said.values()
                    .forEach(declaration ->
                            put(merged.states.computeIfAbsent(state, _ -> new LinkedHashMap<>()), declaration)));
            return merged;
        }

        /// The text that identifies what this says, for sharing one class between
        /// two elements that say the same thing.
        String key() {
            var key = new StringBuilder();
            append(key, normal);
            states.forEach((state, said) -> {
                key.append('|').append(state.cssName()).append('|');
                append(key, said);
            });
            return key.toString();
        }

        private static void append(StringBuilder key, Map<String, Declaration> said) {
            for (var declaration : said.values()) {
                key.append(declaration.property()).append(':');
                for (var token : declaration.value()) {
                    key.append(token.is(TokenType.WHITESPACE) ? " " : token.cssText());
                }
                key.append(';');
            }
        }
    }

    /// Later wins, and moves to the end, so that a shorthand after a longhand is
    /// applied after it: the order the toolkit's cascade applies declarations in.
    private static void put(Map<String, Declaration> said, Declaration declaration) {
        said.remove(declaration.property());
        said.put(declaration.property(), declaration);
    }

    /// What colours an element sits on and inherits, as far as the mail painted
    /// them.
    ///
    /// @param ground the opaque background the mail painted under this element,
    ///               or null for the theme's surface
    /// @param ink    the text colour the mail gave this element's text, after
    ///               adapting, or null for the theme's
    private record Ground(
            @Nullable Integer ground, @Nullable Integer ink) {
        static final Ground NONE = new Ground(null, null);
    }

    // --- the walk ----------------------------------------------------------------

    private static final class Walk {

        private final AuthorSheet sheet;
        private final Colours colours;
        private final IdentityHashMap<Element, String> classes = new IdentityHashMap<>();
        private final List<StyleRule> rules = new ArrayList<>();
        private final Map<String, String> minted = new LinkedHashMap<>();

        Walk(AuthorSheet sheet, Colours colours) {
            this.sheet = sheet;
            this.colours = colours;
        }

        /// @param inline what the inline elements round these nodes say, merged,
        ///               or null when they are in a block of their own
        void nodes(List<HtmlNode> nodes, @Nullable StyleNode parent, Ground ground, @Nullable Said inline) {
            for (var node : nodes) {
                switch (node) {
                    case Element element -> element(element, parent, ground, inline);
                    case HtmlDocument document -> nodes(document.children(), parent, ground, inline);
                    case HtmlText _, Comment _ -> {}
                }
            }
        }

        private void element(Element element, @Nullable StyleNode parent, Ground around, @Nullable Said inline) {
            var node = StyleNode.child(element, parent);
            if (Tags.isMetadata(element.tag())) {
                // A `<style>` is read by the sheet; a `<head>` holds nothing drawn.
                // Each still counts as a sibling, as it does in the document.
                return;
            }
            var said = said(node, element);
            var adapted = switch (colours) {
                case AUTHOR -> new Adapted(said, around);
                case THEME -> new Adapted(themed(said), Ground.NONE);
                case ADAPT -> adapt(said, around, "a".equals(element.tag()));
            };
            // Inside an inline element everything is inline, whatever its tag: the
            // fold folds it into the same run of words.
            var inRun = inline != null || Tags.isInline(element.tag());
            var effective = inline == null ? adapted.said() : inline.under(adapted.said());
            if (!effective.isEmpty()) {
                classes.put(element, mint(effective));
            }
            nodes(element.children(), node, adapted.ground(), inRun ? effective : null);
        }

        /// The presentational attributes, the matching `<style>` rules and the
        /// `style=`, cascaded.
        private Said said(StyleNode node, Element element) {
            var said = Said.empty();
            AuthorDeclarations.presentational(element).forEach(declaration -> put(said.normal(), declaration));
            var matched = new ArrayList<>(sheet.matching(node));
            matched.sort(
                    Comparator.comparingInt(AuthorSheet.Rule::specificity).thenComparingInt(AuthorSheet.Rule::order));
            for (var rule : matched) {
                var target = rule.state() == null
                        ? said.normal()
                        : said.states().computeIfAbsent(rule.state(), _ -> new LinkedHashMap<>());
                rule.declarations().forEach(declaration -> put(target, declaration));
            }
            var inline = element.attribute("style");
            if (inline != null) {
                AuthorDeclarations.parse(inline).forEach(declaration -> put(said.normal(), declaration));
            }
            return said;
        }

        private String mint(Said said) {
            var key = said.key();
            var name = minted.get(key);
            if (name != null) {
                return name;
            }
            name = PREFIX + (minted.size() + 1);
            minted.put(key, name);
            if (!said.normal().isEmpty()) {
                rules.add(rule(name, null, said.normal()));
            }
            for (var state : said.states().entrySet()) {
                if (!state.getValue().isEmpty()) {
                    rules.add(rule(name, state.getKey(), state.getValue()));
                }
            }
            return name;
        }

        private StyleRule rule(String name, Selector.@Nullable PseudoClass state, Map<String, Declaration> said) {
            var compound = new Selector.Compound(null, null, List.of(name), state == null ? List.of() : List.of(state));
            var selector = new Selector(List.of(new Selector.Part(compound, Selector.Combinator.NONE)));
            return new StyleRule(List.of(selector), List.copyOf(said.values()), rules.size());
        }
    }

    // --- colours -----------------------------------------------------------------

    /// What an element says once its colours are seen to, and what its children
    /// sit on.
    private record Adapted(Said said, Ground ground) {}

    /// Every colour taken out: [Colours#THEME].
    private static Said themed(Said said) {
        var themed = Said.empty();
        themed.normal().putAll(withoutColours(said.normal()));
        said.states()
                .forEach((state, values) -> themed.states().put(state, new LinkedHashMap<>(withoutColours(values))));
        return themed;
    }

    private static Map<String, Declaration> withoutColours(Map<String, Declaration> said) {
        var kept = new LinkedHashMap<String, Declaration>();
        for (var declaration : said.values()) {
            var property = declaration.property();
            if (Inks.INK.contains(property) || Inks.SOLID.contains(property)) {
                continue;
            }
            var value = Inks.MIXED.contains(property) ? Inks.withoutColour(declaration) : declaration;
            if (value != null) {
                kept.put(property, value);
            }
        }
        return kept;
    }

    /// [Colours#ADAPT], as the class documentation lists it.
    private static Adapted adapt(Said said, Ground around, boolean anchor) {
        var normal = new LinkedHashMap<>(said.normal());
        var own = Inks.ground(normal.get("background-color"));
        var ground = own != null ? own : around.ground();
        adaptInk(normal, ground, anchor);
        if (own != null && !normal.containsKey("color") && (around.ink() == null || !Inks.reads(around.ink(), own))) {
            put(normal, colour("color", anchor ? Inks.link(own) : Inks.legible(own)));
        } else if (anchor && ground != null && !normal.containsKey("color")) {
            put(normal, colour("color", Inks.link(ground)));
        }
        var text = normal.get("text-decoration");
        if (text != null) {
            // The line follows the text's colour, which is the one that was checked.
            var plain = Inks.withoutColour(text);
            if (plain == null) {
                normal.remove("text-decoration");
            } else {
                put(normal, plain);
            }
        }
        var written = normal.get("color");
        var ink = written == null ? around.ink() : Inks.colour(written);
        var adapted = Said.empty();
        adapted.normal().putAll(normal);
        for (var state : said.states().entrySet()) {
            var values = new LinkedHashMap<>(state.getValue());
            var stateGround = Inks.ground(values.get("background-color"));
            adaptInk(values, stateGround != null ? stateGround : ground, anchor);
            if (stateGround != null && !values.containsKey("color") && (ink == null || !Inks.reads(ink, stateGround))) {
                put(values, colour("color", anchor ? Inks.link(stateGround) : Inks.legible(stateGround)));
            }
            adapted.states().put(state.getKey(), values);
        }
        return new Adapted(adapted, new Ground(ground, ink));
    }

    /// The text colours in `said`, kept where they read on `ground`, replaced
    /// where they do not, and dropped where there is no ground to read them on.
    private static void adaptInk(Map<String, Declaration> said, @Nullable Integer ground, boolean anchor) {
        for (var property : Inks.INK) {
            var declaration = said.get(property);
            if (declaration == null) {
                continue;
            }
            var colour = Inks.colour(declaration);
            if (colour == null) {
                // `inherit` and its kind say nothing new about contrast.
                continue;
            }
            if (ground == null) {
                said.remove(property);
            } else if (!Inks.reads(colour, ground)) {
                if ("color".equals(property)) {
                    put(said, colour(property, anchor ? Inks.link(ground) : Inks.legible(ground)));
                } else {
                    said.remove(property);
                }
            }
        }
    }

    private static Declaration colour(String property, int argb) {
        return Objects.requireNonNull(
                AuthorDeclarations.declaration(property + ": " + Inks.hex(argb)), "a hex colour is a declaration");
    }
}
