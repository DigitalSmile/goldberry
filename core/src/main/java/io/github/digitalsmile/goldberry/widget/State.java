package io.github.digitalsmile.goldberry.widget;

import java.util.Objects;

/// The mutable half of a [Widget.Stateful], living on its element.
///
/// This is the API ADR-0004 called "the largest gap in the current design", and
/// [ADR-0052] is where the shape of it is argued. The short version: state is a
/// plain mutable object, changes go through [#setState], and `setState` marks the
/// element dirty rather than rebuilding on the spot.
///
/// ```java
/// record Counter(String label) implements Widget.Stateful {
///     public State<?> createState() { return new CounterState(); }
/// }
///
/// final class CounterState extends State<Counter> {
///     private int clicks;
///
///     public Widget build(BuildContext context) {
///         return new Label(widget().label() + ": " + clicks);
///     }
///
///     void onClick() {
///         setState(() -> clicks++);
///     }
/// }
/// ```
///
/// @param <W> the widget type this state belongs to
public abstract class State<W extends Widget> {

    private Element element;
    private W widget;

    /// Subclasses only. A state is created by [Widget.Stateful#createState()]
    /// and mounted by the framework; constructing one directly gives you an
    /// object that cannot [#setState].
    protected State() {
    }

    /// The widget this state is currently attached to.
    ///
    /// **Re-read it on every build.** A rebuild can hand the same state a new
    /// widget value — that is what happens when a parent rebuilds with different
    /// arguments — so a field captured in the constructor goes stale.
    protected final W widget() {
        if (widget == null) {
            throw new IllegalStateException("this state is not mounted yet");
        }
        return widget;
    }

    /// Describes the UI for the current widget and state.
    ///
    /// Called on the UI thread, and must be pure with respect to everything
    /// except this state's own fields.
    public abstract Widget build(BuildContext context);

    /// Runs `mutation` and marks this element as needing a rebuild.
    ///
    /// The mutation runs **immediately**; only the rebuild is deferred. That
    /// ordering is deliberate: code after `setState` sees the new value, which is
    /// what everyone expects, while the rebuild is coalesced with every other
    /// change in the same frame.
    ///
    /// Safe to call more than once before a frame; the element is dirty or it is
    /// not.
    ///
    /// @throws IllegalStateException if called before the state is mounted or
    ///         after it is disposed — both mean a callback outlived the widget
    ///         that registered it, which is a leak worth hearing about
    protected final void setState(Runnable mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (element == null) {
            throw new IllegalStateException(
                    "setState() on a state that is not mounted."
                            + " Mutate the field directly in the constructor instead.");
        }
        mutation.run();
        element.markNeedsBuild();
    }

    /// Called once, after the state is attached and before the first build.
    ///
    /// Where a subscription belongs. [#dispose()] is where it is cancelled.
    protected void initState() {
    }

    /// Called when the element is rebuilt with a new widget of the same type.
    ///
    /// `previous` is the widget that was in force. The default does nothing;
    /// override to react to a changed argument — restarting an animation when a
    /// target value changes, say.
    protected void didUpdateWidget(W previous) {
    }

    /// Called once when the element leaves the tree for good.
    ///
    /// Cancel subscriptions here. After this, [#setState] throws rather than
    /// silently doing nothing, so a callback that outlived its widget is a noisy
    /// bug rather than a quiet leak.
    protected void dispose() {
    }

    /// Whether this state is attached to a live element.
    public final boolean isMounted() {
        return element != null;
    }

    // --- framework side ---------------------------------------------------

    @SuppressWarnings("unchecked")
    final void mount(Element element, Widget widget) {
        this.element = element;
        this.widget = (W) widget;
        initState();
    }

    @SuppressWarnings("unchecked")
    final void update(Widget next) {
        var previous = this.widget;
        this.widget = (W) next;
        if (previous != null && !previous.equals(next)) {
            didUpdateWidget(previous);
        }
    }

    final void unmount() {
        dispose();
        element = null;
        widget = null;
    }
}
