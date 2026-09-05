package io.github.digitalsmile.goldberry.widget;

import java.util.Optional;

/// What a widget can ask about where it is in the tree.
///
/// Handed to every `build()`. Deliberately narrow: a build must be a pure
/// function of its widget, its state and this — so anything reachable through it
/// is something the framework can track and invalidate.
public interface BuildContext {

    /// The nearest enclosing widget of `type`, if any.
    ///
    /// How a control finds the thing it belongs to: a `radio` finding its
    /// `radio-group`, a `field` finding its `form`. Walks up the *element* tree,
    /// so it costs the depth of the tree and no allocation.
    <T extends Widget> Optional<T> findAncestor(Class<T> type);

    /// The nearest enclosing [State] of `type`, if any.
    ///
    /// [#findAncestor] finds an ancestor's *description*, which is what a `radio`
    /// wants of its group: the value, the name, the change handler are all on the
    /// widget. This finds the ancestor's **live half**, and exists for the case
    /// where the answer is not a value but an action — Flutter's
    /// `Scrollable.of(context)`, and here `scrollIntoView`
    /// (ADR-0120).
    ///
    /// A scroll view's offset lives on its state and cannot live anywhere else: a
    /// widget is a value rebuilt every frame, so a descendant that reached the
    /// `Scroll` record would find a description with no position in it and
    /// nothing to ask.
    ///
    /// **Narrower than it looks.** A state reached this way is one an ancestor
    /// owns, and the only thing worth doing with it is calling a method the
    /// ancestor deliberately exposed. Reading another state's fields is how two
    /// widgets end up with one bug, and nothing here makes it convenient.
    <S extends State<?>> Optional<S> findAncestorState(Class<S> type);

    /// The window this element is being built into, if it has one.
    ///
    /// Flutter's `Overlay.of(context)`, and the door a control needs when the
    /// thing it has to do is not describable as a widget: a `select` opens a
    /// popup window under itself, and a popup is the platform's rather than the
    /// tree's (ADR-0140).
    ///
    /// **For acting, not for reading.** A build must stay a pure function of its
    /// widget, its state and this context, so what a build may do with a host is
    /// *capture* it for a handler that runs later. Reading anything off it —
    /// [io.github.digitalsmile.goldberry.Host#anchor], the placeable area — makes
    /// the build depend on the last frame, which nothing invalidates.
    ///
    /// **Empty is a normal answer**, and the reason this is an `Optional` rather
    /// than a nullable: a widget test builds an [ElementTree] with no window at
    /// all, and so does a golden image. A control that cannot open its popup
    /// should stay closed rather than throw, which is exactly what a still
    /// picture of it wants.
    Optional<io.github.digitalsmile.goldberry.Host> host();

    /// A **length** custom property, in logical pixels, as the cascade resolves
    /// it here.
    ///
    /// [io.github.digitalsmile.goldberry.widget.style.Paints.Context#length]'s
    /// build-time twin, and it exists because some numbers are wanted *before*
    /// there is a box to paint ([ADR-0254]). A virtualized `list` decides how
    /// many rows to build from how tall a row is, and that decision is made here
    /// — a value banked from `render` would be a frame late in the one place a
    /// frame late means building the wrong rows.
    ///
    /// Resolved against **this element**, so it inherits and can be overridden
    /// per node like any other custom property.
    ///
    /// **Answers the fallback when there is no cascade**, which is a normal
    /// answer rather than an error: a widget test builds a tree with no renderer
    /// at all, and so does a golden. A list in one of those virtualizes at its
    /// default rather than refusing to build.
    ///
    /// **The very first build of a tree is one of those**, and it is worth
    /// knowing rather than working around. A `Stateful` widget builds once inside
    /// the [ElementTree] constructor — before any renderer has taken the tree on,
    /// and therefore before any cascade exists — so a token asked for there
    /// answers its default and the *second* build is the first that can see the
    /// stylesheet. Everything that reads one is expected to settle, which a
    /// virtualized list does by construction: its window is recomputed from the
    /// geometry each frame, so the frame after the first is already right.
    /// Closing it properly means handing the resolver to the tree at
    /// construction, which nothing has needed enough to widen the constructor
    /// for.
    ///
    /// @param name     the property, `--` included
    /// @param fallback what to answer when it is unset, unparseable, a
    ///                 percentage, or when nothing has styled this tree
    double token(String name, double fallback);

    /// A **duration** custom property, in milliseconds.
    ///
    /// [#token]'s sibling, and it exists for the same reason with a different
    /// unit: §3 pins component metrics as token defaults, and some of those
    /// metrics are **times** rather than lengths — `tooltip`'s row says "delay
    /// 500ms show / 100ms move-between" in as many words.
    ///
    /// It is a third accessor rather than a general one for
    /// `Paints.Context.length`'s stated reason: lengths, colours and now
    /// durations are values *the cascade already parses*, and a general token
    /// reader would invite a caller to reimplement the parser. `ms` and `s` are
    /// accepted and a bare number is refused, because that is what
    /// [io.github.digitalsmile.goldberry.css.ComputedStyle#durationMillis] does
    /// for `transition` and one syntax should not have two readers
    /// ([ADR-0262]).
    ///
    /// @param name           the custom property, `--gb-` and all
    /// @param fallbackMillis what to answer when nothing defines it, or defines
    ///                       it as something that is not a duration
    double duration(String name, double fallbackMillis);

    /// The depth of this element from the root. Mostly for diagnostics.
    int depth();
}
