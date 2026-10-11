package dev.goldberry.css.cascade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.css.Declaration;
import dev.goldberry.css.Keyframes;
import dev.goldberry.css.StyleElement;
import dev.goldberry.css.StyleRule;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.media.MediaContext;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.property.GlobalKeyword;
import dev.goldberry.css.property.Properties;
import dev.goldberry.css.select.Functional;
import dev.goldberry.css.select.PseudoElement;
import dev.goldberry.css.select.Selector;
import dev.goldberry.css.select.SelectorMatcher;
import dev.goldberry.css.select.SiblingPosition;
import dev.goldberry.css.select.Structural;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.css.value.Viewport;
import dev.goldberry.log.Logs;

/// Runs the cascade for one element and substitutes `var()`.
///
/// ```java
/// var resolver = new StyleResolver(stylesheets);
/// Map<String, List<Token>> declared = resolver.resolve(element);
/// ```
///
/// Built once per stylesheet set and kept by the renderer for as long as that set
/// is in force; a theme swap builds a new one. The output is still tokens, not
/// typed values: what `4px 8px` means depends on the property, and turning it
/// into a `ComputedStyle` is the next step. What is settled here is which
/// declaration wins, by `!important`, then specificity, then [CascadeLayer], then
/// source order, and what its `var()`s stand for. Custom properties inherit here,
/// because that is the whole theming mechanism; ordinary inheritance belongs to
/// `ComputedStyle`.
///
/// Rules are bucketed by the type their rightmost compound names, so a rule for
/// `button` is never tested against a `text`, and a node's custom properties are
/// cached against the map its parent handed down, so a tree resolves each level
/// once. A `var()` that resolves to nothing drops its declaration, and is
/// reported once per property and element type rather than once per frame.
///
/// A rule inside `@media` applies only while its condition holds under this
/// resolver's [MediaContext]. A resolver is immutable, so [#under] answers a
/// new context with a new resolver sharing every index with this one, and only
/// when some condition in the sheets answers differently: every style cached
/// against the old resolver then misses at once, exactly as a theme swap does,
/// and a resize that crosses no breakpoint keeps every cache.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#the-cascade-four-layers).
public final class StyleResolver {

    private static final Logger LOG = Logs.of(StyleResolver.class);

    /// What has already been reported dropped, so one bad `var()` is a message
    /// rather than a stream.
    ///
    /// A stylesheet is static, so a declaration that cannot be resolved cannot
    /// resolve on the next frame either; but a style is resolved per element per
    /// invalidation, so without this one missing token would report itself sixty
    /// times a second for as long as the screen it was on kept moving. That is
    /// not a louder warning, it is a quieter log.
    ///
    /// **An instance field, where `ComputedStyle`'s is static**, and that is the
    /// difference worth having. A resolver is built per stylesheet set and lives
    /// as long as the renderer holding it, so "once per resolver" *is* "once per
    /// stylesheet" — a theme swap builds a new one and legitimately reports what
    /// the new theme is missing, and a test gets its isolation from constructing
    /// its own rather than from a static `forget` hook.
    ///
    /// Keyed by property and element type: the same property dropped on
    /// `button` and on `text` is two facts, and which types an unresolvable token
    /// reaches is the blast radius somebody debugging it wants. Bounded either
    /// way, because a stylesheet has finitely many declarations and a tree
    /// finitely many types.
    private final java.util.Set<String> reported = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// How many distinct drops are remembered before deduplication gives up and
    /// lets them all through.
    ///
    /// `ComputedStyle.REPORT_LIMIT`'s number and its argument: letting them
    /// through rather than falling silent, because past this many distinct
    /// unresolvable declarations something is generating them and a log that went
    /// quiet would hide it.
    private static final int REPORT_LIMIT = 512;

    /// Custom properties inherit; ordinary ones do not, at this layer.
    ///
    /// Inheritance of ordinary properties (`color`, `font-size`) is a property
    /// table that does not exist yet and belongs with `ComputedStyle`. Custom
    /// properties have to inherit here because that is the whole theming
    /// mechanism: `:root { --gb-accent }` is useless if a button cannot see it.
    private final List<Stylesheet> stylesheets;

    /// For each pseudo-class, the element **types** that carry it in an ancestor
    /// position of some selector — `checkbox` for `:hover`, from
    /// `checkbox:hover check-indicator`.
    ///
    /// What it is for: a node whose state changed has to invalidate its
    /// descendants only if some rule can reach them *through* that state. See
    /// [#reachesDescendants].
    private final java.util.Map<Selector.PseudoClass, java.util.Set<String>> ancestorStates =
            new java.util.EnumMap<>(Selector.PseudoClass.class);

    /// The pseudo-classes used in an ancestor position by a compound that names
    /// **no type** — `.section:affixed > affix-content`.
    ///
    /// Those cannot be narrowed by type, so any node changing one of them stays
    /// conservative and invalidates its whole subtree.
    private final java.util.Set<Selector.PseudoClass> untypedAncestorStates =
            java.util.EnumSet.noneOf(Selector.PseudoClass.class);

    /// One rule, with the layer and the sheet it came from: what a bucket holds.
    ///
    /// Both travel with the rule because the buckets flatten the sheets away, and
    /// the cascade needs them back. [StyleRule#order] is a rule's position within
    /// its own stylesheet, so two sheets in one layer need the sheet's index as
    /// well: without it an earlier sheet's rule 12 would beat a later sheet's
    /// rule 0 at equal specificity, and `controls.css`, `MarkdownStyles` and
    /// `HtmlStyles` all sit in [CascadeLayer#TOOLKIT_BASE]. The index the
    /// resolver was handed the sheets in is what source order means when there is
    /// more than one source.
    ///
    /// @param sheet the sheet's position in the list this resolver was built from
    private record Candidate(StyleRule rule, CascadeLayer layer, int sheet) {}

    /// Rules whose **rightmost** compound names a type, bucketed by that type.
    ///
    /// A selector's rightmost compound is the one that has to match the element
    /// being styled, so a rule for `button` cannot possibly apply to a `text`.
    /// Testing it anyway would cost over a millisecond per style resolve in the
    /// showcase: four stylesheets, some two thousand rules, matched in full
    /// against every element, and then again against every one of its ancestors,
    /// because custom properties are collected by walking to the root.
    private final java.util.Map<String, List<Candidate>> byType = new java.util.HashMap<>();

    /// Rules whose rightmost compound names no type — `.primary`, `#gain`, `*`.
    ///
    /// These have to be tested against everything, and there are few of them: the
    /// toolkit's own sheets are written type-first, which is what makes the
    /// bucketing worth having.
    private final List<Candidate> untyped = new java.util.ArrayList<>();

    /// `@starting-style` rules, bucketed the same way and kept **out** of the two
    /// maps above: a starting rule is never part of the style an element has,
    /// only of the one it transitions from on its first frame.
    private final java.util.Map<String, List<Candidate>> startingByType = new java.util.HashMap<>();

    private final List<Candidate> startingUntyped = new java.util.ArrayList<>();

    /// Every `@keyframes` block by name, in the order declared. The last one
    /// whose `@media` holds replaces every earlier one whole, which is CSS's
    /// rule. Keyframes do not merge.
    private final java.util.Map<String, List<Keyframes>> keyframes = new java.util.HashMap<>();

    /// The window and desktop facts the `@media` conditions are asked about.
    private final MediaContext media;

    /// Every distinct `@media` condition in the sheets, in the order first met,
    /// for [#under] to ask all of them about a new context.
    private final List<MediaCondition> conditions;

    /// Every distinct structural pseudo-class in the sheets — `:first-child`,
    /// `:nth-child(2n)` — for [#positionMatters].
    private final List<Structural> structural;

    /// The types a structural pseudo-class sits on in an **ancestor** position
    /// — `list-row` for `list-row:first-child text` — for
    /// [#positionReachesDescendants].
    private final Set<String> structuralAncestorTypes;

    /// Whether a structural pseudo-class sits on an untyped compound in an
    /// ancestor position, which reaches below every element.
    private final boolean untypedStructuralAncestor;

    /// Whether any declaration here writes `inherit` on a property that does
    /// not inherit. See [#inheritsExplicitly()].
    private final boolean inheritsExplicitly;

    /// Whether any declaration or keyframe here writes a viewport unit, which
    /// makes a resize a restyle. See [#under].
    private final boolean viewportUnits;

    /// Whether any rule here is inside `@container` or writes a container
    /// unit. See [#readsContainers()].
    private final boolean containerQueries;

    /// Whether any selector reads an element's siblings: counts them through a
    /// filter, `:nth-child(An+B of S)`, or joins two with `+` or `~`. See
    /// [#siblingsMatter()].
    private final boolean siblingsMatter;

    /// Where the sheets' `:has()` sits, or null when no selector writes one.
    /// See [#hasReach].
    private final @Nullable HasIndex hasIndex;

    /// The compounds that carry a `:has()`.
    ///
    /// @param types              the types they name
    /// @param untyped            whether one names no type
    /// @param ancestorTypes      the types of those in an ancestor position,
    ///                           `card:has(.x) text`, whose subtree reads them
    /// @param untypedAncestor    whether one in an ancestor position names no
    ///                           type
    /// @param looksAtSiblings    whether an argument starts at a sibling,
    ///                           `:has(+ x)`
    private record HasIndex(
            Set<String> types,
            boolean untyped,
            Set<String> ancestorTypes,
            boolean untypedAncestor,
            boolean looksAtSiblings) {}

    /// The rank of each `@layer` in the application's sheets, lowest first;
    /// a rule in none ranks above them all. See [#CASCADE].
    private final Map<String, Integer> sublayers;

    public StyleResolver(List<Stylesheet> stylesheets) {
        this.stylesheets = List.copyOf(Objects.requireNonNull(stylesheets, "stylesheets"));
        this.media = MediaContext.UNKNOWN;
        indexAncestorStates();
        indexByType();
        for (var sheet : this.stylesheets) {
            for (var block : sheet.keyframes()) {
                keyframes
                        .computeIfAbsent(block.name(), _ -> new java.util.ArrayList<>())
                        .add(block);
            }
        }
        var distinct = new java.util.LinkedHashSet<MediaCondition>();
        var positions = new java.util.LinkedHashSet<Structural>();
        var ancestorTypes = new HashSet<String>();
        // Arrays, because the walk is a lambda and these are what it reports.
        var filtered = new boolean[1];
        var untypedAncestorPosition = new boolean[1];
        var hasTypes = new HashSet<String>();
        var hasAncestorTypes = new HashSet<String>();
        // Untyped subject, untyped in an ancestor position, starts at a sibling.
        var hasFlags = new boolean[3];
        var anyHas = new boolean[1];
        for (var sheet : this.stylesheets) {
            for (var block : sheet.keyframes()) {
                if (block.isConditional()) {
                    distinct.add(block.media());
                }
            }
            for (var rule : sheet.rules()) {
                if (rule.isConditional()) {
                    distinct.add(rule.media());
                }
                for (var selector : rule.selectors()) {
                    eachCompound(selector, false, (part, ancestor) -> {
                        var compound = part.compound();
                        positions.addAll(compound.structural());
                        var counts = !compound.structural().isEmpty();
                        if (part.combinator().isSibling()) {
                            // `a + b`: b's match reads its siblings.
                            filtered[0] = true;
                            counts = true;
                        }
                        for (var functional : compound.functional()) {
                            switch (functional) {
                                case Functional.NthOf _ -> {
                                    filtered[0] = true;
                                    counts = true;
                                }
                                case Functional.Has has -> {
                                    anyHas[0] = true;
                                    hasFlags[2] |= has.looksAtSiblings();
                                    if (compound.type() == null) {
                                        hasFlags[0] = true;
                                        hasFlags[1] |= ancestor;
                                    } else {
                                        hasTypes.add(compound.type());
                                        if (ancestor) {
                                            hasAncestorTypes.add(compound.type());
                                        }
                                    }
                                }
                                case Functional.Is _, Functional.Where _, Functional.Not _ -> {}
                            }
                        }
                        if (ancestor && counts) {
                            if (compound.type() == null) {
                                untypedAncestorPosition[0] = true;
                            } else {
                                ancestorTypes.add(compound.type());
                            }
                        }
                    });
                }
            }
        }
        this.conditions = List.copyOf(distinct);
        this.structural = List.copyOf(positions);
        this.structuralAncestorTypes = Set.copyOf(ancestorTypes);
        this.untypedStructuralAncestor = untypedAncestorPosition[0];
        this.siblingsMatter = filtered[0];
        this.hasIndex = anyHas[0]
                ? new HasIndex(
                        Set.copyOf(hasTypes), hasFlags[0], Set.copyOf(hasAncestorTypes), hasFlags[1], hasFlags[2])
                : null;
        this.inheritsExplicitly = this.stylesheets.stream()
                .flatMap(sheet -> sheet.rules().stream())
                .flatMap(rule -> rule.declarations().stream())
                .anyMatch(StyleResolver::readsParentBeyondInheritance);
        this.viewportUnits = this.stylesheets.stream().anyMatch(StyleResolver::writesViewportUnits);
        this.containerQueries = this.stylesheets.stream().anyMatch(StyleResolver::readsContainers);
        var names = new ArrayList<String>();
        for (var sheet : this.stylesheets) {
            if (sheet.layer() == CascadeLayer.APPLICATION) {
                names.addAll(sheet.layers());
            }
        }
        this.sublayers = ranks(names);
    }

    /// Ranks for `names`, in the order first met: a layer's own sublayers rank
    /// below its rules that are in none of them, as in CSS, so `a.b` is below
    /// `a`, and `a` and everything in it below a layer first met after `a`.
    static Map<String, Integer> ranks(List<String> names) {
        var root = new Level();
        for (var name : names) {
            var level = root;
            for (var segment : name.split("\\.", -1)) {
                level = level.children.computeIfAbsent(segment, _ -> new Level());
            }
        }
        var ranks = new HashMap<String, Integer>();
        root.rank("", ranks);
        return Map.copyOf(ranks);
    }

    /// One level of the layer tree, its children in the order first met.
    private static final class Level {

        private final Map<String, Level> children = new LinkedHashMap<>();

        /// Ranks every layer under this one, each after its own sublayers.
        void rank(String prefix, Map<String, Integer> ranks) {
            for (var entry : children.entrySet()) {
                var full = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                entry.getValue().rank(full, ranks);
                ranks.put(full, ranks.size());
            }
        }
    }

    /// Where a rule in `layer` of the cascade ranks among the application's
    /// `@layer`s: [#UNLAYERED] outside the application layer and for a rule in
    /// none.
    private int sublayerOf(CascadeLayer layer, StyleRule rule) {
        if (layer != CascadeLayer.APPLICATION || rule.layer() == null) {
            return UNLAYERED;
        }
        return sublayers.getOrDefault(rule.layer(), UNLAYERED);
    }

    /// The rank of a rule in no `@layer`, above every layer.
    private static final int UNLAYERED = Integer.MAX_VALUE;

    /// Whether a declaration anywhere in `sheet`, a keyframe's included, holds
    /// a viewport unit. Custom properties count: `--gutter: 2vw` is read
    /// wherever `var(--gutter)` is.
    /// Whether `sheet` has an `@container` rule or writes a container unit,
    /// which make a container's laid-out size something a style reads.
    private static boolean readsContainers(Stylesheet sheet) {
        if (sheet.rules().stream().anyMatch(StyleRule::isContained)) {
            return true;
        }
        return sheet.rules().stream()
                .flatMap(rule -> rule.declarations().stream())
                .flatMap(declaration -> declaration.value().stream())
                .anyMatch(token -> token.is(TokenType.DIMENSION)
                        && CssLength.isContainerUnit(token.unit().toLowerCase(Locale.ROOT)));
    }

    private static boolean writesViewportUnits(Stylesheet sheet) {
        var declarations = Stream.concat(
                sheet.rules().stream().flatMap(rule -> rule.declarations().stream()),
                sheet.keyframes().stream()
                        .flatMap(block -> block.frames().stream())
                        .flatMap(frame -> frame.declarations().stream()));
        return declarations
                .flatMap(declaration -> declaration.value().stream())
                .anyMatch(token -> token.is(TokenType.DIMENSION)
                        && Viewport.isUnit(token.unit().toLowerCase(Locale.ROOT)));
    }

    /// `property: inherit`, where `property` has a longhand that does not
    /// inherit on its own.
    private static boolean readsParentBeyondInheritance(Declaration declaration) {
        if (GlobalKeyword.of(declaration.value()) != GlobalKeyword.INHERIT) {
            return false;
        }
        if (Properties.find(declaration.property()).isEmpty()) {
            return false;
        }
        return Properties.expand(declaration.property()).stream().anyMatch(longhand -> !longhand.inherited());
    }

    /// `base` under another media context: every index shared, one field new.
    private StyleResolver(StyleResolver base, MediaContext media) {
        this.stylesheets = base.stylesheets;
        this.media = media;
        this.reported.addAll(base.reported);
        this.ancestorStates.putAll(base.ancestorStates);
        this.untypedAncestorStates.addAll(base.untypedAncestorStates);
        this.byType.putAll(base.byType);
        this.untyped.addAll(base.untyped);
        this.startingByType.putAll(base.startingByType);
        this.startingUntyped.addAll(base.startingUntyped);
        this.keyframes.putAll(base.keyframes);
        this.conditions = base.conditions;
        this.structural = base.structural;
        this.structuralAncestorTypes = base.structuralAncestorTypes;
        this.untypedStructuralAncestor = base.untypedStructuralAncestor;
        this.inheritsExplicitly = base.inheritsExplicitly;
        this.viewportUnits = base.viewportUnits;
        this.containerQueries = base.containerQueries;
        this.siblingsMatter = base.siblingsMatter;
        this.hasIndex = base.hasIndex;
        this.sublayers = base.sublayers;
    }

    /// Calls `visit` with every part in `selector` and in the selectors its
    /// functional pseudo-classes hold, at any depth, and whether it stands in
    /// an **ancestor** position: left of a combinator, or inside an argument of
    /// a compound that does.
    ///
    /// `:is(a:hover b) c` puts `a:hover` and `b` in ancestor positions, and
    /// `x:not(a:hover b)` puts `a:hover` in one and `b` in the subject's.
    private static void eachCompound(Selector selector, boolean ancestor, BiConsumer<Selector.Part, Boolean> visit) {
        var parts = selector.parts();
        for (var i = 0; i < parts.size(); i++) {
            var compound = parts.get(i).compound();
            var here = ancestor || i > 0;
            visit.accept(parts.get(i), here);
            for (var functional : compound.functional()) {
                for (var argument : functional.arguments()) {
                    eachCompound(argument, here, visit);
                }
            }
        }
    }

    /// This resolver, as it would cascade under `context`.
    ///
    /// **This very instance** when no `@media` condition in the sheets answers
    /// differently under `context` than under the context this one has — which
    /// is every call for sheets with no `@media` at all, and every frame of a
    /// resize that crosses no breakpoint. Identity is what the element tree's
    /// style cache is keyed on, so returning `this` is what keeps it warm.
    ///
    /// Otherwise a new resolver sharing every index with this one. Every cached
    /// style misses against it at once, which is the whole invalidation: the
    /// same route a theme swap takes.
    ///
    /// A sheet that writes a viewport unit (`50vw`, `100dvh`) gets a new
    /// resolver for every change of the window's size, since every such length
    /// has moved. One that writes none keeps this one through a resize.
    public StyleResolver under(MediaContext context) {
        Objects.requireNonNull(context, "context");
        if (context.equals(media)) {
            return this;
        }
        if (viewportUnits
                && (Double.compare(context.width(), media.width()) != 0
                        || Double.compare(context.height(), media.height()) != 0)) {
            return new StyleResolver(this, context);
        }
        for (var condition : conditions) {
            if (condition.matches(context) != condition.matches(media)) {
                return new StyleResolver(this, context);
            }
        }
        return this;
    }

    /// The context this resolver's `@media` conditions are answered under.
    public MediaContext media() {
        return media;
    }

    /// Whether moving an element from `oldIndex` of `oldCount` siblings to
    /// `index` of `count` changes what any structural pseudo-class in the sheets
    /// says about it.
    ///
    /// The question the element tree asks when it reconciles a parent's
    /// children: a list that grew by one at the end moves nobody's index, and
    /// under sheets that only say `:first-child` it invalidates nothing. With no
    /// structural pseudo-class anywhere, which is the toolkit's own sheets, the
    /// answer is always no and costs one empty loop.
    public boolean positionMatters(SiblingPosition before, SiblingPosition after) {
        if (siblingsMatter) {
            return true;
        }
        for (var position : structural) {
            if (position.matches(before) != position.matches(after)) {
                return true;
            }
        }
        return false;
    }

    /// Whether some selector reads an element's siblings: counts them through
    /// a filter, `:nth-child(An+B of S)`, or joins two with `+` or `~`, so
    /// that a sibling's classes or state decide what matches an element.
    ///
    /// When true, a parent that rebuilds its children restyles every one of
    /// them, and a child whose state changes restyles its siblings: which of
    /// them match may have changed, and nothing cheaper says which. With no
    /// such selector, which is the toolkit's own sheets, it costs nothing.
    public boolean siblingsMatter() {
        return siblingsMatter;
    }

    /// Whether any rule is inside `@container` or writes a container unit
    /// (`cqw` and the rest), so that a container's laid-out size decides a
    /// style. With none, which is the toolkit's own sheets, a frame is laid out
    /// once; with some, the renderer restyles under a container whose size
    /// changed and lays the frame out once more.
    public boolean readsContainers() {
        return containerQueries;
    }

    /// Whether any selector writes `:has()`, so that a change to an element
    /// can change what matches above it. With none, which is the toolkit's own
    /// sheets, the element tree never walks up.
    public boolean hasMatters() {
        return hasIndex != null;
    }

    /// Whether some `:has()` starts at a sibling, `:has(+ x)` or `:has(~ x)`,
    /// so that an element's earlier siblings read it too.
    public boolean hasLooksAtSiblings() {
        return hasIndex != null && hasIndex.looksAtSiblings();
    }

    /// What a change below or after an element of `type` restyles on it,
    /// through the sheets' `:has()`.
    ///
    /// [HasReach#NONE] when no `:has()` sits on a compound that could match
    /// it, which narrows the walk the element tree makes up from a change to
    /// the types the sheets name: `card:has(:checked)` restyles the `card`s
    /// above a checkbox, and nothing else on the way to the root.
    /// [HasReach#SUBTREE] when such a compound stands left of a combinator,
    /// `card:has(.x) text`, since what is below it then reads it too.
    public HasReach hasReach(@Nullable String type) {
        var index = hasIndex;
        if (index == null) {
            return HasReach.NONE;
        }
        if (index.untypedAncestor() || (type != null && index.ancestorTypes().contains(type))) {
            return HasReach.SUBTREE;
        }
        if (index.untyped() || (type != null && index.types().contains(type))) {
            return HasReach.OWN;
        }
        return HasReach.NONE;
    }

    /// Whether a change in position on an element of `type` can change what
    /// matches **below** it — `list-row:first-child text` says yes for a
    /// `list-row`. [#reachesDescendants]'s question for structure.
    public boolean positionReachesDescendants(@Nullable String type) {
        if (untypedStructuralAncestor) {
            return true;
        }
        return type != null && structuralAncestorTypes.contains(type);
    }

    /// Buckets every rule by the type its rightmost compound names.
    ///
    /// A rule with several selectors goes in every bucket any of them names, and
    /// in [#untyped] if any of them names none — a rule is a unit and the
    /// cascade has to see it whole, so over-collecting is the only safe error.
    private void indexByType() {
        for (var index = 0; index < stylesheets.size(); index++) {
            var sheet = stylesheets.get(index);
            for (var rule : sheet.rules()) {
                var candidate = new Candidate(rule, sheet.layer(), index);
                var typed = rule.starting() ? startingByType : byType;
                var everywhere = false;
                for (var selector : rule.selectors()) {
                    var subjects = subjectTypes(selector.key());
                    if (subjects == null) {
                        everywhere = true;
                    } else {
                        for (var subject : subjects) {
                            var bucket = typed.computeIfAbsent(subject, key -> new java.util.ArrayList<>());
                            if (bucket.isEmpty() || bucket.getLast() != candidate) {
                                bucket.add(candidate);
                            }
                        }
                    }
                }
                if (everywhere) {
                    (rule.starting() ? startingUntyped : untyped).add(candidate);
                }
            }
        }
    }

    /// The types an element must have for `compound` to match it, or null when
    /// it may have any. A pseudo-element counts as a type of its own, see
    /// [#bucketOf].
    ///
    /// Its own type when it names one. Otherwise an `:is()` or `:where()` whose
    /// every argument's subject names types narrows it to those:
    /// `:is(button, toggle):hover` can only match a `button` or a `toggle`, and
    /// is bucketed under both rather than tested against every element.
    private static @Nullable Set<String> subjectTypes(Selector.Compound compound) {
        if (compound.type() != null) {
            return Set.of(compound.type());
        }
        if (compound.pseudoElement() != null) {
            return Set.of(bucketOf(compound.pseudoElement()));
        }
        for (var functional : compound.functional()) {
            var arguments = switch (functional) {
                case Functional.Is(var held) -> held;
                case Functional.Where(var held) -> held;
                case Functional.Not _, Functional.NthOf _, Functional.Has _ -> null;
            };
            if (arguments == null) {
                continue;
            }
            var types = new HashSet<String>();
            var narrowed = true;
            for (var argument : arguments) {
                var inner = subjectTypes(argument.key());
                if (inner == null) {
                    narrowed = false;
                    break;
                }
                types.addAll(inner);
            }
            if (narrowed) {
                return types;
            }
        }
        return null;
    }

    /// The rules that could match an element of this type.
    ///
    /// **Null is the case this exists for**, not a defensive branch: a
    /// composition node has no CSS type ([StyleElement#type()]), and the rules
    /// that can match it are exactly the ones naming no type either.
    private List<Candidate> candidatesFor(StyleElement element) {
        return candidatesFor(element, byType, untyped);
    }

    /// The rules that could match `element`: those for its type, and those for
    /// the pseudo-element it stands for when it stands for one.
    private static List<Candidate> candidatesFor(
            StyleElement element, java.util.Map<String, List<Candidate>> byType, List<Candidate> untyped) {
        var typed = candidatesFor(element.type(), byType, untyped);
        var pseudo = element.pseudoElement();
        var parts = pseudo == null ? null : byType.get(bucketOf(pseudo));
        if (parts == null) {
            return typed;
        }
        var all = new java.util.ArrayList<Candidate>(typed.size() + parts.size());
        all.addAll(typed);
        all.addAll(parts);
        return all;
    }

    /// The bucket a rule whose subject is `::placeholder` goes in: a key no
    /// element type can be, so the rule is tried on the parts that stand for
    /// the pseudo-element and on nothing else.
    private static String bucketOf(PseudoElement pseudo) {
        return "::" + pseudo.cssName();
    }

    private static List<Candidate> candidatesFor(
            @Nullable String type, java.util.Map<String, List<Candidate>> byType, List<Candidate> untyped) {
        if (type == null) {
            return untyped;
        }
        var typed = byType.get(type);
        if (typed == null) {
            return untyped;
        }
        if (untyped.isEmpty()) {
            return typed;
        }
        var all = new java.util.ArrayList<Candidate>(typed.size() + untyped.size());
        all.addAll(typed);
        all.addAll(untyped);
        return all;
    }

    /// Walks every selector once, recording which pseudo-classes appear to the
    /// **left** of a combinator and on what.
    ///
    /// Once per resolver, which is once per theme change — against a cascade pass
    /// per element per frame, which is what this saves.
    private void indexAncestorStates() {
        for (var sheet : stylesheets) {
            for (var rule : sheet.rules()) {
                for (var selector : rule.selectors()) {
                    eachCompound(selector, false, (part, ancestor) -> {
                        if (!ancestor) {
                            return;
                        }
                        var compound = part.compound();
                        for (var written : compound.pseudoClasses()) {
                            // `:enabled` is answered from `:disabled`, so it is
                            // a change to that state that reaches below.
                            var state =
                                    written == Selector.PseudoClass.ENABLED ? Selector.PseudoClass.DISABLED : written;
                            if (compound.type() == null) {
                                untypedAncestorStates.add(state);
                            } else {
                                ancestorStates
                                        .computeIfAbsent(state, key -> new java.util.HashSet<>())
                                        .add(compound.type());
                            }
                        }
                    });
                }
            }
        }
    }

    /// Whether `state` changing on an element of `type` can change what matches
    /// **below** it.
    ///
    /// The question [dev.goldberry.widget.Element#setPseudoClass]
    /// asks before throwing a subtree's styles away. `checkbox:hover
    /// check-indicator` means yes for `:hover` on a `checkbox`; nothing in any
    /// sheet says `column:hover …`, so hovering a `column` — which is what a
    /// click on empty space does — changes that node and nothing under it.
    ///
    /// Conservative in both directions it can be: an untyped ancestor compound
    /// makes its pseudo-class reach everything, and a caller with no type of its
    /// own gets `true`.
    public boolean reachesDescendants(Selector.PseudoClass state, @Nullable String type) {
        Objects.requireNonNull(state, "state");
        if (untypedAncestorStates.contains(state)) {
            return true;
        }
        if (type == null) {
            // A node with no CSS type is a composition node or a bare painter:
            // the only compound that can name it is one that names no type
            // either, and every such compound is in the set just checked. So
            // there is no rule left that could reach through this node's state
            // -- which matters because the hover and active chains run to the
            // root through several of them, and treating those as unknown would
            // be the whole tree re-resolving on every click.
            return false;
        }
        var types = ancestorStates.get(state);
        return types != null && types.contains(type);
    }

    /// The declarations that apply to `element`, with `var()` resolved.
    ///
    /// @return property name to value tokens, in no particular order
    public Map<String, List<Token>> resolve(StyleElement element) {
        Objects.requireNonNull(element, "element");
        // One cascade, two readers. Custom properties are collected from the same
        // declarations the cascade produces, so asking for them separately would
        // run it twice for every element resolved.
        var declared = cascade(element);
        return substituted(element, declared, customPropertiesFor(element, declared));
    }

    /// Whether a child styled here may read more of its parent than the
    /// inherited half: true when some sheet writes `inherit` on a property that
    /// does not inherit, such as `padding: inherit`.
    ///
    /// The renderer keys a child's cached style on its parent's style, and
    /// normally compares parents on the inherited half alone. With this true it
    /// compares them whole.
    public boolean inheritsExplicitly() {
        return inheritsExplicitly;
    }

    /// Whether any sheet here has an `@starting-style` rule at all — the check
    /// that keeps an element's first frame from running a second cascade in the
    /// overwhelmingly common case of a sheet with none.
    public boolean hasStartingStyles() {
        return !startingByType.isEmpty() || !startingUntyped.isEmpty();
    }

    /// The declarations `element` **starts** from on its first frame, or null when
    /// no `@starting-style` rule matches it.
    ///
    /// CSS's "before-change style" for an element with no previous style: the
    /// ordinary cascade with the matching starting rules added in their source
    /// positions. So a starting rule that says only `opacity: 0` leaves every
    /// other property where the ordinary rules put it, and only `opacity`
    /// transitions.
    ///
    /// @return property name to value tokens, or null when nothing would differ
    public @Nullable Map<String, List<Token>> resolveStarting(StyleElement element) {
        Objects.requireNonNull(element, "element");
        if (!hasStartingStyles()) {
            return null;
        }
        var starting = candidatesFor(element, startingByType, startingUntyped);
        var matched = false;
        for (var candidate : starting) {
            var rule = candidate.rule();
            if ((!rule.isConditional() || rule.media().matches(media))
                    && (!rule.isContained() || holds(rule, element))
                    && matchesAny(rule, element)) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return null;
        }
        var all = new ArrayList<Candidate>(candidatesFor(element));
        all.addAll(starting);
        // The element's own custom properties, not the starting cascade's: a
        // starting rule that declared one would otherwise be cached as the
        // element's for every later frame.
        return substituted(element, cascade(element, all), customPropertiesFor(element));
    }

    /// The `@keyframes` block called `name`, or null when no sheet declares one.
    public @Nullable Keyframes keyframes(String name) {
        var blocks = keyframes.get(Objects.requireNonNull(name, "name"));
        if (blocks == null) {
            return null;
        }
        for (var i = blocks.size() - 1; i >= 0; i--) {
            var block = blocks.get(i);
            if (!block.isConditional() || block.media().matches(media)) {
                return block;
            }
        }
        return null;
    }

    /// One keyframe's declarations, with `var()` resolved **for `element`**, in
    /// source order — or null for a declaration whose `var()` resolves to nothing,
    /// which is dropped here as it would be anywhere else.
    ///
    /// Per element, because `var(--gb-accent)` in a keyframe means the accent of
    /// the element the animation runs on.
    public Map<String, List<Token>> resolveKeyframe(StyleElement element, Keyframes.Frame frame) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(frame, "frame");
        var declared = new java.util.LinkedHashMap<String, List<Token>>();
        for (var declaration : frame.declarations()) {
            declared.remove(declaration.property());
            declared.put(declaration.property(), declaration.value());
        }
        return substituted(element, declared, customPropertiesFor(element));
    }

    private static boolean matchesAny(StyleRule rule, StyleElement element) {
        for (var selector : rule.selectors()) {
            if (SelectorMatcher.matches(selector, element)) {
                return true;
            }
        }
        return false;
    }

    /// `declared` with every `var()` substituted against `customProperties`, and
    /// custom properties themselves left out.
    private Map<String, List<Token>> substituted(
            StyleElement element, Map<String, List<Token>> declared, Map<String, List<Token>> customProperties) {

        var resolved = new LinkedHashMap<String, List<Token>>();
        for (var entry : declared.entrySet()) {
            if (entry.getKey().startsWith("--")) {
                // Already resolved into customProperties, and a custom property
                // is never itself a target for substitution.
                continue;
            }
            var value = substitute(entry.getValue(), customProperties, new HashSet<>());
            if (value == null) {
                // "Invalid at computed-value time": an unresolvable var() with no
                // fallback. CSS drops the declaration, and so does this -- but at
                // resolve time, which is inside the frame loop, so it warns
                // rather than throwing the way a parse error does.
                if (shouldReport(entry.getKey() + '<' + element.type() + '>')) {
                    LOG.warn(
                            "dropping \"{}\" on <{}>: a var() in it resolves to nothing",
                            entry.getKey(),
                            element.type());
                }
                continue;
            }
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    /// Every custom property visible to `element`, its own overriding those it
    /// inherits.
    public Map<String, List<Token>> customPropertiesFor(StyleElement element) {
        return customPropertiesFor(element, null);
    }

    /// One written value, with its `var()`s resolved as they would be on
    /// `element` — **whether or not the declaration it came from wins**.
    ///
    /// [#resolve] answers "what does this element end up with", and that is the
    /// wrong question for a reader checking declarations rather than elements. A
    /// lint that asks it gets the winner's value back for a property some other
    /// rule won, which both hides a bad losing declaration and reports the
    /// winner's mistake at the loser's line.
    ///
    /// Nothing is cascaded here: the custom properties are the element's, because
    /// that is what a `var()` in any rule about it would see, and the value is the
    /// one that was written.
    ///
    /// @return the substituted tokens, or null when a `var()` in them resolves to
    ///         nothing — which is the same "invalid at computed-value time" that
    ///         drops the declaration inside [#resolve], reported there and not
    ///         again here
    public @Nullable List<Token> substitutedFor(StyleElement element, List<Token> value) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(value, "value");
        return substitute(value, customPropertiesFor(element), new HashSet<>());
    }

    /// One custom property's value, with any `var()` in it resolved.
    ///
    /// For the one reader outside the cascade: a widget that needs a value the
    /// cascade has nowhere to put — a chart's eight series colours, which cannot
    /// be eight properties on one node.
    ///
    /// **Substituted, because a custom property may hold another one.**
    /// `--gb-chart-1: var(--gb-warning)` is the natural way to say "this series
    /// is the warning hue", and CSS resolves such a value at *use* time, which is
    /// here rather than where it was written. Reading the raw tokens instead sees
    /// `var(--gb-warning)`, decides it is not a colour, and falls back silently.
    ///
    /// @return the substituted tokens, or null when the property is unset or its
    ///         `var()` resolves to nothing
    public @Nullable List<Token> customProperty(StyleElement element, String name) {
        var properties = customPropertiesFor(element);
        var tokens = properties.get(name);
        return tokens == null ? null : substitute(tokens, properties, new HashSet<>());
    }

    /// The same, when the caller has already cascaded `element` and would
    /// otherwise pay for it twice.
    ///
    /// @param ownCascade this element's winning declarations, or null to compute
    ///                   them here
    private Map<String, List<Token>> customPropertiesFor(
            StyleElement element, @Nullable Map<String, List<Token>> ownCascade) {
        // Built from the root down so a nearer definition overwrites a farther
        // one. Recursion rather than a loop because the chain is walked upward
        // and applied downward.
        var parent = element.parent();
        var inherited = parent == null ? EMPTY : customPropertiesFor(parent, null);

        // Cached against what the parent handed down, by identity. Without this
        // the recursion above runs a full cascade at every level of the tree, so
        // one node at depth ten costs eleven of them.
        var cached = element.cachedCustomProperties(this, inherited);
        if (cached != null) {
            return cached;
        }

        // Only what this node changes, and the parent's map untouched. The
        // inherited map is the root's 180-odd `--gb-*` properties, and copying
        // all of them into a fresh map at every node to find out, nearly always,
        // that the node declares none would be more than half of a first frame's
        // style resolution, at any depth; the walk above is one percent of it.
        Map<String, List<Token>> own = null;
        for (var entry : (ownCascade == null ? cascade(element) : ownCascade).entrySet()) {
            var name = entry.getKey();
            if (name.startsWith("--") && !entry.getValue().equals(inherited.get(name))) {
                if (own == null) {
                    own = new HashMap<>(inherited);
                }
                own.put(name, entry.getValue());
            }
        }
        // The parent's own map when this node changes nothing, so a chain of
        // nodes that define nothing shares one instance -- and a child's cache
        // stays valid through all of them, because the identity it is keyed on
        // does not change on the way down. A node matched by the same rule as its
        // parent re-declares the very tokens it inherits, changes nothing, and
        // shares too.
        var resolved = own == null ? inherited : Map.copyOf(own);
        element.cacheCustomProperties(this, inherited, resolved);
        return resolved;
    }

    /// The empty map every root starts from, as one instance — so a tree whose
    /// root declares no custom property still hands its children something with a
    /// stable identity.
    private static final Map<String, List<Token>> EMPTY = Map.of();

    /// The winning declaration for each property on `element`, before `var()`.
    private Map<String, List<Token>> cascade(StyleElement element) {
        return cascade(element, candidatesFor(element));
    }

    private Map<String, List<Token>> cascade(StyleElement element, List<Candidate> candidates) {
        var matches = new ArrayList<Match>();
        // Only the rules whose rightmost compound could name this element. The
        // order they come out in does not matter: every match carries its layer,
        // its specificity and the rule's own order, and the sort below is what
        // decides the winner.
        for (var candidate : candidates) {
            var rule = candidate.rule();
            if (rule.isConditional() && !rule.media().matches(media)) {
                continue;
            }
            if (rule.isContained() && !holds(rule, element)) {
                continue;
            }
            // The most specific *matching* selector in the list is the one
            // that represents the rule, per the cascade.
            var best = -1;
            for (var selector : rule.selectors()) {
                if (SelectorMatcher.matches(selector, element)) {
                    best = Math.max(best, selector.specificity());
                }
            }
            if (best < 0) {
                continue;
            }
            var sublayer = sublayerOf(candidate.layer(), rule);
            for (var declaration : rule.declarations()) {
                matches.add(new Match(declaration, candidate.layer(), sublayer, best, candidate.sheet(), rule.order()));
            }
        }

        // Weakest first, so a later put() overwrites a weaker one.
        matches.sort(CASCADE);

        // Ordered, and each winner moved to the end as it wins.
        //
        // Nothing between this and `ComputedStyle.apply` re-orders, so the order
        // properties come out in is the order they are *applied* in -- and a
        // `padding` applied after a `padding-left` overwrites the edge the
        // longhand set. Under a `HashMap` that order would be whatever the
        // property names' buckets happen to give, and `inset: 8px; left: 20px`
        // could quietly lose its `left`.
        //
        // `remove` before `put` because `LinkedHashMap` keeps a re-put key at its
        // **first** position, and the position that matters is the winning
        // declaration's. Three rules -- `padding-left`, then `padding`, then
        // `padding-left` again -- must end with the longhand last, and without
        // the remove it would sit where the first, losing one was.
        var winners = new LinkedHashMap<String, List<Token>>();
        var reverts = false;
        for (var match : matches) {
            var property = match.declaration().property();
            winners.remove(property);
            winners.put(property, match.declaration().value());
            reverts |= isRevert(match.declaration().value());
        }
        if (reverts) {
            revert(winners, matches);
        }
        return winners;
    }

    private static boolean isRevert(List<Token> value) {
        var keyword = GlobalKeyword.of(value);
        return keyword == GlobalKeyword.REVERT || keyword == GlobalKeyword.REVERT_LAYER;
    }

    /// Replaces each winning `revert` or `revert-layer` with the declaration it
    /// rolls back to, or removes the property when there is none, which leaves
    /// it unset.
    ///
    /// The layers here break ties after specificity rather than ranking above
    /// it, so "the layers below" means the declarations that lost to this one
    /// from a lower layer, and the strongest of them wins. `revert` treats the
    /// toolkit's base sheets as CSS treats the browser's own: it rolls back to
    /// the strongest of their declarations, and in a base sheet it unsets.
    /// Reverting a longhand reaches only declarations of that same name, not a
    /// shorthand that would also have set it.
    ///
    /// @param matches every match, weakest first, as [#cascade] sorted them
    private static void revert(Map<String, List<Token>> winners, List<Match> matches) {
        var iterator = winners.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!isRevert(entry.getValue())) {
                continue;
            }
            var at = matches.size();
            List<Token> value = null;
            Match below = null;
            boolean toBase = false;
            // Walk down from the winner, which is the strongest match of this
            // property, until a declaration that is not itself a revert.
            while (true) {
                at = previous(matches, entry.getKey(), at, below, toBase);
                if (at < 0) {
                    break;
                }
                var match = matches.get(at);
                var keyword = GlobalKeyword.of(match.declaration().value());
                if (keyword == GlobalKeyword.REVERT_LAYER) {
                    below = match;
                    toBase = false;
                } else if (keyword == GlobalKeyword.REVERT) {
                    if (match.layer() == CascadeLayer.TOOLKIT_BASE) {
                        break;
                    }
                    below = null;
                    toBase = true;
                } else {
                    value = match.declaration().value();
                    break;
                }
            }
            if (value == null) {
                iterator.remove();
            } else {
                entry.setValue(value);
            }
        }
    }

    /// The index of the strongest match of `property` before `before` that is
    /// in a layer below `below`'s (when given), an application `@layer`
    /// counting as a layer of its own, or in the toolkit's base layer (when
    /// `toBase`), or -1.
    private static int previous(
            List<Match> matches, String property, int before, @Nullable Match below, boolean toBase) {
        for (var i = before - 1; i >= 0; i--) {
            var match = matches.get(i);
            if (!match.declaration().property().equals(property)) {
                continue;
            }
            if (toBase && match.layer() != CascadeLayer.TOOLKIT_BASE) {
                continue;
            }
            if (below != null && LAYER_ORDER.compare(match, below) >= 0) {
                continue;
            }
            return i;
        }
        return -1;
    }

    /// The cascade order, weakest first.
    ///
    /// `!important` first because it outranks everything — with fixed layers
    /// there is no origin for it to invert, so it is simply the top key.
    ///
    /// Then specificity, then layer, then source order. Layer *after* specificity
    /// is the rule: a later layer wins at equal specificity, which makes a layer
    /// an extension of source order rather than the override `@layer` provides. A
    /// more specific toolkit rule therefore still beats a vaguer application one,
    /// exactly as two rules in one stylesheet would.
    ///
    /// **Source order is two numbers, not one.** [Match#order] is
    /// [StyleRule#order], the rule's index *inside its own stylesheet*, so a layer
    /// holding more than one sheet needs an ordering between them, or an earlier
    /// sheet's rule 12 would outrank a later sheet's rule 0 at equal specificity.
    /// The sheet index goes between layer and rule order, which is where "later
    /// source wins" belongs: it leaves every layer comparison above it and every
    /// specificity comparison above that untouched.
    ///
    /// **An application's `@layer`s order after the four layers, before the
    /// sheet**, the same kind of key: a later layer wins at equal specificity,
    /// and a rule in no layer wins over every layered one. `!important` turns
    /// the order round, as CSS does, so an important declaration in an earlier
    /// layer wins over one in a later layer or in none. They do not rank above
    /// specificity as CSS's do: inside the application layer they could, but a
    /// cascade where the application layer ranked by layer and the others by
    /// specificity would have no consistent order between the two.
    /// Which layer a match is in, the four and the application's `@layer`s
    /// together: what `revert-layer` rolls back below.
    private static final Comparator<Match> LAYER_ORDER =
            Comparator.comparing(Match::layer).thenComparingInt(Match::sublayerKey);

    private static final Comparator<Match> CASCADE = Comparator.<Match, Boolean>comparing(
                    m -> m.declaration().important())
            .thenComparingInt(Match::specificity)
            .thenComparing(Match::layer)
            .thenComparingInt(Match::sublayerKey)
            .thenComparingInt(Match::sheet)
            .thenComparingInt(Match::order);

    /// @param sublayer the rule's `@layer` rank, [#UNLAYERED] for none
    /// @param sheet    the sheet's index in this resolver's list — see [Candidate]
    /// @param order    the rule's index inside that sheet
    private record Match(
            Declaration declaration, CascadeLayer layer, int sublayer, int specificity, int sheet, int order) {

        /// The `@layer` rank as the cascade sorts it: as it is for a normal
        /// declaration, and the other way round for an important one.
        int sublayerKey() {
            return declaration.important() ? -sublayer : sublayer;
        }
    }

    /// Replaces every `var()` in `value`.
    ///
    /// @param inProgress the custom properties currently being expanded, so a
    ///                   cycle is caught rather than overflowing the stack
    /// @return the substituted tokens, or null if the value is invalid at
    ///         computed-value time
    @Nullable
    List<Token> substitute(List<Token> value, Map<String, List<Token>> variables, Set<String> inProgress) {

        if (value.stream().noneMatch(t -> t.is(TokenType.FUNCTION) && t.text().equalsIgnoreCase("var"))) {
            return value;
        }

        var out = new ArrayList<Token>();
        var i = 0;
        while (i < value.size()) {
            var token = value.get(i);
            if (!(token.is(TokenType.FUNCTION) && token.text().equalsIgnoreCase("var"))) {
                out.add(token);
                i++;
                continue;
            }

            var close = matchingParen(value, i);
            if (close < 0) {
                return null;
            }
            var arguments = value.subList(i + 1, close);
            var expanded = expandVar(arguments, variables, inProgress);
            if (expanded == null) {
                return null;
            }
            out.addAll(expanded);
            i = close + 1;
        }
        return out;
    }

    /// `--name` or `--name, fallback…`.
    private @Nullable List<Token> expandVar(
            List<Token> arguments, Map<String, List<Token>> variables, Set<String> inProgress) {

        var trimmed = trim(arguments);
        if (trimmed.isEmpty()
                || !trimmed.getFirst().is(TokenType.IDENT)
                || !trimmed.getFirst().text().startsWith("--")) {
            // var(4px) and var() are not things; treat as unresolvable rather
            // than guessing what was meant.
            return null;
        }
        var name = trimmed.getFirst().text();

        List<Token> fallback = null;
        for (var i = 1; i < trimmed.size(); i++) {
            if (trimmed.get(i).is(TokenType.COMMA)) {
                fallback = trim(trimmed.subList(i + 1, trimmed.size()));
                break;
            }
            if (!trimmed.get(i).is(TokenType.WHITESPACE)) {
                return null;
            }
        }

        var defined = variables.get(name);
        if (defined != null) {
            if (!inProgress.add(name)) {
                // --a: var(--b); --b: var(--a). Without this the stack goes.
                if (shouldReport("cycle:" + name)) {
                    LOG.warn("custom property {} refers to itself; dropping the declaration", name);
                }
                return null;
            }
            try {
                var substituted = substitute(defined, variables, inProgress);
                if (substituted != null) {
                    return substituted;
                }
            } finally {
                inProgress.remove(name);
            }
        }
        if (fallback != null) {
            return substitute(fallback, variables, inProgress);
        }
        return null;
    }

    /// The index of the `)` closing the function that starts at `open`.
    private static int matchingParen(List<Token> value, int open) {
        var depth = 0;
        for (var i = open; i < value.size(); i++) {
            var token = value.get(i);
            if (token.is(TokenType.FUNCTION) || token.is(TokenType.OPEN_PAREN)) {
                depth++;
            } else if (token.is(TokenType.CLOSE_PAREN)) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<Token> trim(List<Token> value) {
        var from = 0;
        var to = value.size();
        while (from < to && value.get(from).is(TokenType.WHITESPACE)) {
            from++;
        }
        while (to > from && value.get(to - 1).is(TokenType.WHITESPACE)) {
            to--;
        }
        return value.subList(from, to);
    }

    /// Whether `key` has not been reported by this resolver yet.
    ///
    /// `add` returning false is the whole test — it says the key was already
    /// there. See [#reported] for what the key is and why the cap lets drops
    /// through rather than silencing them.
    private boolean shouldReport(String key) {
        return reported.size() >= REPORT_LIMIT || reported.add(key);
    }

    /// How many rules landed in [#untyped].
    ///
    /// Bucketing by type saves exactly as much as the stylesheet lets it: a rule
    /// for `button` is never looked at for a `text`, but a sheet written entirely
    /// in classes puts every rule in the untyped bucket, where it is checked
    /// against every element of every kind. The toolkit's own sheets are
    /// type-first, and this is what a lint reads to hold them to it.
    ///
    /// Public rather than package-private because the lint that reads it is in
    /// `:example`, beside the others that check the toolkit's own stylesheets: a
    /// sheet's shape is the same kind of fact as a dropped declaration, and it
    /// belongs in the same place.
    public int untypedRuleCount() {
        return untyped.size();
    }

    /// The selectors of the rules in [#untyped], for a lint that has to say
    /// *which* ones rather than merely how many.
    ///
    /// A count alone makes a threshold that fails without naming anything, which
    /// is the kind of check somebody raises the number on instead of reading.
    public List<String> untypedSelectors() {
        return untyped.stream()
                .flatMap(candidate -> candidate.rule().selectors().stream())
                .map(Object::toString)
                .distinct()
                .sorted()
                .toList();
    }

    /// Every rule this resolver indexed, counting a rule once however many
    /// buckets its selectors put it in.
    public int ruleCount() {
        return stylesheets.stream().mapToInt(sheet -> sheet.rules().size()).sum();
    }

    /// How many distinct drops this resolver has reported.
    ///
    /// Package-private and for [StyleResolverTest] alone. The thing worth
    /// asserting is that one bad `var()` is *one* message however many elements
    /// hit it, and only `slf4j-api` is on the classpath — there is no appender to
    /// read the log back from, and adding a logging backend to test a counter
    /// would be a dependency bought for one assertion.
    int reportedDrops() {
        return reported.size();
    }

    /// Whether every `@container` query `rule` is inside holds for `element`.
    private static boolean holds(StyleRule rule, StyleElement element) {
        for (var query : rule.containers()) {
            if (!query.holds(element)) {
                return false;
            }
        }
        return true;
    }
}
