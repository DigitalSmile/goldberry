package dev.goldberry.gpu.render.staging;

import java.util.Objects;

/// How staged rows and regions are aligned so that a driver copies them into
/// a texture as they are, rather than copying them again first.
///
/// Direct3D 12 copies from a buffer into a texture only when each row starts a
/// multiple of 256 bytes after the last and the region starts at a multiple of
/// 512. SDL hides that rule: without the driver's unrestricted-pitch feature it
/// copies any other upload, row by row on the CPU, into a temporary buffer it
/// allocates for that one upload. SDL also takes a row pitch as the region's
/// packed row rounded up to the alignment, and nothing longer, so the pitch is
/// exactly [#pitch], not any multiple of the alignment.
///
/// Every other driver copies packed rows from any whole texel, so [#PACKED]
/// leaves them packed, as before.
///
/// @param rowBytes    the multiple of bytes a staged row is rounded up to
/// @param regionBytes the multiple of bytes a staged region starts at
public record UploadAlignment(int rowBytes, int regionBytes) {

    /// Rows packed and regions back to back: Vulkan and Metal.
    public static final UploadAlignment PACKED = new UploadAlignment(1, 1);

    /// Rows padded to 256 bytes and regions at multiples of 512: Direct3D 12's
    /// `D3D12_TEXTURE_DATA_PITCH_ALIGNMENT` and
    /// `D3D12_TEXTURE_DATA_PLACEMENT_ALIGNMENT`.
    public static final UploadAlignment DIRECT3D12 = new UploadAlignment(256, 512);

    /// Checks both are powers of two.
    ///
    /// @throws IllegalArgumentException when either is not
    public UploadAlignment {
        requirePowerOfTwo("row", rowBytes);
        requirePowerOfTwo("region", regionBytes);
    }

    /// The alignment uploads on `driver` take, by SDL's name for it.
    public static UploadAlignment of(String driver) {
        Objects.requireNonNull(driver, "driver");
        return switch (driver) {
            case "direct3d12" -> DIRECT3D12;
            default -> PACKED;
        };
    }

    /// Whether rows are staged longer than they are.
    public boolean padsRows() {
        return rowBytes > 1;
    }

    /// The bytes from one staged row to the next, for rows of `packedRow`
    /// bytes.
    ///
    /// @throws IllegalArgumentException when `packedRow` is not positive, or the
    ///                                  padded row is past 2 GiB
    public int pitch(long packedRow) {
        if (packedRow <= 0) {
            throw new IllegalArgumentException("a row of " + packedRow + " bytes");
        }
        var padded = align(packedRow, rowBytes);
        if (padded > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a row of " + packedRow + " bytes is past 2 GiB, padded");
        }
        return (int) padded;
    }

    /// Where a region staged at or after byte `at` starts.
    public long place(long at) {
        return align(at, regionBytes);
    }

    private static long align(long value, int to) {
        return (value + to - 1) & -to;
    }

    private static void requirePowerOfTwo(String what, int value) {
        if (value <= 0 || Integer.bitCount(value) != 1) {
            throw new IllegalArgumentException("a " + what + " alignment of " + value + " is not a power of two");
        }
    }
}
