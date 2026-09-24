package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuCommandCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuDeviceCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlGpuResourceCalls;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlPropertiesCalls;

/// The four holder records this package calls through, bound once, the first
/// time anything here is used: the lazy holder idiom every binding class uses.
///
/// @param device     device creation and queries
/// @param resources  textures and transfer buffers
/// @param commands   command buffers, passes and fences
/// @param properties the property groups a device is configured with
record GpuCalls(
        SdlGpuDeviceCalls device,
        SdlGpuResourceCalls resources,
        SdlGpuCommandCalls commands,
        SdlPropertiesCalls properties) {

    /// The bound calls.
    static GpuCalls get() {
        return Holder.CALLS;
    }

    private static final class Holder {
        private static final GpuCalls CALLS = bind();

        private static GpuCalls bind() {
            var lookup = NativeLibrary.get().lookup();
            return new GpuCalls(
                    SdlGpuDeviceCalls.bind(lookup),
                    SdlGpuResourceCalls.bind(lookup),
                    SdlGpuCommandCalls.bind(lookup),
                    SdlPropertiesCalls.bind(lookup));
        }
    }
}
