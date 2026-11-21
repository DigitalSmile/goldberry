package dev.goldberry.mic.view;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import dev.goldberry.mic.source.SampleListener;
import dev.goldberry.mic.source.SampleSource;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;

/// The state of a widget that draws what an analysis of a source says: it
/// makes the analysis, feeds it every block on the source's thread, and
/// rebuilds on the UI thread at most once a turn.
///
/// A widget whose source, or whose analysis' shape, changes gets a fresh
/// analysis: what the old one heard was of another source.
///
/// @param <W> the widget
/// @param <A> the analysis, fed on the source's thread and read on the UI's
abstract class Listening<W extends Widget, A extends SampleListener> extends State<W> {

    private final AtomicBoolean pending = new AtomicBoolean();
    private @Nullable AutoCloseable subscription;
    private @Nullable A analysis;

    /// The source `widget` follows.
    abstract SampleSource source(W widget);

    /// A fresh analysis for `widget`, of samples in its source's format.
    abstract A analysis(W widget);

    /// Whether `current` needs another analysis than `previous` had. A
    /// different source always does; a subclass adds what shapes its analysis.
    boolean restarts(W previous, W current) {
        return source(previous) != source(current);
    }

    /// The analysis feeding this state now.
    final A analysis() {
        return Objects.requireNonNull(analysis, "not mounted");
    }

    @Override
    protected void initState() {
        start();
    }

    @Override
    protected void didUpdateWidget(W previous) {
        if (restarts(previous, widget())) {
            stop();
            start();
        }
    }

    @Override
    protected void dispose() {
        stop();
        super.dispose();
    }

    private void start() {
        var fresh = analysis(widget());
        analysis = fresh;
        var redraw = Redraw.coalesced(pending, Redraw.UI, this::refresh);
        subscription = source(widget()).onSamples(block -> {
            fresh.onSamples(block);
            redraw.run();
        });
    }

    private void refresh() {
        if (isMounted()) {
            setState(() -> {});
        }
    }

    private void stop() {
        var current = subscription;
        subscription = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                throw new IllegalStateException("the source refused to let go of a listener", e);
            }
        }
    }
}
