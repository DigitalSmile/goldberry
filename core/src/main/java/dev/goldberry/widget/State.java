package dev.goldberry.widget;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// The mutable half of a [Widget.Stateful]: what a widget remembers across
/// rebuilds, living on its element.
///
/// A state is a plain mutable object. Changes go through [#setState], which runs
/// the change now and marks the element dirty, and the tree rebuilds the element
/// once per frame, however many changes arrived.
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
///         return new Button(widget().label() + ": " + clicks, () -> setState(() -> clicks++));
///     }
/// }
/// ```
///
/// The state is created once, when the element is first mounted, and lives
/// until the element leaves the tree. [#initState] is where a subscription goes
/// and [#dispose] is where it is cancelled. Everything here runs on the UI
/// thread.
///
/// Read more:
/// [Writing a widget](https://goldberry.dev/docs/guide/writing-a-widget.html#the-three-shapes).
///
/// @param <W> the widget type this state belongs to
public abstract class State<W extends Widget> {

    private @Nullable Element element;
    private @Nullable W widget;

    /// Subclasses only. A state is created by [Widget.Stateful#createState()]
    /// and mounted by the framework; constructing one directly gives you an
    /// object that cannot [#setState].
    protected State() {}

    /// The widget this state is currently attached to.
    ///
    /// **Re-read it on every build.** A rebuild can hand the same state a new
    /// widget value, which is what happens when a parent rebuilds with different
    /// arguments, so a field captured in the constructor goes stale.
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
    /// The mutation runs **immediately**; only the rebuild is deferred. Code
    /// after `setState` sees the new value, which is what everyone expects, while
    /// the rebuild is coalesced with every other change in the same frame.
    ///
    /// Safe to call more than once before a frame; the element is dirty or it is
    /// not.
    ///
    /// @throws IllegalStateException if called before the state is mounted or
    ///         after it is disposed; both mean a callback outlived the widget
    ///         that registered it, which is a leak worth hearing about
    protected final void setState(Runnable mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (element == null) {
            throw new IllegalStateException("setState() on a state that is not mounted."
                    + " Mutate the field directly in the constructor instead.");
        }
        mutation.run();
        element.markNeedsBuild();
    }

    /// Called once, after the state is attached and before the first build.
    ///
    /// Where a subscription belongs. [#dispose()] is where it is cancelled.
    protected void initState() {}

    /// Called when the element is rebuilt with a new widget of the same type.
    ///
    /// `previous` is the widget that was in force. The default does nothing;
    /// override to react to a changed argument, such as restarting an animation
    /// when a target value changes.
    protected void didUpdateWidget(W previous) {}

    /// Called once when the element leaves the tree for good.
    ///
    /// Cancel subscriptions here. After this, [#setState] throws rather than
    /// silently doing nothing, so a callback that outlived its widget is a noisy
    /// bug rather than a quiet leak.
    protected void dispose() {}

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
