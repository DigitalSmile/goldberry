package io.github.digitalsmile.goldberry.widget;

import java.util.LinkedHashMap;
import java.util.Map;

/// What one frame did to the element tree, for when a number on the `hud` needs
/// explaining.
///
/// ```
/// ./gradlew :example:run -Dgoldberry.trace.frames=true   # frames that did something
/// ./gradlew :example:run -Dgoldberry.trace.frames=all    # and the quiet ones
/// ```
///
/// ## Why this is not logging
///
/// The stage timings ([io.github.digitalsmile.goldberry.FrameStats]) say *which*
/// part of a frame is expensive. They cannot say why, and the two times that has
/// mattered the answer was a count rather than a duration: the style cache
/// missing on every element ([ADR-0142](../../../../../../book/src/adr/0142-a-style-handed-down-keeps-its-identity.md)),
/// and a click invalidating the whole tree
/// ([ADR-0149](../../../../../../book/src/adr/0149-a-state-invalidates-what-it-can-reach.md)).
/// Both took a purpose-built probe and a counter compiled into the renderer to
/// find. This is that counter, kept.
///
/// **Off unless asked for**, and free when off: the counters are plain `int`
/// increments behind one `static final boolean`, which the JIT folds away
/// entirely when it is false. Nothing here allocates on a traced frame either
/// except the walk map, which is only touched when a subtree is actually thrown
/// away.
///
/// Confined to the UI thread, like everything the frame loop touches.
public final class FrameTrace {

    /// The property both readings come from: unset, `true`, or `all`.
    public static final String TRACE_PROPERTY = "goldberry.trace.frames";

    /// Whether to count anything at all.
    ///
    /// A system property and not a log level: a diagnostic that costs an
    /// `isTraceEnabled()` per element per frame would be measuring itself, which
    /// is [ADR-0101](../../../../../../book/src/adr/0101-a-diagnostic-must-not-be-the-thing-it-measures.md)'s
    /// whole subject.
    public static final boolean ENABLED = enabled(System.getProperty(TRACE_PROPERTY));

    /// Whether to report frames in which nothing happened.
    ///
    /// Off by default because an idle loop at 60 fps produces a line a frame that
    /// says "nothing changed", and the frames worth reading are the ones next to
    /// the click.
    public static final boolean ALL_FRAMES = allFrames(System.getProperty(TRACE_PROPERTY));

    /// Whether `value` asks for tracing at all.
    ///
    /// **`all` implies `true`**, which is the whole reason this is a method. It
    /// used to be `Boolean.getBoolean`, which is false for `all` — so the louder
    /// of the two settings turned the quieter one off and `=all` printed nothing
    /// whatsoever. A flag whose stronger form does less than its weaker one is
    /// the kind of thing only a test catches, so there is one.
    ///
    /// @param value the raw property, or null when it is unset
    static boolean enabled(String value) {
        return allFrames(value) || Boolean.parseBoolean(value);
    }

    /// Whether `value` asks for the quiet frames too.
    ///
    /// @param value the raw property, or null when it is unset
    static boolean allFrames(String value) {
        return "all".equalsIgnoreCase(value);
    }

    /// One per element tree, made by it — there is nothing useful to do with a
    /// trace that is not attached to a tree.
    FrameTrace() {
    }

    /// Elements whose `build` ran.
    private int built;

    /// Elements whose style was computed by the cascade.
    private int resolved;

    /// Elements whose cached style was thrown away.
    private int invalidated;

    /// Elements walked by the render pass, whether or not they resolved.
    private int walked;

    /// Nanoseconds inside the cascade — `StyleResolver.resolve` and turning its
    /// tokens into a [io.github.digitalsmile.goldberry.css.ComputedStyle].
    private long cascadeNanos;

    /// Nanoseconds spent keeping a style's identity — `restyle` and the value
    /// comparison that decides whether the children can keep their caches
    /// ([ADR-0142](../../../../../../book/src/adr/0142-a-style-handed-down-keeps-its-identity.md)).
    private long identityNanos;

    /// Nanoseconds in the transition overlay — observing a target, interpolating
    /// what is in flight, and settling what has arrived.
    private long motionNanos;

    /// Nanoseconds inside widgets' own `render` — building boxes, and measuring
    /// paragraphs that are not in the cache.
    private long boxNanos;

    /// Paragraphs this frame found in the cache, and paragraphs it had to shape.
    ///
    /// A miss is 56 microseconds of HarfBuzz
    /// ([ADR-0037](../../../../../../book/src/adr/0037-what-the-text-path-costs.md)),
    /// so a frame with a handful of them has spent more on text than on
    /// everything else — and a *steady* trickle of them means something is
    /// building a string per frame rather than reusing one.
    private int textHits;
    private int textMisses;

    void text(int hits, int misses) {
        textHits = hits;
        textMisses = misses;
    }

    void countWalk() {
        walked++;
    }

    void cascade(long nanos) {
        cascadeNanos += nanos;
    }

    void identity(long nanos) {
        identityNanos += nanos;
    }

    void motion(long nanos) {
        motionNanos += nanos;
    }

    void boxes(long nanos) {
        boxNanos += nanos;
    }

    /// What asked for a **subtree** to be thrown away, and how many nodes went
    /// with it — `column:ACTIVE → 61`.
    ///
    /// The interesting line. A frame that re-resolves everything says so here,
    /// with the node and the state that did it.
    private final Map<String, Integer> walks = new LinkedHashMap<>();

    void countBuild() {
        built++;
    }

    void countResolve() {
        resolved++;
    }

    void countInvalidation() {
        invalidated++;
    }

    void walked(String source, int nodes) {
        walks.merge(source, nodes, Integer::sum);
    }

    /// Whether this frame did anything to the tree at all.
    public boolean isQuiet() {
        return built == 0 && invalidated == 0 && walks.isEmpty();
    }

    public int built() {
        return built;
    }

    public int resolved() {
        return resolved;
    }

    public int invalidated() {
        return invalidated;
    }

    /// Cleared at the start of every frame by the launcher.
    public void reset() {
        built = 0;
        resolved = 0;
        invalidated = 0;
        walked = 0;
        cascadeNanos = 0;
        identityNanos = 0;
        motionNanos = 0;
        boxNanos = 0;
        textHits = 0;
        textMisses = 0;
        walks.clear();
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    @Override
    public String toString() {
        var text = new StringBuilder()
                .append("elements ").append(walked)
                .append(", built ").append(built)
                .append(", resolved ").append(resolved)
                .append(", invalidated ").append(invalidated)
                .append(" | cascade ").append(millis(cascadeNanos))
                .append(" identity ").append(millis(identityNanos))
                .append(" motion ").append(millis(motionNanos))
                .append(" boxes ").append(millis(boxNanos))
                .append(" | text ").append(textHits).append(" cached, ")
                .append(textMisses).append(" shaped");
        if (!walks.isEmpty()) {
            text.append(" | subtree walks: ");
            var first = true;
            for (var walk : walks.entrySet()) {
                if (!first) {
                    text.append(", ");
                }
                first = false;
                text.append(walk.getKey()).append(" -> ").append(walk.getValue());
            }
        }
        return text.toString();
    }
}
