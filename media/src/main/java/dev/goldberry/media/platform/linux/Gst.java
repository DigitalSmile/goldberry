package dev.goldberry.media.platform.linux;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import dev.goldberry.media.platform.linux.GLib.GError;

/// `libgstreamer-1.0`: pipelines, their buses, buffers, caps and the registry of
/// elements.
///
/// The providers build every pipeline from a description string with
/// `gst_parse_launch`, so the element and pad API is not bound at all: the
/// string names the elements, their properties and their links.
final class Gst {

    /// `GST_STATE_NULL`: every resource freed.
    static final int STATE_NULL = 1;
    /// `GST_STATE_PLAYING`: data flows.
    static final int STATE_PLAYING = 4;

    /// `GST_STATE_CHANGE_FAILURE`.
    static final int STATE_CHANGE_FAILURE = 0;

    /// `GST_MESSAGE_ERROR`.
    static final int MESSAGE_ERROR = 1 << 1;

    /// `GST_MAP_READ`.
    static final int MAP_READ = 1;

    /// `GST_CLOCK_TIME_NONE`: no time.
    static final long CLOCK_TIME_NONE = -1L;

    /// `GST_ELEMENT_FACTORY_TYPE_DECODER`.
    static final long FACTORY_TYPE_DECODER = 1L;
    /// `GST_ELEMENT_FACTORY_TYPE_MEDIA_VIDEO`. The media bits start at 49:
    /// `MEDIA_ANY` is every bit from 48 up, and `MEDIA_VIDEO` the second.
    static final long FACTORY_TYPE_MEDIA_VIDEO = 1L << 49;
    /// `GST_ELEMENT_FACTORY_TYPE_MEDIA_AUDIO`.
    static final long FACTORY_TYPE_MEDIA_AUDIO = 1L << 50;

    /// `GST_RANK_MARGINAL`: the lowest rank an element is plugged at by
    /// `decodebin`. Anything below is never chosen by GStreamer itself, so not
    /// by the providers either.
    static final int RANK_MARGINAL = 64;

    /// `GST_PAD_SINK`.
    static final int PAD_SINK = 2;

    /// `gboolean gst_init_check(int *argc, char ***argv, GError **error)`
    private static final MethodHandle FD_gst_init_check =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `GstElement *gst_parse_launch(const gchar *pipeline_description, GError **error)`,
    /// `GstElement *gst_bin_get_by_name(GstBin *bin, const gchar *name)` and
    /// `const gchar *gst_structure_get_string(const GstStructure *structure, const gchar *fieldname)`
    private static final MethodHandle FD_pointer_pointer_pointer =
            GstLibrary.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

    /// `GstStateChangeReturn gst_element_set_state(GstElement *element, GstState state)`
    private static final MethodHandle FD_gst_element_set_state =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    /// `GstBus *gst_element_get_bus(GstElement *element)`,
    /// `GstBuffer *gst_sample_get_buffer(GstSample *sample)`,
    /// `GstCaps *gst_sample_get_caps(GstSample *sample)`,
    /// `GstCaps *gst_caps_from_string(const gchar *string)`,
    /// `GstElementFactory *gst_element_factory_find(const gchar *name)` and
    /// `gchar *gst_object_get_name(GstObject *object)`
    private static final MethodHandle FD_pointer_pointer = GstLibrary.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

    /// `GstMessage *gst_bus_pop_filtered(GstBus *bus, GstMessageType types)`, and
    /// `GstStructure *gst_caps_get_structure(const GstCaps *caps, guint index)`
    private static final MethodHandle FD_pointer_pointer_int =
            GstLibrary.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

    /// `void gst_message_parse_error(GstMessage *message, GError **gerror, gchar **debug)`
    private static final MethodHandle FD_void_pointer_pointer_pointer =
            GstLibrary.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

    /// `void gst_object_unref(gpointer object)`, `void gst_mini_object_unref(GstMiniObject *mini_object)`,
    /// `void gst_plugin_feature_list_free(GList *list)`
    private static final MethodHandle FD_void_pointer = GstLibrary.link(FunctionDescriptor.ofVoid(ADDRESS));

    /// `GstBuffer *gst_buffer_new_allocate(GstAllocator *allocator, gsize size, GstAllocationParams *params)`
    private static final MethodHandle FD_gst_buffer_new_allocate =
            GstLibrary.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));

    /// `gsize gst_buffer_fill(GstBuffer *buffer, gsize offset, gconstpointer src, gsize size)`
    private static final MethodHandle FD_gst_buffer_fill =
            GstLibrary.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS, JAVA_LONG));

    /// `gboolean gst_buffer_map(GstBuffer *buffer, GstMapInfo *info, GstMapFlags flags)`
    private static final MethodHandle FD_gst_buffer_map =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

    /// `void gst_buffer_unmap(GstBuffer *buffer, GstMapInfo *info)`
    private static final MethodHandle FD_void_pointer_pointer =
            GstLibrary.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

    /// `gboolean gst_structure_get_int(const GstStructure *structure, const gchar *fieldname, gint *value)`
    private static final MethodHandle FD_gst_structure_get_int =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /// `GList *gst_element_factory_list_get_elements(GstElementFactoryListType type, GstRank minrank)`
    private static final MethodHandle FD_gst_element_factory_list_get_elements =
            GstLibrary.link(FunctionDescriptor.of(ADDRESS, JAVA_LONG, JAVA_INT));

    /// `GList *gst_element_factory_list_filter(GList *list, const GstCaps *caps, GstPadDirection direction,
    /// gboolean subsetonly)`
    private static final MethodHandle FD_gst_element_factory_list_filter =
            GstLibrary.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

    /// `guint gst_plugin_feature_get_rank(GstPluginFeature *feature)`
    private static final MethodHandle FD_gst_plugin_feature_get_rank =
            GstLibrary.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private final GLib glib;
    private final MemorySegment initCheck;
    private final MemorySegment parseLaunch;
    private final MemorySegment binGetByName;
    private final MemorySegment elementSetState;
    private final MemorySegment elementGetBus;
    private final MemorySegment busPopFiltered;
    private final MemorySegment messageParseError;
    private final MemorySegment objectUnref;
    private final MemorySegment miniObjectUnref;
    private final MemorySegment bufferNewAllocate;
    private final MemorySegment bufferFill;
    private final MemorySegment bufferMap;
    private final MemorySegment bufferUnmap;
    private final MemorySegment sampleGetBuffer;
    private final MemorySegment sampleGetCaps;
    private final MemorySegment capsFromString;
    private final MemorySegment capsGetStructure;
    private final MemorySegment structureGetString;
    private final MemorySegment structureGetInt;
    private final MemorySegment elementFactoryFind;
    private final MemorySegment factoryListGetElements;
    private final MemorySegment factoryListFilter;
    private final MemorySegment featureListFree;
    private final MemorySegment featureGetRank;
    private final MemorySegment objectGetName;

    Gst(SymbolLookup lookup, GLib glib) {
        this.glib = glib;
        var l = GstLibrary.GSTREAMER;
        this.initCheck = l.symbol(lookup, "gst_init_check");
        this.parseLaunch = l.symbol(lookup, "gst_parse_launch");
        this.binGetByName = l.symbol(lookup, "gst_bin_get_by_name");
        this.elementSetState = l.symbol(lookup, "gst_element_set_state");
        this.elementGetBus = l.symbol(lookup, "gst_element_get_bus");
        this.busPopFiltered = l.symbol(lookup, "gst_bus_pop_filtered");
        this.messageParseError = l.symbol(lookup, "gst_message_parse_error");
        this.objectUnref = l.symbol(lookup, "gst_object_unref");
        this.miniObjectUnref = l.symbol(lookup, "gst_mini_object_unref");
        this.bufferNewAllocate = l.symbol(lookup, "gst_buffer_new_allocate");
        this.bufferFill = l.symbol(lookup, "gst_buffer_fill");
        this.bufferMap = l.symbol(lookup, "gst_buffer_map");
        this.bufferUnmap = l.symbol(lookup, "gst_buffer_unmap");
        this.sampleGetBuffer = l.symbol(lookup, "gst_sample_get_buffer");
        this.sampleGetCaps = l.symbol(lookup, "gst_sample_get_caps");
        this.capsFromString = l.symbol(lookup, "gst_caps_from_string");
        this.capsGetStructure = l.symbol(lookup, "gst_caps_get_structure");
        this.structureGetString = l.symbol(lookup, "gst_structure_get_string");
        this.structureGetInt = l.symbol(lookup, "gst_structure_get_int");
        this.elementFactoryFind = l.symbol(lookup, "gst_element_factory_find");
        this.factoryListGetElements = l.symbol(lookup, "gst_element_factory_list_get_elements");
        this.factoryListFilter = l.symbol(lookup, "gst_element_factory_list_filter");
        this.featureListFree = l.symbol(lookup, "gst_plugin_feature_list_free");
        this.featureGetRank = l.symbol(lookup, "gst_plugin_feature_get_rank");
        this.objectGetName = l.symbol(lookup, "gst_object_get_name");
    }

    /// Initialises GStreamer: loads the registry of plugins, scanning them the
    /// first time a user runs it. Safe to call more than once.
    ///
    /// @throws IllegalStateException when GStreamer cannot initialise
    void init() {
        try (var arena = Arena.ofConfined()) {
            var error = arena.allocate(ADDRESS);
            var ok = (int) FD_gst_init_check.invokeExact(initCheck, MemorySegment.NULL, MemorySegment.NULL, error);
            if (ok == 0) {
                throw new IllegalStateException("gst_init_check failed: "
                        + glib.takeError(error.get(ADDRESS, 0)).message());
            }
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_init_check", t);
        }
    }

    /// The pipeline `description` makes, stopped.
    ///
    /// @throws GstException when the description does not parse or an element in
    ///                      it does not exist
    MemorySegment parseLaunch(String description) {
        try (var arena = Arena.ofConfined()) {
            var error = arena.allocate(ADDRESS);
            error.set(ADDRESS, 0, MemorySegment.NULL);
            var pipeline = (MemorySegment)
                    FD_pointer_pointer_pointer.invokeExact(parseLaunch, arena.allocateFrom(description), error);
            var raised = error.get(ADDRESS, 0);
            if (!raised.equals(MemorySegment.NULL)) {
                // A pipeline may come back with a recoverable error: it is not used.
                if (!pipeline.equals(MemorySegment.NULL)) {
                    unref(pipeline);
                }
                throw new GstException("gst_parse_launch", glib.takeError(raised));
            }
            if (pipeline.equals(MemorySegment.NULL)) {
                throw new GstException("gst_parse_launch returned no pipeline for " + description);
            }
            return pipeline;
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_parse_launch", t);
        }
    }

    /// The element of `bin` called `name`, with a reference the caller releases.
    ///
    /// @throws GstException when there is none
    MemorySegment byName(MemorySegment bin, String name) {
        try (var arena = Arena.ofConfined()) {
            var element =
                    (MemorySegment) FD_pointer_pointer_pointer.invokeExact(binGetByName, bin, arena.allocateFrom(name));
            if (element.equals(MemorySegment.NULL)) {
                throw new GstException("the pipeline has no element called " + name);
            }
            return element;
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_bin_get_by_name", t);
        }
    }

    /// Asks `element` to go to `state`. Answers the `GstStateChangeReturn`: a
    /// pipeline going to playing answers "async" until its sink has a buffer.
    int setState(MemorySegment element, int state) {
        try {
            return (int) FD_gst_element_set_state.invokeExact(elementSetState, element, state);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_element_set_state", t);
        }
    }

    /// `element`'s bus, with a reference the caller releases.
    MemorySegment bus(MemorySegment element) {
        return pointer(elementGetBus, element, "gst_element_get_bus");
    }

    /// The first error waiting on `bus`, taken off it, or empty when there is
    /// none. Never blocks.
    Optional<GError> popError(MemorySegment bus) {
        MemorySegment message;
        try {
            message = (MemorySegment) FD_pointer_pointer_int.invokeExact(busPopFiltered, bus, MESSAGE_ERROR);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_bus_pop_filtered", t);
        }
        if (message.equals(MemorySegment.NULL)) {
            return Optional.empty();
        }
        try (var arena = Arena.ofConfined()) {
            var error = arena.allocate(ADDRESS);
            var debug = arena.allocate(ADDRESS);
            FD_void_pointer_pointer_pointer.invokeExact(messageParseError, message, error, debug);
            var detail = glib.takeString(debug.get(ADDRESS, 0));
            var raised = glib.takeError(error.get(ADDRESS, 0));
            return Optional.of(detail.isEmpty() ? raised : raised.withDetail(detail));
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_message_parse_error", t);
        } finally {
            unrefMini(message);
        }
    }

    /// Releases a `GstObject`: an element, a pipeline, a bus, a factory.
    void unref(MemorySegment object) {
        try {
            FD_void_pointer.invokeExact(objectUnref, object);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_object_unref", t);
        }
    }

    /// Releases a `GstMiniObject`: a buffer, a sample, caps, a message.
    void unrefMini(MemorySegment object) {
        try {
            FD_void_pointer.invokeExact(miniObjectUnref, object);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_mini_object_unref", t);
        }
    }

    /// A new buffer of `size` bytes from the default allocator.
    MemorySegment newBuffer(long size) {
        try {
            var buffer = (MemorySegment) FD_gst_buffer_new_allocate.invokeExact(
                    bufferNewAllocate, MemorySegment.NULL, size, MemorySegment.NULL);
            if (buffer.equals(MemorySegment.NULL)) {
                throw new OutOfMemoryError("gst_buffer_new_allocate(" + size + ") failed");
            }
            return buffer;
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_buffer_new_allocate", t);
        }
    }

    /// Copies `data` into the start of `buffer`.
    void fill(MemorySegment buffer, MemorySegment data) {
        try {
            var copied = (long) FD_gst_buffer_fill.invokeExact(bufferFill, buffer, 0L, data, data.byteSize());
            if (copied != data.byteSize()) {
                throw new IllegalStateException(
                        "gst_buffer_fill copied " + copied + " of " + data.byteSize() + " bytes");
            }
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_buffer_fill", t);
        }
    }

    /// Maps `buffer` for reading into `info`, a [GstLayout#MAP_INFO]-sized segment.
    ///
    /// @throws GstException when it cannot be mapped
    void mapRead(MemorySegment buffer, MemorySegment info) {
        try {
            if ((int) FD_gst_buffer_map.invokeExact(bufferMap, buffer, info, MAP_READ) == 0) {
                throw new GstException("gst_buffer_map could not map a decoded buffer for reading");
            }
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_buffer_map", t);
        }
    }

    /// Undoes [#mapRead].
    void unmap(MemorySegment buffer, MemorySegment info) {
        try {
            FD_void_pointer_pointer.invokeExact(bufferUnmap, buffer, info);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_buffer_unmap", t);
        }
    }

    /// The buffer `sample` holds. Borrowed: valid while the sample is.
    MemorySegment sampleBuffer(MemorySegment sample) {
        return pointer(sampleGetBuffer, sample, "gst_sample_get_buffer");
    }

    /// The caps of `sample`'s buffer. Borrowed: valid while the sample is.
    MemorySegment sampleCaps(MemorySegment sample) {
        return pointer(sampleGetCaps, sample, "gst_sample_get_caps");
    }

    /// Caps parsed from `description`, with a reference the caller releases.
    ///
    /// @throws GstException when it does not parse
    MemorySegment caps(String description) {
        try (var arena = Arena.ofConfined()) {
            var caps = (MemorySegment) FD_pointer_pointer.invokeExact(capsFromString, arena.allocateFrom(description));
            if (caps.equals(MemorySegment.NULL)) {
                throw new GstException("caps that do not parse: " + description);
            }
            return caps;
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_caps_from_string", t);
        }
    }

    /// The string field `field` of the first structure of `caps`, or empty.
    @SuppressWarnings("restricted")
    Optional<String> capsString(MemorySegment caps, String field) {
        try (var arena = Arena.ofConfined()) {
            var structure = structure(caps);
            var value = (MemorySegment)
                    FD_pointer_pointer_pointer.invokeExact(structureGetString, structure, arena.allocateFrom(field));
            return value.equals(MemorySegment.NULL)
                    ? Optional.empty()
                    : Optional.of(value.reinterpret(Long.MAX_VALUE).getString(0));
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_structure_get_string", t);
        }
    }

    /// The integer field `field` of the first structure of `caps`, or empty.
    OptionalInt capsInt(MemorySegment caps, String field) {
        try (var arena = Arena.ofConfined()) {
            var value = arena.allocate(JAVA_INT);
            var found = (int) FD_gst_structure_get_int.invokeExact(
                    structureGetInt, structure(caps), arena.allocateFrom(field), value);
            return found == 0 ? OptionalInt.empty() : OptionalInt.of(value.get(JAVA_INT, 0));
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_structure_get_int", t);
        }
    }

    /// Whether an element called `name` is registered.
    boolean hasElement(String name) {
        try (var arena = Arena.ofConfined()) {
            var factory = (MemorySegment) FD_pointer_pointer.invokeExact(elementFactoryFind, arena.allocateFrom(name));
            if (factory.equals(MemorySegment.NULL)) {
                return false;
            }
            unref(factory);
            return true;
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_element_factory_find", t);
        }
    }

    /// The decoders registered for `caps` (a `video/…` or `audio/…` description)
    /// at [#RANK_MARGINAL] or above, highest rank first, the way `decodebin`
    /// would try them.
    List<Candidate> decoders(String caps, boolean video) {
        var type = FACTORY_TYPE_DECODER | (video ? FACTORY_TYPE_MEDIA_VIDEO : FACTORY_TYPE_MEDIA_AUDIO);
        var parsed = caps(caps);
        MemorySegment all = MemorySegment.NULL;
        MemorySegment matching = MemorySegment.NULL;
        try {
            all = (MemorySegment)
                    FD_gst_element_factory_list_get_elements.invokeExact(factoryListGetElements, type, RANK_MARGINAL);
            matching = (MemorySegment)
                    FD_gst_element_factory_list_filter.invokeExact(factoryListFilter, all, parsed, PAD_SINK, 0);
            var candidates = new ArrayList<Candidate>();
            for (var node = matching; !node.equals(MemorySegment.NULL); node = GLib.next(node)) {
                var factory = GLib.data(node);
                var rank = (int) FD_gst_plugin_feature_get_rank.invokeExact(featureGetRank, factory);
                var name = glib.takeString(pointer(objectGetName, factory, "gst_object_get_name"));
                candidates.add(new Candidate(name, rank));
            }
            return Candidate.ranked(candidates);
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_element_factory_list_filter", t);
        } finally {
            freeFeatureList(matching);
            freeFeatureList(all);
            unrefMini(parsed);
        }
    }

    private void freeFeatureList(MemorySegment list) {
        if (list.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            FD_void_pointer.invokeExact(featureListFree, list);
        } catch (Throwable t) {
            throw GstLibrary.failure("gst_plugin_feature_list_free", t);
        }
    }

    private MemorySegment structure(MemorySegment caps) {
        try {
            var structure = (MemorySegment) FD_pointer_pointer_int.invokeExact(capsGetStructure, caps, 0);
            if (structure.equals(MemorySegment.NULL)) {
                throw new GstException("caps with no structure");
            }
            return structure;
        } catch (Throwable t) {
            throw t instanceof RuntimeException e ? e : GstLibrary.failure("gst_caps_get_structure", t);
        }
    }

    private static MemorySegment pointer(MemorySegment function, MemorySegment argument, String name) {
        try {
            return (MemorySegment) FD_pointer_pointer.invokeExact(function, argument);
        } catch (Throwable t) {
            throw GstLibrary.failure(name, t);
        }
    }

    /// A decoder element GStreamer has for some caps.
    ///
    /// @param name the element's factory name, as a pipeline description names it
    /// @param rank its `GstRank`: higher is preferred
    record Candidate(String name, int rank) {

        /// `candidates`, highest rank first; equal ranks by name, so the choice is
        /// the same on every run.
        static List<Candidate> ranked(List<Candidate> candidates) {
            return candidates.stream()
                    .sorted((a, b) -> a.rank() != b.rank()
                            ? Integer.compare(b.rank(), a.rank())
                            : a.name().compareTo(b.name()))
                    .toList();
        }
    }
}
