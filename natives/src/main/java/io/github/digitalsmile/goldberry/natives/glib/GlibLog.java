package io.github.digitalsmile.goldberry.natives.glib;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.log.bridge.NativeLogBridge;
import io.github.digitalsmile.goldberry.natives.Upcalls;
import io.github.digitalsmile.goldberry.natives.glib.calls.GlibLogCalls;

/// Sends GLib's log messages to SLF4J instead of to stderr.
///
/// This is what turns
///
/// ```text
/// (java:1034459): libayatana-appindicator-WARNING **: 21:30:36.281:
///     libayatana-appindicator is deprecated.
/// ```
///
/// into an ordinary event on the logger `native.glib.libayatana-appindicator`,
/// where an application's `logback.xml` can level it, route it or switch it off
/// like any other. See
/// [io.github.digitalsmile.goldberry.log.bridge.NativeLogBridge]
/// for the naming, and [ADR-0443][adr] for the decision.
///
/// ## Two hooks, because GLib has two paths
///
/// **The default handler** ([#install]) catches `g_log` — and therefore
/// `g_warning`, `g_message`, `g_critical` and `g_debug`, which are macros over
/// it. This is almost everything a desktop library actually raises, including
/// the message above, and installing it is safe: `g_log_set_default_handler`
/// may be called at any time and returns the handler it replaced.
///
/// **The writer function** ([#installWriter]) catches `g_log_structured`, which
/// GTK 4 and newer GLib code use and which never reaches a default handler. It
/// is **opt-in**, and the reason is severe: `g_log_set_writer_func` calls
/// `g_error` — which aborts the process — if anything in the process has
/// already set one. Goldberry cannot know whether an embedding application, a
/// JNI library or WebKit itself has. So an application that knows its own
/// process sets `-Dgoldberry.log.glib.writer=true` and gets the structured
/// messages too; one that does not say anything keeps its process.
///
/// Both are installed **once**, and a second call does nothing.
///
/// ## When it is installed
///
/// Late, and by the backend: right before the first thing that loads GLib.
/// Looking GLib up `dlopen`s it, so installing at start-up would map GLib into
/// every Goldberry process including the ones that never show a tray or a page.
/// [GlibLibrary] has the longer note.
///
/// ## Nothing here may throw
///
/// Every method below [#handle] runs inside an FFM upcall, called from C with a
/// GLib lock held. An exception crossing back is undefined behaviour, and one
/// raised while GLib is on its way to `abort()` would hide the reason. So the
/// handler catches everything, and `NativeLogBridge.log` catches everything
/// again.
///
/// [adr]: ../../../../../../../../book/src/adr/0443-somebody-elses-log-line-is-still-a-log-line.md
public final class GlibLog {

    /// The `source` segment of every logger name this bridge writes to.
    public static final String SOURCE = "glib";

    /// Set `-Dgoldberry.log.glib.writer=true` to also install a
    /// `GLogWriterFunc`, which catches structured messages a default handler
    /// cannot see.
    ///
    /// **Only if nothing else in the process sets one.** GLib aborts on the
    /// second call — see the class note.
    public static final String WRITER_PROPERTY = "goldberry.log.glib.writer";

    /// `G_LOG_WRITER_HANDLED`. Returned from the writer to tell GLib the record
    /// has been dealt with and must not also go to stderr.
    private static final int WRITER_HANDLED = 1;

    /// The longest native string this bridge will read. A log line is a line;
    /// anything past this is a library with a defect, and a bounded window is
    /// what keeps a missing NUL from being an unbounded read.
    private static final long MAX_STRING_LENGTH = 64 * 1024;

    private static final Logger LOG = Logs.of(GlibLog.class);

    private static final Linker LINKER = Linker.nativeLinker();

    /// ```c
    /// typedef void (*GLogFunc)(const gchar *log_domain, GLogLevelFlags log_level,
    ///                          const gchar *message, gpointer user_data);
    /// ```
    private static final FunctionDescriptor HANDLER_DESCRIPTOR = Upcalls.describe(FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /// ```c
    /// typedef GLogWriterOutput (*GLogWriterFunc)(GLogLevelFlags log_level, const GLogField *fields,
    ///                                            gsize n_fields, gpointer user_data);
    /// ```
    private static final FunctionDescriptor WRITER_DESCRIPTOR = Upcalls.describe(FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS));

    /// ```c
    /// typedef struct { const gchar *key; gconstpointer value; gssize length; } GLogField;
    /// ```
    ///
    /// **The one struct in this module that is not on the layout table.**
    /// ADR-0010's rule is that a hand-written layout is checked against
    /// `goldberry_shim.c`, which reports `sizeof` and `offsetof` as the target's
    /// own C compiler computed them — and it cannot report this one, because
    /// GLib's headers are not a dependency of the superbuild and must not
    /// become one for the sake of a log line.
    ///
    /// What makes that acceptable here and nowhere else: three machine words,
    /// all of them read and none written; GLib has published this struct
    /// unchanged since 2.50; and nothing reaches it unless an application has
    /// opted into the writer function. A wrong offset is a garbled log message,
    /// not corrupted memory — the segments are reinterpreted with a bound before
    /// anything is read out of them.
    private static final MemoryLayout LOG_FIELD = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("key"),
            ValueLayout.ADDRESS.withName("value"),
            ValueLayout.JAVA_LONG.withName("length"));

    /// GLib's name for the log domain inside a structured record — the same
    /// string a `GLogFunc` receives as its first argument.
    private static final String DOMAIN_FIELD = "GLIB_DOMAIN";

    /// And for the text of it.
    private static final String MESSAGE_FIELD = "MESSAGE";

    private static boolean handlerInstalled;
    private static boolean writerInstalled;

    private GlibLog() {}

    /// Routes GLib's legacy log messages to SLF4J, once.
    ///
    /// Safe to call from anywhere and as often as convenient: the second call
    /// and every call after it does nothing. Callers are the backend, at the two
    /// points where GLib is about to be loaded.
    ///
    /// @return true if this call installed the handler
    public static synchronized boolean install() {
        if (handlerInstalled) {
            return false;
        }
        if (!NativeLogBridge.isEnabled()) {
            LOG.debug("{}=false, so GLib keeps its own stderr handler", NativeLogBridge.ENABLED_PROPERTY);
            handlerInstalled = true;
            return false;
        }
        var lookup = GlibLibrary.get();
        if (lookup.isEmpty()) {
            handlerInstalled = true;
            return false;
        }
        var calls = GlibLogCalls.bind(lookup.get());
        if (!calls.setDefaultHandler().isPresent()) {
            LOG.debug("this GLib has no g_log_set_default_handler; its messages will go to stderr");
            handlerInstalled = true;
            return false;
        }
        try {
            calls.setDefaultHandler().call(stub("handle", HANDLER_DESCRIPTOR), MemorySegment.NULL);
            handlerInstalled = true;
            LOG.debug("GLib's log messages now arrive on {}.{}.*", NativeLogBridge.ROOT, SOURCE);
            if (Boolean.getBoolean(WRITER_PROPERTY)) {
                installWriter(calls);
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            // A bridge that cannot be installed is a bridge that is not
            // installed. The messages go where they went before.
            LOG.debug("could not install the GLib log handler ({}); its messages will go to stderr", e.toString());
            handlerInstalled = true;
            return false;
        }
    }

    /// Also routes GLib's **structured** records, which a default handler never
    /// sees.
    ///
    /// Called only when [#WRITER_PROPERTY] is set — read the class note before
    /// setting it, because GLib aborts the process if something else in it has
    /// already installed a writer.
    private static void installWriter(GlibLogCalls calls) {
        if (writerInstalled || !calls.setWriterFunc().isPresent()) {
            return;
        }
        // `warn`, not `debug`: this is the call that can take the process down,
        // and the line before it is what a crash report needs to contain.
        LOG.warn(
                "installing a GLogWriterFunc because -D{}=true; GLib aborts if anything"
                        + " else in this process has already set one",
                WRITER_PROPERTY);
        calls.setWriterFunc().call(stub("write", WRITER_DESCRIPTOR), MemorySegment.NULL, MemorySegment.NULL);
        writerInstalled = true;
    }

    /// Whether the legacy handler has been installed in this process.
    public static synchronized boolean isInstalled() {
        return handlerInstalled;
    }

    /// GLib's `GLogFunc`. **Called from C; must not throw.**
    ///
    /// Private, and reached only through the upcall stub — which is why it is
    /// found by name in [#stub] rather than referenced.
    @SuppressWarnings("unused")
    private static void handle(MemorySegment domain, int flags, MemorySegment message, MemorySegment userData) {
        try {
            report(readString(domain), flags, readString(message));
        } catch (Throwable t) {
            // Nowhere to report it, and a throw would cross back into C.
        }
    }

    /// GLib's `GLogWriterFunc`. **Called from C; must not throw.**
    ///
    /// @return `G_LOG_WRITER_HANDLED`, always: a record this bridge failed to
    ///         read is still one it has taken responsibility for, and answering
    ///         `UNHANDLED` would put it back on stderr — which is the whole
    ///         thing being fixed
    // Restricted: `reinterpret` is how a pointer handed over by C is given the
    // size it always had. Suppressed per call site, like every other crossing in
    // this module.
    @SuppressWarnings({"unused", "restricted"})
    private static int write(int flags, MemorySegment fields, long count, MemorySegment userData) {
        try {
            var stride = LOG_FIELD.byteSize();
            var table = fields.reinterpret(stride * Math.max(0, count));
            String domain = null;
            String message = null;
            for (var index = 0L; index < count; index++) {
                var offset = index * stride;
                var key = readString(table.get(ValueLayout.ADDRESS, offset));
                // `length` is -1 for a NUL-terminated string, which every field
                // this bridge reads is; a non-negative one means binary, and
                // there is nothing useful to log from that.
                var length = table.get(ValueLayout.JAVA_LONG, offset + 2 * ValueLayout.ADDRESS.byteSize());
                if (length >= 0) {
                    continue;
                }
                var value = table.get(ValueLayout.ADDRESS, offset + ValueLayout.ADDRESS.byteSize());
                if (MESSAGE_FIELD.equals(key)) {
                    message = readString(value);
                } else if (DOMAIN_FIELD.equals(key)) {
                    domain = readString(value);
                }
            }
            report(domain, flags, message);
        } catch (Throwable t) {
            // As in `handle`.
        }
        return WRITER_HANDLED;
    }

    /// One GLib record, on the logger it belongs to.
    ///
    /// Package-private and taking only Java values, so the whole translation —
    /// level bits to SLF4J level, domain to logger name — is testable without a
    /// GLib, a display or a native library of any kind.
    static void report(String domain, int flags, String message) {
        NativeLogBridge.log(SOURCE, domain, GlibLogLevel.of(flags).level(), message);
    }

    /// An upcall stub over the private static method named `name`.
    ///
    /// `Arena.global()`: GLib keeps the pointer for the life of the process and
    /// there is no call that takes it back, so a scoped arena would be a
    /// dangling function pointer the moment it closed.
    @SuppressWarnings("restricted")
    private static MemorySegment stub(String name, FunctionDescriptor descriptor) {
        try {
            var target = MethodHandles.lookup().findStatic(GlibLog.class, name, methodType(descriptor));
            return LINKER.upcallStub(target, descriptor, Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no " + name + " to bind a GLib callback to", e);
        }
    }

    /// The Java signature matching `descriptor`, which for the two shapes here
    /// is mechanical: every `ADDRESS` is a `MemorySegment` and every scalar is
    /// its carrier.
    private static MethodType methodType(FunctionDescriptor descriptor) {
        var type = MethodType.methodType(
                descriptor.returnLayout().map(GlibLog::carrier).orElse(void.class));
        for (var argument : descriptor.argumentLayouts()) {
            type = type.appendParameterTypes(carrier(argument));
        }
        return type;
    }

    private static Class<?> carrier(MemoryLayout layout) {
        return layout instanceof ValueLayout value ? value.carrier() : MemorySegment.class;
    }

    /// A C string at `pointer`, or null when there is none.
    ///
    /// Restricted for the reason `Sdl.readString` is: the pointer arrives with
    /// no size, and a bounded window is what turns "read until the NUL" into a
    /// read that cannot run away.
    @SuppressWarnings("restricted")
    private static String readString(MemorySegment pointer) {
        if (pointer == null || MemorySegment.NULL.equals(pointer)) {
            return null;
        }
        return pointer.reinterpret(MAX_STRING_LENGTH).getString(0);
    }
}
