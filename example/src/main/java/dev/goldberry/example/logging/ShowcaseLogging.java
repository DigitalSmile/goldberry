package dev.goldberry.example.logging;

import java.net.URL;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.spi.ContextAwareBase;
import org.jspecify.annotations.Nullable;

/// Configures logback from the showcase's own `logback.xml`, before logback
/// goes looking for one.
///
/// logback runs every [Configurator] it finds through `ServiceLoader` before
/// its default one, and stops at the first that answers
/// `DO_NOT_INVOKE_NEXT_IF_ANY`. This one does, once the file has been read.
///
/// ## Why the default search is skipped
///
/// The default search, having found `logback.xml`, checks whether the class path
/// has more than one by putting every URL for that name into a `HashSet`. A
/// `URL`'s hash code resolves its host. In a native image a resource in a
/// module has the module's name as its host, so every start asked the DNS stub
/// for `dev.goldberry.example`, twice, and waited for a no. That was 70 of the
/// 100 milliseconds before the toolkit's first line. On the JVM a module
/// resource's URL has no host and nothing was resolved, which is why the image
/// alone paid.
///
/// Read with the same `JoranConfigurator` the default would have used, so the
/// file means exactly what it meant before. A file that is missing or does not
/// parse lets the default run, which then says why.
///
/// Read more: [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html#a-logback-configuration).
public final class ShowcaseLogging extends ContextAwareBase implements Configurator {

    /// The file, at the root of this module.
    static final String RESOURCE = "/logback.xml";

    /// For `ServiceLoader`, which is the only caller.
    public ShowcaseLogging() {}

    @Override
    public ExecutionStatus configure(LoggerContext context) {
        return configure(context, ShowcaseLogging.class.getResource(RESOURCE));
    }

    /// The half that takes the file, so a test can hand it one.
    ExecutionStatus configure(LoggerContext context, @Nullable URL file) {
        if (file == null) {
            addWarn("the showcase has no " + RESOURCE + "; leaving it to logback's own search");
            return ExecutionStatus.INVOKE_NEXT_IF_ANY;
        }
        var joran = new JoranConfigurator();
        joran.setContext(context);
        try {
            joran.doConfigure(file);
        } catch (JoranException e) {
            addError("could not read " + file + "; leaving it to logback's own search", e);
            return ExecutionStatus.INVOKE_NEXT_IF_ANY;
        }
        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
    }
}
