package dev.goldberry.example.ui.windows;

import java.util.stream.IntStream;

import dev.goldberry.Goldberry;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.text.Text;

/// Work started off the UI thread with `Goldberry.async`, and its result
/// delivered back on it, where the card may set its state with no hand-off.
///
/// Read more: [Threads](https://goldberry.dev/docs/guide/windows.html#threads).
public record ThreadsCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "windows-threads";

    /// How far the card counts primes: enough to take a moment.
    static final int LIMIT = 3_000_000;

    @Override
    public State<?> createState() {
        return new ThreadsState();
    }

    /// What the work found, and where it ran.
    record Counted(long primes, boolean virtual, long millis) {}

    /// The work: counted on whichever thread runs it.
    static Counted count(int limit) {
        var started = System.nanoTime();
        var primes = IntStream.rangeClosed(2, limit).filter(ThreadsCard::prime).count();
        return new Counted(primes, Thread.currentThread().isVirtual(), (System.nanoTime() - started) / 1_000_000);
    }

    private static boolean prime(int n) {
        for (var d = 2; (long) d * d <= n; d++) {
            if (n % d == 0) {
                return false;
            }
        }
        return true;
    }

    static final class ThreadsState extends State<ThreadsCard> {

        private String answer = "Nothing counted yet.";

        private boolean working;

        @Override
        public Widget build(BuildContext context) {
            return new ShowcaseCard(
                            ID,
                            "Threads",
                            "There is one UI thread. Goldberry.async runs work on a virtual thread and delivers the"
                                    + " result on the UI thread, so the callback may touch the window. The window"
                                    + " keeps answering while the work runs.",
                            DocLink.to("guide/windows", "threads"))
                    .of(
                            new Button("Count the primes under three million", this::start)
                                    .disabled(working)
                                    .id("threads-start"),
                            new Text(
                                    answer, Attributes.NONE.id("threads-answer").classes("readout")));
        }

        private void start() {
            // Only a running frame loop has a UI thread to come back to; a picture
            // or a test has none, and starting one here would open a backend.
            if (!Goldberry.isUiThread()) {
                setState(() -> answer = "No frame loop is running here, so there is no UI thread to come back to.");
                return;
            }
            setState(() -> {
                working = true;
                answer = "Counting on a virtual thread…";
            });
            var _ = Goldberry.async(() -> count(LIMIT)).whenComplete((counted, failure) -> {
                if (!isMounted()) {
                    return;
                }
                setState(() -> {
                    working = false;
                    answer = failure != null
                            ? "The work failed: " + failure.getMessage()
                            : counted.primes() + " primes in " + counted.millis() + " ms, on a "
                                    + (counted.virtual() ? "virtual" : "platform") + " thread; delivered on the UI"
                                    + " thread: " + Goldberry.isUiThread();
                });
            });
        }
    }
}
