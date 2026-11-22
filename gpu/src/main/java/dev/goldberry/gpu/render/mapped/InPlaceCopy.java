package dev.goldberry.gpu.render.mapped;

import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;

/// Where a copy out of a [MappedRaster] is recorded in somebody else's frame,
/// and what says that frame has run: [MappedRaster#upload(SdlGpuCommandBuffer.CopyPass, SdlGpuTexture, java.util.List,
/// MappedRaster.PendingCopy)]'s
/// arguments, taken from an application's copy pass.
///
/// @param pass        the copy pass the copies go in
/// @param destination the texture they go to, on the pass's device
/// @param finished    what the next paint waits on
public record InPlaceCopy(
        SdlGpuCommandBuffer.CopyPass pass, SdlGpuTexture destination, MappedRaster.PendingCopy finished) {

    /// Checks nothing is missing.
    public InPlaceCopy {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(finished, "finished");
    }
}
