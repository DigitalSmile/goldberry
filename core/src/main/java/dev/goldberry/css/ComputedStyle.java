package dev.goldberry.css;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.background.Background;
import dev.goldberry.css.background.BackgroundParser;
import dev.goldberry.css.cascade.KeyframeAnimations;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.cascade.Transitions;
import dev.goldberry.css.compositing.BlendMode;
import dev.goldberry.css.compositing.ClipPathParser;
import dev.goldberry.css.compositing.FilterParser;
import dev.goldberry.css.container.ContainerType;
import dev.goldberry.css.container.Containment;
import dev.goldberry.css.image.BorderImageParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.property.GlobalKeyword;
import dev.goldberry.css.property.LogicalProperties;
import dev.goldberry.css.property.Properties;
import dev.goldberry.css.value.CssColor;
import dev.goldberry.css.value.CssEasing;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.IndividualTransform;
import dev.goldberry.css.value.Shadow;
import dev.goldberry.css.value.Transform;
import dev.goldberry.css.value.TransformBox;
import dev.goldberry.css.value.calc.Maths;
import dev.goldberry.css.value.calc.Quantity;
import dev.goldberry.css.value.colour.CurrentColour;
import dev.goldberry.layout.Align;
import dev.goldberry.layout.BoxSizing;
import dev.goldberry.layout.Direction;
import dev.goldberry.layout.Display;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Justify;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Limits;
import dev.goldberry.layout.Overflow;
import dev.goldberry.layout.Position;
import dev.goldberry.layout.Presence;
import dev.goldberry.layout.Wrap;
import dev.goldberry.log.Logs;
import dev.goldberry.motion.Easing;
import dev.goldberry.paint.Box;
import dev.goldberry.render.Cursor;
import dev.goldberry.text.bidi.Directionality;
import dev.goldberry.text.bidi.UnicodeBidi;
import dev.goldberry.text.flow.OverflowWrap;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.flow.TextJustify;
import dev.goldberry.text.flow.TextOverflow;
import dev.goldberry.text.flow.TextTransform;
import dev.goldberry.text.flow.WhiteSpace;
import dev.goldberry.text.flow.WordBreak;

/// Every property a node resolved to, typed.
///
/// The end of the CSS pipeline and the start of the rendering one: each render
/// object owns a Yoga node and one of these. What arrives is a map of property
/// names to tokens; what leaves is values Yoga and Blend2D can be handed without
/// either of them knowing CSS exists.
///
/// The cascade builds one per element with [#of], and a widget reads it off its
/// element; an application almost never constructs one. Two static helpers serve
/// tools above the cascade: [#applies] asks whether the engine does anything with
/// a declaration, and [#durationMillis] reads a time the way the cascade would.
///
/// ## The property split
///
/// The split is a design invariant, and it is visible in the field list:
/// [#direction()], [#justifyContent()], [#width()] and the rest **compile to
/// Yoga**, while [#background()], [#color()] and [#opacity()] are **resolved for
/// paint**. Nothing here does both, and nothing here is a string.
///
/// [#cursor()] belongs to neither half. It compiles to no engine: it rides along
/// to the box tree so that hit testing can read it off whichever rectangle the
/// pointer is over.
///
/// ## What is not here
///
/// The subset is closed. A property the engine does not know, such as
/// `backdrop-filter` or `letter-spacing`, is ignored, and the stylesheet that
/// names it warns once per property when it is parsed ([#isProperty] is the
/// question it asks); a known property with a value it cannot read is dropped
/// with a warning that quotes the text. Each property arrives with the thing that paints it, which is why the
/// design system's radii, its 1px borders and its focus ring are one
/// [#decoration()]: they are drawn by one rounded-rectangle path.
///
/// Immutable, and every field has a default, so a node with no matching rules is
/// still a usable style rather than a null.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#properties).
public record ComputedStyle(
        // --- layout: compiled to Yoga ---
        FlexDirection direction,
        Justify justifyContent,
        Align alignItems,
        // The per-child companion of `align-items`: where a child shorter than
        // its row sits in the cross axis.
        // `Align.AUTO` is the default and is the only value that means anything
        // *here* rather than on the parent -- it is the enum's own word for
        // "whatever my container said".
        Align alignSelf,
        // The third property over [Align]'s value space, and the one that is
        // about *lines* rather than about children: with `flex-wrap: wrap` a
        // container has as many lines as its children needed, and this is how
        // the leftover cross-axis room is shared between them. Meaningless on a
        // container that does not wrap, which is CSS's own rule and Yoga's.
        Align alignContent,
        // Whether a row that does not fit breaks into lines, which a `select
        // multiple` full of chips needs.
        Wrap wrap,
        Length width,
        Length height,
        // `min-width` / `max-width` and their height pair, so a `dialog`, a
        // `toast` or a `tooltip` can say how small and how large it may be
        // instead of writing a *width* where it means a maximum.
        Limits limits,
        // `Yoga` binds margin **with** its `auto` function, which `padding` and
        // `inset` are bound without, so `margin: 0 auto` is a value here and a
        // dropped declaration there.
        //
        // Separate from `padding` and not a second use of it, because the two
        // answer opposite questions — padding is room *inside* a box for its
        // own content, margin is room *outside* it that its container has to
        // give up. A widget that wanted the second and reached for the first
        // grew instead of moving.
        Insets margin,
        Insets padding,
        // `row-gap` and `column-gap`, which `gap` writes together. Two
        // components because CSS lets a wrapping row space its lines apart
        // differently from its items, and Yoga has a gutter for each.
        Length rowGap,
        Length columnGap,
        double flexGrow,
        double flexShrink,
        // The main-axis size a box *starts* from, before `flex-grow` shares out
        // what is left and `flex-shrink` takes back what is missing. It is what
        // lets a row of equal columns be written as `flex-basis: 0; flex-grow: 1`
        // rather than as a percentage width the widget has to count.
        Length flexBasis,
        // `position` and `inset` are the layout half's answer to a box that is
        // not where the flow would put it, such as a segmented control's
        // travelling indicator sitting over its siblings.
        Position position,
        Insets inset,
        // Yoga reads `overflow` for *sizing* — a HIDDEN parent does not stretch
        // to fit a child — and the painter reads it for the clip, which are two
        // different jobs from one keyword. One per axis, `overflow-x` and
        // `overflow-y`; the box gets the two joined, see [#overflow()].
        Overflow overflowX,
        Overflow overflowY,
        // `display` and `aspect-ratio`: whether the node has a box, and the shape
        // it keeps.
        Presence presence,
        // --- text: read by the paragraph, which is both engines at once ---
        // `white-space` **inherits** and `text-overflow` does not, which is CSS's
        // rule and the only reason these are two components rather than one
        // `TextFlow`: a bundle cannot be half-inherited. [#textFlow()] puts them
        // back together for everything below the cascade.
        WhiteSpace whiteSpace,
        TextOverflow textOverflow,
        // `line-clamp`: how many lines a paragraph keeps, the last one marked
        // when more were cut; 0 for all of them. Not inherited, as
        // `text-overflow` is not, and beside it because it is the same mark on
        // a different line.
        int lineClamp,
        // The too-narrow half of the same question, and the third property on
        // the same value. It inherits, like `white-space` and unlike
        // `text-overflow`, which is CSS's split and the reason these are three
        // components.
        TextAlign textAlign,
        // The fourth property on the same value, and the first that is a mark on
        // the glyphs rather than a placement of them: `text-decoration-line`, as a
        // set because CSS allows both rules at once. It inherits here, which is an
        // approximation of CSS's *propagation* to in-flow descendants and is chosen
        // for `text-align`'s reason -- a control's text is very often an anonymous
        // child box, and a property that stopped at the node it was written on
        // would decorate nothing.
        Set<TextDecoration> textDecoration,
        // Whether a word may be cut: `overflow-wrap` when it is wider than the
        // whole line, `word-break: break-all` anywhere. Both inherit, which is
        // CSS's rule, and they are two components because they are two
        // properties a rule may set one at a time.
        OverflowWrap overflowWrap,
        WordBreak wordBreak,
        // --- paint: resolved into pixels ---
        Background fill,
        int color,
        double opacity,
        Decoration decoration,
        Typography typography,
        Transitions transitions,
        // The other half of motion: what runs by itself rather than moving
        // between two styles, named by `@keyframes`. Resolved like `transition`,
        // and like it not inherited.
        KeyframeAnimations animations,
        // Not finished when the cascade produces it, unlike everything above.
        // `transform: translate(50%)` and the `transform-origin` default of
        // `50% 50%` are proportions of the box, and the box has no size until
        // Yoga has run -- so what is carried is the functions, and the painter
        // resolves them.
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
            // How wrapped *lines* share the cross axis. `stretch` is Yoga's
            // default under `useWebDefaults` and CSS's `normal` for a flex
            // container, so a box that never mentions it leaves the cross axis
            // to `align-items`.
            Align.STRETCH,
            // One line, however much it overflows -- Yoga's default and CSS's.
            Wrap.NO_WRAP,
            Length.UNDEFINED,
            Length.UNDEFINED,
            // No limit on any axis, which is Yoga's default and CSS's. Undefined
            // rather than zero: a minimum of zero constrains nothing, but a
            // maximum of zero is a box that may not exist.
            Limits.NONE,
            // Zero on every edge, which is CSS's initial value. Not
            // `Insets.NONE` like `inset` below: an undefined margin and a zero
            // one lay out identically, and zero is the one a reader can add up.
            Insets.ZERO,
            Insets.ZERO,
            Length.points(0),
            Length.points(0),
            0,
            // CSS's default and Yoga's under `useWebDefaults`: a width is a
            // preferred width, and a cramped row may take it back.
            1,
            // `auto`: the main size comes from `width`/`height` or the content,
            // which is CSS's initial value and Yoga's.
            Length.AUTO,
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
            Overflow.VISIBLE,
            // A flex box with no ratio of its own, which is every node.
            Presence.NORMAL,
            // CSS's initial values for both. `normal` rather than `nowrap`
            // because prose is what a paragraph usually is, and `clip` rather
            // than `ellipsis` because a mark on a box nobody asked to truncate
            // is text quietly going missing -- the same argument that keeps
            // `overflow` at `visible`.
            WhiteSpace.NORMAL,
            TextOverflow.CLIP,
            // Every line kept: `line-clamp: none`.
            0,
            // The leading edge, which is CSS's initial value and where every
            // line in the toolkit sat before the property existed.
            TextAlign.START,
            TextDecoration.NONE,
            // A word breaks only between words, CSS's initial value for both.
            OverflowWrap.NORMAL,
            WordBreak.NORMAL,
            Background.none(),
            0xFF000000,
            1.0,
            Decoration.NONE,
            Typography.INITIAL,
            Transitions.NONE,
            KeyframeAnimations.NONE,
            Transform.NONE,
            Cursor.DEFAULT);

    public ComputedStyle {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(justifyContent, "justifyContent");
        Objects.requireNonNull(alignItems, "alignItems");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(margin, "margin");
        Objects.requireNonNull(padding, "padding");
        Objects.requireNonNull(rowGap, "rowGap");
        Objects.requireNonNull(columnGap, "columnGap");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(inset, "inset");
        Objects.requireNonNull(overflowX, "overflowX");
        Objects.requireNonNull(overflowY, "overflowY");
        Objects.requireNonNull(presence, "presence");
        Objects.requireNonNull(whiteSpace, "whiteSpace");
        Objects.requireNonNull(textOverflow, "textOverflow");
        Objects.requireNonNull(textAlign, "textAlign");
        // Copied rather than merely checked, because a style is a value that is
        // cached and compared, and a set the caller can still add to is neither.
        textDecoration = Set.copyOf(Objects.requireNonNull(textDecoration, "textDecoration"));
        Objects.requireNonNull(overflowWrap, "overflowWrap");
        Objects.requireNonNull(wordBreak, "wordBreak");
        Objects.requireNonNull(fill, "fill");
        Objects.requireNonNull(decoration, "decoration");
        Objects.requireNonNull(typography, "typography");
        Objects.requireNonNull(transitions, "transitions");
        Objects.requireNonNull(animations, "animations");
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
        // and not a refinement of it. `1.2em` on `font-size` means "a fifth
        // larger than my parent"; `1.2em` on `padding` means "a fifth larger
        // than my own text". One pass with one context cannot say both.
        var parentSize = parent == null
                ? context.fontSize()
                : (float) parent.typography().size();
        // `font` sets the size too, so both are read here, in the cascade's
        // order, which leaves the later of the two in force.
        // `ch` and `ex` in `font-size` are the parent's face, as `em` is its size.
        var parentLengths = parent == null
                ? context.withFontSize(parentSize)
                : context.withFontSize(parentSize)
                        .withFace(
                                parent.typography().family(),
                                parent.typography().weight());
        for (var entry : declarations.entrySet()) {
            if (READ_FIRST.contains(entry.getKey())) {
                style = style.declare(entry.getKey(), entry.getValue(), parentLengths, parent);
            }
        }
        var readFirst = readWithTheSize(declarations);
        // Undeclared is the ordinary case and needs no branch: the size is
        // whatever was inherited, which is exactly what `em` should resolve
        // against.
        //
        // **`rem` gets the same treatment, for the root and only for the root.**
        // CSS says `rem` is the *root element's* computed `font-size`, with one
        // exception that is this method's own two-pass structure seen from the
        // other end: on the root element's `font-size` itself, `rem` cannot be
        // the value being computed, so it resolves against the configured size
        // above. Everything else on the root -- and, through the context the
        // renderer hands down, everything below it -- resolves against what the
        // root just computed.
        //
        // A node with a parent takes `rootFontSize` as given, because by then it
        // is the renderer's business rather than this method's: a node is handed
        // its parent's style and not the root's, and only the walk down the tree
        // knows which style the root's was.
        var rootSize = parent == null ? (float) style.typography().size() : context.rootFontSize();
        var own = context.withFontSize((float) style.typography().size())
                .withRootFontSize(rootSize)
                .withFace(style.typography().family(), style.typography().weight());
        // `color` second, and for the same kind of reason: `currentcolor` in
        // every other value means the colour this element ends up with, so that
        // colour has to be known before they are read. In `color` itself it
        // means the inherited colour, which is what the style holds until then.
        style = withColourFirst(style, declarations, own, parent);
        for (var entry : declarations.entrySet()) {
            var property = entry.getKey();
            if (!readFirst.contains(property) && !"color".equals(property)) {
                style = style.declare(property, entry.getValue(), own, parent);
            }
        }
        return style;
    }

    /// The font's longhands, which a `font` read first has already set.
    private static final Set<String> FONT_LONGHANDS =
            Set.of("font-style", "font-weight", "font-size", "line-height", "font-family");

    /// What [#of] reads before anything else, in the cascade's order: the size,
    /// and the face, since `ch` and `ex` everywhere else are measured in it,
    /// and the direction, since a logical property names a side by it.
    private static final Set<String> READ_FIRST =
            Set.of("font", "font-size", "font-family", "font-weight", "font-style", "direction");

    /// What the loop in [#of] must not apply again once the size was read with
    /// the parent's: what [#READ_FIRST] names, and when there is a `font`, every
    /// longhand of it the cascade put before it, which it overrides.
    private static Set<String> readWithTheSize(Map<String, List<Token>> declarations) {
        if (!declarations.containsKey("font")) {
            return READ_FIRST;
        }
        var read = new HashSet<String>(READ_FIRST);
        for (var property : declarations.keySet()) {
            if (property.equals("font")) {
                break;
            }
            if (FONT_LONGHANDS.contains(property)) {
                read.add(property);
            }
        }
        return read;
    }

    /// `style` with the `color` declaration among `declarations` applied, if
    /// there is one, `currentcolor` in it read as the colour `style` has now.
    private static ComputedStyle withColourFirst(
            ComputedStyle style,
            Map<String, List<Token>> declarations,
            CssLength.Context context,
            @Nullable ComputedStyle parent) {
        var colour = declarations.get("color");
        return colour == null ? style : style.declare("color", colour, context, parent);
    }

    /// One declaration applied: a global keyword through the property table,
    /// anything else through [#with] with `currentcolor` read as this style's
    /// colour.
    ///
    /// @param parent what `inherit` reads; null at the root
    private ComputedStyle declare(
            String property, List<Token> value, CssLength.Context context, @Nullable ComputedStyle parent) {
        var keyword = GlobalKeyword.of(value);
        if (keyword != null && LogicalProperties.isLogical(property)) {
            // The sides this box's direction names, not the table's left to
            // right: `margin-inline-start: inherit` in a right-to-left box
            // inherits the right margin.
            var style = this;
            for (var physical : Objects.requireNonNull(
                    LogicalProperties.physical(property, value, presence.direction()), "a keyword is one value")) {
                style = keyword.apply(physical.property(), style, parent);
            }
            return style;
        }
        if (keyword != null) {
            return keyword.apply(property, this, parent);
        }
        return with(property, CurrentColour.substitute(value, color), context);
    }

    /// This style with `declarations` applied **on top of it** — what a keyframe
    /// is.
    ///
    /// Not [#of], which starts from [#INITIAL]: a keyframe that says `opacity: 0`
    /// leaves every other property where the element's own rules put it, so it is
    /// applied to the element's resolved style rather than to a blank one. `em`
    /// resolves against this style's own font size, as it would on the element.
    ///
    /// A declaration that does not parse is dropped with the usual warning.
    public ComputedStyle applied(Map<String, List<Token>> declarations, CssLength.Context context) {
        Objects.requireNonNull(declarations, "declarations");
        Objects.requireNonNull(context, "context");
        var own = context.withFontSize((float) typography.size()).withFace(typography.family(), typography.weight());
        // A keyframe has no parent to hand: `inherit` in one keeps the element's
        // own value, which is what it inherited unless a rule of its own said
        // otherwise.
        var style = withColourFirst(this, declarations, own, this);
        // The direction before the rest, as in [#of]: a logical property names a
        // side by it.
        var direction = declarations.get("direction");
        if (direction != null) {
            style = style.declare("direction", direction, own, this);
        }
        for (var entry : declarations.entrySet()) {
            var property = entry.getKey();
            if (!"color".equals(property) && !"direction".equals(property)) {
                style = style.declare(property, entry.getValue(), own, this);
            }
        }
        return style;
    }

    /// Whether a child would resolve identically against `other` as against
    /// this — that is, whether the two agree on every **inherited** property.
    ///
    /// The inherited half is `color` and `typography`, which is
    /// [#inheritingFrom]'s whole body, plus nothing: a child reads no other
    /// component of its parent's style, so two parents that agree on these are
    /// indistinguishable from below.
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
        return color == other.color
                && typography.equals(other.typography)
                && whiteSpace == other.whiteSpace
                && textAlign == other.textAlign
                && textDecoration.equals(other.textDecoration)
                && overflowWrap == other.overflowWrap
                && wordBreak == other.wordBreak
                && presence.visibility() == other.presence.visibility()
                && presence.pointerEvents() == other.presence.pointerEvents()
                && presence.userSelect() == other.presence.userSelect()
                && presence.direction() == other.presence.direction();
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
    ///   about what is on screen. Inheriting it here as well would be a second
    ///   mechanism for one property, and the two would disagree the first time a
    ///   box was styled without an element behind it.
    /// - **`opacity`**, which CSS does not inherit — its *effect* does, and the
    ///   painter accumulates it down the box tree. Inheriting the value here
    ///   would then apply it once per level per ancestor: a label under a control
    ///   at 45% would be drawn at 20%.
    ///
    /// Read more: [Inheritance](https://goldberry.dev/docs/guide/styling.html#inheritance).
    private ComputedStyle inheritingFrom(ComputedStyle parent) {
        // `transition` is deliberately absent: CSS does not inherit it, and a
        // panel that faded its background must not make every label inside it
        // fade too. A control declares what *it* animates.
        // `white-space` is on this list and `text-overflow` is not, which is
        // exactly what CSS says about the pair. It is also what an author means:
        // `menu { white-space: nowrap }` is a statement about the rows, and
        // `text-overflow` on a container that draws no text of its own would
        // otherwise put a mark on every label underneath it.
        return INITIAL.color(parent.color())
                .typography(parent.typography())
                .whiteSpace(parent.whiteSpace())
                // `text-align` inherits in CSS and is written on a *container*
                // far more often than on the thing that draws the text -- which
                // is exactly why it has to: `slider-value { text-align: end }`
                // is a rule about a node whose text is its own, and
                // `column.numeric { text-align: end }` is one about nodes whose
                // text is not.
                .textAlign(parent.textAlign())
                // And `text-decoration`, which CSS does not inherit but *propagates*
                // to in-flow descendants -- a rule drawn across the whole of an
                // element's text, children included. Inheritance is how that reads
                // here, because the child is usually an anonymous box holding the
                // paragraph: `button.link { text-decoration: underline }` has to
                // reach the label inside it or it decorates nothing at all.
                .textDecoration(parent.textDecoration())
                // And whether a word may be cut, which CSS inherits for the
                // reason it inherits `white-space`: `.log { overflow-wrap:
                // anywhere }` is about the lines inside, not the box.
                .overflowWrap(parent.overflowWrap())
                .wordBreak(parent.wordBreak())
                // And whether a box is seen, hit and selected, which CSS
                // inherits so that `visibility: hidden` on a panel hides what
                // is in it unless a child says `visible`.
                .presence(INITIAL.presence().inheritingFrom(parent.presence()));
    }

    /// One declaration applied, or this style unchanged if it does not apply.
    private ComputedStyle with(String property, List<Token> value, CssLength.Context context) {
        return switch (property) {
            case "flex-direction" ->
                keyword(value, FlexDirection.class).map(this::direction).orElseGet(() -> dropped(property, value));

            // `stretch` is CSS's word for a flex container's start: there is
            // nothing to stretch along the main axis.
            case "justify-content" ->
                justify(value).map(this::justifyContent).orElseGet(() -> dropped(property, value));

            // Read and checked, and nothing more: CSS ignores both on a flex
            // item, and every box here is one. A value CSS would refuse still
            // drops, so a typo is still caught.
            case "justify-items", "justify-self" ->
                selfAlignment(value, property.equals("justify-self")) ? read() : dropped(property, value);

            // `<align> [<justify>]`, the one value being both when it is alone.
            case "place-content", "place-items", "place-self" -> place(property, value);

            case "align-items" ->
                keyword(value, Align.class).map(this::alignItems).orElseGet(() -> dropped(property, value));

            // The per-child companion, and the one place `auto` is a value rather
            // than a missing one: it is the enum's word for "whatever my
            // container said", so `align-self: auto` is a real declaration that
            // undoes a more general rule.
            case "align-self" ->
                keyword(value, Align.class).map(this::alignSelf).orElseGet(() -> dropped(property, value));

            // The same value space again, read by a container about its wrapped
            // lines.
            case "align-content" ->
                keyword(value, Align.class).map(this::alignContent).orElseGet(() -> dropped(property, value));

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
            //
            // `fixed` and not `length`, here and everywhere below: Yoga has no
            // `YGNodeStyleSetPaddingAuto`, so `padding: auto` would reach a
            // binding that refuses `auto` **by name** and throw mid-frame. The
            // rule for a value the engine cannot honour is to drop the
            // declaration, and this is where that decision belongs — the layout
            // pass is far too late to be making it.
            case "padding" ->
                insets(value, context, false).map(this::padding).orElseGet(() -> dropped(property, value));

            case "padding-top", "padding-right", "padding-bottom", "padding-left" ->
                fixed(value, context)
                        .map(v -> padding(edge(padding, edgeOf(property), v)))
                        .orElseGet(() -> dropped(property, value));

            // The same shorthand over the other side of the box — and the one
            // property here where `auto` is a *value* rather than a refusal.
            // `margin: 0 auto` is how a box centres itself in a container it
            // does not control, which is the whole reason the property was
            // wanted: `align-self: center` centres on the **cross** axis, and
            // nothing else in the subset centres on the main one.
            //
            // Negative margins are allowed and deliberately not clamped. A
            // negative margin pulls a box over its neighbour, which is how a
            // row of overlapping avatars is written and how a control escapes
            // its container's padding on one edge.
            case "margin" -> insets(value, context, true).map(this::margin).orElseGet(() -> dropped(property, value));

            case "margin-top", "margin-right", "margin-bottom", "margin-left" ->
                length(value, context)
                        .map(v -> margin(edge(margin, edgeOf(property), v)))
                        .orElseGet(() -> dropped(property, value));

            // The four bounds. A `dialog` is "min width 320, max 80% window", and
            // without them the scrim's padding would be a de-facto maximum with
            // no minimum at all: a dialog with three words in it would be three
            // words wide. `toast` and `tooltip` want a maximum for the same reason.
            case "min-width" ->
                fixed(value, context).map(v -> limits(limits.minWidth(v))).orElseGet(() -> dropped(property, value));

            case "max-width" ->
                fixed(value, context).map(v -> limits(limits.maxWidth(v))).orElseGet(() -> dropped(property, value));

            case "min-height" ->
                fixed(value, context).map(v -> limits(limits.minHeight(v))).orElseGet(() -> dropped(property, value));

            case "max-height" ->
                fixed(value, context).map(v -> limits(limits.maxHeight(v))).orElseGet(() -> dropped(property, value));

            // `gap: 8px` is both gutters and `gap: 8px 16px` is the row gap and
            // then the column gap, which is CSS's order.
            case "gap" -> gaps(value, context).orElseGet(() -> dropped(property, value));

            case "row-gap" -> fixed(value, context).map(this::rowGap).orElseGet(() -> dropped(property, value));

            case "column-gap" -> fixed(value, context).map(this::columnGap).orElseGet(() -> dropped(property, value));

            // The shorthand for the three below. Applied as three withers, so a
            // later `flex-basis` overrides the basis it wrote, as in CSS.
            case "flex" -> flex(value, context).orElseGet(() -> dropped(property, value));

            case "flex-grow" ->
                number(value).filter(v -> v >= 0).map(this::flexGrow).orElseGet(() -> dropped(property, value));

            // Without `flex-shrink: 0` every fixed-size part in a row is
            // negotiable and a narrow window squashes it, and an icon or a glyph
            // must not be.
            case "flex-shrink" ->
                number(value).filter(v -> v >= 0).map(this::flexShrink).orElseGet(() -> dropped(property, value));

            // The third of the flex trio, and the one whose `auto` is a value:
            // `flex-basis: auto` is "ask `width` or the content", which is what
            // undoes a more general rule.
            case "flex-basis" -> length(value, context).map(this::flexBasis).orElseGet(() -> dropped(property, value));

            // A segmented control's indicator is the kind of box that has to sit
            // *over* its siblings rather than beside them. `static` is admitted
            // as well as CSS's two, because it is how a container declines to be
            // the thing an absolute descendant is placed against.
            case "position" ->
                keyword(value, Position.class).map(this::position).orElseGet(() -> dropped(property, value));

            // The same 1-4 shorthand `padding` takes, over the same [Insets] --
            // an inset is a padding measured from the outside.
            case "inset" -> insets(value, context, false).map(this::inset).orElseGet(() -> dropped(property, value));

            case "top", "right", "bottom", "left" ->
                fixed(value, context)
                        .map(v -> inset(edge(inset, edgeOf(property), v)))
                        .orElseGet(() -> dropped(property, value));

            // Both of Yoga's non-visible values are admitted, and they size
            // identically. What separates them is above the layout engine: a
            // `scroll` offers scrollbars for `scroll` and `auto` and none for
            // `hidden`, so the keyword is how a stylesheet says which of the
            // two a box is. `auto` resolves to SCROLL and is told
            // apart by the widget, not by the box.
            case "overflow" -> overflows(value).orElseGet(() -> dropped(property, value));

            case "overflow-x" -> overflow(value).map(this::overflowX).orElseGet(() -> dropped(property, value));

            case "overflow-y" -> overflow(value).map(this::overflowY).orElseGet(() -> dropped(property, value));

            // One writing mode, horizontal and left to right, so each logical
            // property is the physical one it names: the inline start is the
            // left and the block start the top.
            case "margin-inline",
                    "margin-block",
                    "margin-inline-start",
                    "margin-inline-end",
                    "margin-block-start",
                    "margin-block-end",
                    "padding-inline",
                    "padding-block",
                    "padding-inline-start",
                    "padding-inline-end",
                    "padding-block-start",
                    "padding-block-end",
                    "inset-inline",
                    "inset-block",
                    "inset-inline-start",
                    "inset-inline-end",
                    "inset-block-start",
                    "inset-block-end",
                    "inline-size",
                    "block-size",
                    "min-inline-size",
                    "max-inline-size",
                    "min-block-size",
                    "max-block-size" -> logical(property, value, context);

            // `flex`, `none` and `contents`, the three that mean something when
            // every box is a flex container. `block` and the rest are refused:
            // a stylesheet that asked for flow layout would get a flex box
            // laid out differently from what it wrote.
            case "display" ->
                keyword(value, Display.class)
                        .map(v -> presence(presence.display(v)))
                        .orElseGet(() -> dropped(property, value));

            // `auto`, a number, or `width / height`.
            case "aspect-ratio" ->
                aspectRatio(value)
                        .map(v -> presence(presence.aspectRatio(v)))
                        .orElseGet(() -> dropped(property, value));

            // Where the box is drawn among its siblings: later for a higher
            // number, document order among equals, `auto` as 0. Not inherited.
            case "z-index" ->
                zIndex(value).map(v -> presence(presence.zIndex(v))).orElseGet(() -> dropped(property, value));

            // Whether the box answers `@container` queries, and by which names.
            // Neither inherits. `container` is `name [/ type]`.
            case "container-type" ->
                singleIdent(value)
                        .map(ContainerType::parse)
                        .map(v -> presence(
                                presence.containment(presence.containment().type(v))))
                        .orElseGet(() -> dropped(property, value));

            case "container-name" ->
                containerNames(value)
                        .map(v -> presence(
                                presence.containment(presence.containment().names(v))))
                        .orElseGet(() -> dropped(property, value));

            case "container" -> containerShorthand(value).orElseGet(() -> dropped(property, value));

            // What `width` and `height` measure. A border takes room outside the
            // padding, and `border-box`, the initial value here, keeps a
            // fixed-size box that size. Not inherited.
            case "box-sizing" ->
                singleIdent(value)
                        .map(BoxSizing::parse)
                        .map(v -> presence(presence.boxSizing(v)))
                        .orElseGet(() -> dropped(property, value));

            // Where the box is laid out and drawn among its siblings: lower
            // first, document order among equals. Focus keeps document order.
            // Not inherited.
            case "order" ->
                order(value).map(v -> presence(presence.order(v))).orElseGet(() -> dropped(property, value));

            // What part of the box is drawn, and how it is drawn over what is
            // under it. None of the three inherits, and any of them makes the
            // box a group drawn through a raster of its own.
            // Filters applied, in order, to the box and its children drawn as
            // one picture. `drop-shadow()` without a colour is in this
            // element's `color`, which is known by now.
            case "filter", "-webkit-filter" ->
                FilterParser.parse(value, context, color)
                        .map(v -> presence(
                                presence.compositing(presence.compositing().filter(v))))
                        .orElseGet(() -> dropped(property, value));

            case "clip-path" ->
                ClipPathParser.parse(value, context)
                        .map(v -> presence(
                                presence.compositing(presence.compositing().clip(v))))
                        .orElseGet(() -> dropped(property, value));

            case "mix-blend-mode" -> {
                var name = singleIdent(value);
                if (name.isPresent() && BlendMode.isNonSeparable(name.get())) {
                    yield dropped(
                            property,
                            value,
                            "is not supported: it mixes colour channels across each other, which no compositing"
                                    + " operator here does");
                }
                yield name.map(BlendMode::parse)
                        .map(v -> presence(
                                presence.compositing(presence.compositing().blend(v))))
                        .orElseGet(() -> dropped(property, value));
            }

            case "isolation" ->
                singleIdent(value)
                        .map(v -> v.toLowerCase(Locale.ROOT))
                        .filter(v -> v.equals("auto") || v.equals("isolate"))
                        .map(v -> presence(
                                presence.compositing(presence.compositing().isolate(v.equals("isolate")))))
                        .orElseGet(() -> dropped(property, value));

            // Which way the inline axis runs. Inherited, and read before
            // everything else, since a logical property means a side only once
            // it is known.
            case "direction" ->
                singleIdent(value)
                        .map(Direction::parse)
                        .map(v -> presence(presence.direction(v)))
                        .orElseGet(() -> dropped(property, value));

            // Where the box's text takes its base direction from: its
            // `direction`, each paragraph's first strong character, or every
            // character forced its way. Not inherited.
            case "unicode-bidi" ->
                singleIdent(value)
                        .map(UnicodeBidi::parse)
                        .map(v -> presence(presence.unicodeBidi(v)))
                        .orElseGet(() -> dropped(property, value));

            // Whether the box is drawn, hit and focused; it keeps its room
            // either way, which is the difference from `display: none`. All
            // three inherit, and a child may say otherwise.
            case "visibility" ->
                singleIdent(value)
                        .map(Presence.Visibility::parse)
                        .map(v -> presence(presence.visibility(v)))
                        .orElseGet(() -> dropped(property, value));

            case "pointer-events" ->
                singleIdent(value)
                        .map(Presence.PointerEvents::parse)
                        .map(v -> presence(presence.pointerEvents(v)))
                        .orElseGet(() -> dropped(property, value));

            case "user-select", "-webkit-user-select" ->
                singleIdent(value)
                        .map(Presence.UserSelect::parse)
                        .map(v -> presence(presence.userSelect(v)))
                        .orElseGet(() -> dropped(property, value));
            // A box with text is a measured leaf, so narrowing it *wraps* the
            // text instead of overflowing it. A menu row, an `option`, a
            // `select-value` and a segment all want to be cut instead:
            // `white-space: nowrap` is what stops the wrap and `text-overflow` is
            // what marks the result.
            // Five keywords onto two behaviours: the paragraph never collapses
            // spaces, so `pre-wrap` and `pre-line` are `normal` and `pre` is
            // `nowrap`. See [WhiteSpace].
            case "white-space" ->
                Optional.ofNullable(single(value, WhiteSpace::parse))
                        .map(this::whiteSpace)
                        .orElseGet(() -> dropped(property, value));
            case "overflow-wrap", "word-wrap" ->
                Optional.ofNullable(single(value, OverflowWrap::parse))
                        .map(this::overflowWrap)
                        .orElseGet(() -> dropped(property, value));
            case "word-break" ->
                Optional.ofNullable(single(value, WordBreak::parse))
                        .map(this::wordBreak)
                        .orElseGet(() -> dropped(property, value));
            case "text-overflow" ->
                keyword(value, TextOverflow.class).map(this::textOverflow).orElseGet(() -> dropped(property, value));
            // The paragraph knows its lines' widths and the box does not, so it
            // is the paint that places them and `Box` is untouched. `left` and
            // `right` are constants of their own rather than aliases of
            // `start`/`end`, which they stop being under RTL. `justify` is a
            // CSS value this engine cannot do, and the warning says so.
            case "text-align" ->
                keyword(value, TextAlign.class).map(this::textAlign).orElseGet(() -> dropped(property, value));

            // Where a justified line takes its room: its word gaps, or nowhere.
            // Inherited, with the rest of how letters are set.
            case "text-justify" ->
                singleIdent(value)
                        .map(TextJustify::parse)
                        .map(v -> typography(
                                typography.lettering(typography.lettering().justify(v))))
                        .orElseGet(() -> dropped(property, value));
            // The shorthand and the one longhand of it that exists here. CSS's
            // shorthand also carries a colour and a style, and a declaration that
            // names either is dropped whole rather than half-applied: a rule that
            // asked for `underline wavy red` and got a straight rule in the text's
            // own colour would be a property that lies.
            case "text-decoration", "text-decoration-line" ->
                decorations(value).map(this::textDecoration).orElseGet(() -> dropped(property, value));

            // `background` is CSS's shorthand: gradient layers, top first, and a
            // colour under them. It resets what it does not name, so
            // `background: none` is no fill at all -- which `select text-input`
            // wanted, for the same sentence that made it write `border: none`:
            // an editor inside a control is that control's interior, with no
            // fill of its own. The three longhands change one part each.
            case "background" ->
                Optional.ofNullable(BackgroundParser.shorthand(value, context))
                        .map(this::fill)
                        .orElseGet(() -> dropped(property, value));

            case "background-color" -> colour(value).map(this::background).orElseGet(() -> dropped(property, value));

            case "background-image" ->
                Optional.ofNullable(BackgroundParser.images(value, context))
                        .map(v -> fill(fill.layers(v)))
                        .orElseGet(() -> dropped(property, value));

            case "background-position" ->
                Optional.ofNullable(BackgroundParser.positions(value, context))
                        .map(v -> fill(fill.positions(v)))
                        .orElseGet(() -> dropped(property, value));

            case "background-position-x", "background-position-y" ->
                Optional.ofNullable(BackgroundParser.axis(value, property.endsWith("x"), context))
                        .map(v -> fill(
                                fill.positions(BackgroundParser.withAxis(fill.positions(), v, property.endsWith("x")))))
                        .orElseGet(() -> dropped(property, value));

            // Which of the box's three rectangles a layer is placed in and cut
            // to: the border box, the box less its border, and less its
            // padding too.
            case "background-origin" ->
                Optional.ofNullable(BackgroundParser.boxes(value))
                        .map(v -> fill(fill.origins(v)))
                        .orElseGet(() -> dropped(property, value));

            case "background-clip" ->
                Optional.ofNullable(BackgroundParser.boxes(value))
                        .map(v -> fill(fill.clips(v)))
                        .orElseGet(() -> dropped(property, value));

            // How large a `url()` layer is drawn and whether it is tiled: comma
            // lists matched to the layers by position, as CSS matches them. A
            // gradient is the size of its box whatever these say.
            case "background-size" ->
                Optional.ofNullable(BackgroundParser.sizes(value, context))
                        .map(v -> fill(fill.sizes(v)))
                        .orElseGet(() -> dropped(property, value));

            case "background-repeat" ->
                Optional.ofNullable(BackgroundParser.repeats(value))
                        .map(v -> fill(fill.repeats(v)))
                        .orElseGet(() -> dropped(property, value));

            case "color" -> colour(value).map(this::color).orElseGet(() -> dropped(property, value));

            case "opacity" ->
                number(value)
                        .map(v -> Math.max(0, Math.min(1, v)))
                        .map(this::opacity)
                        .orElseGet(() -> dropped(property, value));

            // --- the decoration half: corners, border, outline, shadow --------
            //
            // CSS's 1-4 corner shorthand, over [Corners]. Every radius the design
            // system pins is uniform (4, 8, 12, full) and writes one number; the
            // second form is for a box that meets a rounded parent on one edge
            // and a square sibling on the other, which is what `group-box-title`
            // is. `full` is spelled `9999px`.
            case "border-radius" ->
                corners(value, context)
                        .map(v -> decoration(decoration.corners(v)))
                        .orElseGet(() -> dropped(property, value));

            // CSS's 1-4 side shorthand, in `padding`'s order, since the sides
            // can differ. One value is still every side, which is what every
            // rule the toolkit ships writes.
            case "border-width" ->
                sides(value, part -> points(part, context))
                        .map(v -> decoration(
                                decoration.border(decoration.border().widths(v.get(0), v.get(1), v.get(2), v.get(3)))))
                        .orElseGet(() -> dropped(property, value));

            case "border-color" ->
                sides(value, ComputedStyle::colour)
                        .map(v -> decoration(
                                decoration.border(decoration.border().colours(v.get(0), v.get(1), v.get(2), v.get(3)))))
                        .orElseGet(() -> dropped(property, value));

            // One side of the border, which rules keep reaching for:
            // `border-bottom` under `table-head` and `tab-new`, `border-left` on
            // a quotation, `border-right` down a gutter, and a rule between a
            // document table's cells. The shorthand is `border`'s grammar over
            // one side -- the style keyword is that side's style, `none` is a
            // zero width -- and it resets that side's colour and style the way
            // `border` resets all four.
            //
            // The cascade applies declarations in the order they won, so `border`
            // then `border-left` is a uniform border with its left side replaced,
            // and `border-left` then `border` is a uniform border: later wins,
            // per side, which is CSS's rule.
            case "border-top", "border-right", "border-bottom", "border-left" ->
                stroke(value, context)
                        .map(v ->
                                decoration(decoration.border(decoration.border().side(sideOf(property), v.line()))))
                        .orElseGet(() -> dropped(property, value));

            case "border-top-width", "border-right-width", "border-bottom-width", "border-left-width" ->
                points(value, context)
                        .map(v -> {
                            var side = sideOf(property);
                            var border = decoration.border();
                            return decoration(decoration.border(
                                    border.side(side, border.side(side).width(v))));
                        })
                        .orElseGet(() -> dropped(property, value));

            case "border-top-color", "border-right-color", "border-bottom-color", "border-left-color" ->
                colour(value)
                        .map(v -> {
                            var side = sideOf(property);
                            var border = decoration.border();
                            return decoration(decoration.border(
                                    border.side(side, border.side(side).argb(v))));
                        })
                        .orElseGet(() -> dropped(property, value));

            // The style on its own, one side or all four. `none` and `hidden`
            // are a side with no width, which is how a border without a style
            // is carried here.
            case "border-style" ->
                sides(value, ComputedStyle::sideStyle)
                        .map(v -> decoration(decoration.border(new Border(
                                styled(decoration.border().top(), v.get(0)),
                                styled(decoration.border().right(), v.get(1)),
                                styled(decoration.border().bottom(), v.get(2)),
                                styled(decoration.border().left(), v.get(3))))))
                        .orElseGet(() -> dropped(property, value));

            case "border-top-style", "border-right-style", "border-bottom-style", "border-left-style" ->
                sideStyle(value)
                        .map(v -> {
                            var side = sideOf(property);
                            var border = decoration.border();
                            return decoration(decoration.border(border.side(side, styled(border.side(side), v))));
                        })
                        .orElseGet(() -> dropped(property, value));

            // One corner of `border-radius`.
            case "border-top-left-radius",
                    "border-top-right-radius",
                    "border-bottom-right-radius",
                    "border-bottom-left-radius" ->
                cornerRadius(value, context)
                        .map(v -> decoration(decoration.corners(corner(decoration.corners(), property, v))))
                        .orElseGet(() -> dropped(property, value));

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
                        .map(v -> {
                            var line = v.line();
                            return decoration(decoration.border(new Border(line, line, line, line)));
                        })
                        .orElseGet(() -> dropped(property, value));

            // A picture cut in nine and drawn in place of the border's colours.
            // Part of the decoration, so `:hover` and `:disabled` pick their
            // sprite through the cascade. The shorthand resets the four parts it
            // does not name, as CSS's does.
            case "border-image" ->
                Optional.ofNullable(BorderImageParser.shorthand(value, context))
                        .map(v -> decoration(decoration.borderImage(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border-image-source" ->
                Optional.ofNullable(BorderImageParser.source(value, decoration.borderImage()))
                        .map(v -> decoration(decoration.borderImage(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border-image-slice" ->
                Optional.ofNullable(BorderImageParser.slice(value, decoration.borderImage()))
                        .map(v -> decoration(decoration.borderImage(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border-image-width" ->
                Optional.ofNullable(BorderImageParser.widths(value, context, decoration.borderImage()))
                        .map(v -> decoration(decoration.borderImage(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border-image-outset" ->
                Optional.ofNullable(BorderImageParser.outsets(value, context, decoration.borderImage()))
                        .map(v -> decoration(decoration.borderImage(v)))
                        .orElseGet(() -> dropped(property, value));

            case "border-image-repeat" ->
                Optional.ofNullable(BorderImageParser.repeat(value, decoration.borderImage()))
                        .map(v -> decoration(decoration.borderImage(v)))
                        .orElseGet(() -> dropped(property, value));

            // A ring is drawn solid whatever its style says: it is a focus
            // indicator, and the design system pins it solid.
            case "outline" ->
                stroke(value, context)
                        .map(v -> decoration(
                                decoration.outline(v.width(), v.argb(), v.style(), decoration.outlineOffset())))
                        .orElseGet(() -> dropped(property, value));

            // How the ring is drawn: any style a border side may be, `auto` as
            // `solid`. `none` takes the ring away, which CSS says by computing
            // its width to zero.
            case "outline-style" ->
                outlineStyle(value)
                        .map(v -> decoration(
                                v.isEmpty()
                                        ? decoration.outlineWidth(0)
                                        : decoration.outlineStyle(Objects.requireNonNull(Border.Style.parse(v)))))
                        .orElseGet(() -> dropped(property, value));

            // A shadow paints outside the box's own rectangle, as the focus ring
            // does, and the damage rectangle already grows for it. A rasterizer
            // with no blur still draws the fade out of the one primitive it is
            // fastest at: a stack of rectangles. `--gb-elevation-1/-2/-3` are
            // what a rule should name; the numbers in them are the theme's,
            // because the same alpha that reads as a shadow on nord-light is
            // invisible on nord-dark.
            case "box-shadow" -> {
                var parsed = Shadow.parse(value, context);
                yield parsed == null ? dropped(property, value) : decoration(decoration.shadows(parsed));
            }

            // --- the typography half ------------------------------------------
            //
            // Inherited, which is what makes `panel { font-size: 13px }` reach
            // every label under it and is why `Typography` is one record: the
            // three travel together down the tree and are read together by the
            // one thing that resolves a `Font`.
            case "font-family" ->
                family(value).map(v -> typography(typography.family(v))).orElseGet(() -> dropped(property, value));

            // A length, a percentage of the parent's size, or a keyword: the
            // absolute sizes are steps from the root's size, which is what
            // `medium` means here, and `smaller` and `larger` a step from the
            // parent's. The context is the parent's here; see [#of].
            case "font-size" ->
                fontSize(value, context)
                        .map(v -> typography(typography.size(v)))
                        .orElseGet(() -> dropped(property, value));

            case "font-weight" ->
                weight(value).map(v -> typography(typography.weight(v))).orElseGet(() -> dropped(property, value));

            // `normal` and `italic`, and **not** `oblique`: an italic here is a
            // drawn face rather than a slant, so answering `oblique` with it would
            // answer a different question and answering it with a shear would be a
            // type-design decision taken by a stylesheet.
            case "font-style" ->
                fontStyle(value).map(v -> typography(typography.style(v))).orElseGet(() -> dropped(property, value));

            // How wide a face with a `wdth` axis is drawn: a percentage or one of
            // CSS's nine keywords. A face without the axis ignores it.
            case "font-stretch", "font-width" ->
                FontValues.stretch(value)
                        .map(v -> typography(
                                typography.variation(typography.variation().stretch(v))))
                        .orElseGet(() -> dropped(property, value));

            // Where a variable face is set along its own axes: `normal`, or
            // `"tag" number` pairs. Applied after the weight and the stretch, so
            // it has the last word on `wght` and `wdth` too, as in CSS.
            case "font-variation-settings" ->
                FontValues.variationSettings(value)
                        .map(v -> typography(
                                typography.variation(typography.variation().settings(v))))
                        .orElseGet(() -> dropped(property, value));

            // A bare number is a multiple of the font size -- `line-height: 1.4`
            // -- which is the form that survives a font-size change on a
            // descendant. A length is absolute. Both are CSS's.
            case "line-height" ->
                lineHeight(value, context)
                        .map(v -> typography(typography.lineHeight(v)))
                        .orElseGet(() -> dropped(property, value));

            // Added to what the face sets, after every character and to every
            // space: `normal` is nothing, and a length may be negative, which
            // tightens. Resolved here against the element's own size, so a
            // child inherits the pixels.
            case "letter-spacing" ->
                spacing(value, context)
                        .map(v -> typography(
                                typography.lettering(typography.lettering().letterSpacing(v))))
                        .orElseGet(() -> dropped(property, value));

            case "word-spacing" ->
                spacing(value, context)
                        .map(v -> typography(
                                typography.lettering(typography.lettering().wordSpacing(v))))
                        .orElseGet(() -> dropped(property, value));

            // How many lines a paragraph keeps, the last marked when more were
            // cut. `-webkit-line-clamp` is the spelling most sheets have, and
            // means the same here: a browser also wants a `-webkit-box`, which
            // is a layout this toolkit does not have and does not need for it.
            case "line-clamp", "-webkit-line-clamp" ->
                lineClampCount(value).map(this::lineClamp).orElseGet(() -> dropped(property, value));

            // Shadows under the glyphs, first on top, each an offset, a blur and
            // a colour, the element's own when none is written. No spread and no
            // `inset`, which CSS's text shadow does not have either.
            case "text-shadow" ->
                Optional.ofNullable(Shadow.parse(value, context, color))
                        .filter(list -> list.stream().noneMatch(s -> s.inset() || s.spread() != 0))
                        .map(v -> typography(
                                typography.lettering(typography.lettering().shadows(v))))
                        .orElseGet(() -> dropped(property, value));

            // The case the text is drawn in, never the text a widget holds; one
            // character for one, so a field's caret still lands where it did.
            case "text-transform" ->
                keyword(value, TextTransform.class)
                        .map(v -> typography(
                                typography.lettering(typography.lettering().transform(v))))
                        .orElseGet(() -> dropped(property, value));

            // `[style] [weight] size[/line-height] family`. Every part it does not
            // name goes back to its initial value, as CSS's shorthands do: the
            // style and weight to normal, the line height to `normal`.
            case "font" -> font(value, context).orElseGet(() -> dropped(property, value));

            // --- motion: transitions and keyframe animations ------------------
            //
            // Resolved by the cascade like everything else, which is what lets
            // `button` and `button:hover` declare different transitions and lets
            // an application turn one off by overriding a rule.
            // `@keyframes`, run by name: the shorthand and its seven longhands,
            // each a comma-separated list. A bad value drops the declaration and
            // names it, `transition`'s rule.
            case "animation" -> animationList(value).map(v -> animations(v)).orElseGet(() -> dropped(property, value));

            case "animation-name" ->
                animationNames(value)
                        .map(v -> animations(animations.names(v)))
                        .orElseGet(() -> dropped(property, value));

            case "animation-duration" ->
                eachOf(value, part -> nonNegative(milliseconds(part)))
                        .map(v -> animations(animations.durations(v)))
                        .orElseGet(() -> dropped(property, value));

            case "animation-timing-function" ->
                eachOf(value, ComputedStyle::easing)
                        .map(v -> animations(animations.easings(v)))
                        .orElseGet(() -> dropped(property, value));

            case "animation-delay" ->
                eachOf(value, ComputedStyle::milliseconds)
                        .map(v -> animations(animations.delays(v)))
                        .orElseGet(() -> dropped(property, value));

            case "animation-iteration-count" ->
                eachOf(value, ComputedStyle::iterationCount)
                        .map(v -> animations(animations.iterations(v)))
                        .orElseGet(() -> dropped(property, value));

            case "animation-direction" ->
                eachOf(value, part -> single(part, KeyframeAnimations.Direction::parse))
                        .map(v -> animations(animations.directions(v)))
                        .orElseGet(() -> dropped(property, value));

            case "animation-fill-mode" ->
                eachOf(value, part -> single(part, KeyframeAnimations.FillMode::parse))
                        .map(v -> animations(animations.fillModes(v)))
                        .orElseGet(() -> dropped(property, value));

            // A paused animation holds where its clock stopped, and goes on
            // from there when it runs again.
            case "animation-play-state" ->
                eachOf(value, part -> single(part, KeyframeAnimations.PlayState::parse))
                        .map(v -> animations(animations.playStates(v)))
                        .orElseGet(() -> dropped(property, value));

            case "transition" ->
                transitionList(value)
                        // A lambda and not `this::transitions`: the accessor and the
                        // wither share a name, and a method reference cannot say
                        // which.
                        .map(v -> transitions(v))
                        .orElseGet(() -> dropped(property, value));

            // The shorthand's four parts, each a list. They pair up by position
            // with `transition-property`, which is why the lists are kept.
            case "transition-property" ->
                transitionProperties(value)
                        .map(v -> transitions(Transitions.of(transitions.lists().properties(v))))
                        .orElseGet(() -> dropped(property, value));

            case "transition-duration" ->
                eachOf(value, part -> nonNegative(milliseconds(part)))
                        .map(v -> transitions(Transitions.of(transitions.lists().durations(v))))
                        .orElseGet(() -> dropped(property, value));

            case "transition-timing-function" ->
                eachOf(value, ComputedStyle::easing)
                        .map(v -> transitions(Transitions.of(transitions.lists().easings(v))))
                        .orElseGet(() -> dropped(property, value));

            case "transition-delay" ->
                eachOf(value, ComputedStyle::milliseconds)
                        .map(v -> transitions(Transitions.of(transitions.lists().delays(v))))
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
                yield parsed == null ? dropped(property, value) : transform(transform.functions(parsed.functions()));
            }

            case "transform-origin" -> {
                var parsed = Transform.parseOrigin(value, context);
                yield parsed == null ? dropped(property, value) : transform(transform.origin(parsed));
            }

            // The three a rule sets apart from `transform`, applied before it:
            // translate, then rotate, then scale.
            case "translate" -> {
                var parsed = IndividualTransform.parseTranslate(value, context);
                yield parsed == null
                        ? dropped(property, value)
                        : transform(transform.individual(transform.individual().translate(parsed.function())));
            }

            case "rotate" -> {
                var parsed = IndividualTransform.parseRotate(value, context);
                yield parsed == null
                        ? dropped(property, value)
                        : transform(transform.individual(transform.individual().rotate(parsed.function())));
            }

            case "scale" -> {
                var parsed = IndividualTransform.parseScale(value, context);
                yield parsed == null
                        ? dropped(property, value)
                        : transform(transform.individual(transform.individual().scale(parsed.function())));
            }

            case "transform-box" ->
                Optional.ofNullable(single(value, TransformBox::named))
                        .map(v -> transform(transform.box(v)))
                        .orElseGet(() -> dropped(property, value));

            // Resolved here and read by neither engine: the cursor is carried
            // through the cascade to the box tree, where hit testing picks it
            // up. The enum's names are CSS's, so `ew-resize` maps onto
            // `EW_RESIZE` by the same rule `space-between` maps onto Yoga.
            case "cursor" -> keyword(value, Cursor.class).map(this::cursor).orElseGet(() -> dropped(property, value));

            // `all` takes nothing but the global keywords, which never reach
            // this switch: [#declare] applies them first. Anything else is not
            // a value it has.
            case "all" -> dropped(property, value);

            // Not an error. CSS has more properties than this record, and a
            // stylesheet naming one the toolkit does not implement should not
            // stop a window opening. The warning is the sheet's, once per
            // property when it is parsed (see `Stylesheet`); here, per node per
            // restyle, it would be a stream, so it stays at debug.
            default -> {
                var probe = PROBE.get();
                if (probe != null) {
                    probe[0] = true;
                } else {
                    LOG.debug("ignoring unsupported property \"{}\"", property);
                }
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
        return dropped(
                property,
                value,
                CssLength.parse(value, CssLength.Context.DEFAULT) instanceof Length.Mixed
                        ? "mixes a percentage with a length, which only a painted property can take: the layout"
                                + " engine sizes a box by one or the other"
                        : "is not a valid value");
    }

    /// The same, saying why: for a value CSS defines and this engine does not
    /// do, where "not a valid value" would send the author looking for a typo.
    private ComputedStyle dropped(String property, List<Token> value, String why) {
        if (PROBE.get() != null) {
            // [#isProperty] asking, with a value made to fail: not a drop.
            return this;
        }
        var text = text(value);
        // `add` returns false when it was already there, which is the whole test.
        if (REPORTED.size() >= REPORT_LIMIT || REPORTED.add(property + ':' + text)) {
            LOG.warn("dropping \"{}\": {} {}", property, text, why);
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
    /// failures: the `default` arm, where the property is not in the subset, and
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
    /// through thirty switch arms, for a difference the author reads off the
    /// guide's property list in either case.
    ///
    /// Custom properties are **not** this method's business and answer `false`:
    /// `--gb-accent` reaches the `default` arm and is not a fault, because the
    /// resolver has already consumed it for `var()` substitution. A caller that
    /// does not filter them reports every token in the theme.
    ///
    /// @param property the property name, already lowercased by the parser
    /// @param value    the declaration's tokens, with `var()` already substituted
    /// @param context  what `em` and `rem` resolve against
    public static boolean applies(String property, List<Token> value, CssLength.Context context) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(context, "context");
        if (GlobalKeyword.of(value) != null) {
            return Properties.isReadable(property);
        }
        return INITIAL.with(property, CurrentColour.substitute(value, INITIAL.color()), context) != INITIAL;
    }

    /// Whether `property` is one the engine has at all, whatever its value.
    ///
    /// Answered the way [#applies] is, by **running the engine** rather than by
    /// keeping a list: the property is handed to [#with] with no value, and the
    /// only arm that does not look at the value is the `default` one, which is
    /// the answer "no such property". So a property added to the switch is known
    /// here the moment it exists.
    ///
    /// Remembered per name, because a stylesheet asks once per declaration and
    /// the answer cannot change while the class is loaded.
    ///
    /// Custom properties answer false, as they do for [#applies].
    ///
    /// @param property the property name, lowercased as the parser leaves it
    public static boolean isProperty(String property) {
        Objects.requireNonNull(property, "property");
        return KNOWN.computeIfAbsent(property, ComputedStyle::probe);
    }

    private static boolean probe(String property) {
        var reachedDefault = new boolean[1];
        PROBE.set(reachedDefault);
        try {
            INITIAL.with(property, List.of(), CssLength.Context.DEFAULT);
        } catch (RuntimeException e) {
            // An arm that read the empty value and threw is an arm that exists.
            return true;
        } finally {
            PROBE.remove();
        }
        return !reachedDefault[0];
    }

    /// Set while [#isProperty] asks [#with], so the `default` arm can say it was
    /// reached and [#dropped] stays quiet about a value made to fail. A thread
    /// local rather than a scoped value only because `with` is called from
    /// thirty arms that would all have to pass it on.
    private static final ThreadLocal<boolean @Nullable []> PROBE = new ThreadLocal<>();

    private static final java.util.concurrent.ConcurrentHashMap<String, Boolean> KNOWN =
            new java.util.concurrent.ConcurrentHashMap<>();

    /// Forgets what has been reported, so a test can drive the same bad
    /// declaration twice.
    ///
    /// This exists for tests and for nothing else: a cache that could not be
    /// cleared would make the second test in a class depend on whether the first
    /// one had already tripped the same warning. It is **public** because one of
    /// those tests is in another module — `:example`'s lint over the toolkit's own
    /// stylesheets reads what the cascade said about them, and a drop another
    /// test in the same JVM had already reported would be a lint that passed by
    /// seeing nothing at all.
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
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle justifyContent(Justify v) {
        return new ComputedStyle(
                direction,
                v,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle alignItems(Align v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                v,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// This style with a different `align-content` — how its wrapped lines share
    /// the cross axis.

    public ComputedStyle alignContent(Align v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                v,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle alignSelf(Align v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                v,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle wrap(Wrap v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                v,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle width(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                v,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle height(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                v,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle margin(Insets v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                v,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle padding(Insets v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                v,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
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
                alignContent,
                wrap,
                width,
                height,
                v,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// The gap, for a box whose two gaps agree, which is every box that wrote
    /// `gap` and nothing more specific.
    ///
    /// Where `row-gap` and `column-gap` differ this is the **row** gap. A reader
    /// that lays out in both directions reads [#rowGap()] and [#columnGap()].
    public Length gap() {
        return rowGap;
    }

    /// Both gaps at once, which is what `gap: 8px` writes.
    public ComputedStyle gap(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                v,
                v,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// `row-gap` alone: the space between lines, or between items in a column.
    public ComputedStyle rowGap(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                v,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// `column-gap` alone: the space between items in a row, or between columns.
    public ComputedStyle columnGap(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                v,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle flexGrow(double v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                v,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// This style with a different `flex-basis` — the main-axis size its box
    /// starts from.

    public ComputedStyle flexBasis(Length v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                v,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle flexShrink(double v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                v,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle position(Position v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                v,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle inset(Insets v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                v,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// This style with both axes' overflow replaced: `overflow` with one
    /// value.
    public ComputedStyle overflow(Overflow v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                v,
                v,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// This style with the horizontal overflow replaced: `overflow-x`.
    public ComputedStyle overflowX(Overflow v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                v,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// This style with the vertical overflow replaced: `overflow-y`.
    public ComputedStyle overflowY(Overflow v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                v,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// The two axes joined, which is what the box gets: the one that cuts
    /// most, `scroll` over `hidden` over `clip` over `visible`. Yoga and the
    /// clip are one value each, so an axis left `visible` beside a clipped one
    /// is clipped too, which is also what CSS computes for every pair but
    /// `visible` beside `clip`.
    public Overflow overflow() {
        return overflowX.compareTo(overflowY) >= 0 ? overflowX : overflowY;
    }

    /// This style with `display` and `aspect-ratio` replaced.
    public ComputedStyle presence(Presence v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                v,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle whiteSpace(WhiteSpace v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                v,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle textOverflow(TextOverflow v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                v,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// `line-clamp`: keep `v` lines, 0 for all of them.
    ///
    /// @throws IllegalArgumentException if `v` is negative
    public ComputedStyle lineClamp(int v) {
        if (v < 0) {
            throw new IllegalArgumentException("a paragraph keeps a number of lines, not " + v);
        }
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                v,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle textAlign(TextAlign v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                v,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// This style with a different `text-decoration` — see the component's note on
    /// why it inherits.
    public ComputedStyle textDecoration(Set<TextDecoration> v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                v,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// `overflow-wrap`: whether a word wider than the line is cut.
    public ComputedStyle overflowWrap(OverflowWrap v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                v,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    /// `word-break`: whether a line may break between any two graphemes.
    public ComputedStyle wordBreak(WordBreak v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                v,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
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
    /// [Box#text(dev.goldberry.text.Paragraph, int,
    /// TextFlow)].
    public TextFlow textFlow() {
        return new TextFlow(whiteSpace, textOverflow, lineAlign(), textDecoration, overflowWrap, wordBreak, lineClamp);
    }

    /// `text-align` as a line is placed in this box: `start` and `end` turned
    /// into the sides they are for its `direction`. [#textAlign()] is the
    /// keyword, which is what a child inherits.
    ///
    /// Under `unicode-bidi: plaintext` each paragraph has its own direction, so
    /// `start` and `end` are left as they are for the paragraph to place.
    public TextAlign lineAlign() {
        var align = textAlign == TextAlign.JUSTIFY && typography.lettering().justify() == TextJustify.NONE
                ? TextAlign.START
                : textAlign;
        return presence.unicodeBidi() == UnicodeBidi.PLAINTEXT ? align : align.in(presence.direction());
    }

    /// Where this box's text takes its base direction from: its `direction`
    /// and `unicode-bidi`, which is what a paragraph of its text is shaped
    /// under.
    public Directionality directionality() {
        return Directionality.of(presence.direction(), presence.unicodeBidi());
    }

    /// The background's colour, under any gradient layers on it — what a
    /// contrast check, a `background-color` transition and nearly every
    /// widget mean by "the background".
    public int background() {
        return fill.colour();
    }

    /// The background's colour replaced, its layers kept — `background-color`.
    public ComputedStyle background(int v) {
        return fill(fill.colour(v));
    }

    public ComputedStyle fill(Background v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                v,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle color(int v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                v,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle opacity(double v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                v,
                decoration,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle decoration(Decoration v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                v,
                typography,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle typography(Typography v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                v,
                transitions,
                animations,
                transform,
                cursor);
    }

    public ComputedStyle transitions(Transitions v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                v,
                animations,
                transform,
                cursor);
    }

    /// This style running `v`: what the `animation` properties resolved to.
    public ComputedStyle animations(KeyframeAnimations v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
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
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
                v,
                cursor);
    }

    public ComputedStyle cursor(Cursor v) {
        return new ComputedStyle(
                direction,
                justifyContent,
                alignItems,
                alignSelf,
                alignContent,
                wrap,
                width,
                height,
                limits,
                margin,
                padding,
                rowGap,
                columnGap,
                flexGrow,
                flexShrink,
                flexBasis,
                position,
                inset,
                overflowX,
                overflowY,
                presence,
                whiteSpace,
                textOverflow,
                lineClamp,
                textAlign,
                textDecoration,
                overflowWrap,
                wordBreak,
                fill,
                color,
                opacity,
                decoration,
                typography,
                transitions,
                animations,
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
    private static Optional<Double> points(List<Token> value, CssLength.Context context) {
        return length(value, context).filter(Length.Points.class::isInstance).map(v ->
                (double) ((Length.Points) v).value());
    }

    /// A font family name — an identifier or a quoted string.
    ///
    /// Only the **first** name of a list is taken. CSS's comma-separated list is
    /// a fallback chain, and there is no fallback cascade here: a character
    /// outside the bundled faces renders `.notdef`, deliberately. Honouring the
    /// rest of the list would be pretending to a mechanism that does not exist.
    ///
    /// Read more: [The bundled faces](https://goldberry.dev/docs/guide/text.html#the-bundled-faces).
    private static Optional<String> family(List<Token> value) {
        for (var part : split(value)) {
            if (part.isEmpty()) {
                continue;
            }
            var token = part.getFirst();
            if (token.is(TokenType.IDENT) || token.is(TokenType.STRING)) {
                // `Inter, sans-serif` and `"JetBrains Mono", monospace` both stop
                // at the first name. No comma to strip: the tokenizer emits one as
                // a `COMMA` of its own, so an IDENT or a STRING never carries a
                // trailing one.
                return Optional.of(token.text());
            }
        }
        return Optional.empty();
    }

    /// A CSS weight — a number from 1 to 1000, or `normal` (400) / `bold` (700).
    ///
    /// Carried as the number, rounded to a whole one: which face it lands on
    /// is the font book's question, because it depends on which faces the
    /// family has.
    private static Optional<Integer> weight(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 && !Maths.isMath(value)) {
            return Optional.empty();
        }
        var token = tokens.getFirst();
        if (token.is(TokenType.IDENT)) {
            return switch (token.text().toLowerCase(Locale.ROOT)) {
                case "normal" -> Optional.of(400);
                case "bold" -> Optional.of(700);
                default -> Optional.empty();
            };
        }
        return Optional.ofNullable(CssLength.parseNumber(value))
                .filter(v -> v >= 1 && v <= 1000)
                .map(v -> (int) Math.round(v));
    }

    /// A CSS `font-style`, in the two values a shipped face can honour.
    ///
    /// `oblique` is refused for the reason the parse site gives, and refusing it is
    /// visible: the declaration is dropped with a warning rather than silently read
    /// as `italic`, so a stylesheet that asked for a slant learns that this toolkit
    /// does not synthesize one.
    private static Optional<BundledFont.Style> fontStyle(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 || !tokens.getFirst().is(TokenType.IDENT)) {
            return Optional.empty();
        }
        return switch (tokens.getFirst().text().toLowerCase(Locale.ROOT)) {
            case "normal" -> Optional.of(BundledFont.Style.UPRIGHT);
            case "italic" -> Optional.of(BundledFont.Style.ITALIC);
            default -> Optional.empty();
        };
    }

    /// What `line-height: normal` is: 1.2 times the size, which is within a
    /// pixel of what a browser gives Inter from its own metrics. The face's
    /// metrics are the text engine's and are not known when a style is
    /// computed.
    private static final double NORMAL_LINE_HEIGHT = 1.2;

    /// The ratio CSS's absolute size keywords stand at from `medium`.
    private static final Map<String, Double> FONT_SIZE_KEYWORDS = Map.of(
            "xx-small", 3.0 / 5,
            "x-small", 3.0 / 4,
            "small", 8.0 / 9,
            "medium", 1.0,
            "large", 6.0 / 5,
            "x-large", 3.0 / 2,
            "xx-large", 2.0,
            "xxx-large", 3.0);

    /// How much `larger` grows a size by, and `smaller` shrinks it.
    private static final double RELATIVE_SIZE_STEP = 1.2;

    /// A `font-size`, in logical pixels: a length, a percentage of
    /// `context`'s font size, an absolute keyword stepped from `context`'s
    /// root size, or `smaller` and `larger`. Above zero.
    private static Optional<Double> fontSize(List<Token> value, CssLength.Context context) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() == 1 && tokens.getFirst().is(TokenType.PERCENTAGE)) {
            return Optional.of(tokens.getFirst().numeric() / 100 * context.fontSize())
                    .filter(v -> v > 0);
        }
        if (tokens.size() == 1 && tokens.getFirst().is(TokenType.IDENT)) {
            var word = tokens.getFirst().text().toLowerCase(Locale.ROOT);
            var step = FONT_SIZE_KEYWORDS.get(word);
            if (step != null) {
                return Optional.of(step * context.rootFontSize());
            }
            return switch (word) {
                case "larger" -> Optional.of(context.fontSize() * RELATIVE_SIZE_STEP);
                case "smaller" -> Optional.of(context.fontSize() / RELATIVE_SIZE_STEP);
                default -> Optional.empty();
            };
        }
        return points(value, context).filter(v -> v > 0);
    }

    /// The `font` shorthand applied, or empty when it is not one.
    ///
    /// The style and the weight come first, in either order, then the size,
    /// then an optional `/line-height`, and the family last. A system font name
    /// (`caption`, `menu`) is not one, since the desktop's fonts are not the
    /// toolkit's.
    private Optional<ComputedStyle> font(List<Token> value, CssLength.Context context) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        var style = BundledFont.Style.UPRIGHT;
        var weight = 400;
        var index = 0;
        Double size = null;
        while (index < tokens.size() && size == null) {
            var token = List.of(tokens.get(index));
            var asSize = fontSize(token, context);
            if (asSize.isPresent() && !tokens.get(index).isIdent("normal")) {
                size = asSize.get();
            } else if (tokens.get(index).isIdent("normal")) {
                // `normal` is the style's word and the weight's, and both are
                // their defaults already.
            } else if (fontStyle(token).isPresent()) {
                style = fontStyle(token).get();
            } else if (weight(token).isPresent()) {
                weight = weight(token).get();
            } else {
                return Optional.empty();
            }
            index++;
        }
        if (size == null) {
            return Optional.empty();
        }
        double lineHeight = -NORMAL_LINE_HEIGHT;
        if (index < tokens.size() && tokens.get(index).isDelim('/')) {
            if (index + 1 >= tokens.size()) {
                return Optional.empty();
            }
            // Against the size this declaration sets, which is what `em` in a
            // line height means.
            var own = context.withFontSize(size.floatValue());
            var height = lineHeight(List.of(tokens.get(index + 1)), own);
            if (height.isEmpty()) {
                return Optional.empty();
            }
            lineHeight = height.get();
            index += 2;
        }
        var family = family(value.subList(indexOf(value, tokens, index), value.size()));
        if (index >= tokens.size() || family.isEmpty()) {
            return Optional.empty();
        }
        // The spacing is not the font's, and `font` leaves it where it was.
        return Optional.of(
                typography(new Typography(family.get(), size, weight, style, lineHeight, typography.lettering())));
    }

    /// Where the `index`th of `tokens`, the non-whitespace ones, sits in
    /// `value`.
    private static int indexOf(List<Token> value, List<Token> tokens, int index) {
        if (index >= tokens.size()) {
            return value.size();
        }
        var target = tokens.get(index);
        for (var i = 0; i < value.size(); i++) {
            if (value.get(i) == target) {
                return i;
            }
        }
        return value.size();
    }

    /// A `line-height`: `normal`, a length, or a bare number meaning a multiple
    /// of the size.
    ///
    /// Returned already negated in the ratio case, which is how [Typography]
    /// carries both forms in one field.
    private static Optional<Double> lineHeight(List<Token> value, CssLength.Context context) {
        if (isKeyword(value, "normal")) {
            return Optional.of(-NORMAL_LINE_HEIGHT);
        }

        var absolute = points(value, context);
        if (absolute.isPresent()) {
            return absolute.filter(v -> v > 0);
        }
        return Optional.ofNullable(CssLength.parseNumber(value))
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
    /// `all` names every animatable property, and a later entry for one of
    /// them wins over it, as CSS says. A timing function that cannot be read is
    /// skipped with a warning and the entry keeps the default curve; see
    /// [#unreadableEasing].
    ///
    /// **The whole declaration is dropped if any entry is bad**, and the
    /// property that made it bad is named. Half a transition list is worse than
    /// none: the author sees two of their three properties moving and has
    /// nothing to tell them which one the parser refused. In particular
    /// `transition: width 200ms` is refused rather than ignored — layout
    /// properties never transition, and an author who asked for one is asking
    /// for something the system deliberately will not do.
    ///
    /// Read more:
    /// [Transition and animation](https://goldberry.dev/docs/guide/styling.html#transition-and-animation).
    private Optional<Transitions> transitionList(List<Token> value) {

        var entries = splitOnCommas(value);
        if (entries.size() == 1
                && entries.getFirst().size() == 1
                && entries.getFirst().getFirst().isIdent("none")) {
            return Optional.of(Transitions.NONE);
        }

        var names = new ArrayList<String>();
        var durations = new ArrayList<Double>();
        var easings = new ArrayList<Easing>();
        var delays = new ArrayList<Double>();
        for (var entry : entries) {
            var parts = split(entry);
            if (parts.isEmpty()) {
                return Optional.empty();
            }

            String property = null;
            Easing easing = null;
            Double duration = null;
            Double delay = null;

            for (var part : parts) {
                if (CssEasing.looksLikeEasing(part)) {
                    var curve = CssEasing.parse(part);
                    if (curve == null) {
                        unreadableEasing("transition", part);
                        continue;
                    }
                    if (easing != null) {
                        return Optional.empty();
                    }
                    easing = curve;
                    continue;
                }
                if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
                    var name = part.getFirst().text();
                    if (property == null && name.equalsIgnoreCase("all")) {
                        property = "all";
                        continue;
                    }
                    var asProperty = Transitions.Animatable.parse(name);
                    if (asProperty != null && property == null) {
                        property = asProperty.cssName();
                        continue;
                    }
                    return Optional.empty();
                }
                var time = milliseconds(part);
                if (time == null) {
                    return Optional.empty();
                }
                // CSS's rule: the first time is the duration, the second the
                // delay. Ordering carries meaning here because both are times
                // and neither has a unit the other does not.
                if (duration == null) {
                    duration = time;
                } else if (delay == null) {
                    delay = time;
                } else {
                    return Optional.empty();
                }
            }

            if (property == null || duration == null || duration < 0) {
                return Optional.empty();
            }
            names.add(property);
            durations.add(duration);
            // The default for anything that does not say: an enter curve,
            // because most transitions are something arriving.
            easings.add(easing == null ? Easing.EASE_ENTER : easing);
            delays.add(delay == null ? 0 : delay);
        }
        return Optional.of(Transitions.of(new Transitions.Lists(names, durations, easings, delays)));
    }

    /// `transition-property`: `none`, or a comma-separated list of animatable
    /// properties and `all`. A name that is not one is the whole declaration
    /// dropped, as in the shorthand.
    private static Optional<List<String>> transitionProperties(List<Token> value) {
        var entries = splitOnCommas(value);
        if (entries.size() == 1
                && entries.getFirst().size() == 1
                && entries.getFirst().getFirst().isIdent("none")) {
            return Optional.of(List.of());
        }
        var names = new ArrayList<String>();
        for (var entry : entries) {
            var parts = split(entry);
            if (parts.size() != 1
                    || parts.getFirst().size() != 1
                    || !parts.getFirst().getFirst().is(TokenType.IDENT)) {
                return Optional.empty();
            }
            var name = parts.getFirst().getFirst().text();
            if (name.equalsIgnoreCase("all")) {
                names.add("all");
                continue;
            }
            var property = Transitions.Animatable.parse(name);
            if (property == null) {
                return Optional.empty();
            }
            names.add(property.cssName());
        }
        return Optional.of(names);
    }

    /// An `animation` shorthand: a comma-separated list of
    /// `<name> <duration> [<easing>] [<delay>] [<count>] [<direction>] [<fill>]`,
    /// in any order but with the first time the duration and the second the delay,
    /// which is CSS's rule and `transition`'s.
    ///
    /// `none` alone turns every animation off. A name that collides with a
    /// keyword (`infinite`, `both`, `reverse`) is read as the keyword, which is
    /// also CSS's rule, so a block called `both` can only be named through
    /// `animation-name`.
    ///
    /// All or nothing, like `transition`: a list with one bad entry is dropped
    /// whole. A timing function that cannot be read is the exception, for the
    /// reason [#unreadableEasing] gives.
    private static Optional<KeyframeAnimations> animationList(List<Token> value) {
        var entries = splitOnCommas(value);
        if (entries.size() == 1
                && entries.getFirst().size() == 1
                && entries.getFirst().getFirst().isIdent("none")) {
            return Optional.of(KeyframeAnimations.NONE);
        }
        var names = new ArrayList<String>();
        var durations = new ArrayList<Double>();
        var easings = new ArrayList<Easing>();
        var delays = new ArrayList<Double>();
        var counts = new ArrayList<Double>();
        var directions = new ArrayList<KeyframeAnimations.Direction>();
        var fills = new ArrayList<KeyframeAnimations.FillMode>();
        var states = new ArrayList<KeyframeAnimations.PlayState>();
        for (var entry : entries) {
            String name = null;
            Double duration = null;
            Double delay = null;
            Double count = null;
            Easing easing = null;
            KeyframeAnimations.Direction direction = null;
            KeyframeAnimations.FillMode fill = null;
            KeyframeAnimations.PlayState state = null;
            for (var part : split(entry)) {
                if (easing == null && CssEasing.looksLikeEasing(part)) {
                    var curve = CssEasing.parse(part);
                    if (curve == null) {
                        unreadableEasing("animation", part);
                    } else {
                        easing = curve;
                    }
                    continue;
                }
                if (part.size() != 1) {
                    return Optional.empty();
                }
                var token = part.getFirst();
                // **A bare number is the iteration count, before it is anything
                // else.** `animation` is the one shorthand where a unitless number
                // means something of its own, and [#milliseconds] accepts a
                // unitless zero -- rightly, for `transition`, which has no count
                // to confuse it with. Asked first, it swallowed the `0` in
                // `animation: spin 1s 0` as the delay and set the count to its
                // default of one, so an animation asked to run no times ran once.
                if (token.is(TokenType.NUMBER)) {
                    if (count != null || token.numeric() < 0) {
                        return Optional.empty();
                    }
                    count = token.numeric();
                    continue;
                }
                var time = milliseconds(part);
                if (time != null) {
                    if (duration == null) {
                        duration = time;
                    } else if (delay == null) {
                        delay = time;
                    } else {
                        return Optional.empty();
                    }
                    continue;
                }
                if (!token.is(TokenType.IDENT) && !token.is(TokenType.STRING)) {
                    return Optional.empty();
                }
                var word = token.text();
                if (token.is(TokenType.IDENT)) {
                    if (count == null && word.equalsIgnoreCase("infinite")) {
                        count = Double.POSITIVE_INFINITY;
                        continue;
                    }
                    var asDirection = KeyframeAnimations.Direction.parse(word);
                    if (direction == null && asDirection != null) {
                        direction = asDirection;
                        continue;
                    }
                    var asFill = KeyframeAnimations.FillMode.parse(word);
                    if (fill == null && asFill != null && !word.equalsIgnoreCase("none")) {
                        fill = asFill;
                        continue;
                    }
                    var asState = KeyframeAnimations.PlayState.parse(word);
                    if (state == null && asState != null) {
                        state = asState;
                        continue;
                    }
                }
                if (name != null) {
                    return Optional.empty();
                }
                name = word;
            }
            if (name == null) {
                return Optional.empty();
            }
            names.add(name);
            durations.add(duration == null ? 0 : nonNegativeOr(duration));
            easings.add(easing == null ? Easing.EASE_ENTER : easing);
            delays.add(delay == null ? 0 : delay);
            counts.add(count == null ? 1 : count);
            directions.add(direction == null ? KeyframeAnimations.Direction.NORMAL : direction);
            fills.add(fill == null ? KeyframeAnimations.FillMode.NONE : fill);
            states.add(state == null ? KeyframeAnimations.PlayState.RUNNING : state);
        }
        if (durations.contains(-1.0)) {
            return Optional.empty();
        }
        return Optional.of(
                new KeyframeAnimations(names, durations, easings, delays, counts, directions, fills, states));
    }

    /// `animation-name`: `none`, or a comma-separated list of names.
    private static Optional<List<String>> animationNames(List<Token> value) {
        var entries = splitOnCommas(value);
        if (entries.size() == 1
                && entries.getFirst().size() == 1
                && entries.getFirst().getFirst().isIdent("none")) {
            return Optional.of(List.of());
        }
        var names = new ArrayList<String>();
        for (var entry : entries) {
            var parts = split(entry);
            if (parts.size() != 1 || parts.getFirst().size() != 1) {
                return Optional.empty();
            }
            var token = parts.getFirst().getFirst();
            if (!(token.is(TokenType.IDENT) || token.is(TokenType.STRING))) {
                return Optional.empty();
            }
            names.add(token.text());
        }
        return Optional.of(names);
    }

    /// A comma-separated list where every entry is one value `parse` reads, or
    /// empty when any entry is not.
    private static <T> Optional<List<T>> eachOf(List<Token> value, Function<List<Token>, @Nullable T> parse) {
        var values = new ArrayList<T>();
        for (var entry : splitOnCommas(value)) {
            // One value per entry, with the spacing around the comma taken off.
            var parts = split(entry);
            var parsed = parts.size() == 1 ? parse.apply(parts.getFirst()) : null;
            if (parsed == null) {
                return Optional.empty();
            }
            values.add(parsed);
        }
        return values.isEmpty() ? Optional.empty() : Optional.of(values);
    }

    /// One identifier's worth of `parse`, or null.
    private static <T> @Nullable T single(List<Token> part, Function<String, @Nullable T> parse) {
        return part.size() == 1 && part.getFirst().is(TokenType.IDENT)
                ? parse.apply(part.getFirst().text())
                : null;
    }

    private static @Nullable Easing easing(List<Token> part) {
        return CssEasing.parse(part);
    }

    /// Warns, once per text, that a timing function in `property` was skipped:
    /// one that looks like a curve and cannot be read, such as
    /// `cubic-bezier(2, 0, 0, 1)` or `steps(0)`.
    ///
    /// **Such a part drops itself, not the declaration.** The curve is the least
    /// of what a shorthand says; dropping `orc-pulse 900ms … infinite alternate`
    /// whole over its curve leaves a node that never moves, where skipping the
    /// curve leaves one that moves on the default.
    private static void unreadableEasing(String property, List<Token> part) {
        var text = text(part);
        if (REPORTED.size() >= REPORT_LIMIT || REPORTED.add(property + ":easing:" + text)) {
            LOG.warn(
                    "ignoring the timing function {} in \"{}\": it is not one CSS can read, so the default"
                            + " ease-enter runs instead",
                    text,
                    property);
        }
    }

    /// `infinite` or a non-negative number.
    private static @Nullable Double iterationCount(List<Token> part) {
        if (part.size() != 1) {
            return null;
        }
        var token = part.getFirst();
        if (token.isIdent("infinite")) {
            return Double.POSITIVE_INFINITY;
        }
        return token.is(TokenType.NUMBER) && token.numeric() >= 0 ? token.numeric() : null;
    }

    /// A duration, which may not be negative the way a delay may.
    private static @Nullable Double nonNegative(@Nullable Double millis) {
        return millis == null || millis < 0 ? null : millis;
    }

    /// The same inside a shorthand, where the refusal is noted as `-1` and
    /// checked once the entry is complete.
    private static double nonNegativeOr(double millis) {
        return millis < 0 ? -1.0 : millis;
    }

    /// The milliseconds a duration value says, or empty when it is not one.
    ///
    /// [#milliseconds]'s public face, and it exists for the reason
    /// [#applies] does: something above the cascade has a question only the
    /// cascade's own parser can answer honestly. Here it is a **duration custom
    /// property** — `--gb-tooltip-delay: 500ms` — read by the launcher, which
    /// schedules a timer and has no `ComputedStyle` to read it off: a delay is a
    /// metric, and metrics are tokens in the theme.
    ///
    /// Reusing this rather than writing a second `ms`/`s` reader is the whole
    /// point: two parsers for one syntax disagree the day either grows a unit,
    /// and this one already refuses a bare `200` for a stated reason.
    public static OptionalDouble durationMillis(List<Token> value) {
        Objects.requireNonNull(value, "value");
        var parsed = milliseconds(value);
        return parsed == null ? OptionalDouble.empty() : OptionalDouble.of(parsed);
    }

    /// A time in `ms` or `s`, or a math function of times, as milliseconds.
    ///
    /// Unitless zero is accepted, because `0` has no duration to be wrong about
    /// — the same allowance [CssLength] makes for a zero length. Anything else
    /// unitless is refused: `transition: color 200` almost certainly means
    /// milliseconds, and guessing would make the one stylesheet that meant
    /// seconds silently wrong.
    private static @Nullable Double milliseconds(List<Token> part) {
        if (Maths.isMath(part)) {
            var math = Maths.fold(part, CssLength.Context.DEFAULT);
            return math != null && math.kind() == Quantity.Kind.TIME ? math.value() : null;
        }
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
        var entries = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
        // Only the commas between entries: the ones inside `cubic-bezier(…)`
        // belong to the function, and splitting there turned one entry into
        // four broken ones.
        var depth = 0;
        for (var token : value) {
            if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            }
            if (depth == 0 && token.is(TokenType.COMMA)) {
                entries.add(List.copyOf(current));
                current.clear();
            } else {
                current.add(token);
            }
        }
        entries.add(List.copyOf(current));
        return entries;
    }

    /// The width, colour and style of a `border:`, `border-<side>:` or
    /// `outline:` shorthand.
    private record Stroke(double width, int argb, Border.Style style) {

        /// The side this stroke draws.
        Border.Line line() {
            return new Border.Line(width, argb, style);
        }
    }

    /// CSS's `<width> || <style> || <color>` shorthand, in any order.
    ///
    /// The style is carried to the border, which draws `solid`, `dashed`,
    /// `dotted` and `double` and draws the four bevelled styles solid — saying
    /// so once, at warn, so "my groove is flat" has an answer. A shorthand that
    /// names no style draws solid, which is how every rule the toolkit ships was
    /// written before styles were drawn.
    ///
    /// `none` sets the width to zero, which is how a rule turns a border off
    /// without having to say `border-width: 0`.
    private static Optional<Stroke> stroke(List<Token> value, CssLength.Context context) {
        Double width = null;
        Integer argb = null;
        var style = Border.Style.SOLID;
        for (var part : split(value)) {
            if (part.size() == 1 && part.getFirst().is(TokenType.IDENT)) {
                var keyword = part.getFirst().text().toLowerCase(Locale.ROOT);
                if (keyword.equals("none") || keyword.equals("hidden")) {
                    return Optional.of(new Stroke(0, CssColor.TRANSPARENT, Border.Style.SOLID));
                }
                var named = Border.Style.parse(keyword);
                if (named != null) {
                    style = named;
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
                return Optional.empty();
            }
            argb = asColour;
        }
        // A shorthand always resets what it does not mention, which is what makes
        // it a shorthand rather than three separate declarations: `border: red`
        // after `border: 2px solid blue` is a 0px border, not a red 2px one.
        return Optional.of(new Stroke(width == null ? 0 : width, argb == null ? CssColor.TRANSPARENT : argb, style));
    }

    private static Optional<Integer> colour(List<Token> value) {
        return Optional.ofNullable(CssColor.parse(value));
    }

    /// A length the layout engine can take: not a [Length.Mixed], which it has
    /// no spelling for.
    private static Optional<Length> length(List<Token> value, CssLength.Context context) {
        return Optional.ofNullable(CssLength.parse(value, context)).filter(v -> !(v instanceof Length.Mixed));
    }

    /// [#length] for a property the layout engine has **no `auto` function
    /// for** — `padding`, `inset`, `gap` and the four bounds.
    ///
    /// Yoga's setters come in pairs, a value one and an `auto` one, and four of
    /// them have no second half: there is no `YGNodeStyleSetPaddingAuto` and no
    /// `YGNodeStyleSetMinWidthAuto`. `Yoga` binds those without their auto call
    /// and refuses one **by name** rather than dropping it silently — which is
    /// the right choice there and made `padding: auto` in a stylesheet an
    /// exception thrown in the middle of a layout pass, which is a window
    /// closing over one typo.
    ///
    /// So the refusal moves to where the declaration is read. The rule for a
    /// value the engine cannot honour is to drop it and say so, and dropping it
    /// here is the difference between a rule that does nothing and a frame that
    /// does not happen.
    ///
    /// `width`, `height` and `margin` do **not** go through this: Yoga binds all
    /// three with their auto call, and `margin: 0 auto` is the reason margin was
    /// wanted.
    private static Optional<Length> fixed(List<Token> value, CssLength.Context context) {
        return length(value, context).filter(v -> v != Length.AUTO);
    }

    /// `gap`: one length for both gutters, or the row gap and then the column
    /// gap. Empty when either is not a fixed length, so half a `gap` is never
    /// applied.
    private Optional<ComputedStyle> gaps(List<Token> value, CssLength.Context context) {
        var parts = split(value);
        if (parts.isEmpty() || parts.size() > 2) {
            return Optional.empty();
        }
        var row = fixed(parts.getFirst(), context);
        var column = parts.size() == 1 ? row : fixed(parts.get(1), context);
        if (row.isEmpty() || column.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rowGap(row.get()).columnGap(column.get()));
    }

    /// The `flex` shorthand: `none`, `auto`, `initial`, or up to a grow, a
    /// shrink and a basis.
    ///
    /// CSS's expansions, which are not what the three numbers would suggest on
    /// their own: `flex: 1` is `1 1 0%`, not `1 1 auto`, so that equal growers
    /// share the whole row rather than the room left after their content.
    ///
    /// | written | grow | shrink | basis |
    /// |---|---|---|---|
    /// | `none` | 0 | 0 | auto |
    /// | `auto` | 1 | 1 | auto |
    /// | `initial` | 0 | 1 | auto |
    /// | `2` | 2 | 1 | 0% |
    /// | `2 3` | 2 | 3 | 0% |
    /// | `2 120px` | 2 | 1 | 120px |
    /// | `120px` | 1 | 1 | 120px |
    ///
    /// A first number is always the grow factor, so `flex: 0` is `0 1 0%`,
    /// which is CSS's reading too.
    private Optional<ComputedStyle> flex(List<Token> value, CssLength.Context context) {
        var parts = split(value);
        if (parts.size() == 1
                && parts.getFirst().size() == 1
                && parts.getFirst().getFirst().is(TokenType.IDENT)) {
            return switch (parts.getFirst().getFirst().text().toLowerCase(Locale.ROOT)) {
                case "none" -> Optional.of(flexGrow(0).flexShrink(0).flexBasis(Length.AUTO));
                case "auto" -> Optional.of(flexGrow(1).flexShrink(1).flexBasis(Length.AUTO));
                case "initial" -> Optional.of(flexGrow(0).flexShrink(1).flexBasis(Length.AUTO));
                default ->
                    length(parts.getFirst(), context)
                            .map(basis -> flexGrow(1).flexShrink(1).flexBasis(basis));
            };
        }
        if (parts.isEmpty() || parts.size() > 3) {
            return Optional.empty();
        }
        Double grow = null;
        Double shrink = null;
        Length basis = null;
        for (var part : parts) {
            var asNumber = part.size() == 1 && part.getFirst().is(TokenType.NUMBER)
                    ? (Double) part.getFirst().numeric()
                    : mathNumber(part);
            if (asNumber != null && basis == null && (grow == null || shrink == null)) {
                if (asNumber < 0) {
                    return Optional.empty();
                }
                if (grow == null) {
                    grow = asNumber;
                } else {
                    shrink = asNumber;
                }
                continue;
            }
            if (basis != null) {
                return Optional.empty();
            }
            var asLength = length(part, context);
            if (asLength.isEmpty()) {
                return Optional.empty();
            }
            basis = asLength.get();
        }
        if (grow == null) {
            // A basis alone: `flex: 120px` grows and shrinks from it.
            return Optional.of(flexGrow(1).flexShrink(1).flexBasis(Objects.requireNonNull(basis)));
        }
        return Optional.of(flexGrow(grow)
                .flexShrink(shrink == null ? 1 : shrink)
                .flexBasis(basis == null ? Length.percent(0) : basis));
    }

    /// CSS's 1-4 value edge shorthand.
    ///
    /// Empty if any part fails to parse, so `padding: 8px nonsense` is dropped
    /// whole rather than applied to two edges out of four — a half-applied
    /// shorthand is harder to see than one that did nothing. That includes an
    /// `auto` where `auto` is not allowed: `padding: 8px auto` is dropped whole,
    /// for the same reason and by [#fixed]'s rule.
    ///
    /// @param auto whether `auto` is a value this property takes — true for
    ///        `margin` alone, of the three that use this
    private static Optional<Insets> insets(List<Token> value, CssLength.Context context, boolean auto) {
        var parts = new ArrayList<Length>();
        for (var token : split(value)) {
            var length = CssLength.parse(token, context);
            if (length == null || length instanceof Length.Mixed || (!auto && length == Length.AUTO)) {
                return Optional.empty();
            }
            parts.add(length);
        }
        return Optional.ofNullable(
                switch (parts.size()) {
                    case 1 -> Insets.all(parts.getFirst());
                    case 2 -> Insets.symmetric(parts.get(0), parts.get(1));
                    case 3 -> new Insets(parts.get(0), parts.get(1), parts.get(2), parts.get(1));
                    case 4 -> new Insets(parts.get(0), parts.get(1), parts.get(2), parts.get(3));
                    default -> null;
                });
    }

    /// CSS's 1-4 value side shorthand over any value — `border-width` and
    /// `border-color` — as four values: top, right, bottom, left.
    ///
    /// [#insets]'s fill-in rule and its refusal: empty if any part fails, so
    /// `border-color: red nonsense` is dropped whole rather than colouring two
    /// sides of four.
    private static <T> Optional<List<T>> sides(List<Token> value, Function<List<Token>, Optional<T>> each) {
        var parts = new ArrayList<T>(4);
        for (var token : split(value)) {
            var parsed = each.apply(token);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            parts.add(parsed.get());
        }
        return Optional.ofNullable(
                switch (parts.size()) {
                    case 1 -> List.of(parts.getFirst(), parts.getFirst(), parts.getFirst(), parts.getFirst());
                    case 2 -> List.of(parts.get(0), parts.get(1), parts.get(0), parts.get(1));
                    case 3 -> List.of(parts.get(0), parts.get(1), parts.get(2), parts.get(1));
                    case 4 -> List.copyOf(parts);
                    default -> null;
                });
    }

    /// One `border-style` keyword: a style, or `none` and `hidden`, which are
    /// the empty string here and a side with no width once applied.
    private static Optional<String> sideStyle(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 || !tokens.getFirst().is(TokenType.IDENT)) {
            return Optional.empty();
        }
        var word = tokens.getFirst().text();
        if (word.equalsIgnoreCase("none") || word.equalsIgnoreCase("hidden")) {
            return Optional.of("");
        }
        return Border.Style.parse(word) == null ? Optional.empty() : Optional.of(word);
    }

    /// An `outline-style` keyword: a border style's name, `solid` for `auto`,
    /// or empty for `none`.
    private static Optional<String> outlineStyle(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() == 1 && tokens.getFirst().isIdent("auto")) {
            return Optional.of("solid");
        }
        return tokens.size() == 1 && tokens.getFirst().isIdent("hidden") ? Optional.empty() : sideStyle(value);
    }

    /// `line` in the style `keyword` names, as [#sideStyle] read it.
    private static Border.Line styled(Border.Line line, String keyword) {
        var style = Border.Style.parse(keyword);
        return style == null ? line.width(0) : line.style(style);
    }

    /// `corners` with the one `property` names set to `radius`.
    private static Corners corner(Corners corners, String property, Corners.Radius radius) {
        return switch (property) {
            case "border-top-left-radius" ->
                new Corners(radius, corners.topRightRadius(), corners.bottomRightRadius(), corners.bottomLeftRadius());
            case "border-top-right-radius" ->
                new Corners(corners.topLeftRadius(), radius, corners.bottomRightRadius(), corners.bottomLeftRadius());
            case "border-bottom-right-radius" ->
                new Corners(corners.topLeftRadius(), corners.topRightRadius(), radius, corners.bottomLeftRadius());
            default ->
                new Corners(corners.topLeftRadius(), corners.topRightRadius(), corners.bottomRightRadius(), radius);
        };
    }

    /// One corner's longhand: a radius for both axes, or a horizontal and then
    /// a vertical one, each a length, a percentage or a `calc()` of both.
    private static Optional<Corners.Radius> cornerRadius(List<Token> value, CssLength.Context context) {
        var parts = split(value);
        if (parts.isEmpty() || parts.size() > 2) {
            return Optional.empty();
        }
        var x = radiusLength(parts.getFirst(), context);
        var y = parts.size() == 1 ? x : radiusLength(parts.get(1), context);
        if (x == null || y == null) {
            return Optional.empty();
        }
        return Optional.of(radius(x, y));
    }

    /// A radius as a length: pixels, a percentage, or both; never negative,
    /// never `auto`.
    private static @Nullable Length radiusLength(List<Token> part, CssLength.Context context) {
        return switch (CssLength.parse(part, context)) {
            case Length.Points points when points.value() >= 0 -> points;
            case Length.Percent percent when percent.value() >= 0 -> percent;
            case Length.Mixed mixed when mixed.percent() >= 0 -> mixed;
            case null, default -> null;
        };
    }

    /// The corner whose horizontal radius is `x` and vertical `y`.
    private static Corners.Radius radius(Length x, Length y) {
        return new Corners.Radius(pixelsOf(x), pixelsOf(y), percentOf(x), percentOf(y));
    }

    private static double pixelsOf(Length length) {
        return switch (length) {
            case Length.Points points -> points.value();
            case Length.Mixed mixed -> mixed.points();
            case Length.Percent _, Length.Keyword _ -> 0;
        };
    }

    private static double percentOf(Length length) {
        return switch (length) {
            case Length.Percent percent -> percent.value();
            case Length.Mixed mixed -> mixed.percent();
            case Length.Points _, Length.Keyword _ -> 0;
        };
    }

    /// Which side a border longhand names: the second word of `border-top` and
    /// of `border-top-width`.
    private static Border.Side sideOf(String property) {
        return Border.Side.valueOf(property.split("-", 3)[1].toUpperCase(Locale.ROOT));
    }

    /// CSS's 1-4 value corner shorthand, in CSS's order, and after a `/` the
    /// same again for the vertical radii.
    ///
    /// The order is the one CSS names the corners in — top-left, top-right,
    /// bottom-right, bottom-left, clockwise from the top-left — and the fill-in
    /// rule is CSS's own: one value is every corner, two are the two diagonals,
    /// and three name the fourth as the opposite of the second. With no `/` the
    /// vertical radii are the horizontal ones, and every corner is circular.
    ///
    /// Each radius is a length, a percentage of the box's width (horizontal)
    /// or height (vertical), or a `calc()` of both, resolved when the box is
    /// painted.
    ///
    /// Empty if any part fails to parse, for [#insets]'s reason: a half-applied
    /// shorthand is harder to see than one that did nothing.
    static Optional<Corners> corners(List<Token> value, CssLength.Context context) {
        var slash = -1;
        var depth = 0;
        for (var i = 0; i < value.size(); i++) {
            var token = value.get(i);
            if (token.is(TokenType.OPEN_PAREN) || token.is(TokenType.FUNCTION)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && token.isDelim('/')) {
                if (slash >= 0) {
                    return Optional.empty();
                }
                slash = i;
            }
        }
        var horizontal = cornerLengths(slash < 0 ? value : value.subList(0, slash), context);
        var vertical = slash < 0 ? horizontal : cornerLengths(value.subList(slash + 1, value.size()), context);
        if (horizontal == null || vertical == null) {
            return Optional.empty();
        }
        return Optional.of(new Corners(
                radius(horizontal[0], vertical[0]),
                radius(horizontal[1], vertical[1]),
                radius(horizontal[2], vertical[2]),
                radius(horizontal[3], vertical[3])));
    }

    /// One to four radii, filled in to four by CSS's rule, or null.
    private static Length @Nullable [] cornerLengths(List<Token> value, CssLength.Context context) {
        var parts = new ArrayList<Length>();
        for (var token : split(value)) {
            var radius = radiusLength(token, context);
            if (radius == null) {
                return null;
            }
            parts.add(radius);
        }
        return switch (parts.size()) {
            case 1 -> new Length[] {parts.getFirst(), parts.getFirst(), parts.getFirst(), parts.getFirst()};
            case 2 -> new Length[] {parts.get(0), parts.get(1), parts.get(0), parts.get(1)};
            case 3 -> new Length[] {parts.get(0), parts.get(1), parts.get(2), parts.get(1)};
            case 4 -> new Length[] {parts.get(0), parts.get(1), parts.get(2), parts.get(3)};
            default -> null;
        };
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
    /// its own surface" in a subset with no colour functions — and `card`'s edge,
    /// which is the whole of how a raised thing is told apart, depends on it.
    private static List<List<Token>> split(List<Token> value) {
        var parts = new ArrayList<List<Token>>();
        var current = new ArrayList<Token>();
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

    /// `letter-spacing` or `word-spacing`: `normal`, or a length in pixels,
    /// which may be negative. A percentage is not a spacing here: CSS reads
    /// one for `word-spacing` as a share of a space's advance, which only the
    /// shaper knows.
    private static Optional<Double> spacing(List<Token> value, CssLength.Context context) {
        if (isKeyword(value, "normal")) {
            return Optional.of(0.0);
        }
        return Optional.ofNullable(CssLength.parse(value, context))
                .filter(Length.Points.class::isInstance)
                .map(v -> (double) ((Length.Points) v).value());
    }

    /// `line-clamp`: `none`, which is 0, or a whole number of lines from 1.
    private static Optional<Integer> lineClampCount(List<Token> value) {
        if (isKeyword(value, "none")) {
            return Optional.of(0);
        }
        return Optional.ofNullable(CssLength.parseNumber(value))
                .filter(n -> n >= 1 && n == Math.rint(n) && n <= Integer.MAX_VALUE)
                .map(Double::intValue);
    }

    /// `z-index`: `auto`, which is 0, or a whole number, negative allowed.
    private static Optional<Integer> zIndex(List<Token> value) {
        if (isKeyword(value, "auto")) {
            return Optional.of(0);
        }
        return Optional.ofNullable(CssLength.parseNumber(value))
                .filter(n -> n == Math.rint(n) && Math.abs(n) <= Integer.MAX_VALUE)
                .filter(n -> value.stream().noneMatch(t -> t.is(TokenType.PERCENTAGE)))
                .map(Double::intValue);
    }

    /// `container-name`: `none`, or one or more names.
    private static Optional<List<String>> containerNames(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.isEmpty() || !tokens.stream().allMatch(t -> t.is(TokenType.IDENT))) {
            return Optional.empty();
        }
        if (tokens.size() == 1 && tokens.getFirst().isIdent("none")) {
            return Optional.of(List.of());
        }
        var names = new ArrayList<String>();
        for (var token : tokens) {
            var name = token.text();
            switch (name.toLowerCase(Locale.ROOT)) {
                case "none", "and", "or", "not", "initial", "inherit", "unset", "default" -> {
                    return Optional.empty();
                }
                default -> names.add(name);
            }
        }
        return Optional.of(List.copyOf(names));
    }

    /// `container: <name> [/ <type>]`, which resets the type to `normal` when
    /// none is written.
    private Optional<ComputedStyle> containerShorthand(List<Token> value) {
        var slash = -1;
        for (var i = 0; i < value.size(); i++) {
            if (value.get(i).isDelim('/')) {
                slash = i;
                break;
            }
        }
        var names = containerNames(slash < 0 ? value : value.subList(0, slash));
        var type = slash < 0
                ? Optional.of(ContainerType.NORMAL)
                : singleIdent(value.subList(slash + 1, value.size())).map(ContainerType::parse);
        if (names.isEmpty() || type.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(presence(presence.containment(new Containment(type.get(), names.get()))));
    }

    /// `order`: a whole number, negative allowed.
    private static Optional<Integer> order(List<Token> value) {
        return Optional.ofNullable(CssLength.parseNumber(value))
                .filter(n -> n == Math.rint(n) && Math.abs(n) <= Integer.MAX_VALUE)
                .filter(n -> value.stream().noneMatch(t -> t.is(TokenType.PERCENTAGE)))
                .map(Double::intValue);
    }

    /// The one identifier `value` is, or empty.
    private static Optional<String> singleIdent(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        return tokens.size() == 1 && tokens.getFirst().is(TokenType.IDENT)
                ? Optional.of(tokens.getFirst().text())
                : Optional.empty();
    }

    /// A math function that folds to a plain number, or null.
    private static @Nullable Double mathNumber(List<Token> part) {
        if (!Maths.isMath(part)) {
            return null;
        }
        var folded = Maths.fold(part, CssLength.Context.DEFAULT);
        return folded != null && folded.kind() == Quantity.Kind.NUMBER ? folded.value() : null;
    }

    private static Optional<Double> number(List<Token> value) {
        return Optional.ofNullable(CssLength.parseNumber(value));
    }

    /// A CSS keyword mapped onto a Yoga enum by name: `space-between` onto
    /// `SPACE_BETWEEN`, `flex-start` onto `FLEX_START`.
    ///
    /// By name rather than by a hand-written table because the two vocabularies
    /// already agree — Yoga's enums are the CSS names — and a table would be a
    /// second place for them to drift apart. The aliases are CSS's own spellings
    /// for the two alignment keywords Yoga names differently.
    ///
    /// `align-items: start` is **valid CSS** — Box Alignment Level 3 — and Yoga
    /// has only `flex-start`, so without the alias a document that wrote what the
    /// specification allows would have its declaration dropped with a warning:
    /// the toolkit refusing valid CSS rather than the author making a typo.
    ///
    /// Two entries and no more. `start` and `end` are writing-mode-relative in
    /// full CSS and identical to the flex pair in a subset with one writing mode
    /// and no grid, which is what makes the mapping exact rather than
    /// approximate. `left` and `right` are deliberately absent: they are
    /// `justify-content` only, and they are *not* the same as `start`/`end`
    /// under `direction: rtl`, so the toolkit cannot promise they would stay
    /// equivalent.
    private static final java.util.Map<String, String> KEYWORD_ALIASES = java.util.Map.of(
            "START", "FLEX_START",
            "END", "FLEX_END");

    private static <E extends Enum<E>> Optional<E> keyword(List<Token> value, Class<E> type) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() != 1 || !tokens.getFirst().is(TokenType.IDENT)) {
            return Optional.empty();
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
        return alias == null ? Optional.empty() : constant(type, alias);
    }

    private static <E extends Enum<E>> Optional<E> constant(Class<E> type, String name) {
        try {
            return Optional.of(Enum.valueOf(type, name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /// CSS's `text-decoration-line`: `none`, or one or both of the rules.
    ///
    /// Empty — a dropped declaration — for anything else, which deliberately
    /// includes the rest of the shorthand: `underline red` names a colour this
    /// toolkit cannot draw, and dropping it with a warning is how every other
    /// property here reports a value outside the subset.
    ///
    /// `none` beside another keyword is also refused rather than resolved. CSS says
    /// the same, and the alternative is guessing which half of a contradiction the
    /// author meant.
    private static Optional<Set<TextDecoration>> decorations(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.isEmpty() || tokens.stream().anyMatch(t -> !t.is(TokenType.IDENT))) {
            return Optional.empty();
        }
        if (tokens.size() == 1 && tokens.getFirst().text().equalsIgnoreCase("none")) {
            return Optional.of(TextDecoration.NONE);
        }
        var lines = java.util.EnumSet.noneOf(TextDecoration.class);
        for (var token : tokens) {
            var line = TextDecoration.parse(token.text());
            if (line == null || !lines.add(line)) {
                // Unknown, or named twice -- both are a declaration nobody can read
                // back, and neither is worth half-applying.
                return Optional.empty();
            }
        }
        return Optional.of(Set.copyOf(lines));
    }

    /// CSS's `flex-wrap`, whose default keyword is spelled without the hyphen
    /// [#keyword] would need.
    ///
    /// `nowrap` is one word in CSS and `NO_WRAP` is two in the
    /// enum, so the generic keyword parser turns `nowrap` into `NOWRAP` and
    /// finds nothing. The other two spellings agree, so this special-cases one
    /// word and defers.
    private static Optional<Wrap> wrap(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() == 1
                && tokens.getFirst().is(TokenType.IDENT)
                && tokens.getFirst().text().equalsIgnoreCase("nowrap")) {
            return Optional.of(Wrap.NO_WRAP);
        }
        return keyword(value, Wrap.class);
    }

    /// CSS's `overflow`, which has one more keyword than Yoga does.
    ///
    /// `auto` is not a `YGOverflow`, and it sizes exactly as `scroll` does — the
    /// difference in CSS is whether the bars appear only when they are needed.
    /// The design system routes that question elsewhere: overlay auto-hiding bars
    /// are the default for *both*, and the always-visible gutter is an
    /// application setting rather than a keyword. So `auto` maps onto
    /// [Overflow#SCROLL] and nothing downstream has to carry a distinction no
    /// rule can act on.
    ///
    /// Read more: [Scrollbars](https://goldberry.dev/docs/guide/styling.html#themes-density-and-scrollbars).
    /// `overflow`: one value for both axes, or the horizontal then the vertical.
    private Optional<ComputedStyle> overflows(List<Token> value) {
        var words = split(value);
        if (words.isEmpty() || words.size() > 2) {
            return Optional.empty();
        }
        var x = overflow(words.getFirst());
        var y = overflow(words.getLast());
        if (x.isEmpty() || y.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(overflowX(x.get()).overflowY(y.get()));
    }

    /// A style equal to this one and not this one: what an arm answers for a
    /// declaration it read and that changes nothing, since a dropped one is
    /// told apart by being answered with this very style.
    private ComputedStyle read() {
        return justifyContent(justifyContent);
    }

    /// `justify-content`, with `stretch` read as the start.
    private static Optional<Justify> justify(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() == 1 && tokens.getFirst().isIdent("stretch")) {
            return Optional.of(Justify.FLEX_START);
        }
        return keyword(value, Justify.class);
    }

    /// The positions `safe` and `unsafe` may come before.
    private static final Set<String> POSITIONS =
            Set.of("center", "start", "end", "self-start", "self-end", "flex-start", "flex-end", "left", "right");

    /// Whether `value` is one CSS reads for `justify-items`, or with `auto`
    /// for `justify-self`.
    private static boolean selfAlignment(List<Token> value, boolean self) {
        var words = value.stream()
                .filter(t -> !t.is(TokenType.WHITESPACE))
                .map(t -> t.is(TokenType.IDENT) ? t.text().toLowerCase(Locale.ROOT) : "")
                .toList();
        return switch (words.size()) {
            case 1 ->
                switch (words.getFirst()) {
                    case "auto" -> self;
                    case "legacy" -> !self;
                    case "normal",
                            "stretch",
                            "baseline",
                            "center",
                            "start",
                            "end",
                            "self-start",
                            "self-end",
                            "flex-start",
                            "flex-end",
                            "left",
                            "right" -> true;
                    default -> false;
                };
            case 2 -> {
                var first = words.getFirst();
                var second = words.getLast();
                if ((first.equals("first") || first.equals("last")) && second.equals("baseline")) {
                    yield true;
                }
                if (first.equals("safe") || first.equals("unsafe")) {
                    yield POSITIONS.contains(second);
                }
                // `legacy left` and `left legacy`, for `justify-items` only.
                yield !self
                        && words.contains("legacy")
                        && (words.contains("left") || words.contains("right") || words.contains("center"));
            }
            default -> false;
        };
    }

    /// `place-content`, `place-items` or `place-self`: the alignment, then the
    /// justification, one value being both.
    private ComputedStyle place(String property, List<Token> value) {
        var words = split(value);
        if (words.isEmpty() || words.size() > 2) {
            return dropped(property, value);
        }
        var axis = property.substring("place-".length());
        var align = words.getFirst();
        var justify = words.getLast();
        var aligned = switch (axis) {
            case "content" -> keyword(align, Align.class).map(this::alignContent);
            case "items" -> keyword(align, Align.class).map(this::alignItems);
            default -> keyword(align, Align.class).map(this::alignSelf);
        };
        if (aligned.isEmpty()) {
            return dropped(property, value);
        }
        if (axis.equals("content")) {
            return justify(justify).map(v -> aligned.get().justifyContent(v)).orElseGet(() -> dropped(property, value));
        }
        return selfAlignment(justify, axis.equals("self")) ? aligned.get() : dropped(property, value);
    }

    /// A logical property, applied as the physical ones it is here.
    private ComputedStyle logical(String property, List<Token> value, CssLength.Context context) {
        var physical = LogicalProperties.physical(property, value, presence.direction());
        if (physical == null) {
            return dropped(property, value);
        }
        var style = this;
        for (var declaration : physical) {
            style = style.with(declaration.property(), declaration.value(), context);
        }
        return style;
    }

    private static Optional<Overflow> overflow(List<Token> value) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        if (tokens.size() == 1
                && tokens.getFirst().is(TokenType.IDENT)
                && tokens.getFirst().text().equalsIgnoreCase("auto")) {
            return Optional.of(Overflow.SCROLL);
        }
        return keyword(value, Overflow.class);
    }

    /// `aspect-ratio`: `auto` (NaN), a number, or `width / height`, both above
    /// zero. CSS's `auto 16 / 9`, a fallback ratio for a box with content of
    /// its own, is read as the ratio alone.
    private static Optional<Double> aspectRatio(List<Token> value) {
        var tokens = new ArrayList<>(
                value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList());
        if (tokens.size() == 1 && tokens.getFirst().isIdent("auto")) {
            return Optional.of(Double.NaN);
        }
        tokens.removeIf(t -> t.isIdent("auto"));
        if (tokens.size() == 1
                && tokens.getFirst().is(TokenType.NUMBER)
                && tokens.getFirst().numeric() > 0) {
            return Optional.of(tokens.getFirst().numeric());
        }
        if (tokens.size() == 3
                && tokens.get(0).is(TokenType.NUMBER)
                && tokens.get(1).isDelim('/')
                && tokens.get(2).is(TokenType.NUMBER)
                && tokens.get(0).numeric() > 0
                && tokens.get(2).numeric() > 0) {
            return Optional.of(tokens.get(0).numeric() / tokens.get(2).numeric());
        }
        return Optional.empty();
    }

    /// Whether `value` is the one keyword `word`, in any case.
    private static boolean isKeyword(List<Token> value, String word) {
        var tokens = value.stream().filter(t -> !t.is(TokenType.WHITESPACE)).toList();
        return tokens.size() == 1
                && tokens.getFirst().is(TokenType.IDENT)
                && tokens.getFirst().text().equalsIgnoreCase(word);
    }

    private static String text(List<Token> value) {
        var out = new StringBuilder();
        value.forEach(t -> out.append(t.cssText()));
        return out.toString();
    }
}
