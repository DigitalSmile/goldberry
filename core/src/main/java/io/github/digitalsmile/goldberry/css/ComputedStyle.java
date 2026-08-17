package io.github.digitalsmile.goldberry.css;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.backend.Cursor;
import io.github.digitalsmile.goldberry.motion.Easing;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import io.github.digitalsmile.goldberry.natives.yoga.Align;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.Justify;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/// Every property a node resolved to, typed.
///
/// The end of the CSS pipeline and the start of the rendering one: ADR-0004 gives
/// each render object a `YGNode` and one of these. What arrives is a map of
/// property names to tokens; what leaves is values Yoga and Blend2D can be handed
/// without either of them knowing CSS exists.
///
/// ## The property split
///
/// §8 calls the split a design invariant, and it is visible in the field list:
/// [#direction()], [#justifyContent()], [#width()] and the rest **compile to
/// Yoga**, while [#background()], [#color()] and [#opacity()] are **resolved for
/// paint**. Nothing here does both, and nothing here is a string.
///
/// [#cursor()] belongs to neither half, which is the one thing §8's split did not
/// anticipate. It compiles to no engine: it rides along to the box tree so that
/// hit testing can read it off whichever rectangle the pointer is over (§7.3).
///
/// ## What is not here yet
///
/// §8's full list also has `flex-wrap`, `margin`, `min/max`, `position`, `inset`,
/// `aspect-ratio`, `overflow`, shadows, transforms, transitions and the font
/// properties. They are absent because
/// [io.github.digitalsmile.goldberry.layout.Box] cannot express them yet, and a
/// property that resolves into nothing is a property with no test that means
/// anything. Each arrives with the thing that paints it — which is why
/// [#decoration()] is here now and was not before: the design system's radii
/// (§1.5), its 1px borders and its focus ring (§2.2) all arrived together,
/// because they are drawn by one rounded-rectangle path.
///
/// Immutable, and every field has a default, so a node with no matching rules is
/// still a usable style rather than a null.
public record ComputedStyle(
        // --- layout: compiled to Yoga ---
        FlexDirection direction,
        Justify justifyContent,
        Align alignItems,
        StyleLength width,
        StyleLength height,
        Insets padding,
        StyleLength gap,
        double flexGrow,
        // --- paint: resolved into pixels ---
        int background,
        int color,
        double opacity,
        Decoration decoration,
        Typography typography,
        Transitions transitions,
        // --- neither: read by input, not by either engine ---
        Cursor cursor) {

    private static final Logger LOG = Logs.of(ComputedStyle.class);

    /// What a node with no declarations looks like.
    ///
    /// Matches Yoga's own defaults for the layout half and "invisible, black
    /// text, fully opaque" for the paint half — deliberately not Nord, because a
    /// default that is already themed makes a missing stylesheet look like a
    /// working one.
    public static final ComputedStyle INITIAL = new ComputedStyle(
            FlexDirection.ROW,
            Justify.FLEX_START,
            Align.STRETCH,
            StyleLength.UNDEFINED,
            StyleLength.UNDEFINED,
            Insets.ZERO,
            StyleLength.points(0),
            0,
            CssColor.TRANSPARENT,
            0xFF000000,
            1.0,
            Decoration.NONE,
            Typography.INITIAL,
            Transitions.NONE,
            Cursor.DEFAULT);

    public ComputedStyle {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(justifyContent, "justifyContent");
        Objects.requireNonNull(alignItems, "alignItems");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(padding, "padding");
        Objects.requireNonNull(gap, "gap");
        Objects.requireNonNull(decoration, "decoration");
        Objects.requireNonNull(typography, "typography");
        Objects.requireNonNull(transitions, "transitions");
        Objects.requireNonNull(cursor, "cursor");
    }

    /// Builds a style from resolved declarations.
    ///
    /// A declaration whose value will not parse is **dropped with a warning**,
    /// not fatal. This runs per node per restyle, inside the frame loop, and the
    /// rest of the node's style is still perfectly good — the same reasoning that
    /// makes an unresolvable `var()` a warning rather than an exception, and the
    /// opposite of the parse-time strictness in [CssSyntaxException].
    ///
    /// @param declarations property name to value tokens, as [StyleResolver]
    ///                     returns them
    /// @param context      what `em` and `rem` resolve against
    public static ComputedStyle of(Map<String, List<Token>> declarations, CssLength.Context context) {
        return of(declarations, context, null);
    }

    /// Builds a style from resolved declarations, **inheriting** from `parent`.
    ///
    /// CSS divides properties in two: `color` and the font properties inherit,
    /// while `background`, `padding` and the layout half do not. Only the
    /// inherited half is taken from `parent`; everything else starts at
    /// [#INITIAL], which is what makes a child's `background` transparent rather
    /// than its parent's colour.
    ///
    /// A null parent is the root, and inherits nothing.
    ///
    /// @param parent the resolved style of the nearest ancestor, or null
    public static ComputedStyle of(
            Map<String, List<Token>> declarations, CssLength.Context context,
            ComputedStyle parent) {

        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(context, "context");

        var style = parent == null ? INITIAL : INITIAL.inheritingFrom(parent);
        for (var entry : declarations.entrySet()) {
            style = style.with(entry.getKey(), entry.getValue(), context);
        }
        return style;
    }

    /// [#INITIAL] with every inherited property taken from `parent`.
    ///
    /// The whole of the inherited half, in one place, so that adding a property
    /// to it is one edit rather than one per call site. What is deliberately
    /// **not** here:
    ///
    /// - **`cursor`**, which CSS does inherit. Goldberry inherits it through the
    ///   stack of painted rectangles instead — hit testing reads it off whichever
    ///   box the pointer is over, because what the cursor should be is a question
    ///   about what is on screen
    ///   ([ADR-0057](../../../../../../book/src/adr/0057-the-cursor-rides-on-the-painted-box.md)).
    ///   Inheriting it here as well would be a second mechanism for one property,
    ///   and the two would disagree the first time a box was styled without an
    ///   element behind it.
    /// - **`opacity`**, which CSS does not inherit — its *effect* does, and the
    ///   painter accumulates it down the box tree
    ///   ([ADR-0064](../../../../../../book/src/adr/0064-a-rounded-rectangle-is-four-cubics.md)).
    ///   Inheriting the value here would then apply it once per level per
    ///   ancestor: a label under a control at 45% would be drawn at 20%.
    private ComputedStyle inheritingFrom(ComputedStyle parent) {
        // `transition` is deliberately absent: CSS does not inherit it, and a
        // panel that faded its background must not make every label inside it
        // fade too. A control declares what *it* animates.
        return INITIAL.color(parent.color()).typography(parent.typography());
    }

    /// One declaration applied, or this style unchanged if it does not apply.
    private ComputedStyle with(String property, List<Token> value, CssLength.Context context) {
        return switch (property) {
            case "flex-direction" -> keyword(value, FlexDirection.class)
                    .map(this::direction)
                    .orElseGet(() -> dropped(property, value));

            case "justify-content" -> keyword(value, Justify.class)
                    .map(this::justifyContent)
                    .orElseGet(() -> dropped(property, value));

            case "align-items" -> keyword(value, Align.class)
                    .map(this::alignItems)
                    .orElseGet(() -> dropped(property, value));

            case "width" -> length(value, context)
                    .map(this::width)
                    .orElseGet(() -> dropped(property, value));

            case "height" -> length(value, context)
                    .map(this::height)
                    .orElseGet(() -> dropped(property, value));

            // CSS's 1-4 value shorthand: one is every edge, two is
            // vertical/horizontal, three adds a bottom, four is clockwise from
            // the top. `padding: 0 12px` is the form a control is written in, so
            // supporting only the one-value form would mean no button could
            // state its own metrics.
            case "padding" -> insets(value, context)
                    .map(this::padding)
                    .orElseGet(() -> dropped(property, value));

            case "padding-top", "padding-right", "padding-bottom", "padding-left" ->
                    length(value, context)
                            .map(v -> padding(edge(padding, property, v)))
                            .orElseGet(() -> dropped(property, value));

            case "gap" -> length(value, context)
                    .map(this::gap)
                    .orElseGet(() -> dropped(property, value));

            case "flex-grow" -> number(value)
                    .filter(v -> v >= 0)
                    .map(this::flexGrow)
                    .orElseGet(() -> dropped(property, value));

            case "background", "background-color" -> colour(value)
                    .map(this::background)
                    .orElseGet(() -> dropped(property, value));

            case "color" -> colour(value)
                    .map(this::color)
                    .orElseGet(() -> dropped(property, value));

            case "opacity" -> number(value)
                    .map(v -> Math.max(0, Math.min(1, v)))
                    .map(this::opacity)
                    .orElseGet(() -> dropped(property, value));

            // --- the decoration half (docs/design-system.md §1.5, §2.2) -------
            //
            // A single radius rather than CSS's four corners: every radius the
            // design system pins is uniform (4, 8, 12, full), and four would be
            // four numbers to interpolate the day corners animate. `full` is
            // spelled `9999px` until there is a pill to need it.
            case "border-radius" -> points(value, context)
                    .map(v -> decoration(decoration.radius(v)))
                    .orElseGet(() -> dropped(property, value));

            case "border-width" -> points(value, context)
                    .map(v -> decoration(decoration.borderWidth(v)))
                    .orElseGet(() -> dropped(property, value));

            case "border-color" -> colour(value)
                    .map(v -> decoration(decoration.borderColor(v)))
                    .orElseGet(() -> dropped(property, value));

            case "outline-width" -> points(value, context)
                    .map(v -> decoration(decoration.outlineWidth(v)))
                    .orElseGet(() -> dropped(property, value));

            case "outline-color" -> colour(value)
                    .map(v -> decoration(decoration.outlineColor(v)))
                    .orElseGet(() -> dropped(property, value));

            // Negative is legal and meaningful: it pulls the ring inside the
            // border box, which is what a control flush against its neighbour
            // needs. Hence no clamp here and none in `Decoration`.
            case "outline-offset" -> points(value, context)
                    .map(v -> decoration(decoration.outlineOffset(v)))
                    .orElseGet(() -> dropped(property, value));

            case "border" -> stroke(value, context)
                    .map(v -> decoration(decoration.border(v.width(), v.argb())))
                    .orElseGet(() -> dropped(property, value));

            case "outline" -> stroke(value, context)
                    .map(v -> decoration(
                            decoration.outline(v.width(), v.argb(), decoration.outlineOffset())))
                    .orElseGet(() -> dropped(property, value));

            // --- the typography half (docs/design-system.md §1.4) ------------
            //
            // Inherited, which is what makes `panel { font-size: 13px }` reach
            // every label under it and is why `Typography` is one record: the
            // three travel together down the tree and are read together by the
            // one thing that resolves a `Font`.
            case "font-family" -> family(value)
                    .map(v -> typography(typography.family(v)))
                    .orElseGet(() -> dropped(property, value));

            case "font-size" -> points(value, context)
                    .filter(v -> v > 0)
                    .map(v -> typography(typography.size(v)))
                    .orElseGet(() -> dropped(property, value));

            case "font-weight" -> weight(value)
                    .map(v -> typography(typography.weight(v)))
                    .orElseGet(() -> dropped(property, value));

            // A bare number is a multiple of the font size -- `line-height: 1.4`
            // -- which is the form that survives a font-size change on a
            // descendant. A length is absolute. Both are CSS's.
            case "line-height" -> lineHeight(value, context)
                    .map(v -> typography(typography.lineHeight(v)))
                    .orElseGet(() -> dropped(property, value));

            // --- motion (docs/design-system.md §1.7) --------------------------
            //
            // Resolved by the cascade like everything else, which is what lets
            // `button` and `button:hover` declare different transitions and lets
            // an application turn one off by overriding a rule.
            case "transition" -> transitionList(value)
                    // A lambda and not `this::transitions`: the accessor and the
                    // wither share a name, and a method reference cannot say
                    // which.
                    .map(v -> transitions(v))
                    .orElseGet(() -> dropped(property, value));

            // Resolved here and read by neither engine: the cursor is carried
            // through the cascade to the box tree, where hit testing picks it up
            // (§7.3). The enum's names are CSS's, so `ew-resize` maps onto
            // `EW_RESIZE` by the same rule `space-between` maps onto Yoga.
            case "cursor" -> keyword(value, Cursor.class)
                    .map(this::cursor)
                    .orElseGet(() -> dropped(property, value));

            // Not an error. §8's property list is longer than this record, and a
            // stylesheet naming `box-shadow` before it is implemented should not
            // stop a window opening -- but it is logged, because "my shadow does
            // nothing" needs an answer.
            default -> {
                LOG.debug("ignoring unsupported property \"{}\"", property);
                yield this;
            }
        };
    }

    private ComputedStyle dropped(String property, List<Token> value) {
        LOG.warn("dropping \"{}\": {} is not a valid value", property, text(value));
        return this;
    }

    // --- withers -----------------------------------------------------------
    //
    // One per component, so applying a declaration names the field it sets
    // instead of repeating the other twelve in positional order. The old form
    // was correct and unreadable, and a reader could not tell a case that set
    // `width` from one that set `height` without counting commas -- which is
    // exactly the mistake a fourteen-argument constructor invites.

    public ComputedStyle direction(FlexDirection v) {
        return new ComputedStyle(v, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle justifyContent(Justify v) {
        return new ComputedStyle(direction, v, alignItems, width, height, padding, gap,
                flexGrow, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle alignItems(Align v) {
        return new ComputedStyle(direction, justifyContent, v, width, height, padding, gap,
                flexGrow, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle width(StyleLength v) {
        return new ComputedStyle(direction, justifyContent, alignItems, v, height, padding, gap,
                flexGrow, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle height(StyleLength v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, v, padding, gap,
                flexGrow, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle padding(Insets v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, v, gap,
                flexGrow, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle gap(StyleLength v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, v,
                flexGrow, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle flexGrow(double v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                v, background, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle background(int v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, v, color, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle color(int v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, background, v, opacity, decoration, typography, transitions, cursor);
    }

    public ComputedStyle opacity(double v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, background, color, v, decoration, typography, transitions, cursor);
    }

    public ComputedStyle decoration(Decoration v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, background, color, opacity, v, typography, transitions, cursor);
    }

    public ComputedStyle typography(Typography v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, background, color, opacity, decoration, v, transitions, cursor);
    }

    public ComputedStyle transitions(Transitions v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, background, color, opacity, decoration, typography, v, cursor);
    }

    public ComputedStyle cursor(Cursor v) {
        return new ComputedStyle(direction, justifyContent, alignItems, width, height, padding, gap,
                flexGrow, background, color, opacity, decoration, typography, transitions, v);
    }

    // --- value parsing -----------------------------------------------------

    /// A length that must be an absolute one, in logical pixels.
    ///
    /// A radius or a border width has no percentage form the painter could use:
    /// a percentage is resolved against the box's own size, and the box has no
    /// size until Yoga has run — which is after the cascade, in a different
    /// engine. Refused here so `border-radius: 50%` is a dropped declaration with
    /// a warning naming it, rather than a corner that is silently square.
    private static java.util.Optional<Double> points(List<Token> value, CssLength.Context context) {
        return length(value, context)
                .filter(StyleLength.Points.class::isInstance)
                .map(v -> (double) ((StyleLength.Points) v).value());
    }

    /// A font family name — an identifier or a quoted string.
    ///
    /// Only the **first** name of a list is taken. CSS's comma-separated list is
    /// a fallback chain, and §6.1 is explicit that there is no fallback cascade
    /// in v1: a character outside the bundled faces renders `.notdef`,
    /// deliberately. Honouring the rest of the list would be pretending to a
    /// mechanism that does not exist.
    private static java.util.Optional<String> family(List<Token> value) {
        for (var part : split(value)) {
            if (part.isEmpty()) {
                continue;
            }
            var token = part.getFirst();
            if (token.is(TokenType.IDENT) || token.is(TokenType.STRING)) {
                var name = token.text();
                // `Inter, sans-serif` and `"JetBrains Mono", monospace` both stop
                // at the first name; a trailing comma belongs to the list syntax.
                return java.util.Optional.of(
                        name.endsWith(",") ? name.substring(0, name.length() - 1) : name);
            }
        }
        return java.util.Optional.empty();
    }

    /// A CSS weight — a number, or `normal` / `bold`.
    ///
    /// Resolved to one of the two shipped faces here rather than carried as a
    /// number, so a weight no face can honour is discovered in the cascade and
    /// not in the painter.
    private static java.util.Optional<BundledFont.Weight> weight(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1) {
            return java.util.Optional.empty();
        }
        var token = tokens.getFirst();
        if (token.is(TokenType.IDENT)) {
            return switch (token.text().toLowerCase(Locale.ROOT)) {
                case "normal" -> java.util.Optional.of(BundledFont.Weight.REGULAR);
                case "bold" -> java.util.Optional.of(BundledFont.Weight.SEMI_BOLD);
                default -> java.util.Optional.empty();
            };
        }
        return java.util.Optional.ofNullable(CssLength.parseNumber(value))
                .filter(v -> v >= 1 && v <= 1000)
                .map(BundledFont.Weight::nearest);
    }

    /// A `line-height`: a length, or a bare number meaning a multiple of the size.
    ///
    /// Returned already negated in the ratio case, which is how [Typography]
    /// carries both forms in one field.
    private static java.util.Optional<Double> lineHeight(
            List<Token> value, CssLength.Context context) {

        var absolute = points(value, context);
        if (absolute.isPresent()) {
            return absolute.filter(v -> v > 0);
        }
        return java.util.Optional.ofNullable(CssLength.parseNumber(value))
                .filter(v -> v > 0)
                .map(v -> -v);
    }

    /// A `transition` declaration: a comma-separated list of
    /// `<property> <duration> [<easing>] [<delay>]`.
    ///
    /// ```css
    /// transition: background-color var(--gb-motion-fast) ease-enter,
    ///             color var(--gb-motion-fast) ease-enter;
    /// ```
    ///
    /// `none` is the whole list turned off, which is how a rule cancels a
    /// transition an earlier one declared.
    ///
    /// **The whole declaration is dropped if any entry is bad**, and the
    /// property that made it bad is named. Half a transition list is worse than
    /// none: the author sees two of their three properties moving and has
    /// nothing to tell them which one the parser refused. In particular
    /// `transition: width 200ms` is refused rather than ignored — §1.7 says
    /// layout properties never transition, and an author who asked for one is
    /// asking for something the system deliberately will not do.
    private java.util.Optional<Transitions> transitionList(List<Token> value) {

        var entries = splitOnCommas(value);
        if (entries.size() == 1 && entries.getFirst().size() == 1
                && entries.getFirst().getFirst().isIdent("none")) {
            return java.util.Optional.of(Transitions.NONE);
        }

        var parsed = new java.util.EnumMap<Transitions.Animatable, Transitions.Timing>(
                Transitions.Animatable.class);
        for (var entry : entries) {
            var parts = split(entry);
            if (parts.isEmpty()) {
                return java.util.Optional.empty();
            }

            Transitions.Animatable property = null;
            Easing easing = null;
            Double duration = null;
            Double delay = null;

            for (var part : parts) {
                if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
                    var name = part.getFirst().text();
                    var asProperty = Transitions.Animatable.parse(name);
                    if (asProperty != null && property == null) {
                        property = asProperty;
                        continue;
                    }
                    var asEasing = Easing.parse(name);
                    if (asEasing != null && easing == null) {
                        easing = asEasing;
                        continue;
                    }
                    return java.util.Optional.empty();
                }
                var time = milliseconds(part);
                if (time == null) {
                    return java.util.Optional.empty();
                }
                // CSS's rule: the first time is the duration, the second the
                // delay. Ordering carries meaning here because both are times
                // and neither has a unit the other does not.
                if (duration == null) {
                    duration = time;
                } else if (delay == null) {
                    delay = time;
                } else {
                    return java.util.Optional.empty();
                }
            }

            if (property == null || duration == null) {
                return java.util.Optional.empty();
            }
            parsed.put(property, new Transitions.Timing(
                    duration,
                    // §1.7's default for anything that does not say: an enter
                    // curve, because most transitions are something arriving.
                    easing == null ? Easing.EASE_ENTER : easing,
                    delay == null ? 0 : delay));
        }
        return java.util.Optional.of(new Transitions(parsed));
    }

    /// A time in `ms` or `s`, as milliseconds.
    ///
    /// Unitless zero is accepted, because `0` has no duration to be wrong about
    /// — the same allowance [CssLength] makes for a zero length. Anything else
    /// unitless is refused: `transition: color 200` almost certainly means
    /// milliseconds, and guessing would make the one stylesheet that meant
    /// seconds silently wrong.
    private static Double milliseconds(List<Token> part) {
        if (part.size() != 1) {
            return null;
        }
        var token = part.getFirst();
        if (token.is(TokenType.NUMBER)) {
            return token.numeric() == 0 ? 0.0 : null;
        }
        if (!token.is(TokenType.DIMENSION)) {
            return null;
        }
        return switch (token.unit()) {
            case "ms" -> token.numeric();
            case "s" -> token.numeric() * 1000;
            default -> null;
        };
    }

    /// Splits a value on commas — the top-level list separator of a shorthand
    /// that takes several.
    private static List<List<Token>> splitOnCommas(List<Token> value) {
        var entries = new java.util.ArrayList<List<Token>>();
        var current = new java.util.ArrayList<Token>();
        for (var token : value) {
            if (token.is(TokenType.COMMA)) {
                entries.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        entries.add(List.copyOf(current));
        return entries;
    }

    /// The width and colour of a `border:` or `outline:` shorthand.
    private record Stroke(double width, int argb) {
    }

    /// CSS's `<width> || <style> || <color>` shorthand, in any order.
    ///
    /// The style keyword is **accepted and discarded**: `solid` is the only one
    /// the painter can draw, and every rule that ships writes it. Refusing the
    /// others would mean `border: 1px dashed red` failing to parse rather than
    /// drawing a solid line, and drawing something is the more useful of the two
    /// wrong answers — but it is logged, so "my dashes are solid" has an answer.
    ///
    /// `none` sets the width to zero, which is how a rule turns a border off
    /// without having to say `border-width: 0`.
    private static java.util.Optional<Stroke> stroke(List<Token> value, CssLength.Context context) {
        Double width = null;
        Integer argb = null;
        for (var part : split(value)) {
            if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
                var keyword = part.getFirst().text().toLowerCase(Locale.ROOT);
                if (keyword.equals("none") || keyword.equals("hidden")) {
                    return java.util.Optional.of(new Stroke(0, CssColor.TRANSPARENT));
                }
                if (STROKE_STYLES.contains(keyword)) {
                    if (!keyword.equals("solid")) {
                        LOG.debug("drawing \"{}\" as solid: it is the only border style"
                                + " the painter has", keyword);
                    }
                    continue;
                }
            }
            var asLength = points(part, context);
            if (asLength.isPresent()) {
                width = asLength.get();
                continue;
            }
            var asColour = CssColor.parse(part);
            if (asColour == null) {
                return java.util.Optional.empty();
            }
            argb = asColour;
        }
        // A shorthand always resets what it does not mention, which is what makes
        // it a shorthand rather than three separate declarations: `border: red`
        // after `border: 2px solid blue` is a 0px border, not a red 2px one.
        return java.util.Optional.of(new Stroke(
                width == null ? 0 : width,
                argb == null ? CssColor.TRANSPARENT : argb));
    }

    private static final java.util.Set<String> STROKE_STYLES = java.util.Set.of(
            "solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset");

    private static java.util.Optional<Integer> colour(List<Token> value) {
        return java.util.Optional.ofNullable(CssColor.parse(value));
    }

    private static java.util.Optional<StyleLength> length(List<Token> value, CssLength.Context context) {
        return java.util.Optional.ofNullable(CssLength.parse(value, context));
    }

    /// CSS's 1-4 value edge shorthand.
    ///
    /// Empty if any part fails to parse, so `padding: 8px nonsense` is dropped
    /// whole rather than applied to two edges out of four — a half-applied
    /// shorthand is harder to see than one that did nothing.
    private static java.util.Optional<Insets> insets(List<Token> value, CssLength.Context context) {
        var parts = new java.util.ArrayList<StyleLength>();
        for (var token : split(value)) {
            var length = CssLength.parse(token, context);
            if (length == null) {
                return java.util.Optional.empty();
            }
            parts.add(length);
        }
        return java.util.Optional.ofNullable(switch (parts.size()) {
            case 1 -> Insets.all(parts.getFirst());
            case 2 -> Insets.symmetric(parts.get(0), parts.get(1));
            case 3 -> new Insets(parts.get(0), parts.get(1), parts.get(2), parts.get(1));
            case 4 -> new Insets(parts.get(0), parts.get(1), parts.get(2), parts.get(3));
            default -> null;
        });
    }

    /// Splits a value on whitespace into the component values of a shorthand.
    private static List<List<Token>> split(List<Token> value) {
        var parts = new java.util.ArrayList<List<Token>>();
        var current = new java.util.ArrayList<Token>();
        for (var token : value) {
            if (token.is(TokenType.WHITESPACE)) {
                if (!current.isEmpty()) {
                    parts.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        if (!current.isEmpty()) {
            parts.add(List.copyOf(current));
        }
        return parts;
    }

    /// One edge of an existing set replaced, for the longhand properties.
    private static Insets edge(Insets base, String property, StyleLength value) {
        return switch (property) {
            case "padding-top" -> new Insets(value, base.right(), base.bottom(), base.left());
            case "padding-right" -> new Insets(base.top(), value, base.bottom(), base.left());
            case "padding-bottom" -> new Insets(base.top(), base.right(), value, base.left());
            default -> new Insets(base.top(), base.right(), base.bottom(), value);
        };
    }

    private static java.util.Optional<Double> number(List<Token> value) {
        return java.util.Optional.ofNullable(CssLength.parseNumber(value));
    }

    /// A CSS keyword mapped onto a Yoga enum by name: `space-between` onto
    /// `SPACE_BETWEEN`, `flex-start` onto `FLEX_START`.
    ///
    /// By name rather than by a hand-written table because the two vocabularies
    /// already agree — Yoga's enums are the CSS names — and a table would be a
    /// second place for them to drift apart.
    private static <E extends Enum<E>> java.util.Optional<E> keyword(List<Token> value, Class<E> type) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 || !tokens.getFirst().is(TokenType.IDENT)) {
            return java.util.Optional.empty();
        }
        var name = tokens.getFirst().text().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return java.util.Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private static String text(List<Token> value) {
        var out = new StringBuilder();
        value.forEach(t -> out.append(t.cssText()));
        return out.toString();
    }
}
