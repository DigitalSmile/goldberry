package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

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

    /// §3: "siblings reflow via `translate` **base**" — the same 160ms, and the
    /// same number [Phase] already calls base.
    static final double REFLOW_MILLIS = Phase.DURATION_MILLIS;

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

        /// How tall it came out on the last frame that painted it, in logical
        /// pixels, or 0 for one that has never been drawn.
        ///
        /// Banked rather than asked for, because by the time it is wanted the
        /// toast is gone: the hole a departing toast leaves is exactly this tall
        /// and there is nothing left to measure ([ToastBox#measured]).
        double height;

        /// Where it is on its way to because a sibling went, or null when it is
        /// where it belongs.
        ToastBox.Reflow reflow;

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

    /// The space `toaster` keeps between two toasts, in logical pixels, as the
    /// cascade last resolved it — see [ToasterBox.OnFrame].
    private double gap;

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
            boxes.add(new ToastBox(
                    entry.toast,
                    entry.number,
                    widget().corner(),
                    entry.phase,
                    entry.isLeaving(),
                    entry.reflow,
                    hovered -> hover(entry, hovered),
                    () -> pressed(entry),
                    () -> dismissed(entry),
                    height -> entry.height = height));
        }
        return new ToasterBox(boxes, widget().corner(), this::frame);
    }

    /// What every frame tells this state: what time it is, and how far apart the
    /// stylesheet is keeping the toasts.
    private void frame(double millis, double columnGap) {
        now = millis;
        gap = columnGap;
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
        entry.pending = host.after(Duration.ofMillis(Math.max(1, (long) entry.remaining)), () -> {
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

    /// A click on the plate — §7's missing way out, see [ToastBox#onPointer].
    ///
    /// No handler to run and nothing to report: dismissing a notification is not
    /// an answer to it, which is exactly what tells it apart from the action
    /// button above.
    private void dismissed(Entry entry) {
        if (entry.isLeaving()) {
            return;
        }
        setState(() -> leave(entry));
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
        var index = entries.indexOf(entry);
        entries.remove(entry);
        if (index >= 0) {
            closeTheGap(index, entry.height);
        }
        promote();
    }

    /// §3's **sibling reflow**: the toasts left behind travel to where the stack
    /// now puts them rather than jumping there.
    ///
    /// ## Only the older ones move
    ///
    /// Which is not obvious, and falls out of where the column is pinned. A
    /// `toaster` sits in a corner and is as tall as its contents, so it is
    /// anchored by whichever end is against the corner — and that end is the
    /// **newest** toast, by the `column` / `column-reverse` rule `controls.css`
    /// uses to put the newest nearest the corner. Take one out of the middle and
    /// everything between it and the corner is still exactly where it was; it is
    /// the far side of the hole, the older half, that moves in to close it.
    ///
    /// So this walks the entries *before* the departed one, and every one of them
    /// travels the same distance: the height of the hole plus the gap it was
    /// keeping. Which direction that is, is the corner's, and [ToastBox] reads it
    /// there rather than being handed a signed number.
    ///
    /// **A toast on its way out travels too.** It is still in the column for
    /// another 160ms, and one that stood still while the column moved under it
    /// would be the only thing on screen that was not part of the stack.
    private void closeTheGap(int index, double height) {
        if (!(height > 0)) {
            // Nothing to close. A toast dismissed before a frame ever painted it
            // has no height — `Measured` is last frame's ([ADR-0117]) — and a
            // stack that guessed one would move its survivors somewhere no toast
            // had ever been. The check is on the **height** and not on the total,
            // because the gap alone is a real number and would send the whole
            // stack 8px in a direction nothing asked for.
            return;
        }
        var distance = height + gap;
        // Everything before the hole. `index` came from the list this entry was
        // just taken out of, so it is the count of the toasts older than it.
        for (var older : entries.subList(0, index)) {
            older.reflow = travel(older, distance);
        }
    }

    /// A fresh journey for one toast, **adding** to whatever it had left of the
    /// last one.
    ///
    /// Adding, and not replacing. Two toasts going in quick succession are two
    /// holes, and a survivor that restarted for the second would arrive short by
    /// however far it still had to go on the first — it would settle a toast's
    /// height above where it belongs and stay there. That is not a corner case:
    /// it is what [#clear] looks like, and what a burst timing out one after
    /// another looks like.
    ///
    /// This is the one thing §1.7's `AnimationController` was still owed for
    /// (`book/src/TODO.md`), and it is four lines: read what is left, add the new
    /// distance, start again. A controller for a single consumer would be a
    /// mechanism where an arithmetic is
    /// ([ADR-0178](../../../../../../../../book/src/adr/0178-a-stack-closes-its-own-hole.md)).
    private ToastBox.Reflow travel(Entry entry, double distance) {
        var left = entry.reflow == null
                ? 0
                : entry.reflow.distance() * (1 - entry.reflow.phase().progressAt(now));
        return new ToastBox.Reflow(left + distance, new Phase(Phase.Kind.ENTERING, REFLOW_MILLIS));
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
