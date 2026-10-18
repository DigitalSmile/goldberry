package dev.goldberry.example.logging;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator.ExecutionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// The showcase hands logback its own file, so logback's default search, which
/// resolved a resource URL's host in a native image, never runs.
@DisplayName("the showcase's logback configurator")
class ShowcaseLoggingTest {

    @Test
    @DisplayName("reads logback.xml from this module and stops logback looking further")
    void readsTheFile() {
        var context = new LoggerContext();
        var configurator = new ShowcaseLogging();
        configurator.setContext(context);

        var status = configurator.configure(context);

        var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        assertAll(
                () -> assertEquals(ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY, status),
                () -> assertNotNull(root.getAppender("CONSOLE"), "the file's console appender is on the root"));
        context.stop();
    }

    @Test
    @DisplayName("leaves it to logback's own search when there is no file, or it does not parse")
    void fallsBack(@TempDir Path directory) throws IOException {
        var broken = Files.writeString(directory.resolve("logback.xml"), "<configuration><appender");
        var context = new LoggerContext();
        var configurator = new ShowcaseLogging();
        configurator.setContext(context);

        assertAll(
                () -> assertEquals(ExecutionStatus.INVOKE_NEXT_IF_ANY, configurator.configure(context, null)),
                () -> assertEquals(
                        ExecutionStatus.INVOKE_NEXT_IF_ANY,
                        configurator.configure(context, broken.toUri().toURL())));
        context.stop();
    }

    /// The registration is what makes logback find it, on the module path and in
    /// a native image alike, so the compiled descriptor is held to it.
    @Test
    @DisplayName("is the Configurator this module provides")
    void isProvided() throws IOException, URISyntaxException {
        var classes = Path.of(ShowcaseLogging.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        ModuleDescriptor descriptor;
        try (var in = Files.newInputStream(classes.resolve("module-info.class"))) {
            descriptor = ModuleDescriptor.read(in);
        }

        var provided = descriptor.provides().stream()
                .filter(p -> p.service().equals("ch.qos.logback.classic.spi.Configurator"))
                .flatMap(p -> p.providers().stream())
                .toList();
        assertEquals(List.of(ShowcaseLogging.class.getName()), provided);
        assertTrue(Files.isRegularFile(classes.resolve(ShowcaseLogging.RESOURCE.substring(1)))
                || ShowcaseLogging.class.getResource(ShowcaseLogging.RESOURCE) != null);
    }
}
