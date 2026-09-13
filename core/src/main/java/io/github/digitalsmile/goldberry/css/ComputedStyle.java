package io.github.digitalsmile.goldberry.css;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.cascade.StyleResolver;
import io.github.digitalsmile.goldberry.css.cascade.Transitions;
import io.github.digitalsmile.goldberry.css.parse.CssSyntaxException;
import io.github.digitalsmile.goldberry.css.parse.Token;
import io.github.digitalsmile.goldberry.css.parse.TokenType;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.css.value.CssLength;
import io.github.digitalsmile.goldberry.css.value.Shadow;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.layout.Align;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Justify;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Limits;
import io.github.digitalsmile.goldberry.layout.Overflow;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.layout.Wrap;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.motion.Easing;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;
import io.github.digitalsmile.goldberry.text.flow.TextOverflow;
import io.github.digitalsmile.goldberry.text.flow.WhiteSpace;

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
/// [Box] cannot express them yet, and a
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
        // The per-child companion of `align-items`, which §8 listed and this did
        // not: a child shorter than its row had no way to say where in the cross
        // axis it sat, and a tab strip's `+` is what found it (ADR-0244).
        // `Align.AUTO` is the default and is the only value that means anything
        // *here* rather than on the parent -- it is the enum's own word for
        // "whatever my container said".
        Align alignSelf,
        // §8 has listed `flex-wrap` from the start and nothing had needed it
        // either: every row in the catalog was a row that fitted, until `select
        // multiple` grew a row of chips (ADR-0192).
        Wrap wrap,
        Length width,
        Length height,
        // §8 listed `min-width` / `max-width` from the start and nothing had
        // needed them: every box in the catalog was either its content's size or
        // a fixed one, so `dialog`, `toast` and `tooltip` each wrote a *width*
        // where they meant a maximum and lived with it (ADR-0181).
        Limits limits,
        Insets padding,
        Length gap,
        double flexGrow,
        double flexShrink,
        // `position` and `inset` are the layout half's answer to a box that is
        // not where the flow would put it. §8 listed them from the start and
        // nothing had needed one: every widget until `segmented`'s travelling
        // indicator was a box beside another box (ADR-0099).
        Position position,
        Insets inset,
        // §8 has listed `overflow` since the beginning and nothing had needed
        // it: until `scroll`, no box in the catalog had content it was meant to
        // hide rather than grow around. Yoga reads it for *sizing* — a HIDDEN
        // parent does not stretch to fit a child — and the painter reads it for
        // the clip, which are two different jobs from one keyword
        // (ADR-0114).
        Overflow overflow,
        // --- text: read by the paragraph, which is both engines at once ---
        // `white-space` **inherits** and `text-overflow` does not, which is CSS's
        // rule and the only reason these are two components rather than one
        // `TextFlow`: a bundle cannot be half-inherited. [#textFlow()] puts them
        // back together for everything below the cascade (ADR-0255).
        WhiteSpace whiteSpace,
        TextOverflow textOverflow,
        // The too-narrow half of the same question, and the third property on
        // the same value. It inherits, like `white-space` and unlike
        // `text-overflow`, which is CSS's split and the reason these are three
        // components (ADR-0256).
        TextAlign textAlign,
        // --- paint: resolved into pixels ---
        int background,
        int color,
        double opacity,
        Decoration decoration,
        Typography typography,
        Transitions transitions,
        // Not finished when the cascade produces it, unlike everything above.
        // `transform: translate(50%)` and the `transform-origin` default of
        // `50% 50%` are proportions of the box, and the box has no size until
        // Yoga has run -- so what is carried is the functions, and the painter
        // resolves them (ADR-0068).
        Transform transform,
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
            // "Defer to my container", which is Yoga's default and CSS's: a
            // child that says nothing is aligned by `align-items` alone.
            Align.AUTO,
            // One line, however much it overflows -- Yoga's default and CSS's.
            Wrap.NO_WRAP,
            Length.UNDEFINED,
            Length.UNDEFINED,
            // No limit on any axis, which is Yoga's default and CSS's. Undefined
            // rather than zero: a minimum of zero constrains nothing, but a
            // maximum of zero is a box that may not exist.
            Limits.NONE,
            Insets.ZERO,
            Length.points(0),
            0,
            // CSS's default and Yoga's under `useWebDefaults`: a width is a
            // preferred width, and a cramped row may take it back.
            1,
            Position.RELATIVE,
            // Not `Insets.ZERO`: an inset of zero pins a node to its container's
            // edge, and "no inset at all" is what a node that never mentions one
            // must get. Yoga spells that `undefined`, and the difference only
            // shows on an absolute node -- where zero would stretch it and
            // undefined leaves it where the alignment put it.
            Insets.all(Length.UNDEFINED),
            // CSS's initial value, and Yoga's: a box that says nothing lets its
            // content spill rather than cutting it off, because a clip nobody
            // asked for is content that vanishes with no rule to blame.
            Overflow.VISIBLE,
            // CSS's initial values for both. `normal` rather than `nowrap`
            // because prose is what a paragraph usually is, and `clip` rather
            // than `ellipsis` because a mark on a box nobody asked to truncate
            // is text quietly going missing -- the same argument that keeps
            // `overflow` at `visible`.
            WhiteSpace.NORMAL,
            TextOverflow.CLIP,
            // The leading edge, which is CSS's initial value and where every
            // line in the toolkit sat before the property existed.
            TextAlign.START,
            CssColor.TRANSPARENT,
            0xFF000000,
            1.0,
            Decoration.NONE,
            Typography.INITIAL,
            Transitions.NONE,
            Transform.NONE,
            Cursor.DEFAULT);

    public ComputedStyle {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(justifyContent, "justifyContent");
        Objects.requireNonNull(alignItems, "alignItems");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(padding, "padding");
        Objects.requireNonNull(gap, "gap");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(overflow, "overflow");
        Objects.requireNonNull(whiteSpace, "whiteSpace");
        Objects.requireNonNull(textOverflow, "textOverflow");
        Objects.requireNonNull(textAlign, "textAlign");
        Objects.requireNonNull(decoration, "decoration");
        Objects.requireNonNull(typography, "typography");
        Objects.requireNonNull(transitions, "transitions");
        Objects.requireNonNull(transform, "transform");
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
            Map<String, List<Token>> declarations, CssLength.Context context, @Nullable ComputedStyle parent) {

        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(context, "context");

        var style = parent == null ? INITIAL : INITIAL.inheritingFrom(parent);

        // `font-size` is resolved **first and against the parent's size**, and
        // everything else against the size that produced — which is CSS's rule
        // and not a refinement of it ([ADR-0242]). `1.2em` on `font-size` means
        // "a fifth larger than my parent"; `1.2em` on `padding` means "a fifth
        // larger than my own text". One pass with one context cannot say both,
        // and the old code said neither: it used `CssLength.Context`'s constant
        // for every node at every depth, so `1em` was 16 even where the computed
        // font-size was `Typography.INITIAL`'s 13.
        var parentSize = parent == null
                ? context.fontSize()
                : (float) parent.typography().size();
        var fontSize = declarations.get("font-size");
        if (fontSize != null) {
            style = style.with("font-size", fontSize, new CssLength.Context(parentSize, context.rootFontSize()));
        }
        // Undeclared is the ordinary case and needs no branch: the size is
        // whatever was inherited, which is exactly what `em` should resolve
        // against.
        var own = new CssLength.Context((float) style.typography().size(), context.rootFontSize());
        for (var entry : declarations.entrySet()) {
            if (!"font-size".equals(entry.getKey())) {
                style = style.with(entry.getKey(), entry.getValue(), own);
            }
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
    ///   (ADR-0057).
    ///   Inheriting it here as well would be a second mechanism for one property,
    ///   and the two would disagree the first time a box was styled without an
    ///   element behind it.
    /// - **`opacity`**, which CSS does not inherit — its *effect* does, and the
    ///   painter accumulates it down the box tree
    ///   (ADR-0064).
    ///   Inheriting the value here would then apply it once per level per
    ///   ancestor: a label under a control at 45% would be drawn at 20%.
    /// Whether a child would resolve identically against `other` as against
    /// this — that is, whether the two agree on every **inherited** property.
    ///
    /// The inherited half is `color` and `typography`, which is
    /// [#inheritingFrom]'s whole body, plus nothing: a child reads no other
    /// component of its parent's style, so two parents that agree on these are
    /// indistinguishable from below ([ADR-0248]).
    ///
    /// It exists because the alternative is `equals`, and `equals` compares the
    /// **transform** — which nothing inherits and which a `scroll` moves on every
    /// frame of a gesture, so every node inside a scrolling viewport re-resolved
    /// for a change no child could see.
    ///
    /// Keeping this beside [#inheritingFrom] is deliberate: they are the same
    /// list read two ways, and a property that starts inheriting has to be added
    /// to both or the cache goes stale rather than merely cold.
    public boolean inheritsSameAs(ComputedStyle other) {
        return other != null
                && color == other.color
                && typography.equals(other.typography)
                && whiteSpace == other.whiteSpace
                && textAlign == other.textAlign;
    }

    private ComputedStyle inheritingFrom(ComputedStyle parent) {
        // `transition` is deliberately absent: CSS does not inherit it, and a
        // panel that faded its background must not make every label inside it
        // fade too. A control declares what *it* animates.
        // `white-space` is on this list and `text-overflow` is not, which is
        // exactly what CSS says about the pair. It is also what an author means:
        // `menu { white-space: nowrap }` is a statement about the rows, and
        // `text-overflow` on a container that draws no text of its own would
        // otherwise put a mark on every label underneath it (ADR-0255).
        return INITIAL.color(parent.color())
                .typography(parent.typography())
                .whiteSpace(parent.whiteSpace())
                // `text-align` inherits in CSS and is written on a *container*
                // far more often than on the thing that draws the text -- which
                // is exactly why it has to: `slider-value { text-align: end }`
                // is a rule about a node whose text is its own, and
                // `column.numeric { text-align: end }` is one about nodes whose
                // text is not (ADR-0256).
                .textAlign(parent.textAlign());
    }

    /// One declaration applied, or this style unchanged if it does not apply.
    private ComputedStyle with(String property, List<Token> value, CssLength.Context context) {
        return switch (property) {
            case "flex-direction" ->
                keyword(value, FlexDirection.class).map(this::direction).orElseGet(() -> dropped(property, value));

            case "justify-content" ->
                keyword(value, Justify.class).map(this::justifyContent).orElseGet(() -> dropped(property, value));

            case "align-items" ->
                keyword(value, Align.class).map(this::alignItems).orElseGet(() -> dropped(property, value));

            // The per-child companion, and the one place `auto` is a value rather
            // than a missing one: it is the enum's word for "whatever my
            // container said", so `align-self: auto` is a real declaration that
            // undoes a more general rule (ADR-0244).
            case "align-self" ->
                keyword(value, Align.class).map(this::alignSelf).orElseGet(() -> dropped(property, value));

            // Not `keyword(value, Wrap.class)`: CSS spells the default `nowrap`
            // as one word and `YGWrap` spells it `NoWrap`, so the two disagree
            // by a hyphen the generic parser inserts. `wrap-reverse` and `wrap`
            // agree, which is what makes this a one-keyword exception rather
            // than a table.
            case "flex-wrap" -> wrap(value).map(this::wrap).orElseGet(() -> dropped(property, value));

            case "width" -> length(value, context).map(this::width).orElseGet(() -> dropped(property, value));

            case "height" -> length(value, context).map(this::height).orElseGet(() -> dropped(property, value));

            // CSS's 1-4 value shorthand: one is every edge, two is
            // vertical/horizontal, three adds a bottom, four is clockwise from
            // the top. `padding: 0 12px` is the form a control is written in, so
            // supporting only the one-value form would mean no button could
            // state its own metrics.
            case "padding" -> insets(value, context).map(this::padding).orElseGet(() -> dropped(property, value));

            case "padding-top", "padding-right", "padding-bottom", "padding-left" ->
                length(value, context)
                        .map(v -> padding(edge(padding, edgeOf(property), v)))
                        .orElseGet(() -> dropped(property, value));

            // §2 asks a `dialog` for "min width 320, max 80% window", and until
            // these four existed the scrim's padding was a de-facto maximum with
            // no minimum at all: a dialog with three words in it was three words
            // wide. `toast` and `tooltip` each wrote a *width* meaning a maximum
            // for the same reason (ADR-0181).
            case "min-width" ->
                length(value, context).map(v -> limits(limits.minWidth(v))).orElseGet(() -> dropped(property, value));

            case "max-width" ->
                length(value, context).map(v -> limits(limits.maxWidth(v))).orElseGet(() -> dropped(property, value));

            case "min-height" ->
                length(value, context).map(v -> limits(limits.minHeight(v))).orElseGet(() -> dropped(property, value));

            case "max-height" ->
                length(value, context).map(v -> limits(limits.maxHeight(v))).orElseGet(() -> dropped(property, value));

            case "gap" -> length(value, context).map(this::gap).orElseGet(() -> dropped(property, value));

            case "flex-grow" ->
                number(value).filter(v -> v >= 0).map(this::flexGrow).orElseGet(() -> dropped(property, value));

            // §8 lists `flex-grow/shrink/basis` and only grow was implemented, so
            // every fixed-size part in the catalog was negotiable and a narrow
            // window squashed it (ADR-0076).
            case "flex-shrink" ->
                number(value).filter(v -> v >= 0).map(this::flexShrink).orElseGet(() -> dropped(property, value));

            // §8 has listed `position` since the beginning and nothing had
            // needed it: a segmented control's indicator is the first box in
            // the catalog that has to sit *over* its siblings rather than
            // beside them. `static` is admitted as well as CSS's two, because it
            // is how a container declines to be the thing an absolute
            // descendant is placed against (ADR-0099).
            case "position" ->
                keyword(value, Position.class).map(this::position).orElseGet(() -> dropped(property, value));

            // The same 1-4 shorthand `padding` takes, over the same [Insets] --
            // an inset is a padding measured from the outside.
            case "inset" -> insets(value, context).map(this::inset).orElseGet(() -> dropped(property, value));

            case "top", "right", "bottom", "left" ->
                length(value, context)
                        .map(v -> inset(edge(inset, edgeOf(property), v)))
                        .orElseGet(() -> dropped(property, value));

            // Both of Yoga's non-visible values are admitted, and they size
            // identically. What separates them is above the layout engine: a
            // `scroll` offers scrollbars for `scroll` and `auto` and none for
            // `hidden`, so the keyword is how a stylesheet says which of the
            // two a box is (ADR-0114). `auto` resolves to SCROLL and is told
            // apart by the widget, not by the box.
            case "overflow" -> overflow(value).map(this::overflow).orElseGet(() -> dropped(property, value));
            // §8 has listed neither, and four widgets reached for the pair and
            // found nothing: a menu row, an `option`, a `select-value` and a
            // segment all overflow their cells rather than being cut, because a
            // box with text is a measured leaf and narrowing it *wraps* the text
            // instead of overflowing it. `white-space: nowrap` is what stops that
            // and `text-overflow` is what marks the result (ADR-0235, ADR-0255).
            case "white-space" ->
                keyword(value, WhiteSpace.class).map(this::whiteSpace).orElseGet(() -> dropped(property, value));
            case "text-overflow" ->
                keyword(value, TextOverflow.class).map(this::textOverflow).orElseGet(() -> dropped(property, value));
            // §8 has listed `text-align` from the start and §8's own note said
            // `Box` could not express it. That was true of `Box` and never true
            // of the paragraph, which has always known its lines' widths -- so
            // it is the paint that places them, and `Box` is untouched
            // (ADR-0256). `left` and `right` are refused for ADR-0247's reason:
            // they are not the same as `start`/`end` under RTL.
            case "text-align" ->
                keyword(value, TextAlign.class).map(this::textAlign).orElseGet(() -> dropped(property, value));

            // `background` is CSS's shorthand and `background-color` its longhand,
            // and the toolkit implements the one layer of it that exists: a
            // colour. The difference between them is `none` — valid in the
            // shorthand, where it means "no layer at all", and not a colour, so
            // not a value the longhand takes. `select text-input` is what wanted
            // it, for the same sentence that made it write `border: none` on the
            // line above: an editor inside a control is that control's interior,
            // with no fill of its own (ADR-0183).
            case "background" -> backgroundLayer(value).map(this::background).orElseGet(() -> dropped(property, value));

            case "background-color" -> colour(value).map(this::background).orElseGet(() -> dropped(property, value));

            case "color" -> colour(value).map(this::color).orElseGet(() -> dropped(property, value));

            case "opacity" ->
                number(value)
                        .map(v -> Math.max(0, Math.min(1, v)))
                        .map(this::opacity)
                        .orElseGet(() -> dropped(property, value));

            // --- the decoration half (docs/design-system.md §1.5, §2.2) -------
            //
            // CSS's 1-4 corner shorthand, over [Corners]. Every radius the design
            // system pins is uniform (4, 8, 12, full) and writes one number; the
            // second form is for a box that meets a rounded parent on one edge
            // and a square sibling on the other, which is `group-box-title` and
            // which ADR-0216 is about. `full` is spelled `9999px`.
            case "border-radius" ->
                corners(value, context)
                        .map(v -> decoration(decoration.corners(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border-width" ->
                points(value, context)
                        .map(v -> decoration(decoration.borderWidth(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border-color" ->
                colour(value).map(v -> decoration(decoration.borderColor(v))).orElseGet(() -> dropped(property, value));

            case "outline-width" ->
                points(value, context)
                        .map(v -> decoration(decoration.outlineWidth(v)))
                        .orElseGet(() -> dropped(property, value));

            case "outline-color" ->
                colour(value)
                        .map(v -> decoration(decoration.outlineColor(v)))
                        .orElseGet(() -> dropped(property, value));

            // Negative is legal and meaningful: it pulls the ring inside the
            // border box, which is what a control flush against its neighbour
            // needs. Hence no clamp here and none in `Decoration`.
            case "outline-offset" ->
                points(value, context)
                        .map(v -> decoration(decoration.outlineOffset(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border" ->
                stroke(value, context)
                        .map(v -> decoration(decoration.border(v.width(), v.argb())))
                        .orElseGet(() -> dropped(property, value));

            case "outline" ->
                stroke(value, context)
                        .map(v -> decoration(decoration.outline(v.width(), v.argb(), decoration.outlineOffset())))
                        .orElseGet(() -> dropped(property, value));

            // §8 listed it from the beginning and two ADRs turned it down: a
            // card's elevation became an edge (ADR-0166) because `Box` had no
            // field for a shadow and nothing in the toolkit painted outside a
            // box's own rectangle. Both of those stopped being true -- the ring
            // paints outside the rectangle and the damage rectangle already grows
            // for it -- so the reason left was the drawing, and a rasterizer
            // with no blur can still draw a fade out of the one primitive it is
            // fastest at (ADR-0310). `--gb-elevation-1/-2/-3` are what a rule
            // should name; the numbers in them are the theme's, because the same
            // alpha that reads as a shadow on nord-light is invisible on
            // nord-dark.
            case "box-shadow" -> {
                var parsed = Shadow.parse(value, context);
                yield parsed == null ? dropped(property, value) : decoration(decoration.shadow(parsed));
            }

            // --- the typography half (docs/design-system.md §1.4) ------------
            //
            // Inherited, which is what makes `panel { font-size: 13px }` reach
            // every label under it and is why `Typography` is one record: the
            // three travel together down the tree and are read together by the
            // one thing that resolves a `Font`.
            case "font-family" ->
                family(value).map(v -> typography(typography.family(v))).orElseGet(() -> dropped(property, value));

            case "font-size" ->
                points(value, context)
                        .filter(v -> v > 0)
                        .map(v -> typography(typography.size(v)))
                        .orElseGet(() -> dropped(property, value));

            case "font-weight" ->
                weight(value).map(v -> typography(typography.weight(v))).orElseGet(() -> dropped(property, value));

            // A bare number is a multiple of the font size -- `line-height: 1.4`
            // -- which is the form that survives a font-size change on a
            // descendant. A length is absolute. Both are CSS's.
            case "line-height" ->
                lineHeight(value, context)
                        .map(v -> typography(typography.lineHeight(v)))
                        .orElseGet(() -> dropped(property, value));

            // --- motion (docs/design-system.md §1.7) --------------------------
            //
            // Resolved by the cascade like everything else, which is what lets
            // `button` and `button:hover` declare different transitions and lets
            // an application turn one off by overriding a rule.
            case "transition" ->
                transitionList(value)
                        // A lambda and not `this::transitions`: the accessor and the
                        // wither share a name, and a method reference cannot say
                        // which.
                        .map(v -> transitions(v))
                        .orElseGet(() -> dropped(property, value));

            // Unlike every other property here, these two are resolved but not
            // finished: what lands is the function list and the origin, and the
            // matrix comes out of them once Yoga has given the box a size.
            //
            // `transform-origin` is applied to whatever transform is already
            // there and vice versa, so the two declarations commute -- which
            // matters because CSS puts no ordering on them and an author writing
            // the origin first should not lose it.
            case "transform" -> {
                var parsed = Transform.parse(value, transform.origin(), context);
                yield parsed == null ? dropped(property, value) : transform(parsed);
            }

            case "transform-origin" -> {
                var parsed = Transform.parseOrigin(value, context);
                yield parsed == null ? dropped(property, value) : transform(transform.origin(parsed));
            }

            // Resolved here and read by neither engine: the cursor is carried
            // through the cascade to the box tree, where hit testing picks it up
            // (§7.3). The enum's names are CSS's, so `ew-resize` maps onto
            // `EW_RESIZE` by the same rule `space-between` maps onto Yoga.
            case "cursor" -> keyword(value, Cursor.class).map(this::cursor).orElseGet(() -> dropped(property, value));

            // Not an error. §8's property list is longer than this record, and a
            // stylesheet naming a property before it is implemented should not
            // stop a window opening -- but it is logged, because "my
            // `backdrop-filter` does nothing" needs an answer. `box-shadow` was
            // this comment's example for two hundred ADRs and is a case above
            // now (ADR-0310); `backdrop-filter` and `letter-spacing` are what is
            // left of §8's list.
            default -> {
                LOG.debug("ignoring unsupported property \"{}\"", property);
                yield this;
            }
        };
    }

    /// Every declaration already reported as unusable.
    ///
    /// A stylesheet is **static**, so a declaration that cannot be applied cannot
    /// be applied on the next frame either — but a style is resolved per element
    /// per invalidation, so one typo in one rule reported itself sixty times a
    /// second for as long as the screen it was on kept moving. That is not a
    /// louder warning, it is a quieter log: the one line saying `align-items:
    /// start` is not a value gets lost in the thousand identical lines after it.
    ///
    /// Keyed by property **and** value, so two different bad values for one
    /// property are two reports. Bounded because a stylesheet has finitely many
    /// declarations — with a cap anyway, since a `var()` resolving to a fresh bad
    /// value each frame would otherwise be a slow leak in a diagnostic.
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// How many distinct drops are remembered before the deduplication gives up
    /// and lets them all through.
    ///
    /// Letting them through rather than falling silent: past this many distinct
    /// bad declarations something is generating them, and a log that went quiet
    /// would hide it.
    private static final int REPORT_LIMIT = 512;

    private ComputedStyle dropped(String property, List<Token> value) {
        var text = text(value);
        // `add` returns false when it was already there, which is the whole test.
        if (REPORTED.size() >= REPORT_LIMIT || REPORTED.add(property + ':' + text)) {
            LOG.warn("dropping \"{}\": {} is not a valid value", property, text);
        }
        return this;
    }

    /// Whether the engine does anything at all with `property: value`.
    ///
    /// The question a lint asks, and it is answered by **running the engine**
    /// rather than by consulting a list of what the engine supports — which is
    /// the whole point. A list would be a copy of this switch, and a copy drifts:
    /// a property added tomorrow would need an edit in two places and would be
    /// reported as unsupported until somebody made the second one.
    ///
    /// ## How it can be this short
    ///
    /// [#with] returns **`this`** in exactly two places and both of them are
    /// failures: the `default` arm, where the property is not in §8's subset, and
    /// [#dropped], where it is and the value would not parse. Every success goes
    /// through a wither, and every wither allocates. So identity *is* the answer,
    /// and it cannot fall out of step with the behaviour because it is the
    /// behaviour.
    ///
    /// That is a real constraint on this class rather than an observation about
    /// it: an arm that returned `this` on success would silently become "does
    /// nothing" here, and `ComputedStyleTest` says so.
    ///
    /// **The two failures are not told apart**, deliberately. Distinguishing them
    /// would mean the engine reporting rather than being asked — a sink threaded
    /// through thirty switch arms, for a difference the author reads off §8's
    /// list in either case ([ADR-0257]).
    ///
    /// Custom properties are **not** this method's business and answer `false`:
    /// `--gb-accent` reaches the `default` arm and is not a fault, because the
    /// resolver has already consumed it for `var()` substitution ([ADR-0049]). A
    /// caller that does not filter them reports every token in the theme.
    ///
    /// @param property the property name, already lowercased by the parser
    /// @param value    the declaration's tokens, with `var()` already substituted
    /// @param context  what `em` and `rem` resolve against
    public static boolean applies(String property, List<Token> value, CssLength.Context context) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(context, "context");
        return INITIAL.with(property, value, context) != INITIAL;
    }

    /// Forgets what has been reported, so a test can drive the same bad
    /// declaration twice.
    ///
    /// This exists for tests and for nothing else: a cache that could not be
    /// cleared would make the second test in a class depend on whether the first
    /// one had already tripped the same warning. It is **public** because one of
    /// those tests is in another module — `:example`'s lint over the toolkit's own
    /// stylesheets reads what the cascade said about them (ADR-0215), and a drop
    /// another test in the same JVM had already reported would be a lint that
    /// passed by seeing nothing at all (ADR-0216).
    public static void forgetReportedDrops() {
        REPORTED.clear();
    }

    // --- withers -----------------------------------------------------------
    //
    // One per component, so applying a declaration names the field it sets
    // instead of repeating the other twelve in positional order. The old form
    // was correct and unreadable, and a reader could not tell a case that set
    // `width` from one that set `height` without counting commas -- which is
    // exactly the mistake a fourteen-argument constructor invites.

    public ComputedStyle direction(FlexDirection v) {
        return new ComputedStyle(
                v,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle justifyContent(Justify v) {
        return new ComputedStyle(
                direction,
                v,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle alignItems(Align v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                v,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle alignSelf(Align v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                v,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle wrap(Wrap v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                v,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle width(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                v,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle height(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                v,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle padding(Insets v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                v,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    /// See [#limits]. Public for the same reason every other wither is: the
    /// cascade builds a style one declaration at a time.
    public ComputedStyle limits(Limits v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                v,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle gap(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                v,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle flexGrow(double v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                v,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle flexShrink(double v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                v,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle position(Position v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                v,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle inset(Insets v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                v,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle overflow(Overflow v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                v,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle whiteSpace(WhiteSpace v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                v,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle textOverflow(TextOverflow v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                v,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle textAlign(TextAlign v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                v,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    /// The three text properties as the one value everything below the cascade
    /// reads.
    ///
    /// The cascade is the only place they are apart, and it is apart for one
    /// reason: `white-space` and `text-align` inherit and `text-overflow` does
    /// not, so a single component could not have been handed down correctly. Past
    /// that point they are always read together — by the measure function, which
    /// needs the first, and by the painter, which needs all three — so this is
    /// what
    /// [Box#style(ComputedStyle)] carries onto a [Box.Text] and what a widget
    /// building an anonymous label box passes to
    /// [Box#text(io.github.digitalsmile.goldberry.text.Paragraph, int,
    /// TextFlow)] ([ADR-0255]).
    public TextFlow textFlow() {
        return new TextFlow(whiteSpace, textOverflow, textAlign);
    }

    public ComputedStyle background(int v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                v,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle color(int v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                v,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle opacity(double v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                v,
                decoration,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle decoration(Decoration v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                v,
                typography,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle typography(Typography v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                v,
                transitions,
                transform,
                cursor);
    }

    public ComputedStyle transitions(Transitions v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                v,
                transform,
                cursor);
    }

    public ComputedStyle transform(Transform v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                v,
                cursor);
    }

    public ComputedStyle cursor(Cursor v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                wrap,
                width,
                height,
                limits,
                padding,
                gap,
                flexGrow,
                flexShrink,
                position,
                inset,
                overflow,
                whiteSpace,
                textOverflow,
                textAlign,
                background,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                transform,
                v);
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
        return length(value, context).filter(Length.Points.class::isInstance).map(v ->
                (double) ((Length.Points) v).value());
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
                return java.util.Optional.of(name.endsWith(",") ? name.substring(0, name.length() - 1) : name);
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
    private static java.util.Optional<Double> lineHeight(List<Token> value, CssLength.Context context) {

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
        if (entries.size() == 1
                && entries.getFirst().size() == 1
                && entries.getFirst().getFirst().isIdent("none")) {
            return java.util.Optional.of(Transitions.NONE);
        }

        var parsed = new java.util.EnumMap<Transitions.Animatable, Transitions.Timing>(Transitions.Animatable.class);
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
            parsed.put(
                    property,
                    new Transitions.Timing(
                            duration,
                            // §1.7's default for anything that does not say: an enter
                            // curve, because most transitions are something arriving.
                            easing == null ? Easing.EASE_ENTER : easing,
                            delay == null ? 0 : delay));
        }
        return java.util.Optional.of(new Transitions(parsed));
    }

    /// The milliseconds a duration value says, or empty when it is not one.
    ///
    /// [#milliseconds]'s public face, and it exists for the reason
    /// [#applies] does: something above the cascade has a question only the
    /// cascade's own parser can answer honestly. Here it is a **duration custom
    /// property** — `--gb-tooltip-delay: 500ms` — read by the launcher, which
    /// schedules a timer and has no `ComputedStyle` to read it off
    /// ([ADR-0262]).
    ///
    /// Reusing this rather than writing a second `ms`/`s` reader is the whole
    /// point: two parsers for one syntax disagree the day either grows a unit,
    /// and this one already refuses a bare `200` for a stated reason.
    public static java.util.OptionalDouble durationMillis(List<Token> value) {
        Objects.requireNonNull(value, "value");
        var parsed = milliseconds(value);
        return parsed == null ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(parsed);
    }

    /// A time in `ms` or `s`, as milliseconds.
    ///
    /// Unitless zero is accepted, because `0` has no duration to be wrong about
    /// — the same allowance [CssLength] makes for a zero length. Anything else
    /// unitless is refused: `transition: color 200` almost certainly means
    /// milliseconds, and guessing would make the one stylesheet that meant
    /// seconds silently wrong.
    private static @Nullable Double milliseconds(List<Token> part) {
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
    private record Stroke(double width, int argb) {}

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
                        LOG.debug("drawing \"{}\" as solid: it is the only border style" + " the painter has", keyword);
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
        return java.util.Optional.of(new Stroke(width == null ? 0 : width, argb == null ? CssColor.TRANSPARENT : argb));
    }

    private static final java.util.Set<String> STROKE_STYLES =
            java.util.Set.of("solid", "dashed", "dotted", "double", "groove", "ridge", "inset", "outset");

    private static java.util.Optional<Integer> colour(List<Token> value) {
        return java.util.Optional.ofNullable(CssColor.parse(value));
    }

    private static java.util.Optional<Length> length(List<Token> value, CssLength.Context context) {
        return java.util.Optional.ofNullable(CssLength.parse(value, context));
    }

    /// CSS's 1-4 value edge shorthand.
    ///
    /// Empty if any part fails to parse, so `padding: 8px nonsense` is dropped
    /// whole rather than applied to two edges out of four — a half-applied
    /// shorthand is harder to see than one that did nothing.
    private static java.util.Optional<Insets> insets(List<Token> value, CssLength.Context context) {
        var parts = new java.util.ArrayList<Length>();
        for (var token : split(value)) {
            var length = CssLength.parse(token, context);
            if (length == null) {
                return java.util.Optional.empty();
            }
            parts.add(length);
        }
        return java.util.Optional.ofNullable(
                switch (parts.size()) {
                    case 1 -> Insets.all(parts.getFirst());
                    case 2 -> Insets.symmetric(parts.get(0), parts.get(1));
                    case 3 -> new Insets(parts.get(0), parts.get(1), parts.get(2), parts.get(1));
                    case 4 -> new Insets(parts.get(0), parts.get(1), parts.get(2), parts.get(3));
                    default -> null;
                });
    }

    /// CSS's 1-4 value corner shorthand, in CSS's order.
    ///
    /// The order is the one CSS names the corners in — top-left, top-right,
    /// bottom-right, bottom-left, clockwise from the top-left — and the fill-in
    /// rule is CSS's own: one value is every corner, two are the two diagonals,
    /// and three name the fourth as the opposite of the second.
    ///
    /// Empty if any part fails to parse, for [#insets]'s reason: a half-applied
    /// shorthand is harder to see than one that did nothing. That is also what
    /// refuses the elliptical form — `10px / 20px` has a `/` in it, which is not
    /// a length, and `RoundRect` draws circles.
    private static java.util.Optional<Corners> corners(List<Token> value, CssLength.Context context) {
        var parts = new java.util.ArrayList<Double>();
        for (var token : split(value)) {
            var radius = points(token, context);
            if (radius.isEmpty()) {
                return java.util.Optional.empty();
            }
            parts.add(radius.get());
        }
        return java.util.Optional.ofNullable(
                switch (parts.size()) {
                    case 1 -> Corners.all(parts.getFirst());
                    case 2 -> new Corners(parts.get(0), parts.get(1), parts.get(0), parts.get(1));
                    case 3 -> new Corners(parts.get(0), parts.get(1), parts.get(2), parts.get(1));
                    case 4 -> new Corners(parts.get(0), parts.get(1), parts.get(2), parts.get(3));
                    default -> null;
                });
    }

    /// The `background` shorthand: a colour, or `none`.
    ///
    /// `none` is transparent here. In CSS it turns off the *image* layers and
    /// leaves `background-color` alone, but the toolkit has no image layer — so
    /// what "no background" can mean is the one thing it does mean to a painter,
    /// and it is how a rule turns a fill off without having to know what colour
    /// it is turning off. The same answer `border: none` gives, one property up.
    private static java.util.Optional<Integer> backgroundLayer(List<Token> value) {
        var parts = split(value);
        if (parts.size() == 1
                && parts.getFirst().size() == 1
                && parts.getFirst().getFirst().is(TokenType.IDENT)
                && parts.getFirst().getFirst().text().toLowerCase(Locale.ROOT).equals("none")) {
            return java.util.Optional.of(CssColor.TRANSPARENT);
        }
        return colour(value);
    }

    /// Splits a value on whitespace into the component values of a shorthand.
    ///
    /// **Not inside a function.** `border: 1px solid rgba(255, 255, 255, 0.2)` is
    /// three components and not seven: the spaces between a function's arguments
    /// belong to the function, and splitting on them hands `CssColor.parse` the
    /// fragment `rgba(255,` — which parses as nothing, so the whole declaration is
    /// dropped.
    ///
    /// That is not a hypothetical. `--gb-border-strong` is `rgba(…)` by design —
    /// an alpha over whatever is underneath is the only way to say "lighter than
    /// its own surface" in a subset with no colour functions (ADR-0166) — and
    /// `card`'s edge, which is the whole of how a raised thing is told apart, has
    /// been silently absent on both themes ever since. The warning was there and
    /// said "dropping border"; nothing was looking at it.
    private static List<List<Token>> split(List<Token> value) {
        var parts = new java.util.ArrayList<List<Token>>();
        var current = new java.util.ArrayList<Token>();
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.WHITESPACE)) {
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
    ///
    /// Keyed on the edge rather than on the property, because two shorthands now
    /// have longhands over the same [Insets] — `padding-top` and `top` set the
    /// same edge of different sets, and a switch over full property names would
    /// have to list both spellings of each of the four.
    private static Insets edge(Insets base, Edge edge, Length value) {
        return switch (edge) {
            case TOP -> new Insets(value, base.right(), base.bottom(), base.left());
            case RIGHT -> new Insets(base.top(), value, base.bottom(), base.left());
            case BOTTOM -> new Insets(base.top(), base.right(), value, base.left());
            case LEFT -> new Insets(base.top(), base.right(), base.bottom(), value);
        };
    }

    /// Which edge a longhand names: the last word of `padding-top`, or the whole
    /// of `top`.
    private static Edge edgeOf(String property) {
        var name = property.substring(property.lastIndexOf('-') + 1);
        return Edge.valueOf(name.toUpperCase(Locale.ROOT));
    }

    /// The four sides an [Insets] has, named once.
    private enum Edge {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
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
    /// CSS's own spellings for the two alignment keywords Yoga names
    /// differently ([ADR-0247]).
    ///
    /// `align-items: start` is **valid CSS** — Box Alignment Level 3 — and Yoga
    /// has only `flex-start`, so a document that wrote what the specification
    /// allows had its declaration dropped with a warning. That is the toolkit
    /// refusing valid CSS rather than the author making a typo, and it filled the
    /// console on the Panels screen for long enough to need deduplicating
    /// (ADR-0216).
    ///
    /// Two entries and no more. `start` and `end` are writing-mode-relative in
    /// full CSS and identical to the flex pair in a subset with one writing mode
    /// and no grid, which is what makes the mapping exact rather than
    /// approximate. `left` and `right` are deliberately absent: they are
    /// `justify-content` only, they are *not* the same as `start`/`end` under
    /// RTL, and §2.4's bidi support (ADR-0218) means the toolkit cannot promise
    /// they would stay equivalent.
    private static final java.util.Map<String, String> KEYWORD_ALIASES = java.util.Map.of(
            "START", "FLEX_START",
            "END", "FLEX_END");

    private static <E extends Enum<E>> java.util.Optional<E> keyword(List<Token> value, Class<E> type) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 || !tokens.getFirst().is(TokenType.IDENT)) {
            return java.util.Optional.empty();
        }
        var name = tokens.getFirst().text().toUpperCase(Locale.ROOT).replace('-', '_');
        var direct = constant(type, name);
        if (direct.isPresent()) {
            return direct;
        }
        // Only after the enum's own name has failed, so an enum that ever gains a
        // constant called `START` keeps its own meaning rather than being
        // shadowed by an alias written for a different one.
        var alias = KEYWORD_ALIASES.get(name);
        return alias == null ? java.util.Optional.empty() : constant(type, alias);
    }

    private static <E extends Enum<E>> java.util.Optional<E> constant(Class<E> type, String name) {
        try {
            return java.util.Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    /// CSS's `flex-wrap`, whose default keyword is spelled without the hyphen
    /// [#keyword] would need.
    ///
    /// `nowrap` is one word in CSS and `NO_WRAP` is two in the
    /// enum, so the generic keyword parser turns `nowrap` into `NOWRAP` and
    /// finds nothing. The other two spellings agree, so this special-cases one
    /// word and defers.
    private static java.util.Optional<Wrap> wrap(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() == 1
                && tokens.getFirst().is(TokenType.IDENT)
                && tokens.getFirst().text().equalsIgnoreCase("nowrap")) {
            return java.util.Optional.of(Wrap.NO_WRAP);
        }
        return keyword(value, Wrap.class);
    }

    /// CSS's `overflow`, which has one more keyword than Yoga does.
    ///
    /// `auto` is not a `YGOverflow`, and it sizes exactly as `scroll` does — the
    /// difference in CSS is whether the bars appear only when they are needed.
    /// The design system routes that question elsewhere: §2.4 makes overlay
    /// auto-hiding bars the default for *both* and gives the always-visible
    /// gutter to an application setting rather than to a keyword. So `auto` maps
    /// onto [Overflow#SCROLL] and nothing downstream has to carry a distinction
    /// no rule in the canon can act on (ADR-0114).
    private static java.util.Optional<Overflow> overflow(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() == 1
                && tokens.getFirst().is(TokenType.IDENT)
                && tokens.getFirst().text().equalsIgnoreCase("auto")) {
            return java.util.Optional.of(Overflow.SCROLL);
        }
        return keyword(value, Overflow.class);
    }

    private static String text(List<Token> value) {
        var out = new StringBuilder();
        value.forEach(t -> out.append(t.cssText()));
        return out.toString();
    }
}
