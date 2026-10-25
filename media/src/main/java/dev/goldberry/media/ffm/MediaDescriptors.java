package dev.goldberry.media.ffm;

import java.lang.foreign.FunctionDescriptor;
import java.util.LinkedHashSet;
import java.util.List;

import dev.goldberry.media.decoder.flac.FlacDecoder;
import dev.goldberry.media.ffm.calls.Dav1dCalls;
import dev.goldberry.media.ffm.calls.FlacCalls;
import dev.goldberry.media.ffm.calls.MediaCalls;
import dev.goldberry.media.ffm.calls.Mp3Calls;
import dev.goldberry.media.ffm.calls.OpusCalls;
import dev.goldberry.media.ffm.calls.TheoraCalls;
import dev.goldberry.media.ffm.calls.VorbisCalls;
import dev.goldberry.media.ffm.calls.VpxCalls;
import dev.goldberry.media.ffm.calls.YuvCalls;

/// Every foreign-call shape of the media bindings, for the module's
/// `reachability-metadata.json`, which `…media.nativeimage` writes. A foreign
/// call is registered because it exists, not because a run reached it.
///
/// `:natives`' `ForeignMetadata` does the same thing before asking: it
/// initialises every holder, so each `FD_…` constant has been through
/// [Downcalls#link] and is recorded. A traced run would record only what that
/// run happened to call. This records every holder there is.
///
/// Public in a package the module does not export, so that the writer beside
/// the system decoders' shapes can read it; the module, not the package, is
/// the fence, and nothing outside it sees this.
public final class MediaDescriptors {

    /// The `…Calls` records whose nested holders are the downcall surface.
    static final List<Class<?>> CALLS = List.of(
            MediaCalls.class,
            Dav1dCalls.class,
            VpxCalls.class,
            OpusCalls.class,
            VorbisCalls.class,
            TheoraCalls.class,
            FlacCalls.class,
            YuvCalls.class,
            Mp3Calls.class);

    /// Every upcall shape the decoders make, each once: the callbacks C calls
    /// back into Java with. Each decoder that has any lists them on a `UPCALLS`
    /// constant and is named here.
    public static final List<FunctionDescriptor> UPCALLS = upcalls();

    private MediaDescriptors() {}

    /// Every downcall shape, after initialising every holder.
    public static List<DowncallShape> downcalls() {
        for (var calls : CALLS) {
            for (var component : calls.getRecordComponents()) {
                try {
                    Class.forName(component.getType().getName(), true, calls.getClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return Downcalls.linked();
    }

    private static List<FunctionDescriptor> upcalls() {
        // The FLAC decoder's read, write and error callbacks; the dav1d and
        // libvpx decoders make none.
        var all = new LinkedHashSet<FunctionDescriptor>(FlacDecoder.UPCALLS);
        return List.copyOf(all);
    }
}
