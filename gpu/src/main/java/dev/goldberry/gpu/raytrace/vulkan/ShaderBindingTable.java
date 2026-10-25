package dev.goldberry.gpu.raytrace.vulkan;

/// Where each shader group's handle goes in the shader binding table: one
/// ray-generation record, then the miss records, then the hit records, each
/// region starting on the device's base alignment.
///
/// Vulkan's rules, which [#of] follows: a record's stride is the handle size
/// rounded up to the handle alignment; a region starts on the base alignment;
/// the ray-generation region's size is its stride.
///
/// @param raygen the ray-generation region
/// @param miss   the miss region, one record a miss shader
/// @param hit    the hit region, one record a hit group
/// @param size   the whole table, in bytes
record ShaderBindingTable(Region raygen, Region miss, Region hit, long size) {

    /// One region of the table.
    ///
    /// @param offset where it starts in the table
    /// @param stride from one record to the next
    /// @param size   how many bytes it covers
    /// @param count  how many records it holds
    record Region(long offset, long stride, long size, int count) {

        /// Where record `index` of it starts in the table.
        long record(int index) {
            if (index < 0 || index >= count) {
                throw new IndexOutOfBoundsException("record " + index + " of " + count);
            }
            return offset + index * stride;
        }
    }

    /// The layout for `missCount` miss records and `hitCount` hit groups on a
    /// device whose handles are `handleSize` bytes, placed on
    /// `handleAlignment`, in regions starting on `baseAlignment`.
    ///
    /// @throws IllegalArgumentException when a size or alignment is not
    ///                                  positive, or a count is
    static ShaderBindingTable of(int handleSize, int handleAlignment, int baseAlignment, int missCount, int hitCount) {
        if (handleSize <= 0 || handleAlignment <= 0 || baseAlignment <= 0) {
            throw new IllegalArgumentException("handle size " + handleSize + ", alignment " + handleAlignment
                    + ", base alignment " + baseAlignment);
        }
        if (missCount < 1 || hitCount < 1) {
            throw new IllegalArgumentException(missCount + " miss records and " + hitCount + " hit groups");
        }
        var stride = DeviceBuffer.align(handleSize, handleAlignment);
        var raygenStride = DeviceBuffer.align(stride, baseAlignment);
        var raygen = new Region(0, raygenStride, raygenStride, 1);
        var miss = new Region(raygen.size(), stride, DeviceBuffer.align(missCount * stride, baseAlignment), missCount);
        var hit = new Region(
                miss.offset() + miss.size(), stride, DeviceBuffer.align(hitCount * stride, baseAlignment), hitCount);
        return new ShaderBindingTable(raygen, miss, hit, hit.offset() + hit.size());
    }

    /// Where each group's handle goes, in the order the groups were made:
    /// ray generation, the miss shaders, then the hit groups.
    long[] groupOffsets() {
        var offsets = new long[1 + miss.count() + hit.count()];
        offsets[0] = raygen.record(0);
        for (var i = 0; i < miss.count(); i++) {
            offsets[1 + i] = miss.record(i);
        }
        for (var i = 0; i < hit.count(); i++) {
            offsets[1 + miss.count() + i] = hit.record(i);
        }
        return offsets;
    }
}
