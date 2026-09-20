package io.github.digitalsmile.goldberry.log.bridge;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Where a message raised by a native library becomes an SLF4J event.
///
/// ## The problem this exists for
///
/// A Goldberry process holds libraries that log for themselves, to a file
/// descriptor, with no idea that a logging framework is present. The one every
/// Linux user sees is GLib's:
///
/// ```text
/// (java:1034459): libayatana-appindicator-WARNING **: 21:30:36.281:
///     libayatana-appindicator is deprecated. Please use
///     libayatana-appindicator-glib in newly written code.
/// ```
///
/// That is `g_log_default_handler` writing to stderr from inside the tray
/// (`docs/core-widgets.md` §9's `tray-icon`), and from an application's point of
/// view it is indistinguishable from Goldberry shouting at it. It cannot be
/// filtered, it cannot be given a level, it does not reach the file the rest of
/// the logs are in, and it appears in the console of an application that
/// deliberately configured logging to be silent.
///
/// So each source is given a handler of its own that ends here.
///
/// ## The logger a message lands on
///
/// `native.<source>.<domain>` — `native.glib.libayatana-appindicator`,
/// `native.sdl.video`. Three segments, because all three are things an
/// application legitimately wants to configure separately:
///
/// ```xml
/// <!-- everything the platform says, at warn -->
/// <logger name="native" level="warn"/>
/// <!-- except this one deprecation notice, which nobody can act on -->
/// <logger name="native.glib.libayatana-appindicator" level="off"/>
/// ```
///
/// The domain is the library's own name for the subsystem — GLib's log domain,
/// SDL's category — and it is **not** sanitised into a Java package name. It is
/// a logger name, and `libayatana-appindicator` is what the message says it is.
///
/// ## Nothing here may throw
///
/// Every caller is an FFM upcall stub, and an exception crossing back into C is
/// undefined behaviour at best. [#log] therefore catches everything and drops
/// it: a logging bridge that can take the process down is worse than the stderr
/// line it replaced. That is the one place in the toolkit where a bare
/// `catch (Throwable)` is the correct code.
///
/// **Toolkit plumbing**, like
/// [io.github.digitalsmile.goldberry.log.Logs]
/// beside it. Applications configure the logger names above; nothing calls this
/// but the bridges in `:natives`.
public final class NativeLogBridge {

    /// The root every native logger hangs off.
    public static final String ROOT = "native";

    /// Set `-Dgoldberry.log.native=false` to install no bridge at all and get
    /// each library's own stderr behaviour back.
    ///
    /// Worth having because a bridge is a filter: a message dropped by a logging
    /// configuration is a message somebody debugging the platform layer wanted.
    public static final String ENABLED_PROPERTY = "goldberry.log.native";

    /// What a logger name segment may contain. Everything else becomes `-`,
    /// so a domain carrying a space or a control character cannot produce a
    /// logger name no configuration file can spell.
    private static final String ALLOWED = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-";

    private NativeLogBridge() {}

    /// Whether the bridges should be installed at all.
    ///
    /// Read on each call rather than cached, so a test can set the property and
    /// see it take effect without a fresh class loader.
    public static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    /// The logger name a message from `source` and `domain` lands on.
    ///
    /// Public so that a test asserts the convention rather than reproducing it,
    /// and so that the two bridges in `:natives` can name a logger for a level
    /// check without going through [#log].
    ///
    /// @param source the library, lower-case and stable — `glib`, `sdl`
    /// @param domain the library's own name for the subsystem, or null when it
    ///        did not say; the name is then the source's alone
    public static String loggerName(String source, @Nullable String domain) {
        var head = ROOT + "." + clean(source);
        var tail = domain == null ? "" : clean(domain);
        return tail.isEmpty() ? head : head + "." + tail;
    }

    /// The logger for `source` and `domain`.
    public static Logger logger(String source, @Nullable String domain) {
        return LoggerFactory.getLogger(loggerName(source, domain));
    }

    /// Routes one native message, and **never throws**.
    ///
    /// A blank message is dropped rather than logged as an empty line: GLib
    /// hands a handler the message it was given, and a library that called
    /// `g_message("")` has said nothing.
    ///
    /// @param message the text the library formatted, trailing newline and all
    public static void log(String source, @Nullable String domain, NativeLogLevel level, @Nullable String message) {
        try {
            log(logger(source, domain), level, message);
        } catch (Throwable t) {
            // Deliberately swallowed -- see the class note. There is nowhere to
            // report this: the logger is what failed.
        }
    }

    /// The half that takes the logger, so a test can watch where a message went
    /// without an SLF4J provider on the class path.
    ///
    /// Goldberry binds no logging implementation, tests included (ADR-0023), so
    /// `LoggerFactory.getLogger` in a test hands back a NOP logger that records
    /// nothing. Splitting the routing from the lookup is what makes both
    /// halves checkable: [#loggerName] says which logger, and this says what
    /// arrives on it.
    ///
    /// **Also never throws** when reached through [#log]; called directly, a
    /// logger that throws is the caller's problem.
    public static void log(Logger logger, NativeLogLevel level, @Nullable String message) {
        if (message == null) {
            return;
        }
        // Trailing only: a multi-line native message keeps its shape, and
        // leading whitespace is sometimes the library's own indentation.
        var text = message.stripTrailing();
        if (text.isBlank()) {
            return;
        }
        level.log(logger, text);
    }

    /// `domain` with everything a logger name should not carry replaced, and the
    /// separators that would leave an empty segment trimmed off the ends.
    private static String clean(String segment) {
        var cleaned = new StringBuilder(segment.length());
        for (var index = 0; index < segment.length(); index++) {
            var character = segment.charAt(index);
            var replacement = ALLOWED.indexOf(character) >= 0 ? character : '-';
            // One `-` for a run of them, so "a  b" and "a-b" are one name.
            if (replacement == '-' && !cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) == '-') {
                continue;
            }
            cleaned.append(replacement);
        }
        while (!cleaned.isEmpty() && isSeparator(cleaned.charAt(0))) {
            cleaned.deleteCharAt(0);
        }
        while (!cleaned.isEmpty() && isSeparator(cleaned.charAt(cleaned.length() - 1))) {
            cleaned.deleteCharAt(cleaned.length() - 1);
        }
        return cleaned.toString();
    }

    private static boolean isSeparator(char character) {
        return character == '-' || character == '.';
    }
}
