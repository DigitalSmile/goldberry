package dev.goldberry.media.platform.linux;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.codec.DecoderRequest;

/// GStreamer, bound: opened and initialised once per process, the first time a
/// provider is asked about a track.
///
/// A provider is constructed by `ServiceLoader` on every operating system, so
/// nothing is opened until a provider is actually asked. The state is
/// [State.Unavailable] in four cases: on anything but Linux; without GStreamer
/// installed; with a GStreamer older than the bindings need; and with one whose
/// structs are not laid out as [GstLayout] expects. Every provider then supports
/// nothing.
///
/// @param glib  GLib's freeing functions
/// @param gst   the core library
/// @param app   `appsrc` and `appsink`
/// @param video raw video's caps
record GStreamer(GLib glib, Gst gst, GstApp app, GstVideo video) {

    private static final Logger LOG = Logs.of(GStreamer.class);

    /// The decoders found for each codec at each size, by the caps asked for,
    /// highest rank first. The registry does not change while the process runs,
    /// so it is asked once for each.
    private static final Map<String, List<Gst.Candidate>> DECODERS = new ConcurrentHashMap<>();

    GStreamer {
        Objects.requireNonNull(glib, "glib");
        Objects.requireNonNull(gst, "gst");
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(video, "video");
    }

    /// Whether GStreamer was bound, and why not.
    sealed interface State {

        /// Bound, initialised and checked.
        record Loaded(GStreamer gstreamer) implements State {}

        /// Not bound: not Linux, not installed, too old, or laid out differently.
        record Unavailable(String reason) implements State {}
    }

    /// GStreamer, once bound, or empty when it cannot be.
    static Optional<GStreamer> get() {
        return Holder.STATE instanceof State.Loaded(var gstreamer) ? Optional.of(gstreamer) : Optional.empty();
    }

    /// Why GStreamer is not available, or empty when it is.
    static Optional<String> unavailableReason() {
        return Holder.STATE instanceof State.Unavailable(var reason) ? Optional.of(reason) : Optional.empty();
    }

    /// Whether `osName`, the `os.name` property, is Linux.
    static boolean isLinux(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("linux");
    }

    /// The decoder elements this system has for `request`'s track in `codec`,
    /// highest rank first, when it also has the codec's parser; empty when either
    /// is missing.
    List<Gst.Candidate> decoders(GstCodec codec, DecoderRequest request) {
        var caps = codec.decoderCaps(request);
        return DECODERS.computeIfAbsent(
                caps, c -> gst.hasElement(codec.parser()) ? gst.decoders(c, codec.video()) : List.of());
    }

    /// Opens, initialises and checks GStreamer. Package-private for the tests,
    /// which see what an ordinary run sees through [#get].
    static State load(String osName) {
        if (!isLinux(osName)) {
            return new State.Unavailable("the GStreamer decoders are Linux's, and this is " + osName);
        }
        try {
            var glib = new GLib(GstLibrary.GLIB.open());
            var gst = new Gst(GstLibrary.GSTREAMER.open(), glib);
            var gstreamer = new GStreamer(
                    glib, gst, new GstApp(GstLibrary.GST_APP.open()), new GstVideo(GstLibrary.GST_VIDEO.open()));
            gst.init();
            var problems = GstLayout.check(gst, gstreamer.video());
            if (!problems.isEmpty()) {
                return new State.Unavailable("this GStreamer's structs are not laid out as the bindings expect: "
                        + String.join("; ", problems));
            }
            return new State.Loaded(gstreamer);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            // Not installed (IllegalArgumentException), too old (UnsatisfiedLinkError),
            // failing to initialise, or refusing what the layout check asked of it.
            return new State.Unavailable("GStreamer could not be bound: " + e.getMessage());
        }
    }

    /// The lazy holder idiom: the class is initialised, and GStreamer opened, on
    /// the first call that reads [#STATE].
    private static final class Holder {
        static final State STATE = loadAndLog();

        private static State loadAndLog() {
            var state = load(System.getProperty("os.name", ""));
            switch (state) {
                case State.Loaded _ -> LOG.info("bound GStreamer for the platform decoders");
                case State.Unavailable(var reason) -> LOG.debug("no platform decoders: {}", reason);
            }
            return state;
        }
    }
}
