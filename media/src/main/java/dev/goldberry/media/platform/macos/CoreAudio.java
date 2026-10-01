package dev.goldberry.media.platform.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/// Core Audio's hardware properties: the default output device, and the four
/// numbers its latency is made of (ADR-0474).
///
/// One function, `AudioObjectGetPropertyData`, asked for one property at a time
/// by its address: a selector, a scope and an element, three `UInt32`s with no
/// padding (12 bytes, measured against the SDK). Every property read here is a
/// `UInt32` but the sample rate, a `Float64`, and the stream list, an array of
/// `AudioObjectID`s of which only the first is read.
///
/// Every query answers empty rather than throwing when the system refuses: a
/// device unplugged between two calls is the ordinary case, and the answer to it
/// is "no latency known", not an exception on the audio thread.
final class CoreAudio {

    /// `kAudioObjectSystemObject`: the object the hardware properties are on.
    static final int SYSTEM_OBJECT = 1;
    /// `kAudioObjectUnknown`: what "no default device" reads as.
    static final int UNKNOWN_OBJECT = 0;

    /// `kAudioHardwarePropertyDefaultOutputDevice`.
    static final int DEFAULT_OUTPUT_DEVICE = OsStatus.code("dOut");
    /// `kAudioDevicePropertyLatency`, and `kAudioStreamPropertyLatency`, which
    /// is the same code asked of a stream.
    static final int LATENCY = OsStatus.code("ltnc");
    /// `kAudioDevicePropertySafetyOffset`.
    static final int SAFETY_OFFSET = OsStatus.code("saft");
    /// `kAudioDevicePropertyBufferFrameSize`.
    static final int BUFFER_FRAME_SIZE = OsStatus.code("fsiz");
    /// `kAudioDevicePropertyNominalSampleRate`.
    static final int NOMINAL_SAMPLE_RATE = OsStatus.code("nsrt");
    /// `kAudioDevicePropertyStreams`.
    static final int STREAMS = OsStatus.code("stm#");
    /// `kAudioDevicePropertyTransportType`.
    static final int TRANSPORT_TYPE = OsStatus.code("tran");

    /// `kAudioObjectPropertyScopeGlobal`.
    static final int SCOPE_GLOBAL = OsStatus.code("glob");
    /// `kAudioObjectPropertyScopeOutput`.
    static final int SCOPE_OUTPUT = OsStatus.code("outp");
    /// `kAudioObjectPropertyElementMain`.
    static final int ELEMENT_MAIN = 0;

    /// `AudioObjectPropertyAddress`.
    static final MemoryLayout PROPERTY_ADDRESS = MemoryLayout.structLayout(
            JAVA_INT.withName("mSelector"), JAVA_INT.withName("mScope"), JAVA_INT.withName("mElement"));

    /// How many stream IDs the stream list is read into: a device has one output
    /// stream, or a few, and only the first is asked about.
    private static final int MAX_STREAMS = 16;

    /// `OSStatus AudioObjectGetPropertyData(AudioObjectID inObjectID, const AudioObjectPropertyAddress *inAddress,
    /// UInt32 inQualifierDataSize, const void *inQualifierData, UInt32 *ioDataSize, void *outData)`
    private static final MethodHandle FD_AudioObjectGetPropertyData =
            Framework.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    private final MemorySegment getPropertyData;

    /// Binds Core Audio from `lookup`, the opened framework.
    ///
    /// @throws UnsatisfiedLinkError when the framework does not export the function
    CoreAudio(SymbolLookup lookup) {
        this.getPropertyData = Framework.CORE_AUDIO.symbol(lookup, "AudioObjectGetPropertyData");
    }

    /// The device sound plays on unless an application picks one: the one SDL's
    /// default stream follows. Empty when there is none.
    OptionalInt defaultOutputDevice() {
        var device = uint32(SYSTEM_OBJECT, DEFAULT_OUTPUT_DEVICE, SCOPE_GLOBAL);
        return device.isPresent() && device.getAsInt() != UNKNOWN_OBJECT ? device : OptionalInt.empty();
    }

    /// What the default output device's latency is made of, read now; empty when
    /// there is no device, or it would not say its sample rate.
    ///
    /// A part the device will not give is counted as zero: a device that says
    /// nothing about its safety offset has none worth waiting for.
    Optional<DeviceLatency> defaultOutputLatency() {
        var found = defaultOutputDevice();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var device = found.getAsInt();
        var rate = float64(device, NOMINAL_SAMPLE_RATE, SCOPE_GLOBAL);
        if (rate.isEmpty() || !(rate.getAsDouble() > 0)) {
            return Optional.empty();
        }
        var stream = firstObject(device, STREAMS, SCOPE_OUTPUT);
        return Optional.of(new DeviceLatency(
                device,
                rate.getAsDouble(),
                uint32(device, LATENCY, SCOPE_OUTPUT).orElse(0),
                uint32(device, SAFETY_OFFSET, SCOPE_OUTPUT).orElse(0),
                uint32(device, BUFFER_FRAME_SIZE, SCOPE_OUTPUT).orElse(0),
                stream.isPresent()
                        ? uint32(stream.getAsInt(), LATENCY, SCOPE_GLOBAL).orElse(0)
                        : 0,
                uint32(device, TRANSPORT_TYPE, SCOPE_GLOBAL).orElse(0)));
    }

    /// A `UInt32` property, or empty when the object does not have it.
    OptionalInt uint32(int object, int selector, int scope) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_INT);
            return get(arena, object, selector, scope, out)
                    ? OptionalInt.of(out.get(JAVA_INT, 0))
                    : OptionalInt.empty();
        }
    }

    /// A `Float64` property, or empty when the object does not have it.
    OptionalDouble float64(int object, int selector, int scope) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_DOUBLE);
            return get(arena, object, selector, scope, out)
                    ? OptionalDouble.of(out.get(JAVA_DOUBLE, 0))
                    : OptionalDouble.empty();
        }
    }

    /// The first `AudioObjectID` of a list property, or empty when the list is
    /// empty or missing.
    OptionalInt firstObject(int object, int selector, int scope) {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(JAVA_INT, MAX_STREAMS);
            var size = arena.allocate(JAVA_INT);
            size.set(JAVA_INT, 0, (int) out.byteSize());
            if (!call(arena, object, selector, scope, size, out) || size.get(JAVA_INT, 0) < Integer.BYTES) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(out.get(JAVA_INT, 0));
        }
    }

    /// Reads a property exactly the size of `out`.
    private boolean get(Arena arena, int object, int selector, int scope, MemorySegment out) {
        var size = arena.allocate(JAVA_INT);
        size.set(JAVA_INT, 0, (int) out.byteSize());
        return call(arena, object, selector, scope, size, out) && size.get(JAVA_INT, 0) == out.byteSize();
    }

    private boolean call(Arena arena, int object, int selector, int scope, MemorySegment size, MemorySegment out) {
        var address = arena.allocate(PROPERTY_ADDRESS);
        address.set(JAVA_INT, 0, selector);
        address.set(JAVA_INT, 4, scope);
        address.set(JAVA_INT, 8, ELEMENT_MAIN);
        try {
            var status = (int) FD_AudioObjectGetPropertyData.invokeExact(
                    getPropertyData, object, address, 0, MemorySegment.NULL, size, out);
            return status == OsStatus.OK;
        } catch (Throwable t) {
            throw Framework.failure("AudioObjectGetPropertyData", t);
        }
    }
}
