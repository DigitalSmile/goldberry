package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// SDL's audio streams, for `goldberry-media`'s audio sink and
/// `goldberry-mic`'s recordings.
///
/// A push stream on the default playback device: the media engine puts
/// interleaved f32 into it and reads back how much is still queued, which is its
/// audio clock. A recording stream is the other way round: SDL's device thread
/// fills it, and the caller takes what is there. See [Downcalls] for why each handle is a `static final` constant.
public record SdlAudioCalls(
        OpenAudioDeviceStream openAudioDeviceStream,
        ResumeAudioStreamDevice resumeAudioStreamDevice,
        PauseAudioStreamDevice pauseAudioStreamDevice,
        PutAudioStreamData putAudioStreamData,
        GetAudioStreamQueued getAudioStreamQueued,
        ClearAudioStream clearAudioStream,
        SetAudioStreamGain setAudioStreamGain,
        SetAudioStreamFrequencyRatio setAudioStreamFrequencyRatio,
        DestroyAudioStream destroyAudioStream,
        GetAudioRecordingDevices getAudioRecordingDevices,
        GetAudioDeviceName getAudioDeviceName,
        GetAudioDeviceFormat getAudioDeviceFormat,
        GetAudioStreamData getAudioStreamData,
        GetAudioStreamAvailable getAudioStreamAvailable) {

    /// `SDL_AUDIO_F32`: 32-bit float samples in native byte order, which on
    /// every target here is little-endian (`SDL_AUDIO_F32LE`).
    public static final int SDL_AUDIO_F32 = 0x8120;

    /// `SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK`: whichever output the user chose,
    /// followed when they change it.
    public static final int SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK = 0xFFFFFFFF;

    /// `SDL_AUDIO_DEVICE_DEFAULT_RECORDING`: whichever input the user chose,
    /// followed when they change it.
    public static final int SDL_AUDIO_DEVICE_DEFAULT_RECORDING = 0xFFFFFFFE;

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlAudioCalls bind(SymbolLookup lookup) {
        return new SdlAudioCalls(
                new OpenAudioDeviceStream(lookup),
                new ResumeAudioStreamDevice(lookup),
                new PauseAudioStreamDevice(lookup),
                new PutAudioStreamData(lookup),
                new GetAudioStreamQueued(lookup),
                new ClearAudioStream(lookup),
                new SetAudioStreamGain(lookup),
                new SetAudioStreamFrequencyRatio(lookup),
                new DestroyAudioStream(lookup),
                new GetAudioRecordingDevices(lookup),
                new GetAudioDeviceName(lookup),
                new GetAudioDeviceFormat(lookup),
                new GetAudioStreamData(lookup),
                new GetAudioStreamAvailable(lookup));
    }

    /// `SDL_AudioStream *SDL_OpenAudioDeviceStream(SDL_AudioDeviceID devid,`
    /// `const SDL_AudioSpec *spec, SDL_AudioStreamCallback callback, void *userdata)`
    ///
    /// Opens the device and a stream bound to it, **paused**. `spec` is the format
    /// the stream is fed; SDL converts to what the device takes. With no callback
    /// the caller pushes data. Answers null on failure, with `SDL_GetError` set.
    public static final class OpenAudioDeviceStream {

        private static final MethodHandle FD_SDL_OpenAudioDeviceStream =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        OpenAudioDeviceStream(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_OpenAudioDeviceStream");
        }

        public MemorySegment call(int device, MemorySegment spec, MemorySegment callback, MemorySegment userdata) {
            try {
                return (MemorySegment)
                        FD_SDL_OpenAudioDeviceStream.invokeExact(address, device, spec, callback, userdata);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_OpenAudioDeviceStream", t);
            }
        }
    }

    /// `bool SDL_ResumeAudioStreamDevice(SDL_AudioStream *stream)`
    public static final class ResumeAudioStreamDevice {

        private static final MethodHandle FD_SDL_ResumeAudioStreamDevice =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        ResumeAudioStreamDevice(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ResumeAudioStreamDevice");
        }

        public boolean call(MemorySegment stream) {
            try {
                return (boolean) FD_SDL_ResumeAudioStreamDevice.invokeExact(address, stream);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ResumeAudioStreamDevice", t);
            }
        }
    }

    /// `bool SDL_PauseAudioStreamDevice(SDL_AudioStream *stream)`
    public static final class PauseAudioStreamDevice {

        private static final MethodHandle FD_SDL_PauseAudioStreamDevice =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        PauseAudioStreamDevice(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PauseAudioStreamDevice");
        }

        public boolean call(MemorySegment stream) {
            try {
                return (boolean) FD_SDL_PauseAudioStreamDevice.invokeExact(address, stream);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PauseAudioStreamDevice", t);
            }
        }
    }

    /// `bool SDL_PutAudioStreamData(SDL_AudioStream *stream, const void *buf, int len)`
    ///
    /// Copies the bytes; the caller keeps its buffer.
    public static final class PutAudioStreamData {

        private static final MethodHandle FD_SDL_PutAudioStreamData =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        PutAudioStreamData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PutAudioStreamData");
        }

        public boolean call(MemorySegment stream, MemorySegment data, int length) {
            try {
                return (boolean) FD_SDL_PutAudioStreamData.invokeExact(address, stream, data, length);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PutAudioStreamData", t);
            }
        }
    }

    /// `int SDL_GetAudioStreamQueued(SDL_AudioStream *stream)`
    ///
    /// Bytes put and not yet consumed by the device, in the input format; -1 on failure.
    public static final class GetAudioStreamQueued {

        private static final MethodHandle FD_SDL_GetAudioStreamQueued =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetAudioStreamQueued(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetAudioStreamQueued");
        }

        public int call(MemorySegment stream) {
            try {
                return (int) FD_SDL_GetAudioStreamQueued.invokeExact(address, stream);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetAudioStreamQueued", t);
            }
        }
    }

    /// `bool SDL_ClearAudioStream(SDL_AudioStream *stream)`
    public static final class ClearAudioStream {

        private static final MethodHandle FD_SDL_ClearAudioStream =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        ClearAudioStream(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ClearAudioStream");
        }

        public boolean call(MemorySegment stream) {
            try {
                return (boolean) FD_SDL_ClearAudioStream.invokeExact(address, stream);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ClearAudioStream", t);
            }
        }
    }

    /// `bool SDL_SetAudioStreamFrequencyRatio(SDL_AudioStream *stream, float ratio)`
    ///
    /// Plays the input `ratio` times as fast, from 0.01 to 100, by resampling it:
    /// pitch moves with speed. [GetAudioStreamQueued] still counts input bytes.
    public static final class SetAudioStreamFrequencyRatio {

        private static final MethodHandle FD_SDL_SetAudioStreamFrequencyRatio =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        SetAudioStreamFrequencyRatio(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetAudioStreamFrequencyRatio");
        }

        public boolean call(MemorySegment stream, float ratio) {
            try {
                return (boolean) FD_SDL_SetAudioStreamFrequencyRatio.invokeExact(address, stream, ratio);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetAudioStreamFrequencyRatio", t);
            }
        }
    }

    /// `bool SDL_SetAudioStreamGain(SDL_AudioStream *stream, float gain)`
    public static final class SetAudioStreamGain {

        private static final MethodHandle FD_SDL_SetAudioStreamGain =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        SetAudioStreamGain(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetAudioStreamGain");
        }

        public boolean call(MemorySegment stream, float gain) {
            try {
                return (boolean) FD_SDL_SetAudioStreamGain.invokeExact(address, stream, gain);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetAudioStreamGain", t);
            }
        }
    }

    /// `void SDL_DestroyAudioStream(SDL_AudioStream *stream)`
    ///
    /// Also closes the device it opened.
    public static final class DestroyAudioStream {

        private static final MethodHandle FD_SDL_DestroyAudioStream =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroyAudioStream(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyAudioStream");
        }

        public void call(MemorySegment stream) {
            try {
                FD_SDL_DestroyAudioStream.invokeExact(address, stream);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyAudioStream", t);
            }
        }
    }

    /// `SDL_AudioDeviceID *SDL_GetAudioRecordingDevices(int *count)`
    ///
    /// The recording devices there are now, `count` of them and a zero after,
    /// in memory the caller releases with `SDL_free`. Null on failure.
    public static final class GetAudioRecordingDevices {

        private static final MethodHandle FD_SDL_GetAudioRecordingDevices =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetAudioRecordingDevices(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetAudioRecordingDevices");
        }

        public MemorySegment call(MemorySegment count) {
            try {
                return (MemorySegment) FD_SDL_GetAudioRecordingDevices.invokeExact(address, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetAudioRecordingDevices", t);
            }
        }
    }

    /// `const char *SDL_GetAudioDeviceName(SDL_AudioDeviceID devid)`
    ///
    /// SDL's string, valid while the device is; null for an id it does not know.
    public static final class GetAudioDeviceName {

        private static final MethodHandle FD_SDL_GetAudioDeviceName =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetAudioDeviceName(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetAudioDeviceName");
        }

        public MemorySegment call(int device) {
            try {
                return (MemorySegment) FD_SDL_GetAudioDeviceName.invokeExact(address, device);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetAudioDeviceName", t);
            }
        }
    }

    /// `bool SDL_GetAudioDeviceFormat(SDL_AudioDeviceID devid, SDL_AudioSpec *spec, int *sample_frames)`
    ///
    /// The format the device runs at, or would open in, and its buffer size in
    /// sample frames.
    public static final class GetAudioDeviceFormat {

        private static final MethodHandle FD_SDL_GetAudioDeviceFormat =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetAudioDeviceFormat(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetAudioDeviceFormat");
        }

        public boolean call(int device, MemorySegment spec, MemorySegment sampleFrames) {
            try {
                return (boolean) FD_SDL_GetAudioDeviceFormat.invokeExact(address, device, spec, sampleFrames);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetAudioDeviceFormat", t);
            }
        }
    }

    /// `int SDL_GetAudioStreamData(SDL_AudioStream *stream, void *buf, int len)`
    ///
    /// Takes up to `len` bytes of converted samples out of the stream: the
    /// number of bytes taken, or -1 on failure.
    public static final class GetAudioStreamData {

        private static final MethodHandle FD_SDL_GetAudioStreamData =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetAudioStreamData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetAudioStreamData");
        }

        public int call(MemorySegment stream, MemorySegment buffer, int length) {
            try {
                return (int) FD_SDL_GetAudioStreamData.invokeExact(address, stream, buffer, length);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetAudioStreamData", t);
            }
        }
    }

    /// `int SDL_GetAudioStreamAvailable(SDL_AudioStream *stream)`
    ///
    /// Bytes of converted samples ready to be taken, or -1 on failure.
    public static final class GetAudioStreamAvailable {

        private static final MethodHandle FD_SDL_GetAudioStreamAvailable =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetAudioStreamAvailable(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetAudioStreamAvailable");
        }

        public int call(MemorySegment stream) {
            try {
                return (int) FD_SDL_GetAudioStreamAvailable.invokeExact(address, stream);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetAudioStreamAvailable", t);
            }
        }
    }
}
