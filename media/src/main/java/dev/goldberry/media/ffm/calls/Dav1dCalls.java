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
/// the send-data/get-picture loop, a packet's bytes in a buffer of dav1d's own,
/// and releasing what was handed back.
public record Dav1dCalls(
        DefaultSettings defaultSettings,
        Open open,
        SendData sendData,
        GetPicture getPicture,
        Close close,
        Flush flush,
        DataCreate dataCreate,
        DataUnref dataUnref,
        PictureUnref pictureUnref) {

    private static final java.lang.foreign.ValueLayout SIZE_T = NativeTypes.SIZE_T;

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static Dav1dCalls bind(SymbolLookup lookup) {
        return new Dav1dCalls(
                new DefaultSettings(lookup),
                new Open(lookup),
                new SendData(lookup),
                new GetPicture(lookup),
                new Close(lookup),
                new Flush(lookup),
                new DataCreate(lookup),
                new DataUnref(lookup),
                new PictureUnref(lookup));
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
