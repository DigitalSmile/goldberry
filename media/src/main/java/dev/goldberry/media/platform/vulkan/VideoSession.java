package dev.goldberry.media.platform.vulkan;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;

/// One decoder's Vulkan video session: the session and its memory, the decoded
/// picture buffer, a bitstream buffer and a read-back buffer, both mapped, and
/// the commands that decode a picture into a slot and copy it back.
///
/// The picture buffer is one image of as many array layers as slots, in
/// `VIDEO_DECODE_DPB` layout throughout, shared by the decode and copy queue
/// families so that no ownership moves between them. A decoded picture is also
/// the output (the capability every decoder here asks for), so the copy reads
/// the slot it was decoded into: layer to `TRANSFER_SRC`, copied, and back.
///
/// **One picture at a time**: decode, then copy, then the CPU waits for both,
/// and the planes are in the read-back buffer until the next picture. A
/// decoder's thread is the only one that calls it.
public final class VideoSession implements AutoCloseable {

    /// What a codec hands over for one picture.
    ///
    /// @param bitstream   the picture's bytes, as the codec's decode expects
    ///                    them: H.264's and HEVC's slices each after a start
    ///                    code, VP9's frame, AV1's tile groups
    /// @param pictureInfo the codec's picture info, `pNext` of
    ///                    `VkVideoDecodeInfoKHR`, allocated in the arena given
    /// @param setup       the slot the picture is decoded into, and its codec
    ///                    slot info
    /// @param references  the slots it refers to
    /// @param parameters  the session parameters, or 0 for a codec with none
    /// @param width       its coded width, or 0 for the session's
    /// @param height      its coded height, or 0 for the session's
    @SuppressWarnings("ArrayRecordComponent")
    public record Picture(
            byte[] bitstream,
            MemorySegment pictureInfo,
            Slot setup,
            List<Slot> references,
            long parameters,
            int width,
            int height) {

        public Picture {
            references = List.copyOf(references);
        }

        /// A picture of the session's coded size.
        public Picture(
                byte[] bitstream, MemorySegment pictureInfo, Slot setup, List<Slot> references, long parameters) {
            this(bitstream, pictureInfo, setup, references, parameters, 0, 0);
        }
    }

    /// A picture buffer slot as a decode names it: its index, and the codec's
    /// `VkVideoDecode…DpbSlotInfoKHR`, or `NULL`.
    public record Slot(int index, MemorySegment codecInfo) {}

    private final VideoDevice device;
    private final VideoProfile profile;
    private final VideoCapabilities capabilities;
    private final Arena arena = Arena.ofShared();
    private final int codedWidth;
    private final int codedHeight;
    // The pictures' own coded size, which the images are aligned up from.
    private final int extentWidth;
    private final int extentHeight;
    // The coded size of the picture being decoded: the session's, or its own.
    private int pictureWidth;
    private int pictureHeight;
    private final int slots;
    private final long session;
    private final List<Long> sessionMemory = new ArrayList<>();
    private final long image;
    private final long imageMemory;
    private final long view;
    private long bitstreamBuffer;
    private long bitstreamMemory;
    private MemorySegment bitstreamMapped = MemorySegment.NULL;
    private long bitstreamSize;
    private final long readbackBuffer;
    private final long readbackMemory;
    private final MemorySegment readbackMapped;
    private final long readbackChroma;
    private final long decodePool;
    private final long copyPool;
    private final MemorySegment decodeCommands;
    private final MemorySegment copyCommands;
    private final long fence;
    private final long semaphore;
    private boolean started;
    private boolean closed;

    /// A session for `profile` decoding pictures of up to `width` by `height`
    /// into `slots` slots.
    ///
    /// @throws VulkanException when the device refuses any of it
    public VideoSession(
            VideoDevice device,
            VideoProfile profile,
            VideoCapabilities capabilities,
            int width,
            int height,
            int slots) {
        this.device = device;
        this.profile = profile;
        this.capabilities = capabilities;
        this.codedWidth = align(width, capabilities.granularityWidth());
        this.codedHeight = align(height, capabilities.granularityHeight());
        this.extentWidth = width;
        this.extentHeight = height;
        this.pictureWidth = width;
        this.pictureHeight = height;
        this.slots = slots;
        var f = device.functions();
        var handle = device.handle();
        try (var call = Arena.ofConfined()) {
            // The session.
            var version = Structs.allocate(call, VkStructs.VkExtensionProperties.LAYOUT);
            version.setString(
                    VkStructs.VkExtensionProperties.extensionName,
                    profile.codec().stdName());
            version.set(
                    JAVA_INT,
                    VkStructs.VkExtensionProperties.specVersion,
                    profile.codec().stdVersion());
            var info = Structs.allocate(
                    call,
                    VkStructs.VkVideoSessionCreateInfoKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_SESSION_CREATE_INFO_KHR);
            info.set(JAVA_INT, VkStructs.VkVideoSessionCreateInfoKHR.queueFamilyIndex, device.decodeFamily());
            info.set(ADDRESS, VkStructs.VkVideoSessionCreateInfoKHR.pVideoProfile, profile.write(call));
            info.set(JAVA_INT, VkStructs.VkVideoSessionCreateInfoKHR.pictureFormat, capabilities.format());
            info.set(JAVA_INT, VkStructs.VkVideoSessionCreateInfoKHR.maxCodedExtent, codedWidth);
            info.set(JAVA_INT, VkStructs.VkVideoSessionCreateInfoKHR.maxCodedExtent + 4, codedHeight);
            info.set(JAVA_INT, VkStructs.VkVideoSessionCreateInfoKHR.referencePictureFormat, capabilities.format());
            info.set(JAVA_INT, VkStructs.VkVideoSessionCreateInfoKHR.maxDpbSlots, slots);
            info.set(
                    JAVA_INT,
                    VkStructs.VkVideoSessionCreateInfoKHR.maxActiveReferencePictures,
                    Math.min(slots - 1, capabilities.maxActiveReferences()));
            info.set(ADDRESS, VkStructs.VkVideoSessionCreateInfoKHR.pStdHeaderVersion, version);
            var out = call.allocate(JAVA_LONG);
            VulkanException.check(
                    device.video().createVideoSessionKHR().call(handle, info, MemorySegment.NULL, out),
                    "vkCreateVideoSessionKHR");
            this.session = out.get(JAVA_LONG, 0);
            bindSessionMemory(call);

            // The picture buffer: one image, a layer a slot.
            var families = call.allocateFrom(JAVA_INT, device.decodeFamily(), device.transferFamily());
            var imageInfo = Structs.allocate(
                    call, VkStructs.VkImageCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);
            Structs.chain(imageInfo, profile.writeList(call));
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.imageType, VkConstants.VK_IMAGE_TYPE_2D);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.format, capabilities.format());
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.extent, codedWidth);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.extent + 4, codedHeight);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.extent + 8, 1);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.mipLevels, 1);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.arrayLayers, slots);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.samples, VkConstants.VK_SAMPLE_COUNT_1_BIT);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.tiling, VkConstants.VK_IMAGE_TILING_OPTIMAL);
            imageInfo.set(
                    JAVA_INT,
                    VkStructs.VkImageCreateInfo.usage,
                    VkConstants.VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR
                            | VkConstants.VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR
                            | VkConstants.VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
            var shared = device.decodeFamily() != device.transferFamily();
            imageInfo.set(
                    JAVA_INT,
                    VkStructs.VkImageCreateInfo.sharingMode,
                    shared ? VkConstants.VK_SHARING_MODE_CONCURRENT : VkConstants.VK_SHARING_MODE_EXCLUSIVE);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.queueFamilyIndexCount, shared ? 2 : 0);
            imageInfo.set(
                    ADDRESS, VkStructs.VkImageCreateInfo.pQueueFamilyIndices, shared ? families : MemorySegment.NULL);
            imageInfo.set(JAVA_INT, VkStructs.VkImageCreateInfo.initialLayout, VkConstants.VK_IMAGE_LAYOUT_UNDEFINED);
            VulkanException.check(f.createImage().call(handle, imageInfo, MemorySegment.NULL, out), "vkCreateImage");
            this.image = out.get(JAVA_LONG, 0);
            var requirements = Structs.allocate(call, VkStructs.VkMemoryRequirements.LAYOUT);
            f.getImageMemoryRequirements().call(handle, image, requirements);
            this.imageMemory = allocate(call, requirements, VkConstants.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VulkanException.check(f.bindImageMemory().call(handle, image, imageMemory, 0), "vkBindImageMemory");

            var usage = Structs.allocate(
                    call,
                    VkStructs.VkImageViewUsageCreateInfo.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_IMAGE_VIEW_USAGE_CREATE_INFO);
            usage.set(
                    JAVA_INT,
                    VkStructs.VkImageViewUsageCreateInfo.usage,
                    VkConstants.VK_IMAGE_USAGE_VIDEO_DECODE_DPB_BIT_KHR
                            | VkConstants.VK_IMAGE_USAGE_VIDEO_DECODE_DST_BIT_KHR);
            var viewInfo = Structs.allocate(
                    call, VkStructs.VkImageViewCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);
            Structs.chain(viewInfo, usage);
            viewInfo.set(JAVA_LONG, VkStructs.VkImageViewCreateInfo.image, image);
            viewInfo.set(JAVA_INT, VkStructs.VkImageViewCreateInfo.viewType, VkConstants.VK_IMAGE_VIEW_TYPE_2D_ARRAY);
            viewInfo.set(JAVA_INT, VkStructs.VkImageViewCreateInfo.format, capabilities.format());
            var range = VkStructs.VkImageViewCreateInfo.subresourceRange;
            viewInfo.set(
                    JAVA_INT,
                    range + VkStructs.VkImageSubresourceRange.aspectMask,
                    VkConstants.VK_IMAGE_ASPECT_COLOR_BIT);
            viewInfo.set(JAVA_INT, range + VkStructs.VkImageSubresourceRange.levelCount, 1);
            viewInfo.set(JAVA_INT, range + VkStructs.VkImageSubresourceRange.layerCount, slots);
            VulkanException.check(
                    f.createImageView().call(handle, viewInfo, MemorySegment.NULL, out), "vkCreateImageView");
            this.view = out.get(JAVA_LONG, 0);

            // The read-back buffer: both planes of the largest picture.
            var sampleBytes = profile.bitDepth() > 8 ? 2 : 1;
            this.readbackChroma = align((long) codedWidth * codedHeight * sampleBytes, 256);
            var readbackSize = readbackChroma + (long) codedWidth * ((codedHeight + 1) / 2) * sampleBytes;
            var buffer = buffer(
                    call,
                    readbackSize,
                    VkConstants.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    false,
                    VkConstants.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkConstants.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            this.readbackBuffer = buffer[0];
            this.readbackMemory = buffer[1];
            this.readbackMapped = map(call, readbackMemory, readbackSize);
            growBitstream(call, 4L << 20);

            // Commands, a fence, and the semaphore between the two queues.
            this.decodePool = commandPool(call, device.decodeFamily());
            this.copyPool = commandPool(call, device.transferFamily());
            this.decodeCommands = commandBuffer(call, decodePool);
            this.copyCommands = commandBuffer(call, copyPool);
            var fenceInfo = Structs.allocate(
                    call, VkStructs.VkFenceCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);
            VulkanException.check(f.createFence().call(handle, fenceInfo, MemorySegment.NULL, out), "vkCreateFence");
            this.fence = out.get(JAVA_LONG, 0);
            var semaphoreInfo = Structs.allocate(
                    call, VkStructs.VkSemaphoreCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VulkanException.check(
                    f.createSemaphore().call(handle, semaphoreInfo, MemorySegment.NULL, out), "vkCreateSemaphore");
            this.semaphore = out.get(JAVA_LONG, 0);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    /// Session parameters made from `codecInfo`, the codec's
    /// `VkVideoDecode…SessionParametersCreateInfoKHR` chained to the create info.
    public long parameters(MemorySegment codecInfo) {
        try (var call = Arena.ofConfined()) {
            var info = Structs.allocate(
                    call,
                    VkStructs.VkVideoSessionParametersCreateInfoKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_SESSION_PARAMETERS_CREATE_INFO_KHR);
            Structs.chain(info, codecInfo);
            info.set(JAVA_LONG, VkStructs.VkVideoSessionParametersCreateInfoKHR.videoSession, session);
            var out = call.allocate(JAVA_LONG);
            VulkanException.check(
                    device.video()
                            .createVideoSessionParametersKHR()
                            .call(device.handle(), info, MemorySegment.NULL, out),
                    "vkCreateVideoSessionParametersKHR");
            return out.get(JAVA_LONG, 0);
        }
    }

    /// Destroys session parameters this session made.
    public void destroyParameters(long parameters) {
        if (parameters != 0) {
            device.video().destroyVideoSessionParametersKHR().call(device.handle(), parameters, MemorySegment.NULL);
        }
    }

    /// The planes of a picture copied back: luma, then interleaved chroma, each
    /// `stride` bytes a row, in the read-back buffer until the next decode.
    ///
    /// @param luma   the luma plane
    /// @param chroma the chroma plane
    /// @param stride bytes a row of either
    public record Planes(MemorySegment luma, MemorySegment chroma, int stride) {}

    /// Decodes the picture `build` makes in an arena of this call's, into its
    /// setup slot, and copies `width` by `height` of it from (`x`, `y`) back.
    ///
    /// @throws VulkanException when a call fails or the device is lost
    public Planes decode(Function<Arena, Picture> build, int x, int y, int width, int height) {
        if (closed) {
            throw new IllegalStateException("the video session is closed");
        }
        var f = device.functions();
        try (var call = Arena.ofConfined()) {
            var picture = build.apply(call);
            pictureWidth = picture.width() > 0 ? Math.min(picture.width(), codedWidth) : extentWidth;
            pictureHeight = picture.height() > 0 ? Math.min(picture.height(), codedHeight) : extentHeight;
            var bitstream = picture.bitstream();
            var range = align(bitstream.length, capabilities.minBitstreamSizeAlignment());
            if (range > bitstreamSize) {
                growBitstream(call, Math.max(range, bitstreamSize * 2));
            }
            MemorySegment.copy(MemorySegment.ofArray(bitstream), 0, bitstreamMapped, 0, bitstream.length);
            bitstreamMapped.asSlice(bitstream.length, range - bitstream.length).fill((byte) 0);

            // Decode.
            VulkanException.check(f.resetCommandBuffer().call(decodeCommands, 0), "vkResetCommandBuffer");
            begin(call, decodeCommands);
            if (!started) {
                barrier(
                        call,
                        decodeCommands,
                        0,
                        slots,
                        VkConstants.VK_IMAGE_LAYOUT_UNDEFINED,
                        VkConstants.VK_IMAGE_LAYOUT_VIDEO_DECODE_DPB_KHR,
                        VkConstants.VK_PIPELINE_STAGE_2_NONE,
                        0,
                        VkConstants.VK_PIPELINE_STAGE_2_VIDEO_DECODE_BIT_KHR,
                        VkConstants.VK_ACCESS_2_VIDEO_DECODE_WRITE_BIT_KHR
                                | VkConstants.VK_ACCESS_2_VIDEO_DECODE_READ_BIT_KHR);
            }
            var slotLayout = VkStructs.VkVideoReferenceSlotInfoKHR.LAYOUT;
            var references = picture.references();
            // Begin coding names every slot in play: the references, and the
            // setup slot as not yet holding a picture.
            var bound = Structs.array(
                    call,
                    slotLayout,
                    references.size() + 1,
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_REFERENCE_SLOT_INFO_KHR);
            for (var i = 0; i < references.size(); i++) {
                slot(
                        call,
                        Structs.at(bound, slotLayout, i),
                        references.get(i).index(),
                        references.get(i).index(),
                        MemorySegment.NULL);
            }
            slot(
                    call,
                    Structs.at(bound, slotLayout, references.size()),
                    -1,
                    picture.setup().index(),
                    MemorySegment.NULL);
            var beginInfo = Structs.allocate(
                    call,
                    VkStructs.VkVideoBeginCodingInfoKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_BEGIN_CODING_INFO_KHR);
            beginInfo.set(JAVA_LONG, VkStructs.VkVideoBeginCodingInfoKHR.videoSession, session);
            beginInfo.set(JAVA_LONG, VkStructs.VkVideoBeginCodingInfoKHR.videoSessionParameters, picture.parameters());
            beginInfo.set(JAVA_INT, VkStructs.VkVideoBeginCodingInfoKHR.referenceSlotCount, references.size() + 1);
            beginInfo.set(ADDRESS, VkStructs.VkVideoBeginCodingInfoKHR.pReferenceSlots, bound);
            device.video().cmdBeginVideoCodingKHR().call(decodeCommands, beginInfo);
            if (!started) {
                var control = Structs.allocate(
                        call,
                        VkStructs.VkVideoCodingControlInfoKHR.LAYOUT,
                        VkConstants.VK_STRUCTURE_TYPE_VIDEO_CODING_CONTROL_INFO_KHR);
                control.set(
                        JAVA_INT,
                        VkStructs.VkVideoCodingControlInfoKHR.flags,
                        VkConstants.VK_VIDEO_CODING_CONTROL_RESET_BIT_KHR);
                device.video().cmdControlVideoCodingKHR().call(decodeCommands, control);
                started = true;
            }
            var referenceSlots = Structs.array(
                    call,
                    slotLayout,
                    Math.max(references.size(), 1),
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_REFERENCE_SLOT_INFO_KHR);
            for (var i = 0; i < references.size(); i++) {
                var reference = references.get(i);
                slot(
                        call,
                        Structs.at(referenceSlots, slotLayout, i),
                        reference.index(),
                        reference.index(),
                        reference.codecInfo());
            }
            var setup = Structs.allocate(call, slotLayout, VkConstants.VK_STRUCTURE_TYPE_VIDEO_REFERENCE_SLOT_INFO_KHR);
            slot(
                    call,
                    setup,
                    picture.setup().index(),
                    picture.setup().index(),
                    picture.setup().codecInfo());
            var decode = Structs.allocate(
                    call, VkStructs.VkVideoDecodeInfoKHR.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_VIDEO_DECODE_INFO_KHR);
            Structs.chain(decode, picture.pictureInfo());
            decode.set(JAVA_LONG, VkStructs.VkVideoDecodeInfoKHR.srcBuffer, bitstreamBuffer);
            decode.set(JAVA_LONG, VkStructs.VkVideoDecodeInfoKHR.srcBufferOffset, 0);
            decode.set(JAVA_LONG, VkStructs.VkVideoDecodeInfoKHR.srcBufferRange, range);
            resource(
                    decode.asSlice(
                            VkStructs.VkVideoDecodeInfoKHR.dstPictureResource,
                            VkStructs.VkVideoPictureResourceInfoKHR.LAYOUT.byteSize()),
                    picture.setup().index());
            decode.set(ADDRESS, VkStructs.VkVideoDecodeInfoKHR.pSetupReferenceSlot, setup);
            decode.set(JAVA_INT, VkStructs.VkVideoDecodeInfoKHR.referenceSlotCount, references.size());
            decode.set(
                    ADDRESS,
                    VkStructs.VkVideoDecodeInfoKHR.pReferenceSlots,
                    references.isEmpty() ? MemorySegment.NULL : referenceSlots);
            device.video().cmdDecodeVideoKHR().call(decodeCommands, decode);
            var end = Structs.allocate(
                    call,
                    VkStructs.VkVideoEndCodingInfoKHR.LAYOUT,
                    VkConstants.VK_STRUCTURE_TYPE_VIDEO_END_CODING_INFO_KHR);
            device.video().cmdEndVideoCodingKHR().call(decodeCommands, end);
            VulkanException.check(f.endCommandBuffer().call(decodeCommands), "vkEndCommandBuffer");

            submit(call, device.decodeQueue(), decodeCommands, false, true, 0, device.decodeLock());
            return copyBack(call, picture.setup().index(), x, y, width, height, true);
        }
    }

    /// Copies `width` by `height` from (`x`, `y`) of the picture in `slot`
    /// back, with no decode: a picture a stream shows again.
    ///
    /// @throws VulkanException when a call fails or the device is lost
    public Planes readBack(int slot, int x, int y, int width, int height) {
        if (closed) {
            throw new IllegalStateException("the video session is closed");
        }
        if (!started || slot < 0 || slot >= slots) {
            throw new IllegalArgumentException("slot " + slot + " holds no picture");
        }
        try (var call = Arena.ofConfined()) {
            return copyBack(call, slot, x, y, width, height, false);
        }
    }

    /// Records and submits the copy of a picture in `layer` to the read-back
    /// buffer, after the decode just submitted when `afterDecode`, and waits.
    private Planes copyBack(Arena call, int layer, int x, int y, int width, int height, boolean afterDecode) {
        var f = device.functions();
        var handle = device.handle();
        VulkanException.check(f.resetCommandBuffer().call(copyCommands, 0), "vkResetCommandBuffer");
        begin(call, copyCommands);
        barrier(
                call,
                copyCommands,
                layer,
                1,
                VkConstants.VK_IMAGE_LAYOUT_VIDEO_DECODE_DPB_KHR,
                VkConstants.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkConstants.VK_PIPELINE_STAGE_2_NONE,
                0,
                VkConstants.VK_PIPELINE_STAGE_2_COPY_BIT,
                VkConstants.VK_ACCESS_2_TRANSFER_READ_BIT);
        var copyLayout = VkStructs.VkBufferImageCopy.LAYOUT;
        var copies = Structs.array(call, copyLayout, 2, 0);
        var sampleBytes = profile.bitDepth() > 8 ? 2 : 1;
        var stride = codedWidth * sampleBytes;
        copyRegion(
                Structs.at(copies, copyLayout, 0),
                0,
                codedWidth,
                VkConstants.VK_IMAGE_ASPECT_PLANE_0_BIT,
                layer,
                x,
                y,
                width,
                height);
        copyRegion(
                Structs.at(copies, copyLayout, 1),
                readbackChroma,
                codedWidth / 2,
                VkConstants.VK_IMAGE_ASPECT_PLANE_1_BIT,
                layer,
                x / 2,
                y / 2,
                (width + 1) / 2,
                (height + 1) / 2);
        f.cmdCopyImageToBuffer()
                .call(copyCommands, image, VkConstants.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readbackBuffer, 2, copies);
        barrier(
                call,
                copyCommands,
                layer,
                1,
                VkConstants.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkConstants.VK_IMAGE_LAYOUT_VIDEO_DECODE_DPB_KHR,
                VkConstants.VK_PIPELINE_STAGE_2_COPY_BIT,
                VkConstants.VK_ACCESS_2_TRANSFER_READ_BIT,
                VkConstants.VK_PIPELINE_STAGE_2_NONE,
                0);
        VulkanException.check(f.endCommandBuffer().call(copyCommands), "vkEndCommandBuffer");

        submit(call, device.transferQueue(), copyCommands, afterDecode, false, fence, device.transferLock());
        var fences = call.allocateFrom(JAVA_LONG, fence);
        VulkanException.check(f.waitForFences().call(handle, 1, fences, 1, Long.MAX_VALUE), "vkWaitForFences");
        VulkanException.check(f.resetFences().call(handle, 1, fences), "vkResetFences");
        return new Planes(
                readbackMapped.asSlice(0, (long) stride * height),
                readbackMapped.asSlice(readbackChroma, (long) stride * ((height + 1) / 2)),
                stride);
    }

    private void slot(Arena call, MemorySegment slot, int slotIndex, int layer, MemorySegment codecInfo) {
        Structs.chain(slot, codecInfo);
        slot.set(JAVA_INT, VkStructs.VkVideoReferenceSlotInfoKHR.slotIndex, slotIndex);
        var resource = Structs.allocate(
                call,
                VkStructs.VkVideoPictureResourceInfoKHR.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_VIDEO_PICTURE_RESOURCE_INFO_KHR);
        resource(resource, layer);
        slot.set(ADDRESS, VkStructs.VkVideoReferenceSlotInfoKHR.pPictureResource, resource);
    }

    private void resource(MemorySegment resource, int layer) {
        resource.set(JAVA_INT, Structs.STYPE, VkConstants.VK_STRUCTURE_TYPE_VIDEO_PICTURE_RESOURCE_INFO_KHR);
        resource.set(JAVA_INT, VkStructs.VkVideoPictureResourceInfoKHR.codedExtent, pictureWidth);
        resource.set(JAVA_INT, VkStructs.VkVideoPictureResourceInfoKHR.codedExtent + 4, pictureHeight);
        resource.set(JAVA_INT, VkStructs.VkVideoPictureResourceInfoKHR.baseArrayLayer, layer);
        resource.set(JAVA_LONG, VkStructs.VkVideoPictureResourceInfoKHR.imageViewBinding, view);
    }

    private static void copyRegion(
            MemorySegment region,
            long offset,
            int rowTexels,
            int aspect,
            int layer,
            int x,
            int y,
            int width,
            int height) {
        region.set(JAVA_LONG, VkStructs.VkBufferImageCopy.bufferOffset, offset);
        region.set(JAVA_INT, VkStructs.VkBufferImageCopy.bufferRowLength, rowTexels);
        var subresource = VkStructs.VkBufferImageCopy.imageSubresource;
        region.set(JAVA_INT, subresource + VkStructs.VkImageSubresourceLayers.aspectMask, aspect);
        region.set(JAVA_INT, subresource + VkStructs.VkImageSubresourceLayers.baseArrayLayer, layer);
        region.set(JAVA_INT, subresource + VkStructs.VkImageSubresourceLayers.layerCount, 1);
        region.set(JAVA_INT, VkStructs.VkBufferImageCopy.imageOffset, x);
        region.set(JAVA_INT, VkStructs.VkBufferImageCopy.imageOffset + 4, y);
        region.set(JAVA_INT, VkStructs.VkBufferImageCopy.imageExtent, width);
        region.set(JAVA_INT, VkStructs.VkBufferImageCopy.imageExtent + 4, height);
        region.set(JAVA_INT, VkStructs.VkBufferImageCopy.imageExtent + 8, 1);
    }

    private void begin(Arena call, MemorySegment commands) {
        var info = Structs.allocate(
                call,
                VkStructs.VkCommandBufferBeginInfo.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
        info.set(
                JAVA_INT,
                VkStructs.VkCommandBufferBeginInfo.flags,
                VkConstants.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        VulkanException.check(device.functions().beginCommandBuffer().call(commands, info), "vkBeginCommandBuffer");
    }

    private void barrier(
            Arena call,
            MemorySegment commands,
            int layer,
            int layers,
            int oldLayout,
            int newLayout,
            long srcStage,
            long srcAccess,
            long dstStage,
            long dstAccess) {
        var barrier = Structs.allocate(
                call, VkStructs.VkImageMemoryBarrier2.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2);
        barrier.set(JAVA_LONG, VkStructs.VkImageMemoryBarrier2.srcStageMask, srcStage);
        barrier.set(JAVA_LONG, VkStructs.VkImageMemoryBarrier2.srcAccessMask, srcAccess);
        barrier.set(JAVA_LONG, VkStructs.VkImageMemoryBarrier2.dstStageMask, dstStage);
        barrier.set(JAVA_LONG, VkStructs.VkImageMemoryBarrier2.dstAccessMask, dstAccess);
        barrier.set(JAVA_INT, VkStructs.VkImageMemoryBarrier2.oldLayout, oldLayout);
        barrier.set(JAVA_INT, VkStructs.VkImageMemoryBarrier2.newLayout, newLayout);
        barrier.set(JAVA_INT, VkStructs.VkImageMemoryBarrier2.srcQueueFamilyIndex, (int)
                VkConstants.VK_QUEUE_FAMILY_IGNORED);
        barrier.set(JAVA_INT, VkStructs.VkImageMemoryBarrier2.dstQueueFamilyIndex, (int)
                VkConstants.VK_QUEUE_FAMILY_IGNORED);
        barrier.set(JAVA_LONG, VkStructs.VkImageMemoryBarrier2.image, image);
        var range = VkStructs.VkImageMemoryBarrier2.subresourceRange;
        barrier.set(
                JAVA_INT, range + VkStructs.VkImageSubresourceRange.aspectMask, VkConstants.VK_IMAGE_ASPECT_COLOR_BIT);
        barrier.set(JAVA_INT, range + VkStructs.VkImageSubresourceRange.levelCount, 1);
        barrier.set(JAVA_INT, range + VkStructs.VkImageSubresourceRange.baseArrayLayer, layer);
        barrier.set(JAVA_INT, range + VkStructs.VkImageSubresourceRange.layerCount, layers);
        var dependency = Structs.allocate(
                call, VkStructs.VkDependencyInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_DEPENDENCY_INFO);
        dependency.set(JAVA_INT, VkStructs.VkDependencyInfo.imageMemoryBarrierCount, 1);
        dependency.set(ADDRESS, VkStructs.VkDependencyInfo.pImageMemoryBarriers, barrier);
        device.functions().cmdPipelineBarrier2().call(commands, dependency);
    }

    private void submit(
            Arena call,
            MemorySegment queue,
            MemorySegment commands,
            boolean wait,
            boolean signal,
            long signalFence,
            ReentrantLock lock) {
        var info = Structs.allocate(call, VkStructs.VkSubmitInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_SUBMIT_INFO);
        var semaphores = call.allocateFrom(JAVA_LONG, semaphore);
        if (wait) {
            info.set(JAVA_INT, VkStructs.VkSubmitInfo.waitSemaphoreCount, 1);
            info.set(ADDRESS, VkStructs.VkSubmitInfo.pWaitSemaphores, semaphores);
            info.set(
                    ADDRESS,
                    VkStructs.VkSubmitInfo.pWaitDstStageMask,
                    call.allocateFrom(JAVA_INT, VkConstants.VK_PIPELINE_STAGE_TRANSFER_BIT));
        }
        info.set(JAVA_INT, VkStructs.VkSubmitInfo.commandBufferCount, 1);
        info.set(ADDRESS, VkStructs.VkSubmitInfo.pCommandBuffers, call.allocateFrom(ADDRESS, commands));
        if (signal) {
            info.set(JAVA_INT, VkStructs.VkSubmitInfo.signalSemaphoreCount, 1);
            info.set(ADDRESS, VkStructs.VkSubmitInfo.pSignalSemaphores, semaphores);
        }
        lock.lock();
        try {
            VulkanException.check(device.functions().queueSubmit().call(queue, 1, info, signalFence), "vkQueueSubmit");
        } finally {
            lock.unlock();
        }
    }

    private void bindSessionMemory(Arena call) {
        var video = device.video();
        var count = call.allocate(JAVA_INT);
        VulkanException.check(
                video.getVideoSessionMemoryRequirementsKHR().call(device.handle(), session, count, MemorySegment.NULL),
                "vkGetVideoSessionMemoryRequirementsKHR");
        var n = count.get(JAVA_INT, 0);
        var layout = VkStructs.VkVideoSessionMemoryRequirementsKHR.LAYOUT;
        var requirements =
                Structs.array(call, layout, n, VkConstants.VK_STRUCTURE_TYPE_VIDEO_SESSION_MEMORY_REQUIREMENTS_KHR);
        VulkanException.check(
                video.getVideoSessionMemoryRequirementsKHR().call(device.handle(), session, count, requirements),
                "vkGetVideoSessionMemoryRequirementsKHR");
        var bindLayout = VkStructs.VkBindVideoSessionMemoryInfoKHR.LAYOUT;
        var binds =
                Structs.array(call, bindLayout, n, VkConstants.VK_STRUCTURE_TYPE_BIND_VIDEO_SESSION_MEMORY_INFO_KHR);
        for (var i = 0; i < n; i++) {
            var requirement = Structs.at(requirements, layout, i);
            var memoryRequirements = requirement.asSlice(
                    VkStructs.VkVideoSessionMemoryRequirementsKHR.memoryRequirements,
                    VkStructs.VkMemoryRequirements.LAYOUT.byteSize());
            var memory = allocate(call, memoryRequirements, 0);
            sessionMemory.add(memory);
            var bind = Structs.at(binds, bindLayout, i);
            bind.set(
                    JAVA_INT,
                    VkStructs.VkBindVideoSessionMemoryInfoKHR.memoryBindIndex,
                    requirement.get(JAVA_INT, VkStructs.VkVideoSessionMemoryRequirementsKHR.memoryBindIndex));
            bind.set(JAVA_LONG, VkStructs.VkBindVideoSessionMemoryInfoKHR.memory, memory);
            bind.set(
                    JAVA_LONG,
                    VkStructs.VkBindVideoSessionMemoryInfoKHR.memorySize,
                    memoryRequirements.get(JAVA_LONG, VkStructs.VkMemoryRequirements.size));
        }
        VulkanException.check(
                video.bindVideoSessionMemoryKHR().call(device.handle(), session, n, binds),
                "vkBindVideoSessionMemoryKHR");
    }

    /// Memory for `requirements`, of a type with every flag of `properties`
    /// where there is one, else of any type it allows.
    private long allocate(Arena call, MemorySegment requirements, int properties) {
        var bits = requirements.get(JAVA_INT, VkStructs.VkMemoryRequirements.memoryTypeBits);
        int type;
        try {
            type = device.memoryType(bits, properties);
        } catch (VulkanException e) {
            type = device.memoryType(bits, 0);
        }
        var info = Structs.allocate(
                call, VkStructs.VkMemoryAllocateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);
        info.set(
                JAVA_LONG,
                VkStructs.VkMemoryAllocateInfo.allocationSize,
                requirements.get(JAVA_LONG, VkStructs.VkMemoryRequirements.size));
        info.set(JAVA_INT, VkStructs.VkMemoryAllocateInfo.memoryTypeIndex, type);
        var out = call.allocate(JAVA_LONG);
        VulkanException.check(
                device.functions().allocateMemory().call(device.handle(), info, MemorySegment.NULL, out),
                "vkAllocateMemory");
        return out.get(JAVA_LONG, 0);
    }

    /// A buffer of `size` bytes and its memory, bound: `{buffer, memory}`.
    private long[] buffer(Arena call, long size, int usage, boolean video, int properties) {
        var f = device.functions();
        var info = Structs.allocate(
                call, VkStructs.VkBufferCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);
        if (video) {
            Structs.chain(info, profile.writeList(call));
        }
        info.set(JAVA_LONG, VkStructs.VkBufferCreateInfo.size, size);
        info.set(JAVA_INT, VkStructs.VkBufferCreateInfo.usage, usage);
        info.set(JAVA_INT, VkStructs.VkBufferCreateInfo.sharingMode, VkConstants.VK_SHARING_MODE_EXCLUSIVE);
        var out = call.allocate(JAVA_LONG);
        VulkanException.check(f.createBuffer().call(device.handle(), info, MemorySegment.NULL, out), "vkCreateBuffer");
        var buffer = out.get(JAVA_LONG, 0);
        var requirements = Structs.allocate(call, VkStructs.VkMemoryRequirements.LAYOUT);
        f.getBufferMemoryRequirements().call(device.handle(), buffer, requirements);
        var memory = allocate(call, requirements, properties);
        VulkanException.check(f.bindBufferMemory().call(device.handle(), buffer, memory, 0), "vkBindBufferMemory");
        return new long[] {buffer, memory};
    }

    @SuppressWarnings("restricted")
    private MemorySegment map(Arena call, long memory, long size) {
        var out = call.allocate(ADDRESS);
        VulkanException.check(
                device.functions().mapMemory().call(device.handle(), memory, 0, size, 0, out), "vkMapMemory");
        return out.get(ADDRESS, 0).reinterpret(size, arena, null);
    }

    private void growBitstream(Arena call, long size) {
        var f = device.functions();
        if (bitstreamBuffer != 0) {
            f.unmapMemory().call(device.handle(), bitstreamMemory);
            f.destroyBuffer().call(device.handle(), bitstreamBuffer, MemorySegment.NULL);
            f.freeMemory().call(device.handle(), bitstreamMemory, MemorySegment.NULL);
        }
        var aligned = align(size, capabilities.minBitstreamSizeAlignment());
        var made = buffer(
                call,
                aligned,
                VkConstants.VK_BUFFER_USAGE_VIDEO_DECODE_SRC_BIT_KHR,
                true,
                VkConstants.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkConstants.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        bitstreamBuffer = made[0];
        bitstreamMemory = made[1];
        bitstreamSize = aligned;
        bitstreamMapped = map(call, bitstreamMemory, aligned);
    }

    private long commandPool(Arena call, int family) {
        var info = Structs.allocate(
                call, VkStructs.VkCommandPoolCreateInfo.LAYOUT, VkConstants.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
        info.set(
                JAVA_INT,
                VkStructs.VkCommandPoolCreateInfo.flags,
                VkConstants.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
        info.set(JAVA_INT, VkStructs.VkCommandPoolCreateInfo.queueFamilyIndex, family);
        var out = call.allocate(JAVA_LONG);
        VulkanException.check(
                device.functions().createCommandPool().call(device.handle(), info, MemorySegment.NULL, out),
                "vkCreateCommandPool");
        return out.get(JAVA_LONG, 0);
    }

    private MemorySegment commandBuffer(Arena call, long pool) {
        var info = Structs.allocate(
                call,
                VkStructs.VkCommandBufferAllocateInfo.LAYOUT,
                VkConstants.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
        info.set(JAVA_LONG, VkStructs.VkCommandBufferAllocateInfo.commandPool, pool);
        info.set(JAVA_INT, VkStructs.VkCommandBufferAllocateInfo.level, VkConstants.VK_COMMAND_BUFFER_LEVEL_PRIMARY);
        info.set(JAVA_INT, VkStructs.VkCommandBufferAllocateInfo.commandBufferCount, 1);
        var out = call.allocate(ADDRESS);
        VulkanException.check(
                device.functions().allocateCommandBuffers().call(device.handle(), info, out),
                "vkAllocateCommandBuffers");
        return out.get(ADDRESS, 0);
    }

    private static int align(int value, int to) {
        return to <= 1 ? value : (value + to - 1) / to * to;
    }

    private static long align(long value, long to) {
        return to <= 1 ? value : (value + to - 1) / to * to;
    }

    /// Waits for the device's queues, then destroys everything made. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var f = device.functions();
        var handle = device.handle();
        f.deviceWaitIdle().call(handle);
        var none = MemorySegment.NULL;
        if (semaphore != 0) {
            f.destroySemaphore().call(handle, semaphore, none);
        }
        if (fence != 0) {
            f.destroyFence().call(handle, fence, none);
        }
        if (decodePool != 0) {
            f.destroyCommandPool().call(handle, decodePool, none);
        }
        if (copyPool != 0) {
            f.destroyCommandPool().call(handle, copyPool, none);
        }
        if (bitstreamBuffer != 0) {
            f.destroyBuffer().call(handle, bitstreamBuffer, none);
            f.freeMemory().call(handle, bitstreamMemory, none);
        }
        if (readbackBuffer != 0) {
            f.destroyBuffer().call(handle, readbackBuffer, none);
            f.freeMemory().call(handle, readbackMemory, none);
        }
        if (view != 0) {
            f.destroyImageView().call(handle, view, none);
        }
        if (image != 0) {
            f.destroyImage().call(handle, image, none);
            f.freeMemory().call(handle, imageMemory, none);
        }
        if (session != 0) {
            device.video().destroyVideoSessionKHR().call(handle, session, none);
        }
        for (var memory : sessionMemory) {
            f.freeMemory().call(handle, memory, none);
        }
        arena.close();
        Objects.requireNonNull(profile);
    }
}
