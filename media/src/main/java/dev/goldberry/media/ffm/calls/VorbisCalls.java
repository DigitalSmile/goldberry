package dev.goldberry.media.ffm.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.media.ffm.Downcalls;

/// The synthesis half of libvorbis, which the Vorbis decoder calls: the three
/// header packets in, then one audio packet at a time through a block into the
/// DSP state, and the float planes out.
public record VorbisCalls(
        VersionString versionString,
        InfoInit infoInit,
        InfoClear infoClear,
        CommentInit commentInit,
        CommentClear commentClear,
        SynthesisHeaderin synthesisHeaderin,
        SynthesisInit synthesisInit,
        SynthesisRestart synthesisRestart,
        BlockInit blockInit,
        BlockClear blockClear,
        DspClear dspClear,
        Synthesis synthesis,
        SynthesisBlockin synthesisBlockin,
        SynthesisPcmout synthesisPcmout,
        SynthesisRead synthesisRead) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static VorbisCalls bind(SymbolLookup lookup) {
        return new VorbisCalls(
                new VersionString(lookup),
                new InfoInit(lookup),
                new InfoClear(lookup),
                new CommentInit(lookup),
                new CommentClear(lookup),
                new SynthesisHeaderin(lookup),
                new SynthesisInit(lookup),
                new SynthesisRestart(lookup),
                new BlockInit(lookup),
                new BlockClear(lookup),
                new DspClear(lookup),
                new Synthesis(lookup),
                new SynthesisBlockin(lookup),
                new SynthesisPcmout(lookup),
                new SynthesisRead(lookup));
    }

    /// `const char *vorbis_version_string(void)`
    public static final class VersionString {

        private static final MethodHandle FD_vorbis_version_string = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        VersionString(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_version_string");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_vorbis_version_string.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_version_string", t);
            }
        }
    }

    /// `void vorbis_info_init(vorbis_info *vi)`
    public static final class InfoInit {

        private static final MethodHandle FD_vorbis_info_init = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        InfoInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_info_init");
        }

        public void call(MemorySegment info) {
            try {
                FD_vorbis_info_init.invokeExact(address, info);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_info_init", t);
            }
        }
    }

    /// `void vorbis_info_clear(vorbis_info *vi)`
    public static final class InfoClear {

        private static final MethodHandle FD_vorbis_info_clear = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        InfoClear(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_info_clear");
        }

        public void call(MemorySegment info) {
            try {
                FD_vorbis_info_clear.invokeExact(address, info);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_info_clear", t);
            }
        }
    }

    /// `void vorbis_comment_init(vorbis_comment *vc)`
    public static final class CommentInit {

        private static final MethodHandle FD_vorbis_comment_init = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        CommentInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_comment_init");
        }

        public void call(MemorySegment comment) {
            try {
                FD_vorbis_comment_init.invokeExact(address, comment);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_comment_init", t);
            }
        }
    }

    /// `void vorbis_comment_clear(vorbis_comment *vc)`
    public static final class CommentClear {

        private static final MethodHandle FD_vorbis_comment_clear = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        CommentClear(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_comment_clear");
        }

        public void call(MemorySegment comment) {
            try {
                FD_vorbis_comment_clear.invokeExact(address, comment);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_comment_clear", t);
            }
        }
    }

    /// `int vorbis_synthesis_headerin(vorbis_info *vi, vorbis_comment *vc, ogg_packet *op)`
    public static final class SynthesisHeaderin {

        private static final MethodHandle FD_vorbis_synthesis_headerin =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SynthesisHeaderin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_synthesis_headerin");
        }

        public int call(MemorySegment info, MemorySegment comment, MemorySegment packet) {
            try {
                return (int) FD_vorbis_synthesis_headerin.invokeExact(address, info, comment, packet);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_synthesis_headerin", t);
            }
        }
    }

    /// `int vorbis_synthesis_init(vorbis_dsp_state *v, vorbis_info *vi)`
    public static final class SynthesisInit {

        private static final MethodHandle FD_vorbis_synthesis_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SynthesisInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_synthesis_init");
        }

        public int call(MemorySegment dsp, MemorySegment info) {
            try {
                return (int) FD_vorbis_synthesis_init.invokeExact(address, dsp, info);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_synthesis_init", t);
            }
        }
    }

    /// `int vorbis_synthesis_restart(vorbis_dsp_state *v)`
    public static final class SynthesisRestart {

        private static final MethodHandle FD_vorbis_synthesis_restart =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        SynthesisRestart(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_synthesis_restart");
        }

        public int call(MemorySegment dsp) {
            try {
                return (int) FD_vorbis_synthesis_restart.invokeExact(address, dsp);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_synthesis_restart", t);
            }
        }
    }

    /// `int vorbis_block_init(vorbis_dsp_state *v, vorbis_block *vb)`
    public static final class BlockInit {

        private static final MethodHandle FD_vorbis_block_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        BlockInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_block_init");
        }

        public int call(MemorySegment dsp, MemorySegment block) {
            try {
                return (int) FD_vorbis_block_init.invokeExact(address, dsp, block);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_block_init", t);
            }
        }
    }

    /// `int vorbis_block_clear(vorbis_block *vb)`
    public static final class BlockClear {

        private static final MethodHandle FD_vorbis_block_clear =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        BlockClear(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_block_clear");
        }

        public int call(MemorySegment block) {
            try {
                return (int) FD_vorbis_block_clear.invokeExact(address, block);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_block_clear", t);
            }
        }
    }

    /// `void vorbis_dsp_clear(vorbis_dsp_state *v)`
    public static final class DspClear {

        private static final MethodHandle FD_vorbis_dsp_clear = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DspClear(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_dsp_clear");
        }

        public void call(MemorySegment dsp) {
            try {
                FD_vorbis_dsp_clear.invokeExact(address, dsp);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_dsp_clear", t);
            }
        }
    }

    /// `int vorbis_synthesis(vorbis_block *vb, ogg_packet *op)`
    public static final class Synthesis {

        private static final MethodHandle FD_vorbis_synthesis =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Synthesis(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_synthesis");
        }

        public int call(MemorySegment block, MemorySegment packet) {
            try {
                return (int) FD_vorbis_synthesis.invokeExact(address, block, packet);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_synthesis", t);
            }
        }
    }

    /// `int vorbis_synthesis_blockin(vorbis_dsp_state *v, vorbis_block *vb)`
    public static final class SynthesisBlockin {

        private static final MethodHandle FD_vorbis_synthesis_blockin =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SynthesisBlockin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_synthesis_blockin");
        }

        public int call(MemorySegment dsp, MemorySegment block) {
            try {
                return (int) FD_vorbis_synthesis_blockin.invokeExact(address, dsp, block);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_synthesis_blockin", t);
            }
        }
    }

    /// `int vorbis_synthesis_pcmout(vorbis_dsp_state *v, float ***pcm)`
    ///
    /// The samples per channel ready; `*pcm` then points at one float plane per channel.
    public static final class SynthesisPcmout {

        private static final MethodHandle FD_vorbis_synthesis_pcmout =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SynthesisPcmout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_synthesis_pcmout");
        }

        public int call(MemorySegment dsp, MemorySegment pcmOut) {
            try {
                return (int) FD_vorbis_synthesis_pcmout.invokeExact(address, dsp, pcmOut);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_synthesis_pcmout", t);
            }
        }
    }

    /// `int vorbis_synthesis_read(vorbis_dsp_state *v, int samples)`
    public static final class SynthesisRead {

        private static final MethodHandle FD_vorbis_synthesis_read =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        SynthesisRead(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "vorbis_synthesis_read");
        }

        public int call(MemorySegment dsp, int samples) {
            try {
                return (int) FD_vorbis_synthesis_read.invokeExact(address, dsp, samples);
            } catch (Throwable t) {
                throw Downcalls.failure("vorbis_synthesis_read", t);
            }
        }
    }
}
