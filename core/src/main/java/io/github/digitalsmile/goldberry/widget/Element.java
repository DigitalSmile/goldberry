package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.bind.Subscription;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.StyleElement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// The persistent instantiation of a [Widget] — the second of ADR-0004's three
/// trees.
///
/// A widget is thrown away and rebuilt constantly. An element is not: it is
/// created when a widget first appears at a position, updated when a compatible
/// widget appears there next time, and unmounted only when one does not. That
/// persistence is what gives a node identity for state, focus, semantics and
/// animation to hang off.
///
/// It is also what makes CSS work. `Element` implements [StyleElement]: the
/// cascade asks it for its type, classes and ancestors, and gets answers that
/// survive a rebuild — so `:hover` on a node does not evaporate because its
/// parent re-described it.
public final class Element implements BuildContext, StyleElement {

    private final ElementTree tree;
    private Element parent;

    private Widget widget;
    private State<?> state;
    private List<Element> children = List.of();

    private boolean needsBuild = true;
    private boolean mounted = true;
    private final Set<Selector.PseudoClass> states = new LinkedHashSet<>();

    /// Live for as long as this element describes a widget with a [Widget#binding].
    private Subscription binding;

    Element(ElementTree tree, Element parent, Widget widget) {
        this.tree = tree;
        this.parent = parent;
        this.widget = widget;
        if (widget instanceof Widget.Stateful stateful) {
            this.state = stateful.createState();
            this.state.mount(this, widget);
        }
        subscribeToBinding(null);
    }

    /// The widget currently describing this element.
    public Widget widget() {
        return widget;
    }

    /// This element's state, if its widget is stateful.
    public Optional<State<?>> state() {
        return Optional.ofNullable(state);
    }

    public List<Element> children() {
        return children;
    }

    public boolean isMounted() {
        return mounted;
    }

    /// Whether this element is waiting to be rebuilt.
    public boolean needsBuild() {
        return needsBuild;
    }

    /// Marks this element as needing a rebuild.
    ///
    /// Does **not** rebuild. The tree collects dirty elements and rebuilds them
    /// once per frame, so ten `setState` calls in one handler cost one build
    /// ([ADR-0052]).
    public void markNeedsBuild() {
        if (!mounted || needsBuild) {
            return;
        }
        needsBuild = true;
        tree.markDirty(this);
    }

    // --- reconciliation ----------------------------------------------------

    /// Whether `next` can update this element in place, or whether the element
    /// has to be replaced.
    ///
    /// Type and key, which is the whole of ADR-0004's "diffed by type and key".
    /// A different type means a different kind of node; a different key means the
    /// author said these are different things even though they look alike.
    private boolean canUpdateTo(Widget next) {
        return widget.getClass() == next.getClass() && Objects.equals(widget.key(), next.key());
    }

    /// Replaces this element's widget, if it can, and rebuilds.
    void update(Widget next) {
        var previous = widget;
        widget = next;
        subscribeToBinding(previous);
        if (state != null) {
            state.update(next);
        }
        rebuild();
    }

    /// Follows the widget's [Widget#binding] — §9's `bind`.
    ///
    /// The subscription belongs to the element rather than to the widget, because
    /// the widget is a value that is thrown away and rebuilt while the element is
    /// what persists ([ADR-0004]). An element that re-subscribed on every rebuild
    /// would accumulate one listener per frame; one that never re-subscribed would
    /// keep listening to the property a *previous* widget named.
    ///
    /// Identity, not equality: two properties holding the same value are two
    /// places a value can change.
    ///
    /// @param previous the widget being replaced, or null when mounting
    private void subscribeToBinding(Widget previous) {
        var property = widget.binding();
        if (previous != null && previous.binding() == property) {
            return;
        }
        if (binding != null) {
            binding.close();
            binding = null;
        }
        if (property != null) {
            // markNeedsBuild rather than an immediate rebuild, for the reason
            // setState defers: a property that several widgets watch would
            // otherwise rebuild each of them separately, mid-change.
            binding = property.subscribe(value -> markNeedsBuild());
        }
    }

    /// Rebuilds this element's subtree from its widget.
    void rebuild() {
        needsBuild = false;
        var described = describe();
        children = reconcile(children, described);
    }

    /// What this element's widget says its children should be.
    ///
    /// The one place the three widget shapes differ, and the reason [Widget] is
    /// three interfaces rather than one with a nullable method.
    private List<Widget> describe() {
        return switch (widget) {
            case Widget.Stateful ignored -> List.of(state.build(this));
            case Widget.Stateless stateless -> List.of(stateless.build(this));
            case Widget.Leaf leaf -> leaf.children();
            default -> throw new IllegalStateException(
                    widget.getClass().getName() + " implements Widget but none of its three shapes");
        };
    }

    /// Matches existing children against new descriptions.
    ///
    /// Keyed children are matched by key wherever they moved to; unkeyed ones by
    /// position. That split is what makes a reordered list keep its state while
    /// an ordinary list stays cheap — and why the documentation on [Widget#key()]
    /// tells authors to key list items.
    private List<Element> reconcile(List<Element> existing, List<Widget> descriptions) {
        var byKey = new java.util.HashMap<Object, Element>();
        for (var child : existing) {
            if (child.widget.key() != null) {
                byKey.put(child.widget.key(), child);
            }
        }

        var reused = new java.util.IdentityHashMap<Element, Boolean>();
        var next = new ArrayList<Element>(descriptions.size());

        for (var i = 0; i < descriptions.size(); i++) {
            var description = descriptions.get(i);
            Element match = null;

            if (description.key() != null) {
                var candidate = byKey.get(description.key());
                if (candidate != null && candidate.canUpdateTo(description) && !reused.containsKey(candidate)) {
                    match = candidate;
                }
            } else if (i < existing.size()) {
                var candidate = existing.get(i);
                // An unkeyed description must not steal an element that a key
                // claimed, or a reorder would silently swap two nodes' state.
                if (candidate.widget.key() == null
                        && candidate.canUpdateTo(description)
                        && !reused.containsKey(candidate)) {
                    match = candidate;
                }
            }

            if (match != null) {
                reused.put(match, Boolean.TRUE);
                match.parent = this;
                match.update(description);
                next.add(match);
            } else {
                var created = new Element(tree, this, description);
                created.rebuild();
                next.add(created);
            }
        }

        for (var child : existing) {
            if (!reused.containsKey(child)) {
                child.unmount();
            }
        }
        return List.copyOf(next);
    }

    void unmount() {
        if (!mounted) {
            return;
        }
        mounted = false;
        // Depth first, so a child's dispose() still sees a live parent chain.
        children.forEach(Element::unmount);
        children = List.of();
        if (binding != null) {
            // Before the state's dispose(), and unconditionally: a property
            // outlives the tree it was bound into -- it is the application's --
            // and a listener left on it would keep this whole subtree alive and
            // rebuild something nobody can see.
            binding.close();
            binding = null;
        }
        if (state != null) {
            state.unmount();
        }
        tree.forget(this);
    }

    // --- BuildContext ------------------------------------------------------

    @Override
    public <T extends Widget> Optional<T> findAncestor(Class<T> type) {
        Objects.requireNonNull(type, "type");
        for (var current = parent; current != null; current = current.parent) {
            if (type.isInstance(current.widget)) {
                return Optional.of(type.cast(current.widget));
            }
        }
        return Optional.empty();
    }

    @Override
    public int depth() {
        var depth = 0;
        for (var current = parent; current != null; current = current.parent) {
            depth++;
        }
        return depth;
    }

    // --- StyleElement ------------------------------------------------------

    /// The CSS type name for this element's widget, or **null** for a widget
    /// that is not [Styled].
    ///
    /// Taken from the widget rather than stored, so `row` in a stylesheet and
    /// `row` in KDL and `Row` in Java are one name in one place.
    ///
    /// Null rather than a derived name is the important half. Deriving one for
    /// every widget would put every private composition class into the cascade
    /// as a selectable type — so an application refactoring `Wrapper` into
    /// `Shell` would break a stylesheet that never named either, and a toolkit
    /// internal would be styleable by accident.
    @Override
    public String type() {
        return widget instanceof Styled styled ? styled.cssType() : null;
    }

    @Override
    public String id() {
        return widget instanceof Styled styled ? styled.id() : null;
    }

    @Override
    public Set<String> classes() {
        return widget instanceof Styled styled ? styled.classes() : Set.of();
    }

    /// The nearest ancestor that the cascade should see.
    ///
    /// Every element is a style element here, including the ones a
    /// [Widget.Stateless] introduces purely to compose. That is deliberate and
    /// documented rather than filtered: a composition wrapper with no CSS type
    /// matches no type selector and carries no classes, so it is invisible to
    /// every selector except a descendant combinator — which is exactly the
    /// behaviour HTML has for a `<div>` nobody styled.
    @Override
    public StyleElement parent() {
        return parent;
    }

    @Override
    public boolean hasState(Selector.PseudoClass state) {
        return states.contains(state);
    }

    /// Sets or clears a pseudo-class on this element.
    ///
    /// What input will call when the pointer enters a node or focus moves. Kept
    /// on the element rather than on the widget because it must survive a
    /// rebuild: a button does not stop being hovered because its parent
    /// re-described it.
    ///
    /// @return whether this changed anything, so a caller can skip an
    ///         invalidation it does not need
    public boolean setPseudoClass(Selector.PseudoClass pseudoClass, boolean active) {
        Objects.requireNonNull(pseudoClass, "pseudoClass");
        return active ? states.add(pseudoClass) : states.remove(pseudoClass);
    }

    @Override
    public String toString() {
        return "<" + type() + (needsBuild ? " dirty" : "") + ">";
    }
}
