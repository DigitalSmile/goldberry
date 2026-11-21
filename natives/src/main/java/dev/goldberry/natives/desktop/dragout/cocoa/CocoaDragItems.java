package dev.goldberry.natives.desktop.dragout.cocoa;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.Downcalls;
import dev.goldberry.natives.desktop.macos.ObjC;

/// The `NSDraggingItem`s of a drag out: one per file, as a file `NSURL`, and
/// one `NSPasteboardItem` whose data the drag's source provides when a target
/// asks, and the `NSImage` the first of them shows.
///
/// Main thread, inside an autorelease pool: what is made here is autoreleased
/// or handed to an array that is.
///
/// UNVERIFIED: written against AppKit's documentation.
public final class CocoaDragItems {

    /// `setDraggingFrame:contents:`: an `NSRect` by value and an object.
    static final FunctionDescriptor FRAME_AND_CONTENTS =
            Downcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ObjC.RECT, ADDRESS));

    /// `initWithSize:`.
    static final FunctionDescriptor WITH_SIZE =
            Downcalls.describe(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ObjC.SIZE));

    /// `windowNumberAtPoint:belowWindowWithWindowNumber:`.
    static final FunctionDescriptor WINDOW_AT =
            Downcalls.describe(FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, ObjC.POINT, JAVA_LONG));

    /// `convertPointFromScreen:`.
    static final FunctionDescriptor CONVERT_POINT =
            Downcalls.describe(FunctionDescriptor.of(ObjC.POINT, ADDRESS, ADDRESS, ObjC.POINT));

    /// `initWithBitmapDataPlanes:pixelsWide:pixelsHigh:bitsPerSample:
    /// samplesPerPixel:hasAlpha:isPlanar:colorSpaceName:bitmapFormat:
    /// bytesPerRow:bitsPerPixel:`.
    static final FunctionDescriptor BITMAP = Downcalls.describe(FunctionDescriptor.of(
            ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_BYTE, JAVA_BYTE,
            ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG));

    /// `NSBitmapFormatAlphaFirst | NSBitmapFormatThirtyTwoBitLittleEndian`:
    /// premultiplied BGRA in memory, which is what the toolkit paints.
    static final long BGRA_PREMULTIPLIED = 1L | (1L << 9);

    private CocoaDragItems() {}

    /// A pasteboard item whose `types` `provider` makes on request.
    public static MemorySegment pasteboardItem(ObjC objc, MemorySegment provider, List<String> types) {
        var item = objc.send(objc.send(objc.send(objc.cls("NSPasteboardItem"), "alloc"), "init"), "autorelease");
        var array = objc.send(objc.cls("NSMutableArray"), "array");
        for (var type : types) {
            objc.sendVoid(array, "addObject:", objc.string(type));
        }
        if (!objc.sendBoolean(item, "setDataProvider:forTypes:", provider, array)) {
            throw new IllegalStateException("the pasteboard item would not take its data provider");
        }
        return item;
    }

    /// The dragging items: a file `NSURL` for each of `files`, then `item` if
    /// there is one, every one framed at `frame` and the first showing
    /// `picture`.
    public static MemorySegment draggingItems(
            ObjC objc,
            List<Path> files,
            @Nullable MemorySegment item,
            ObjC.Rect frame,
            @Nullable MemorySegment picture) {
        var items = objc.send(objc.cls("NSMutableArray"), "array");
        var first = true;
        for (var file : files) {
            var url = objc.send(objc.cls("NSURL"), "fileURLWithPath:", objc.string(file.toString()));
            add(objc, items, url, frame, first ? picture : null);
            first = false;
        }
        if (item != null) {
            add(objc, items, item, frame, first ? picture : null);
        }
        return items;
    }

    private static void add(
            ObjC objc, MemorySegment items, MemorySegment writer, ObjC.Rect frame, @Nullable MemorySegment picture) {
        var dragging = objc.send(objc.send(objc.cls("NSDraggingItem"), "alloc"), "initWithPasteboardWriter:", writer);
        objc.sendVoid(dragging, "setDraggingFrame:contents:", frame, picture == null ? MemorySegment.NULL : picture);
        objc.sendVoid(items, "addObject:", dragging);
        objc.sendVoid(dragging, "release");
    }

    /// An autoreleased `NSImage` of premultiplied BGRA pixels, `scale` pixels
    /// to a point.
    @SuppressWarnings("restricted")
    public static MemorySegment image(ObjC objc, int width, int height, int stride, ByteBuffer pixels, int scale) {
        var representation = objc.send(
                objc.send(objc.cls("NSBitmapImageRep"), "alloc"),
                "initWithBitmapDataPlanes:pixelsWide:pixelsHigh:bitsPerSample:samplesPerPixel:hasAlpha:isPlanar:"
                        + "colorSpaceName:bitmapFormat:bytesPerRow:bitsPerPixel:",
                MemorySegment.NULL,
                (long) width,
                (long) height,
                8L,
                4L,
                true,
                false,
                objc.string("NSDeviceRGBColorSpace"),
                BGRA_PREMULTIPLIED,
                width * 4L,
                32L);
        if (representation.address() == 0) {
            throw new IllegalStateException("AppKit would not make a " + width + "x" + height + " bitmap");
        }
        try {
            var target = objc.send(representation, "bitmapData").reinterpret((long) width * height * 4);
            var source = MemorySegment.ofBuffer(pixels);
            for (var row = 0; row < height; row++) {
                MemorySegment.copy(
                        source, JAVA_BYTE, (long) row * stride, target, JAVA_BYTE, (long) row * width * 4, width * 4L);
            }
            var image = objc.send(
                    objc.send(objc.cls("NSImage"), "alloc"),
                    "initWithSize:",
                    new ObjC.Size((double) width / scale, (double) height / scale));
            objc.sendVoid(image, "addRepresentation:", representation);
            return objc.send(image, "autorelease");
        } finally {
            objc.sendVoid(representation, "release");
        }
    }

    /// The window number at `screen` among every application's windows.
    public static long windowNumberAt(ObjC objc, ObjC.Point screen) {
        return objc.sendLong(objc.cls("NSWindow"), "windowNumberAtPoint:belowWindowWithWindowNumber:", screen, 0L);
    }

    /// `screen` in `window`'s coordinates: points from its bottom-left corner.
    public static ObjC.Point fromScreen(ObjC objc, MemorySegment window, ObjC.Point screen) {
        return objc.sendPoint(window, "convertPointFromScreen:", screen);
    }
}
