package dev.goldberry.example.ui.shell;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.Overlay;

/// Takes the reader to a card: scrolls it to the top of its viewport, then
/// rings it for a moment so the eye lands on it.
///
/// One per window, because one ring at a time is the point: a second jump
/// takes the first ring away.
///
/// Read more: [Scroll](https://goldberry.dev/docs/layout/scroll.html).
public final class CardSpotlight {

    /// How long a viewport takes to glide to a card before it is ringed.
    static final Duration SETTLED = Duration.ofMillis(320);

    /// How long the ring stays.
    static final Duration SHOWN = Duration.ofMillis(1400);

    private @Nullable Overlay ring;

    /// Scrolls to the node `id` and rings it.
    ///
    /// @return whether the node was found; a card its screen has not built is
    ///         not, and nothing happens
    public boolean show(Host host, String id) {
        if (!Reveal.atTop(host, id)) {
            return false;
        }
        host.after(
                SETTLED,
                () -> Reveal.painted(host, id).ifPresent(target -> {
                    clear();
                    var shown = host.fill(new Spotlight(target));
                    ring = shown;
                    host.after(SHOWN, () -> {
                        shown.remove();
                        if (ring == shown) {
                            ring = null;
                        }
                    });
                }));
        return true;
    }

    /// Takes the ring away now, if one is up.
    public void clear() {
        var shown = ring;
        ring = null;
        if (shown != null) {
            shown.remove();
        }
    }
}
