package io.github.digitalsmile.goldberry.natives.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Where every Goldberry logger comes from.
///
/// Goldberry logs through SLF4J and binds no implementation, so an application
/// that wants logs adds Logback or whatever it already uses, and one that does
/// not gets nothing. "Nothing" has to mean nothing: SLF4J 2 prints
///
/// ```text
/// SLF4J(W): No SLF4J providers were found.
/// SLF4J(W): Defaulting to no-operation (NOP) logger implementation
/// ```
///
/// to stderr on first use, which is noise on the console of every application
/// that made a deliberate choice not to configure logging — and reads like a
/// warning from Goldberry rather than from a library it depends on.
///
/// So this class turns SLF4J's own internal reporting down to errors before the
/// first logger is created. Every logger in the toolkit comes from here, which is
/// what guarantees the ordering: the static initializer below runs before any
/// call to [#of], and therefore before SLF4J's `Reporter` reads the property.
///
/// It is set **only if the application has not set it**, so anyone debugging a
/// provider problem can pass `-Dslf4j.internal.verbosity=INFO` and see everything
/// SLF4J has to say.
///
/// **Toolkit plumbing.** Applications call `LoggerFactory.getLogger` as usual;
/// this exists so that Goldberry's own loggers are all created after the property
/// above is set. It is exported because `:core` needs it and a qualified export
/// cannot name a module that is not on `:natives`' compile module path.
public final class Logs {

    /// Read by `org.slf4j.helpers.Reporter` when it is first loaded. Accepts
    /// `ERROR`, `WARN` (SLF4J's default), `INFO` and `DEBUG`.
    private static final String VERBOSITY_PROPERTY = "slf4j.internal.verbosity";

    static {
        // Not an override: an application that has an opinion keeps it.
        if (System.getProperty(VERBOSITY_PROPERTY) == null) {
            System.setProperty(VERBOSITY_PROPERTY, "ERROR");
        }
    }

    private Logs() {
    }

    /// A logger named after `type`.
    public static Logger of(Class<?> type) {
        return LoggerFactory.getLogger(type);
    }

    /// The property this class sets, so a test can assert on it rather than
    /// hard-coding the string in two places.
    public static String verbosityProperty() {
        return VERBOSITY_PROPERTY;
    }
}
