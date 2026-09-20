package io.github.digitalsmile.goldberry.natives.sdl.log;

import io.github.digitalsmile.goldberry.log.bridge.NativeLogLevel;

/// SDL's `SDL_LogPriority`, and the SLF4J level each one is worth.
///
/// Ordinals in a C enum, like
/// [io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlSystemCursor]
/// and for its reason: SDL has inserted a value into the middle of an enum
/// before. `SDL_LOG_PRIORITY_TRACE` is itself an insertion — it went in at 1,
/// below `VERBOSE`, and every value above it moved. So every constant here is on
/// the layout table and the C compiler is what says it is right.
///
/// Unlike GLib's ladder ([io.github.digitalsmile.goldberry.natives.glib.GlibLogLevel]),
/// this one maps almost name to name: SDL's names and SLF4J's mean the same
/// things, and SDL decides for itself what to emit — the bridge is not what
/// filters it. The two joins are at the ends, where SDL has one rung more than
/// SLF4J does at each.
public enum SdlLogPriority {

    /// `SDL_LOG_PRIORITY_INVALID`. Never emitted; an argument SDL rejects.
    /// Logged at `debug` rather than dropped, because a message that arrived at
    /// a priority SDL calls invalid is itself worth knowing about.
    INVALID(0, NativeLogLevel.DEBUG),

    /// `SDL_LOG_PRIORITY_TRACE` — the rung inserted below `VERBOSE`.
    TRACE(1, NativeLogLevel.TRACE),

    /// `SDL_LOG_PRIORITY_VERBOSE`. Joined with [#TRACE], SLF4J having no rung
    /// below `trace`.
    VERBOSE(2, NativeLogLevel.TRACE),

    DEBUG(3, NativeLogLevel.DEBUG),

    INFO(4, NativeLogLevel.INFO),

    WARN(5, NativeLogLevel.WARN),

    ERROR(6, NativeLogLevel.ERROR),

    /// `SDL_LOG_PRIORITY_CRITICAL`. Joined with [#ERROR], SLF4J having no rung
    /// above `error`.
    CRITICAL(7, NativeLogLevel.ERROR);

    private final int value;
    private final NativeLogLevel level;

    SdlLogPriority(int value, NativeLogLevel level) {
        this.value = value;
        this.level = level;
    }

    /// The `SDL_LOG_PRIORITY_*` value.
    public int value() {
        return value;
    }

    /// Where a message at this priority goes.
    public NativeLogLevel level() {
        return level;
    }

    /// The name `goldberry_shim.c` reports this value under.
    ///
    /// Mechanical rather than a field, for
    /// [io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem]'s reason:
    /// SDL's names are this enum's names with one prefix, and a constant that
    /// stopped matching would be a constant whose row is missing — which the
    /// verifier reports by name.
    public String nativeName() {
        return "SDL_LOG_PRIORITY_" + name();
    }

    /// Reads an `SDL_LogPriority` back.
    ///
    /// A value this enum does not know answers [#INFO] rather than throwing.
    /// `SdlSubsystem.decode` states the rule and the reason: this is read inside
    /// an upcall, on a message SDL is emitting right now, and turning an
    /// unrecognised priority into an exception thrown back into C would be a
    /// crash caused by a log line. A future SDL with a ninth rung logs at `info`
    /// until somebody adds it here.
    public static SdlLogPriority of(int value) {
        for (var priority : values()) {
            if (priority.value == value) {
                return priority;
            }
        }
        return INFO;
    }
}
