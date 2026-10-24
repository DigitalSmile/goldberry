package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The stream decoder of libFLAC, which the FLAC decoder calls. It pulls its
/// bytes through a read callback and hands each decoded block to a write callback,
/// both upcalls into Java (`FlacDecoder`).
public record FlacCalls(
        NewDecoder newDecoder,
        DeleteDecoder deleteDecoder,
        SetMd5Checking setMd5Checking,
        SetMetadataIgnoreAll setMetadataIgnoreAll,
        InitStream initStream,
        Finish finish,
        Flush flush,
        Reset reset,
        ProcessSingle processSingle,
        GetState getState,
        GetResolvedStateString getResolvedStateString) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static FlacCalls bind(SymbolLookup lookup) {
        return new FlacCalls(
                new NewDecoder(lookup),
                new DeleteDecoder(lookup),
                new SetMd5Checking(lookup),
                new SetMetadataIgnoreAll(lookup),
                new InitStream(lookup),
                new Finish(lookup),
                new Flush(lookup),
                new Reset(lookup),
                new ProcessSingle(lookup),
                new GetState(lookup),
                new GetResolvedStateString(lookup));
    }

    /// `FLAC__StreamDecoder *FLAC__stream_decoder_new(void)`
    public static final class NewDecoder {

        private static final MethodHandle FD_FLAC__stream_decoder_new = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        NewDecoder(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_new");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_FLAC__stream_decoder_new.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_new", t);
            }
        }
    }

    /// `void FLAC__stream_decoder_delete(FLAC__StreamDecoder *decoder)`
    public static final class DeleteDecoder {

        private static final MethodHandle FD_FLAC__stream_decoder_delete =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DeleteDecoder(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_delete");
        }

        public void call(MemorySegment decoder) {
            try {
                FD_FLAC__stream_decoder_delete.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_delete", t);
            }
        }
    }

    /// `FLAC__bool FLAC__stream_decoder_set_md5_checking(FLAC__StreamDecoder *decoder, FLAC__bool
    /// value)`
    public static final class SetMd5Checking {

        private static final MethodHandle FD_FLAC__stream_decoder_set_md5_checking =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        SetMd5Checking(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_set_md5_checking");
        }

        public int call(MemorySegment decoder, int value) {
            try {
                return (int) FD_FLAC__stream_decoder_set_md5_checking.invokeExact(address, decoder, value);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_set_md5_checking", t);
            }
        }
    }

    /// `FLAC__bool FLAC__stream_decoder_set_metadata_ignore_all(FLAC__StreamDecoder *decoder)`
    public static final class SetMetadataIgnoreAll {

        private static final MethodHandle FD_FLAC__stream_decoder_set_metadata_ignore_all =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        SetMetadataIgnoreAll(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_set_metadata_ignore_all");
        }

        public int call(MemorySegment decoder) {
            try {
                return (int) FD_FLAC__stream_decoder_set_metadata_ignore_all.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_set_metadata_ignore_all", t);
            }
        }
    }

    /// `FLAC__StreamDecoderInitStatus FLAC__stream_decoder_init_stream(FLAC__StreamDecoder
    /// *decoder, FLAC__StreamDecoderReadCallback read_callback, FLAC__StreamDecoderSeekCallback
    /// seek_callback, FLAC__StreamDecoderTellCallback tell_callback,
    /// FLAC__StreamDecoderLengthCallback length_callback, FLAC__StreamDecoderEofCallback
    /// eof_callback, FLAC__StreamDecoderWriteCallback write_callback,
    /// FLAC__StreamDecoderMetadataCallback metadata_callback, FLAC__StreamDecoderErrorCallback
    /// error_callback, void *client_data)`
    ///
    /// The seek, tell, length, eof and metadata callbacks may be null.
    public static final class InitStream {

        private static final MethodHandle FD_FLAC__stream_decoder_init_stream = Downcalls.link(FunctionDescriptor.of(
                JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        InitStream(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_init_stream");
        }

        public int call(
                MemorySegment decoder,
                MemorySegment read,
                MemorySegment seek,
                MemorySegment tell,
                MemorySegment length,
                MemorySegment eof,
                MemorySegment write,
                MemorySegment metadata,
                MemorySegment error,
                MemorySegment clientData) {
            try {
                return (int) FD_FLAC__stream_decoder_init_stream.invokeExact(
                        address, decoder, read, seek, tell, length, eof, write, metadata, error, clientData);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_init_stream", t);
            }
        }
    }

    /// `FLAC__bool FLAC__stream_decoder_finish(FLAC__StreamDecoder *decoder)`
    public static final class Finish {

        private static final MethodHandle FD_FLAC__stream_decoder_finish =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Finish(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_finish");
        }

        public int call(MemorySegment decoder) {
            try {
                return (int) FD_FLAC__stream_decoder_finish.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_finish", t);
            }
        }
    }

    /// `FLAC__bool FLAC__stream_decoder_flush(FLAC__StreamDecoder *decoder)`
    public static final class Flush {

        private static final MethodHandle FD_FLAC__stream_decoder_flush =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Flush(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_flush");
        }

        public int call(MemorySegment decoder) {
            try {
                return (int) FD_FLAC__stream_decoder_flush.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_flush", t);
            }
        }
    }

    /// `FLAC__bool FLAC__stream_decoder_reset(FLAC__StreamDecoder *decoder)`
    public static final class Reset {

        private static final MethodHandle FD_FLAC__stream_decoder_reset =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Reset(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_reset");
        }

        public int call(MemorySegment decoder) {
            try {
                return (int) FD_FLAC__stream_decoder_reset.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_reset", t);
            }
        }
    }

    /// `FLAC__bool FLAC__stream_decoder_process_single(FLAC__StreamDecoder *decoder)`
    ///
    /// Decodes one frame, or reads one metadata block, calling the callbacks. False means an error
    /// the error callback saw, or the end of the stream; `getState` says which.
    public static final class ProcessSingle {

        private static final MethodHandle FD_FLAC__stream_decoder_process_single =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ProcessSingle(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_process_single");
        }

        public int call(MemorySegment decoder) {
            try {
                return (int) FD_FLAC__stream_decoder_process_single.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_process_single", t);
            }
        }
    }

    /// `FLAC__StreamDecoderState FLAC__stream_decoder_get_state(const FLAC__StreamDecoder
    /// *decoder)`
    public static final class GetState {

        private static final MethodHandle FD_FLAC__stream_decoder_get_state =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetState(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_get_state");
        }

        public int call(MemorySegment decoder) {
            try {
                return (int) FD_FLAC__stream_decoder_get_state.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_get_state", t);
            }
        }
    }

    /// `const char *FLAC__stream_decoder_get_resolved_state_string(const FLAC__StreamDecoder
    /// *decoder)`
    public static final class GetResolvedStateString {

        private static final MethodHandle FD_FLAC__stream_decoder_get_resolved_state_string =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetResolvedStateString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "FLAC__stream_decoder_get_resolved_state_string");
        }

        public MemorySegment call(MemorySegment decoder) {
            try {
                return (MemorySegment) FD_FLAC__stream_decoder_get_resolved_state_string.invokeExact(address, decoder);
            } catch (Throwable t) {
                throw Downcalls.failure("FLAC__stream_decoder_get_resolved_state_string", t);
            }
        }
    }
}
