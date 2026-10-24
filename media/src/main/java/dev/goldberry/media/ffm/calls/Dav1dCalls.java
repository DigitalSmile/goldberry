package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;
import dev.goldberry.media.ffm.NativeTypes;

/// The functions of dav1d the AV1 decoder calls: opening and closing a context,
/// the send-data/get-picture loop, wrapping a packet's bytes without a copy, and
/// releasing what was handed back.
public record Dav1dCalls(
        Version version,
        DefaultSettings defaultSettings,
        Open open,
        SendData sendData,
        GetPicture getPicture,
        Close close,
        Flush flush,
        GetFrameDelay getFrameDelay,
        DataCreate dataCreate,
        DataWrap dataWrap,
        DataUnref dataUnref,
        PictureUnref pictureUnref) {

    private static final java.lang.foreign.ValueLayout SIZE_T = NativeTypes.SIZE_T;

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static Dav1dCalls bind(SymbolLookup lookup) {
        return new Dav1dCalls(
                new Version(lookup),
                new DefaultSettings(lookup),
                new Open(lookup),
                new SendData(lookup),
                new GetPicture(lookup),
                new Close(lookup),
                new Flush(lookup),
                new GetFrameDelay(lookup),
                new DataCreate(lookup),
                new DataWrap(lookup),
                new DataUnref(lookup),
                new PictureUnref(lookup));
    }

    /// `const char *dav1d_version(void)`
    public static final class Version {

        private static final MethodHandle FD_dav1d_version = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        Version(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_version");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_dav1d_version.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_version", t);
            }
        }
    }

    /// `void dav1d_default_settings(Dav1dSettings *s)`
    public static final class DefaultSettings {

        private static final MethodHandle FD_dav1d_default_settings =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DefaultSettings(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_default_settings");
        }

        public void call(MemorySegment settings) {
            try {
                FD_dav1d_default_settings.invokeExact(address, settings);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_default_settings", t);
            }
        }
    }

    /// `int dav1d_open(Dav1dContext **c_out, const Dav1dSettings *s)`
    ///
    /// 0 on success, a negative `DAV1D_ERR` otherwise.
    public static final class Open {

        private static final MethodHandle FD_dav1d_open =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Open(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_open");
        }

        public int call(MemorySegment contextOut, MemorySegment settings) {
            try {
                return (int) FD_dav1d_open.invokeExact(address, contextOut, settings);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_open", t);
            }
        }
    }

    /// `int dav1d_send_data(Dav1dContext *c, Dav1dData *in)`
    ///
    /// 0 when the data was taken, `DAV1D_ERR(EAGAIN)` when a picture has to be fetched first; the
    /// data is then still the caller's.
    public static final class SendData {

        private static final MethodHandle FD_dav1d_send_data =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SendData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_send_data");
        }

        public int call(MemorySegment context, MemorySegment data) {
            try {
                return (int) FD_dav1d_send_data.invokeExact(address, context, data);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_send_data", t);
            }
        }
    }

    /// `int dav1d_get_picture(Dav1dContext *c, Dav1dPicture *out)`
    ///
    /// 0 with a picture in `out`, `DAV1D_ERR(EAGAIN)` when more data is needed.
    public static final class GetPicture {

        private static final MethodHandle FD_dav1d_get_picture =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetPicture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_get_picture");
        }

        public int call(MemorySegment context, MemorySegment picture) {
            try {
                return (int) FD_dav1d_get_picture.invokeExact(address, context, picture);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_get_picture", t);
            }
        }
    }

    /// `void dav1d_close(Dav1dContext **c_out)`
    public static final class Close {

        private static final MethodHandle FD_dav1d_close = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Close(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_close");
        }

        public void call(MemorySegment contextOut) {
            try {
                FD_dav1d_close.invokeExact(address, contextOut);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_close", t);
            }
        }
    }

    /// `void dav1d_flush(Dav1dContext *c)`
    public static final class Flush {

        private static final MethodHandle FD_dav1d_flush = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Flush(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_flush");
        }

        public void call(MemorySegment context) {
            try {
                FD_dav1d_flush.invokeExact(address, context);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_flush", t);
            }
        }
    }

    /// `int dav1d_get_frame_delay(const Dav1dSettings *s)`
    public static final class GetFrameDelay {

        private static final MethodHandle FD_dav1d_get_frame_delay =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetFrameDelay(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_get_frame_delay");
        }

        public int call(MemorySegment settings) {
            try {
                return (int) FD_dav1d_get_frame_delay.invokeExact(address, settings);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_get_frame_delay", t);
            }
        }
    }

    /// `uint8_t *dav1d_data_create(Dav1dData *data, size_t sz)`
    ///
    /// A buffer of `sz` bytes dav1d owns, which `data` now references; null when out of memory.
    public static final class DataCreate {

        private static final MethodHandle FD_dav1d_data_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, SIZE_T));

        private final MemorySegment address;

        DataCreate(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_data_create");
        }

        public MemorySegment call(MemorySegment data, long size) {
            try {
                return (MemorySegment) FD_dav1d_data_create.invokeExact(address, data, size);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_data_create", t);
            }
        }
    }

    /// `int dav1d_data_wrap(Dav1dData *data, const uint8_t *buf, size_t sz, void
    /// (*free_callback)(const uint8_t *buf, void *cookie), void *cookie)`
    public static final class DataWrap {

        private static final MethodHandle FD_dav1d_data_wrap =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, SIZE_T, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DataWrap(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_data_wrap");
        }

        public int call(
                MemorySegment data, MemorySegment buffer, long size, MemorySegment freeCallback, MemorySegment cookie) {
            try {
                return (int) FD_dav1d_data_wrap.invokeExact(address, data, buffer, size, freeCallback, cookie);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_data_wrap", t);
            }
        }
    }

    /// `void dav1d_data_unref(Dav1dData *data)`
    public static final class DataUnref {

        private static final MethodHandle FD_dav1d_data_unref = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DataUnref(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_data_unref");
        }

        public void call(MemorySegment data) {
            try {
                FD_dav1d_data_unref.invokeExact(address, data);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_data_unref", t);
            }
        }
    }

    /// `void dav1d_picture_unref(Dav1dPicture *p)`
    public static final class PictureUnref {

        private static final MethodHandle FD_dav1d_picture_unref = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        PictureUnref(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "dav1d_picture_unref");
        }

        public void call(MemorySegment picture) {
            try {
                FD_dav1d_picture_unref.invokeExact(address, picture);
            } catch (Throwable t) {
                throw Downcalls.failure("dav1d_picture_unref", t);
            }
        }
    }
}
