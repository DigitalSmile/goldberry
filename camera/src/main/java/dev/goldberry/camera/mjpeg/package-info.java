/// Motion JPEG, as USB cameras send their largest sizes and fastest rates:
/// each frame decoded by the rasterizer's JPEG decoder into a pool of BGRA
/// pictures, with JPEG's standard Huffman tables put in where a camera leaves
/// them out. Not exported; the camera's capture thread is the one caller.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
@NullMarked
package dev.goldberry.camera.mjpeg;

import org.jspecify.annotations.NullMarked;
