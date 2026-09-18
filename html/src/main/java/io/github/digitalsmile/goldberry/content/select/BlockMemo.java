package io.github.digitalsmile.goldberry.content.select;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.Widget;

/// The widgets a fold built last time, so that a block nobody touched is not built
/// again.
///
/// A preview re-parses on every keystroke and hands the view a **new** `Document`,
/// which is what its documentation tells an application to do. One paragraph
/// changed; every block is a new record, every widget under it is a new object, and
/// the element tree has nothing to match the new description to the node it
/// replaced — so it re-describes the note from top to bottom
/// (`docs/gaps.md` G45, [ADR-0389]).
///
/// This is the matching. A block whose source is `equals` to the one at the same
/// place last build, with the fold standing in the same place it stood then, gets
/// the **same widget instance** back — and `Element.update` short-circuits on an
/// identical description without walking below it, which is the mechanism ADR-0315
/// left in place for a caller that could prove nothing changed.
///
/// ## Why the same instance and not an equal one
///
/// `Element.update` compares by identity, deliberately (ADR-0369's viewport rests on
/// it): a widget is a value, and the *same* value describing a node means the node's
/// subtree cannot have changed. An equal-but-different widget would still be a full
/// walk of the subtree comparing equal things.
///
/// ## What invalidates an entry
///
/// - the block's source, compared with `equals` — a record tree, so this is a deep
///   comparison of the text, and cheaper than building the widgets by two orders of
///   magnitude;
/// - **where the fold was standing**: a block built as the fourth block of the
///   document cannot be handed back as the fifth, because its words report into the
///   fourth block's entries;
/// - whether the geometry still holds those entries, which [WordMinter#canResume]
///   answers.
///
/// One of these per mounted document, held by [SelectableDocument] beside the
/// geometry — the two have to be thrown away together, and the state is the only
/// thing in either view that lives longer than a build.
public final class BlockMemo {

    /// What a fold has to be able to do for its blocks to be memoized.
    ///
    /// Three methods rather than a lambda, because skipping a block means carrying on
    /// as if it had been walked — and only the fold knows what it was counting as it
    /// went. `markdown-view` counts words *and* task boxes, and a task ordinal that
    /// reset because a paragraph above it was reused would toggle the wrong line.
    ///
    /// @param <T> the fold's own kind of block
    public interface Fold<T> {

        /// The widgets for `source`, built now.
        Widget build(T source);

        /// Whether what [#build] just produced may be handed back on a later build.
        ///
        /// False for a block that is **not a function of its source alone**. There is
        /// one of those: a block holding an image the application could not find yet.
        /// [io.github.digitalsmile.goldberry.content.ImageSource] answers null while
        /// it loads and the picture arrives on a later frame, so a block that drew alt
        /// text has to be asked again until it stops drawing it.
        default boolean keep() {
            return true;
        }

        /// Where this fold is standing — an opaque value it can be put back to.
        ///
        /// Compared with `equals` against the mark a block was built at, so whatever
        /// the fold counts as it walks belongs in here: the words, the blocks, and —
        /// for `markdown-view` — the task ordinals and which handlers the view has.
        Object mark();

        /// Whether it can carry on from `mark` without having walked what lies
        /// between here and there.
        boolean canResume(Object mark);

        /// Carries on from `mark`, as if it had.
        void resume(Object mark);
    }

    /// One block, as it was built.
    ///
    /// @param source what it was built from
    /// @param before where the fold was standing when it started
    /// @param after where it was standing when it finished
    /// @param widget what it built
    private record Entry(Object source, Object before, Object after, Widget widget) {}

    /// Stands in for the source of a block that must not be handed back — equal to
    /// nothing, including itself in any useful sense.
    private static final Object NOTHING = new Object();

    private final List<Entry> entries = new ArrayList<>();

    /// The fold's own long-lived object, if it asked for one.
    private @Nullable Object held;

    /// Blocks handed back unbuilt by the last [#blocks] call.
    private int kept;

    /// Blocks built by it.
    private int built;

    /// The widgets for `sources`, building only the ones this memo cannot hand back.
    ///
    /// Positional: the `i`th entry answers for the `i`th block and nothing else. A
    /// block that moved is a block that is built again, which is the right answer
    /// rather than a missed optimisation — its words are in a different place, so the
    /// widgets that report them would be wrong.
    ///
    /// @param sources the blocks, in document order
    /// @param fold what builds one, and what knows where it is standing
    public <T> List<Widget> blocks(List<? extends T> sources, Fold<T> fold) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(fold, "fold");
        kept = 0;
        built = 0;
        var widgets = new ArrayList<Widget>(sources.size());
        for (var index = 0; index < sources.size(); index++) {
            var source = sources.get(index);
            var before = fold.mark();
            var entry = index < entries.size() ? entries.get(index) : null;
            if (entry != null
                    && entry.source().equals(source)
                    && entry.before().equals(before)
                    && fold.canResume(entry.after())) {
                fold.resume(entry.after());
                widgets.add(entry.widget());
                kept++;
                continue;
            }
            var widget = fold.build(source);
            // A block the fold will not vouch for is remembered as `NOTHING`, which is
            // equal to no source there will ever be — so the slot stays in step with
            // the document and matches nothing.
            var record = new Entry(fold.keep() ? source : NOTHING, before, fold.mark(), widget);
            if (entry == null) {
                entries.add(record);
            } else {
                entries.set(index, record);
            }
            built++;
            widgets.add(widget);
        }
        // **Trimmed to this build's length, and that is a correctness rule rather than
        // tidiness.** The geometry drops the blocks a shorter document no longer has,
        // and an entry left behind here would match a later build that grew back to
        // the same shape — handing it widgets whose entries nothing reads.
        if (entries.size() > sources.size()) {
            entries.subList(sources.size(), entries.size()).clear();
        }
        return widgets;
    }

    /// The fold's own object, made once and held for as long as this memo is.
    ///
    /// **What a memoized widget must reach the application through.** A block built
    /// three keystrokes ago holds the handlers it was built with, and an application
    /// that writes `onLink(this::open)` in its own `build` hands the view a new object
    /// every frame — so a widget that captured one would call last frame's. It calls
    /// through this instead, which is the same object every frame and is given the
    /// current handlers at the start of each build.
    ///
    /// @param create what to make the first time, and never again
    @SuppressWarnings("unchecked")
    public <W> W held(Supplier<W> create) {
        if (held == null) {
            held = Objects.requireNonNull(create.get(), "held");
        }
        return (W) held;
    }

    /// Blocks the last build handed back without building them.
    ///
    /// For a test: what this class is for is a *count*, and a count is what can be
    /// asserted on a machine somebody else is also using (`docs/testing.md` §4).
    public int kept() {
        return kept;
    }

    /// Blocks the last build built.
    public int built() {
        return built;
    }

    /// Forgets everything, so the next build rebuilds the document.
    ///
    /// Nothing calls this today. It is here because a memo with no way to be emptied
    /// is a memo whose only recovery from a bug is a new window.
    public void clear() {
        entries.clear();
    }

    /// What the `i`th block was built from, or null — for a test that wants to say
    /// which block was kept rather than how many.
    public @Nullable Object sourceAt(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index).source() : null;
    }
}
