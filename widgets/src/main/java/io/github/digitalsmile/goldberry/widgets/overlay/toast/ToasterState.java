package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/// The queue: what is showing, what is waiting, and every clock in the widget.
///
/// ## Why the state holds so much when the toast holds none
///
/// A [Toast] is a value with no lifetime in it. Everything that happens to one
/// happens here — it arrives, it counts down, it stops counting down while the
/// pointer is on it, it fades, and the one behind it comes forward. None of that
/// is describable, and none of it is the application's: §7 asks for "queued,
/// timeout with hover-pause" and an application that had to implement those
/// would be writing a toast stack rather than using one
/// ([ADR-0177](../../../../../../../../book/src/adr/0177-a-toast-is-a-queue-and-the-stack-is-the-widget.md)).
///
/// ## Every duration here is a frame-clock reading
///
/// The pause is the reason. `Host.after` gives a timer and no way to ask how much
/// of it has run, so pausing one and resuming it needs a clock — and the only
/// clock a widget has is the one `render` is handed. So [ToasterBox] reports
/// `nowMillis` on every frame and this remembers the last reading; `carousel`
/// reads the motion preference the same way and for the same reason.
final class ToasterState extends State<Toaster> {

    /// §3: "in: slide 16px from edge + `opacity`, **overlay**" — 240ms.
    static final double ENTER_MILLIS = 240;

    /// §3: "out: `opacity` **base**" — 160ms.
    static final double EXIT_MILLIS = 160;

    /// One toast on screen, and everything that is true of it but not of the
    /// value it is showing.
    static final class Entry {

        final int number;
        final Toast toast;

        /// Arriving, leaving, or neither.
        Phase phase = new Phase(Phase.Kind.ENTERING, ENTER_MILLIS);

        /// The timer that ends its stay, or null while paused, while it is
        /// leaving, or for a toast that never expires.
        EventLoop.Timer pending;

        /// How much of its stay is left, in milliseconds. Counted down rather
        /// than counted up, so a resumed toast gets the time it had left and not
        /// the time it started with.
        double remaining;

        /// When the running timer was started, on the frame clock, or `NaN` while
        /// it is not running.
        double startedAt = Double.NaN;

        /// Whether the pointer is on it — §7's "hover-pause".
        boolean hovered;

        Entry(int number, Toast toast) {
            this.number = number;
            this.toast = toast;
            this.remaining = toast.timeout().toMillis();
        }

        boolean isLeaving() {
            return phase.kind() == Phase.Kind.LEAVING;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    /// Waiting to be shown, oldest first — §7's "queued".
    private final List<Toast> waiting = new ArrayList<>();

    /// Never reused, so no element ever inherits a departing toast's key.
    private int raised;

    /// The last frame's clock reading. See the class note.
    private double now;

    private Host host;

    @Override
    protected void initState() {
        widget().controller().attached = this;
    }

    @Override
    protected void didUpdateWidget(Toaster previous) {
        if (previous.controller() != widget().controller()) {
            if (previous.controller().attached == this) {
                previous.controller().attached = null;
            }
            widget().controller().attached = this;
        }
    }

    @Override
    protected void dispose() {
        // A controller outlives its stack -- an application holds it -- so one
        // still pointing here would raise toasts into a tree that is gone.
        if (widget().controller().attached == this) {
            widget().controller().attached = null;
        }
        for (var entry : entries) {
            cancel(entry);
        }
        entries.clear();
        waiting.clear();
        super.dispose();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var boxes = new ArrayList<Widget>(entries.size());
        for (var entry : entries) {
            boxes.add(new ToastBox(entry.toast, entry.number, widget().corner(), entry.phase,
                    entry.isLeaving(),
                    hovered -> hover(entry, hovered),
                    () -> pressed(entry)));
        }
        return new ToasterBox(boxes, widget().corner(), this::frame);
    }

    /// What every frame tells this state: what time it is.
    private void frame(double millis) {
        now = millis;
    }

    // --- what the controller asks for ----------------------------------------

    void show(Toast toast) {
        if (showingCount() >= widget().maximum()) {
            // Queued rather than dropped and rather than shown: §7 says "queued",
            // and a burst of six notifications is exactly the case the word is
            // there for. They arrive as room appears, in the order they happened.
            waiting.add(toast);
            return;
        }
        setState(() -> {
            var entry = new Entry(++raised, toast);
            entries.add(entry);
            schedule(entry);
        });
    }

    void clear() {
        waiting.clear();
        setState(() -> {
            for (var entry : List.copyOf(entries)) {
                leave(entry);
            }
        });
    }

    List<Toast> showing() {
        return entries.stream().map(entry -> entry.toast).toList();
    }

    /// How many are *staying* — a toast on its way out has given up its place, so
    /// the next one in the queue may take it and the two overlap for 160ms.
    private int showingCount() {
        return (int) entries.stream().filter(entry -> !entry.isLeaving()).count();
    }

    // --- the clock ------------------------------------------------------------

    /// Starts or restarts an entry's stay.
    private void schedule(Entry entry) {
        cancel(entry);
        if (!entry.toast.expires() || entry.isLeaving() || entry.hovered || host == null) {
            return;
        }
        entry.startedAt = now;
        entry.pending = host.after(Duration.ofMillis(Math.max(1, (long) entry.remaining)),
                () -> {
                    entry.pending = null;
                    setState(() -> leave(entry));
                });
    }

    /// Stops the clock and banks what is left of it.
    private void cancel(Entry entry) {
        if (entry.pending != null) {
            entry.pending.cancel();
            entry.pending = null;
            if (!Double.isNaN(entry.startedAt)) {
                entry.remaining = Math.max(0, entry.remaining - (now - entry.startedAt));
            }
        }
        entry.startedAt = Double.NaN;
    }

    /// §7's hover-pause. The clock stops while the pointer is on a toast and
    /// **resumes** rather than restarting: a toast you glanced at should not owe
    /// you another five seconds.
    private void hover(Entry entry, boolean over) {
        if (entry.hovered == over || entry.isLeaving()) {
            return;
        }
        entry.hovered = over;
        if (over) {
            cancel(entry);
        } else {
            schedule(entry);
        }
    }

    /// The action button. It runs the application's handler **and** takes the
    /// toast away, which is `Menus`' rule for a menu command: choosing one is
    /// finishing with the thing that offered it.
    private void pressed(Entry entry) {
        if (entry.isLeaving()) {
            return;
        }
        var action = entry.toast.onPress();
        setState(() -> leave(entry));
        if (action != null) {
            action.run();
        }
    }

    // --- going ----------------------------------------------------------------

    /// Starts an entry's exit, and lets the next one in the queue in.
    ///
    /// Called from inside a `setState` by everything that ends a toast.
    private void leave(Entry entry) {
        if (entry.isLeaving()) {
            return;
        }
        cancel(entry);
        entry.phase = new Phase(Phase.Kind.LEAVING, EXIT_MILLIS);
        if (host == null) {
            // No window, so no clock: it goes now. Every widget test that does
            // not ask for a host is in this case.
            remove(entry);
            return;
        }
        host.after(Duration.ofMillis((long) EXIT_MILLIS), () -> setState(() -> remove(entry)));
        promote();
    }

    private void remove(Entry entry) {
        entries.remove(entry);
        promote();
    }

    /// Brings the oldest waiting toast forward, if there is room.
    private void promote() {
        while (!waiting.isEmpty() && showingCount() < widget().maximum()) {
            var next = waiting.removeFirst();
            var entry = new Entry(++raised, next);
            entries.add(entry);
            schedule(entry);
        }
    }
}
