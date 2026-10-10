package dev.goldberry.widget.attr;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.drop.Drop;
import dev.goldberry.input.drop.DropTarget;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Styled;

/// `id`, `class` and the reconciler's key — what every widget carries and no
/// widget decides — together with the tooltip, context menu, accessible name,
/// hover hooks and drag and drop that attach to any node the same way.
///
/// Shared by every widget in the toolkit: the names markup gives a node, in one
/// value. A record cannot extend a class, so this is the one piece of boilerplate
/// each widget repeats — three accessors — rather than a hierarchy they cannot
/// have. In Java the value is built by chaining; in markup [#of(KdlNode)] reads
/// it off the node.
///
/// ```java
/// new Badge("3", Attributes.NONE.id("unread").classes("pill").tooltip("Unread messages"))
/// ```
///
/// ## Why it is here and not in the catalog
///
/// It lives in `:core`, where the primitives do not, because it is not a widget
/// — it is part of the widget **contract**. [Styled] asks a widget for its `id`
/// and its classes and the cascade matches on the answers; [Widget#key()] is
/// what the reconciler pairs two builds by. A widget in an application's own
/// module implements the same three methods, and it should not have to depend
/// on the catalog to hold them in a value.
///
/// [#of(KdlNode)] is here for the same reason: parsing `id` and `class` off a
/// markup node is the inflater's contract, and the inflater is `:core`'s.
///
/// Read more:
/// [Attributes and binding](https://goldberry.dev/docs/guide/writing-a-widget.html#attributes-and-binding).
public record Attributes(
        @Nullable String id,
        Set<String> classes,
        @Nullable Object key,
        @Nullable String tooltip,
        @Nullable String contextMenu,
        @Nullable String name,
        @Nullable Runnable onPointerEnter,
        @Nullable Runnable onPointerExit,
        @Nullable Object draggable,
        @Nullable DropTarget dropTarget) {

    /// No id, no classes, no key, no tooltip, no name — what a widget built in
    /// Java gets unless it says otherwise.
    public static final Attributes NONE =
            new Attributes(null, Set.of(), null, null, null, null, null, null, null, null);

    /// Written out so that the parameters taking null for a default can say so.
    public Attributes(
            @Nullable String id,
            @Nullable Set<String> classes,
            @Nullable Object key,
            @Nullable String tooltip,
            @Nullable String contextMenu,
            @Nullable String name,
            @Nullable Runnable onPointerEnter,
            @Nullable Runnable onPointerExit,
            @Nullable Object draggable,
            @Nullable DropTarget dropTarget) {
        classes = Set.copyOf(classes == null ? Set.of() : classes);
        this.id = id;
        this.classes = classes;
        this.key = key;
        this.tooltip = tooltip;
        this.contextMenu = contextMenu;
        this.name = name;
        this.onPointerEnter = onPointerEnter;
        this.onPointerExit = onPointerExit;
        this.draggable = draggable;
        this.dropTarget = dropTarget;
    }

    /// The eight without a drag, kept for the reason the three-argument form is.
    public Attributes(
            @Nullable String id,
            @Nullable Set<String> classes,
            @Nullable Object key,
            @Nullable String tooltip,
            @Nullable String contextMenu,
            @Nullable String name,
            @Nullable Runnable onPointerEnter,
            @Nullable Runnable onPointerExit) {
        this(id, classes, key, tooltip, contextMenu, name, onPointerEnter, onPointerExit, null, null);
    }

    /// The six without the hover hooks, kept for the reason the three-argument
    /// form is.
    public Attributes(
            @Nullable String id,
            Set<String> classes,
            @Nullable Object key,
            @Nullable String tooltip,
            @Nullable String contextMenu,
            @Nullable String name) {
        this(id, classes, key, tooltip, contextMenu, name, null, null);
    }

    /// The three that every widget had before a tooltip was one of them.
    ///
    /// Kept because `new Attributes(id, classes, key)` appears in every widget in
    /// the catalog and in most of its tests, and because a fourth positional
    /// argument on all of them would be four hundred edits to say `null`.
    public Attributes(@Nullable String id, Set<String> classes, @Nullable Object key) {
        this(id, classes, key, null, null, null);
    }

    /// The five without the accessible name, kept for the reason the
    /// three-argument form is.
    public Attributes(
            @Nullable String id,
            Set<String> classes,
            @Nullable Object key,
            @Nullable String tooltip,
            @Nullable String contextMenu) {
        this(id, classes, key, tooltip, contextMenu, null);
    }

    /// This, with the text a tooltip would show — markup's `tooltip="…"`, which
    /// attaches to **any** widget.
    ///
    /// Here rather than on each widget because that is what "any widget" means:
    /// a tooltip is not a property of being a button, and a catalog where each
    /// control had to remember to carry one would have thirty chances to forget.
    ///
    /// Read more: [Tooltips](https://goldberry.dev/docs/components/overlays.html#tooltips).
    public Attributes tooltip(String text) {
        return new Attributes(
                id,
                classes,
                key,
                text.isBlank() ? null : text,
                contextMenu,
                name,
                onPointerEnter,
                onPointerExit,
                draggable,
                dropTarget);
    }

    /// This, with the name of the menu a right-click should open — markup's
    /// `context-menu="menuId"`, which like a tooltip attaches to **any** widget.
    ///
    /// A *name*, not a menu: what the name means is a registry's, exactly as it is
    /// for `press=` and `icon=`. A widget holding a menu would be a widget holding
    /// a thing that has to be opened, and opening needs a window.
    ///
    /// Read more: [Context menus](https://goldberry.dev/docs/guide/input.html#context-menus).
    public Attributes contextMenu(String menuId) {
        return new Attributes(
                id,
                classes,
                key,
                tooltip,
                menuId.isBlank() ? null : menuId,
                name,
                onPointerEnter,
                onPointerExit,
                draggable,
                dropTarget);
    }

    /// This, with a different `id` — **and the same id as the key**.
    ///
    /// Keying by id is what lets the element tree match a rebuilt description to
    /// the element that already exists, so a focused button keeps its focus
    /// across a `setState` that replaced every widget in the window. An id that
    /// did not double as a key would be an id that looks like it identifies the
    /// node and does not, which is [#of(KdlNode)]'s rule too.
    ///
    /// Null clears both.
    public Attributes id(@Nullable String id) {
        return new Attributes(
                id, classes, id, tooltip, contextMenu, name, onPointerEnter, onPointerExit, draggable, dropTarget);
    }

    /// This, with a different set of classes.
    public Attributes classes(String... names) {
        return new Attributes(
                id,
                Set.of(names),
                key,
                tooltip,
                contextMenu,
                name,
                onPointerEnter,
                onPointerExit,
                draggable,
                dropTarget);
    }

    /// This, with a key that is not the id — for a list item whose identity is a
    /// row of a model rather than a name in a document.
    public Attributes key(Object key) {
        return new Attributes(
                id, classes, key, tooltip, contextMenu, name, onPointerEnter, onPointerExit, draggable, dropTarget);
    }

    /// This, with the text a reader should announce it as — markup's `name="…"`,
    /// which like a tooltip attaches to **any** widget.
    ///
    /// Here rather than on each widget for the reason a tooltip is here: every
    /// widget is asked for a role *and a name*, and a catalog where each control
    /// remembered its own would have thirty chances to forget. It is also the
    /// only way to name the thing that most needs it — an **icon-only** control,
    /// whose label is the empty string precisely because there is nothing on
    /// screen to read.
    ///
    /// A widget that derives a name from what it is showing keeps doing so; this
    /// wins when it is set, because an author writing one has said something the
    /// widget could not work out.
    ///
    /// Null or blank clears it.
    ///
    /// Read more:
    /// [A role and a name](https://goldberry.dev/docs/guide/writing-a-widget.html#semantics-a-role-and-a-name).
    public Attributes name(@Nullable String text) {
        return new Attributes(
                id,
                classes,
                key,
                tooltip,
                contextMenu,
                text == null || text.isBlank() ? null : text,
                onPointerEnter,
                onPointerExit,
                draggable,
                dropTarget);
    }

    /// This, with something to run when the pointer **enters** this node's
    /// subtree.
    ///
    /// The router derives
    /// [dev.goldberry.input.event.PointerEvent.Kind#ENTERED]
    /// and `EXITED` for every node, and this is how a widget that is not a menu
    /// hears them. A hover-hold preview on a search result is the same fact about
    /// the pointer as a menu bar opening on hover, and choosing a widget for its
    /// event hook would be the tail wagging the dog.
    ///
    /// Here rather than as a `HoverRegion` widget for the reason a tooltip is
    /// here: this is a cross-cutting node property, it composes with **any**
    /// widget rather than wrapping one, and a container that existed only to
    /// report an event would be a second way to spell something the node already
    /// has.
    ///
    /// ## It is the subtree, not the node
    ///
    /// `:hover` applies to a node and every ancestor of it — `.card:hover .title`
    /// has to work — and this is the same walk. So a hook on a `row` fires once
    /// when the pointer arrives anywhere inside it and once when it leaves
    /// altogether, and moving between the row's own children raises nothing.
    ///
    /// ## It consumes nothing
    ///
    /// A press that lands inside still belongs to whatever is inside. The event
    /// these are derived from is synthetic and is delivered to the node rather
    /// than down a chain, so there is nothing here that could swallow a click.
    ///
    /// @param action what to run, or null to carry none
    public Attributes onPointerEnter(@Nullable Runnable action) {
        return new Attributes(
                id, classes, key, tooltip, contextMenu, name, action, onPointerExit, draggable, dropTarget);
    }

    /// This, with something to run when the pointer **leaves** this node's
    /// subtree — [#onPointerEnter]'s other half, and its rules exactly.
    ///
    /// **A node unmounted under the pointer still hears its exit**, which is the
    /// case a hover-hold has to survive. The router lets go of an element that
    /// leaves the tree and re-hit-tests against the frame just painted, and that
    /// is the same walk these are raised from — so the exit arrives on the frame
    /// the router notices rather than never.
    ///
    /// What is still not guaranteed is a teardown with no frame after it: a window
    /// closing takes its tree with it and nobody is told. A caller holding a timer
    /// cancels it on dispose as well, which is what a `tooltip` already does.
    public Attributes onPointerExit(@Nullable Runnable action) {
        return new Attributes(
                id, classes, key, tooltip, contextMenu, name, onPointerEnter, action, draggable, dropTarget);
    }

    /// This, able to be picked up and dragged onto a [#dropTarget] elsewhere in
    /// the window, carrying `payload` there.
    ///
    /// ```java
    /// new Card(ticket.title()).draggable(ticket)
    /// ```
    ///
    /// The pointer router does the rest. A press on the node becomes a drag once
    /// the pointer has moved a few logical pixels with the button down, so a
    /// press that does not move is still a click. A control inside the node that
    /// consumes the press keeps it: a slider in a draggable card drags its thumb,
    /// and the card is picked up from anywhere else. While the drag lasts the
    /// node's own painted box follows the pointer, faded, and `Escape` puts it
    /// back. With the keyboard, `Space` on a focused draggable picks it up.
    ///
    /// The payload is the object itself, handed to the target as it is: the drag
    /// never leaves the application, and is not the platform's drag and drop.
    ///
    /// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#dropped-files-and-text).
    ///
    /// @param payload what a target is handed, or null to make the node not draggable
    public Attributes draggable(@Nullable Object payload) {
        return new Attributes(
                id, classes, key, tooltip, contextMenu, name, onPointerEnter, onPointerExit, payload, dropTarget);
    }

    /// This, taking what is dragged onto it when `accepts` says yes, and doing
    /// `onDrop` with it.
    ///
    /// The node matches `:drag-over` while a drag it accepts is over it, and a
    /// drag it refuses passes it by on the way to an ancestor that takes it.
    /// [Drop#at()] is measured from the corner of the node's content box, which
    /// for a `canvas` is where its painter draws from.
    public Attributes dropTarget(Predicate<Object> accepts, Consumer<Drop> onDrop) {
        return dropTarget(DropTarget.of(accepts, onDrop));
    }

    /// This, taking drags as `target` says — the form with the `whileOver` and
    /// `onLeave` hooks, which a `canvas` drawing its own drop indicator wants.
    ///
    /// @param target what to accept and what to do, or null to take nothing
    public Attributes dropTarget(@Nullable DropTarget target) {
        return new Attributes(
                id, classes, key, tooltip, contextMenu, name, onPointerEnter, onPointerExit, draggable, target);
    }

    /// Parses `id` and `class` off a KDL node, `class` being space-separated as
    /// in HTML.
    ///
    /// The id doubles as the key, which is what makes a node with an id survive a
    /// reorder: two builds of the same document pair their `#save` buttons by
    /// name rather than by position.
    public static Attributes of(KdlNode node) {
        var id = node.stringProperty("id");
        var classes = new LinkedHashSet<String>();
        var raw = node.stringProperty("class");
        if (raw != null) {
            // Split on runs of whitespace after a trim, so there is no trailing
            // empty to drop -- and the loop skips one regardless.
            @SuppressWarnings("StringSplitter")
            var names = raw.trim().split("\\s+");
            for (var name : names) {
                if (!name.isEmpty()) {
                    classes.add(name);
                }
            }
        }
        // No hover hooks: a `Runnable` is not a KDL value, and the registry that
        // turns `press="app.save"` into one is the inflater's `Wiring` rather
        // than this method's — see [#onPointerEnter].
        return new Attributes(
                id,
                classes,
                id,
                node.stringProperty("tooltip"),
                node.stringProperty("context-menu"),
                node.stringProperty("name"));
    }
}
