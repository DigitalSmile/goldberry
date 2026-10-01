package dev.goldberry.natives.sdl.gpu;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/// Runs the tests tagged `gpu` on the thread that calls [#main].
///
/// A GPU device needs SDL's video subsystem, and on macOS that is Cocoa, which
/// refuses every thread but the process's first (ADR-0039). Gradle's `Test` task
/// runs tests on a worker thread whatever the JVM is started with, so the GPU
/// tests cannot run there on a Mac. `:natives:gpuTest` starts a JVM with
/// `-XstartOnFirstThread` and runs this, and JUnit runs the tests on the thread
/// that asks it to: the first one. On Linux and Windows nothing needs the first
/// thread, and the same task runs them the same way, so there is one path.
///
/// The first argument is where the summary is written, which is the task's
/// output; the second, the package whose tests to run, so `:gpu`, which has
/// `:natives`' tests on its class path for these helpers, runs only its own.
/// The exit status is non-zero when a test failed, or when none ran at all:
/// a selection that found nothing is a green tick over nothing.
public final class GpuTestLauncher {

    /// The tag the GPU tests carry, which the ordinary `test` task excludes.
    public static final String TAG = "gpu";

    private GpuTestLauncher() {}

    static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: GpuTestLauncher <summary.txt> <package>");
        }
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectPackage(args[1]))
                .filters(TagFilter.includeTags(TAG))
                .build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);

        var summary = listener.getSummary();
        var text = new StringWriter();
        try (var out = new PrintWriter(text)) {
            summary.printTo(out);
            summary.printFailuresTo(out, 30);
        }
        System.out.print(text);
        var target = Path.of(args[0]);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, text.toString());

        if (summary.getTotalFailureCount() > 0 || summary.getTestsFoundCount() == 0) {
            System.exit(1);
        }
    }
}
