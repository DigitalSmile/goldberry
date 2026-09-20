package io.github.digitalsmile.goldberry.log.bridge;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;

/// An SLF4J logger that keeps what it was told, for the tests below it.
///
/// Goldberry binds no logging implementation anywhere, tests included
/// (ADR-0023), so `LoggerFactory.getLogger` in a test hands back a NOP logger
/// that records nothing and answers `false` to every `isEnabled`. There is
/// therefore no appender to attach and nothing to assert on — which is why
/// [NativeLogBridge#log(org.slf4j.Logger, NativeLogLevel, String)]
/// exists as a separate overload, and why this exists to be passed to it.
///
/// `LegacyAbstractLogger` is SLF4J's own base class and is in `slf4j-api`
/// itself, so this needs no dependency the build does not already have: it
/// funnels all fifty-odd logging methods into one call.
final class RecordingLogger extends LegacyAbstractLogger {

    /// One call that got through, as level and message.
    record Entry(Level level, String message) {}

    private final List<Entry> entries = new ArrayList<>();

    /// Which levels this logger claims to be at. Everything, unless a test says
    /// otherwise — the enabled-ness is itself worth checking, because
    /// [NativeLogLevel#isEnabled] is what keeps a message nobody wants from
    /// being copied out of native memory.
    private Level threshold = Level.TRACE;

    RecordingLogger() {
        this.name = "native.test";
    }

    /// Silences everything below `level`.
    RecordingLogger at(Level level) {
        this.threshold = level;
        return this;
    }

    List<Entry> entries() {
        return List.copyOf(entries);
    }

    /// The single message recorded, failing the test if there is not exactly
    /// one — which is the assertion nearly every test here wants.
    Entry only() {
        if (entries.size() != 1) {
            throw new AssertionError("expected exactly one log entry, got " + entries);
        }
        return entries.getFirst();
    }

    private boolean enabled(Level level) {
        // SLF4J's int values ascend from TRACE(0) to ERROR(40).
        return level.toInt() >= threshold.toInt();
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return RecordingLogger.class.getName();
    }

    @Override
    protected void handleNormalizedLoggingCall(
            Level level, Marker marker, String message, Object[] arguments, Throwable throwable) {
        entries.add(new Entry(level, message));
    }

    @Override
    public boolean isTraceEnabled() {
        return enabled(Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return enabled(Level.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return enabled(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return enabled(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return enabled(Level.ERROR);
    }
}
