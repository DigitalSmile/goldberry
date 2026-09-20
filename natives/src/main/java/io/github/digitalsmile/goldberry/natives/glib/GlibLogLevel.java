package io.github.digitalsmile.goldberry.natives.glib;

import io.github.digitalsmile.goldberry.log.bridge.NativeLogLevel;

/// GLib's `GLogLevelFlags`, and the SLF4J level each one is worth.
///
/// A bit mask rather than an enumeration — a call may set a level bit together
/// with `G_LOG_FLAG_FATAL` or `G_LOG_FLAG_RECURSION` — so the values are
/// declared explicitly and read back through [#of], exactly as
/// [io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem]
/// reads `SDL_INIT_*`.
///
/// ## Where the mapping was drawn, and why there
///
/// GLib's own default handler prints `ERROR`, `CRITICAL`, `WARNING` and
/// `MESSAGE` always, and `INFO` and `DEBUG` only when `G_MESSAGES_DEBUG` names
/// the domain. That line — *what a user sees without asking* — is the one worth
/// preserving, and SLF4J draws it at `INFO`. So the four loud levels land on
/// `error`/`error`/`warn`/`info`, and the two quiet ones fall to `debug` and
/// `trace` below it.
///
/// The alternative, mapping name to name, would put GLib's `INFO` — which is
/// off by default and which GTK uses for per-frame chatter — at SLF4J's `INFO`,
/// which most applications leave on. That is a mapping that turns a silent
/// library into a loud one on the day somebody enables a bridge.
///
/// `ERROR` is `error` rather than anything louder because there is nothing
/// louder, and because it has already happened: `G_LOG_LEVEL_ERROR` is fatal in
/// GLib and the process is about to abort. The line is the last thing written.
public enum GlibLogLevel {

    /// `G_LOG_LEVEL_ERROR` — always fatal; GLib aborts after the handler
    /// returns.
    ERROR(1 << 2, NativeLogLevel.ERROR),

    /// `G_LOG_LEVEL_CRITICAL` — a programming error the library refused to act
    /// on. `g_return_if_fail` raises these.
    CRITICAL(1 << 3, NativeLogLevel.ERROR),

    /// `G_LOG_LEVEL_WARNING` — the deprecation notices, and the level the
    /// libayatana-appindicator message this bridge was built for arrives at.
    WARNING(1 << 4, NativeLogLevel.WARN),

    /// `G_LOG_LEVEL_MESSAGE` — `g_message`, the library talking to the user.
    MESSAGE(1 << 5, NativeLogLevel.INFO),

    /// `G_LOG_LEVEL_INFO` — off unless `G_MESSAGES_DEBUG` asks for it.
    INFO(1 << 6, NativeLogLevel.DEBUG),

    /// `G_LOG_LEVEL_DEBUG` — the same, and chattier.
    DEBUG(1 << 7, NativeLogLevel.TRACE);

    /// Everything that is not a level: `G_LOG_FLAG_RECURSION` and
    /// `G_LOG_FLAG_FATAL`, the low two bits. GLib spells the complement of this
    /// `G_LOG_LEVEL_MASK`.
    private static final int FLAG_BITS = (1 << 0) | (1 << 1);

    /// `G_LOG_FLAG_FATAL` — set when GLib will abort once the handler returns,
    /// either because the level is `ERROR` or because `g_log_set_fatal_mask`
    /// said so.
    public static final int FATAL = 1 << 1;

    private final int bit;
    private final NativeLogLevel level;

    GlibLogLevel(int bit, NativeLogLevel level) {
        this.bit = bit;
        this.level = level;
    }

    /// The `G_LOG_LEVEL_*` value.
    public int bit() {
        return bit;
    }

    /// Where a message at this level goes.
    public NativeLogLevel level() {
        return level;
    }

    /// Reads a `GLogLevelFlags` back into the level it carries.
    ///
    /// **The lowest level bit set wins**, and GLib sets exactly one in practice
    /// — `g_log` takes a single level. Lowest rather than highest because the
    /// bits ascend from most to least serious, so a mask that somehow carried
    /// two is reported at the worse of them, which is the safe way to be wrong.
    ///
    /// An unrecognised mask answers [#MESSAGE] rather than throwing, which is
    /// the rule [io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem]
    /// states and for its reason: a future GLib with a seventh level would
    /// otherwise turn a log line into an exception thrown inside an upcall, and
    /// a message nobody can classify is still a message somebody should see.
    public static GlibLogLevel of(int flags) {
        var levels = flags & ~FLAG_BITS;
        for (var candidate : values()) {
            if ((levels & candidate.bit) != 0) {
                return candidate;
            }
        }
        return MESSAGE;
    }

    /// Whether GLib is going to abort once the handler returns.
    ///
    /// Not used to change the level — the message is what it is — but worth
    /// having: a bridge that swallowed the last line before an abort would make
    /// the crash unreadable, and this is how a caller knows to flush.
    public static boolean isFatal(int flags) {
        return (flags & FATAL) != 0 || (flags & ERROR.bit) != 0;
    }
}
