package io.github.digitalsmile.goldberry.example.webpump;

/// The document [PumpProbe] measures: a CSS animation and three counters.
///
/// ## Why three clocks and not one
///
/// "The page animates badly" has three separable causes that no single number
/// distinguishes, and they belong to three different people — see
/// `docs/web-pump-probe.md` for the table and [FpsLog.Verdict] for the
/// judgement drawn from it.
///
/// The one worth explaining here is `timeline`. It counts how often
/// `document.timeline` advances, which is one tick per *rendering update*, and
/// it is sampled **from the `setInterval` timer rather than from a frame
/// callback**. That is the whole trick: it stays readable on an engine whose
/// frame callbacks are the broken thing. Measured from inside
/// `requestAnimationFrame`, a stalled compositor and a dead animation
/// controller produce the same reading, and they have nothing to do with each
/// other.
///
/// ## Why the box moves in CSS
///
/// A CSS animation rides the rendering update, so the box keeps gliding on an
/// engine where `requestAnimationFrame` never fires. That puts the two failures
/// side by side on screen: a smooth box above a `raf` reading of zero *is* the
/// finding, and it needs no log to see.
final class ProbeDocument {

    private ProbeDocument() {}

    /// The name the page reports through. Bound by [PumpProbe].
    static final String CALLBACK = "goldberryFps";

    /// How often the page reports, in milliseconds. One second is
    /// [io.github.digitalsmile.goldberry.stats.FrameRing]'s window for the same
    /// reason: short enough to see a change, long enough that one slow frame
    /// does not become the reading.
    static final int REPORT_MILLIS = 1_000;

    /// The document, with the callback name and reporting interval woven in so
    /// there is one declaration of each rather than two that must agree.
    static String html() {
        return """
                <!doctype html>
                <meta charset="utf-8">
                <title>pump probe</title>
                <style>
                  html, body { margin: 0; height: 100%%; background: #101014; overflow: hidden; }
                  /* Moved by CSS rather than by script, so the box keeps gliding
                     even on an engine whose frame callbacks never arrive — which
                     makes the two failures visible side by side: a smooth box
                     above a `raf` reading of zero is the whole finding. */
                  #box {
                    position: absolute; top: 40%%; width: 120px; height: 120px;
                    border-radius: 16px; will-change: transform;
                    background: linear-gradient(135deg, #7c5cff, #31d0aa);
                    animation: glide 2s ease-in-out infinite alternate;
                  }
                  @keyframes glide {
                    from { transform: translateX(0); }
                    to   { transform: translateX(calc(100vw - 120px)); }
                  }
                  #fps {
                    position: absolute; left: 16px; top: 16px; color: #e8e8f0;
                    font: 600 30px/1.2 system-ui, sans-serif;
                  }
                </style>
                <div id="box"></div>
                <div id="fps">measuring&hellip;</div>
                <script>
                  const label = document.getElementById('fps');

                  // THREE clocks, because there are three separable failures and
                  // one number cannot tell them apart.
                  //
                  //   timer    -- setInterval, a plain GLib timer source. Low
                  //               means nobody is draining the main context,
                  //               which would be the embedder's fault.
                  //   timeline -- how often document.timeline advances, which is
                  //               one tick per *rendering update*. This is what
                  //               drives CSS animations, and it is measured by
                  //               sampling from the timer rather than from a
                  //               frame callback, so it stays readable even when
                  //               frame callbacks are not arriving at all.
                  //   raf      -- requestAnimationFrame, the scripted animation
                  //               controller. Low while `timeline` is high means
                  //               the engine is compositing and simply not
                  //               running the page's callbacks.
                  //
                  // The middle one is the load-bearing measurement. Without it a
                  // broken rAF and a stalled compositor look identical, and they
                  // have nothing to do with each other.
                  let raf = 0;
                  let timer = 0;
                  let ticks = new Set();
                  let since = performance.now();

                  requestAnimationFrame(function spin() {
                    raf++;
                    requestAnimationFrame(spin);
                  });

                  // Deliberately faster than any display, so this measures how
                  // often the context is drained rather than how often the timer
                  // was scheduled to fire.
                  setInterval(function () {
                    timer++;
                    ticks.add(document.timeline.currentTime);
                    const now = performance.now();
                    const span = now - since;
                    if (span < %d) { return; }
                    const rafFps = raf * 1000 / span;
                    const timerFps = timer * 1000 / span;
                    const timelineFps = ticks.size * 1000 / span;
                    const visible = document.visibilityState === 'visible' ? 1 : 0;
                    label.textContent = rafFps.toFixed(1) + ' raf / '
                      + timelineFps.toFixed(1) + ' timeline / ' + timerFps.toFixed(1) + ' timer';
                    if (window.%s) { window.%s(rafFps, timelineFps, timerFps, visible); }
                    raf = 0;
                    timer = 0;
                    ticks = new Set();
                    since = now;
                  }, 4);
                </script>
                """.formatted(REPORT_MILLIS, CALLBACK, CALLBACK);
    }
}
