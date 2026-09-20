package io.github.digitalsmile.goldberry.log.bridge;

import org.slf4j.Logger;

/// The five levels a native library's message can arrive at, and how each one
/// reaches SLF4J.
///
/// Every logging library under Goldberry has its own ladder — GLib's
/// `GLogLevelFlags`, SDL's `SDL_LogPriority` — and none of them is SLF4J's. This
/// is the one rung they are all translated onto, so the mapping is decided once
/// per source and the routing is decided once here.
///
/// It is an enum with the call on it rather than a `switch` at the call site
/// because there are two call sites and there will be more: a source that
/// forgets a level is a message that vanishes, and a missing constant here is a
/// compile error instead.
public enum NativeLogLevel {

    /// Something the library could not do. GLib's `G_LOG_LEVEL_ERROR` and
    /// `_CRITICAL`, SDL's `SDL_LOG_PRIORITY_ERROR` and `_CRITICAL`.
    ERROR {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isErrorEnabled();
        }

        @Override
        public void log(Logger logger, String message) {
            logger.error(message);
        }
    },

    /// Something the library did anyway and is unhappy about — the deprecation
    /// notices that are the common case.
    WARN {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isWarnEnabled();
        }

        @Override
        public void log(Logger logger, String message) {
            logger.warn(message);
        }
    },

    INFO {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isInfoEnabled();
        }

        @Override
        public void log(Logger logger, String message) {
            logger.info(message);
        }
    },

    DEBUG {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isDebugEnabled();
        }

        @Override
        public void log(Logger logger, String message) {
            logger.debug(message);
        }
    },

    /// The chattiest rung a native library has. Nothing maps here yet; it exists
    /// so that a source with six levels has somewhere to put its sixth without
    /// flattening two of them together.
    TRACE {
        @Override
        public boolean isEnabled(Logger logger) {
            return logger.isTraceEnabled();
        }

        @Override
        public void log(Logger logger, String message) {
            logger.trace(message);
        }
    };

    /// Whether `logger` would keep a message at this level.
    ///
    /// Asked **before** the message is read out of native memory, which is the
    /// only reason this is on the enum at all: a `debug` message from a library
    /// nobody is listening to should cost a level check and not a string copy
    /// across the FFM boundary.
    public abstract boolean isEnabled(Logger logger);

    /// Writes `message` to `logger` at this level.
    ///
    /// The message is the whole event — it carries no placeholders and is never
    /// formatted, because what arrives from C has already been formatted by the
    /// library that raised it. A `{}` in a native message is a literal brace.
    public abstract void log(Logger logger, String message);
}
