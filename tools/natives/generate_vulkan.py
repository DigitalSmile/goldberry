#!/usr/bin/env python3
"""Generates the Vulkan bindings of dev.goldberry.natives.vulkan.

Shared by :media's Vulkan Video decoders and :gpu's ray tracing, each on a
device of its own. Reads the Vulkan headers (vulkan_core.h and the vk_video
codec headers) and writes, for the structs, enums and functions listed below:

  VkStructs.java    one nested class per struct: its StructLayout and the byte
                    offset of each field; a bitfield struct's fields as the shift
                    and mask of each within its 32-bit word
  VkConstants.java  the enum values and #defines named below
  calls/VkCalls.java one holder per function: its FunctionDescriptor, linked
                    once through Downcalls, called through an address the
                    loader resolves. In a ...calls package, as every holder of
                    :natives is, so a native image initialises it at build time
  Vk*Functions.java one record per group of device-level functions: the core
                    ones every device has, and one per extension set, resolved
                    only on a device that enabled it
  vulkan_layout.c   the C probe: sizeof and offsetof of every field the Java
                    layouts state, for VulkanLayoutTest

Every Vulkan type has a fixed width on every 64-bit target the toolkit runs on
(Linux and Windows: x64 and arm64), so there is one shape, not an LP64 and an
LLP64 one: non-dispatchable handles are 64-bit integers there, dispatchable
ones pointers, enums 32-bit. Bitfields are uint32_t words filled from the
least significant bit, as GCC, Clang and MSVC all lay them out.

Run from the repository root after changing a list, then review the diff:

    python3 tools/natives/generate_vulkan.py [path/to/include]
    ./gradlew :natives:spotlessApply

then compile the probe and refresh what VulkanLayoutTest reads:

    cc natives/src/test/vulkan/vulkan_layout.c -o vulkan_layout && ./vulkan_layout \
        > natives/src/test/resources/dev/goldberry/natives/vulkan/vulkan-layout-linux-x64.properties

The include directory defaults to /usr/include; the headers' VK_HEADER_VERSION
is recorded in each file written.
"""
import os
import re
import sys

INCLUDE = sys.argv[1] if len(sys.argv) > 1 else '/usr/include'
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
PACKAGE = 'dev.goldberry.natives.vulkan'
OUT = os.path.join(ROOT, 'natives', 'src', 'main', 'java', *PACKAGE.split('.'))
CALLS_OUT = os.path.join(OUT, 'calls')
PROBE = os.path.join(ROOT, 'natives', 'src', 'test', 'vulkan', 'vulkan_layout.c')

HEADERS = [
    'vulkan/vulkan_core.h',
    'vk_video/vulkan_video_codecs_common.h',
    'vk_video/vulkan_video_codec_h264std.h',
    'vk_video/vulkan_video_codec_h264std_decode.h',
    'vk_video/vulkan_video_codec_h265std.h',
    'vk_video/vulkan_video_codec_h265std_decode.h',
    'vk_video/vulkan_video_codec_vp9std.h',
    'vk_video/vulkan_video_codec_vp9std_decode.h',
    'vk_video/vulkan_video_codec_av1std.h',
    'vk_video/vulkan_video_codec_av1std_decode.h',
]

# The structs bound, each with every struct it holds by value.
STRUCTS = '''
VkApplicationInfo VkInstanceCreateInfo VkDeviceQueueCreateInfo VkDeviceCreateInfo
VkPhysicalDeviceProperties VkPhysicalDeviceProperties2 VkPhysicalDeviceLimits VkPhysicalDeviceSparseProperties
VkQueueFamilyProperties VkQueueFamilyProperties2 VkQueueFamilyVideoPropertiesKHR VkExtent2D VkExtent3D VkOffset2D VkOffset3D
VkExtensionProperties VkPhysicalDeviceMemoryProperties VkMemoryType VkMemoryHeap
VkPhysicalDeviceFeatures2 VkPhysicalDeviceSynchronization2Features VkPhysicalDeviceVulkan13Features
VkPhysicalDeviceVideoMaintenance1FeaturesKHR
VkVideoProfileInfoKHR VkVideoProfileListInfoKHR VkVideoCapabilitiesKHR VkVideoDecodeCapabilitiesKHR
VkVideoDecodeH264ProfileInfoKHR VkVideoDecodeH264CapabilitiesKHR
VkVideoDecodeH265ProfileInfoKHR VkVideoDecodeH265CapabilitiesKHR
VkVideoDecodeVP9ProfileInfoKHR VkVideoDecodeVP9CapabilitiesKHR
VkVideoDecodeAV1ProfileInfoKHR VkVideoDecodeAV1CapabilitiesKHR
VkPhysicalDeviceVideoFormatInfoKHR VkVideoFormatPropertiesKHR VkComponentMapping
VkVideoSessionCreateInfoKHR VkVideoSessionMemoryRequirementsKHR VkMemoryRequirements VkBindVideoSessionMemoryInfoKHR
VkVideoSessionParametersCreateInfoKHR VkVideoSessionParametersUpdateInfoKHR
VkVideoDecodeH264SessionParametersCreateInfoKHR VkVideoDecodeH264SessionParametersAddInfoKHR
VkVideoDecodeH265SessionParametersCreateInfoKHR VkVideoDecodeH265SessionParametersAddInfoKHR
VkVideoDecodeAV1SessionParametersCreateInfoKHR
VkVideoBeginCodingInfoKHR VkVideoEndCodingInfoKHR VkVideoCodingControlInfoKHR
VkVideoReferenceSlotInfoKHR VkVideoPictureResourceInfoKHR VkVideoDecodeInfoKHR
VkVideoDecodeH264PictureInfoKHR VkVideoDecodeH264DpbSlotInfoKHR
VkVideoDecodeH265PictureInfoKHR VkVideoDecodeH265DpbSlotInfoKHR
VkVideoDecodeVP9PictureInfoKHR
VkVideoDecodeAV1PictureInfoKHR VkVideoDecodeAV1DpbSlotInfoKHR
VkImageCreateInfo VkImageViewCreateInfo VkImageViewUsageCreateInfo VkImageSubresourceRange VkImageSubresourceLayers
VkBufferCreateInfo VkMemoryAllocateInfo VkMappedMemoryRange
VkCommandPoolCreateInfo VkCommandBufferAllocateInfo VkCommandBufferBeginInfo
VkSubmitInfo VkFenceCreateInfo VkSemaphoreCreateInfo VkSemaphoreTypeCreateInfo VkTimelineSemaphoreSubmitInfo
VkImageMemoryBarrier2 VkBufferMemoryBarrier2 VkMemoryBarrier2 VkDependencyInfo VkBufferImageCopy
VkQueryPoolCreateInfo
VkPhysicalDeviceVulkan12Features VkPhysicalDeviceAccelerationStructureFeaturesKHR VkPhysicalDeviceRayTracingPipelineFeaturesKHR
VkPhysicalDeviceRayTracingPipelinePropertiesKHR VkPhysicalDeviceAccelerationStructurePropertiesKHR
VkBufferDeviceAddressInfo VkMemoryAllocateFlagsInfo VkBufferCopy
VkTransformMatrixKHR VkAccelerationStructureInstanceKHR
VkAccelerationStructureGeometryTrianglesDataKHR VkAccelerationStructureGeometryInstancesDataKHR VkAccelerationStructureGeometryKHR
VkAccelerationStructureBuildGeometryInfoKHR VkAccelerationStructureBuildRangeInfoKHR VkAccelerationStructureBuildSizesInfoKHR
VkAccelerationStructureCreateInfoKHR VkAccelerationStructureDeviceAddressInfoKHR VkCopyAccelerationStructureInfoKHR
VkShaderModuleCreateInfo VkPipelineShaderStageCreateInfo VkRayTracingShaderGroupCreateInfoKHR VkRayTracingPipelineCreateInfoKHR
VkStridedDeviceAddressRegionKHR VkPushConstantRange VkPipelineLayoutCreateInfo
VkDescriptorSetLayoutBinding VkDescriptorSetLayoutCreateInfo VkDescriptorPoolSize VkDescriptorPoolCreateInfo
VkDescriptorSetAllocateInfo VkDescriptorBufferInfo VkWriteDescriptorSet VkWriteDescriptorSetAccelerationStructureKHR
StdVideoH264SpsFlags StdVideoH264ScalingLists StdVideoH264SequenceParameterSetVui StdVideoH264SpsVuiFlags
StdVideoH264HrdParameters StdVideoH264SequenceParameterSet StdVideoH264PpsFlags StdVideoH264PictureParameterSet
StdVideoDecodeH264PictureInfoFlags StdVideoDecodeH264PictureInfo StdVideoDecodeH264ReferenceInfoFlags StdVideoDecodeH264ReferenceInfo
StdVideoH265ProfileTierLevelFlags StdVideoH265ProfileTierLevel StdVideoH265DecPicBufMgr StdVideoH265SubLayerHrdParameters
StdVideoH265HrdFlags StdVideoH265HrdParameters StdVideoH265VpsFlags StdVideoH265VideoParameterSet
StdVideoH265ScalingLists StdVideoH265ShortTermRefPicSetFlags StdVideoH265ShortTermRefPicSet StdVideoH265LongTermRefPicsSps
StdVideoH265SpsVuiFlags StdVideoH265SequenceParameterSetVui StdVideoH265PredictorPaletteEntries StdVideoH265SpsFlags
StdVideoH265SequenceParameterSet StdVideoH265PpsFlags StdVideoH265PictureParameterSet
StdVideoDecodeH265PictureInfoFlags StdVideoDecodeH265PictureInfo StdVideoDecodeH265ReferenceInfoFlags StdVideoDecodeH265ReferenceInfo
StdVideoVP9ColorConfigFlags StdVideoVP9ColorConfig StdVideoVP9LoopFilterFlags StdVideoVP9LoopFilter
StdVideoVP9SegmentationFlags StdVideoVP9Segmentation StdVideoDecodeVP9PictureInfoFlags StdVideoDecodeVP9PictureInfo
StdVideoAV1ColorConfigFlags StdVideoAV1ColorConfig StdVideoAV1TimingInfoFlags StdVideoAV1TimingInfo
StdVideoAV1SequenceHeaderFlags StdVideoAV1SequenceHeader StdVideoAV1LoopFilterFlags StdVideoAV1LoopFilter
StdVideoAV1QuantizationFlags StdVideoAV1Quantization StdVideoAV1Segmentation StdVideoAV1TileInfoFlags StdVideoAV1TileInfo
StdVideoAV1CDEF StdVideoAV1LoopRestoration StdVideoAV1GlobalMotion StdVideoAV1FilmGrainFlags StdVideoAV1FilmGrain
StdVideoDecodeAV1PictureInfoFlags StdVideoDecodeAV1PictureInfo StdVideoDecodeAV1ReferenceInfoFlags StdVideoDecodeAV1ReferenceInfo
'''.split()

# Enum values and defines the Java side names.
CONSTANT_PREFIXES = [
    'VK_STRUCTURE_TYPE_', 'VK_VIDEO_CODEC_OPERATION_DECODE_', 'VK_VIDEO_CHROMA_SUBSAMPLING_', 'VK_VIDEO_COMPONENT_BIT_DEPTH_',
    'VK_QUEUE_', 'VK_FORMAT_G8_B8R8_2PLANE_420_UNORM', 'VK_FORMAT_G10X6_B10X6R10X6_2PLANE_420_UNORM_3PACK16',
    'VK_IMAGE_USAGE_', 'VK_BUFFER_USAGE_', 'VK_MEMORY_PROPERTY_', 'VK_IMAGE_LAYOUT_', 'VK_IMAGE_ASPECT_',
    'VK_IMAGE_TYPE_2D', 'VK_IMAGE_VIEW_TYPE_2D', 'VK_IMAGE_TILING_OPTIMAL', 'VK_SAMPLE_COUNT_1_BIT',
    'VK_SHARING_MODE_', 'VK_COMMAND_POOL_CREATE_', 'VK_COMMAND_BUFFER_LEVEL_PRIMARY',
    'VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT', 'VK_FENCE_CREATE_SIGNALED_BIT', 'VK_PIPELINE_STAGE_2_',
    'VK_ACCESS_2_', 'VK_PIPELINE_STAGE_TRANSFER_BIT', 'VK_VIDEO_CODING_CONTROL_RESET_BIT_KHR', 'VK_VIDEO_DECODE_CAPABILITY_', 'VK_VIDEO_CAPABILITY_',
    'VK_SUCCESS', 'VK_NOT_READY', 'VK_TIMEOUT', 'VK_INCOMPLETE', 'VK_ERROR_', 'VK_QUEUE_FAMILY_IGNORED',
    'VK_REMAINING_', 'VK_WHOLE_SIZE', 'VK_API_VERSION_1_', 'VK_KHR_VIDEO_', 'VK_KHR_SYNCHRONIZATION_2_EXTENSION_NAME',
    'VK_STD_VULKAN_VIDEO_CODEC_', 'VK_MAX_', 'VK_UUID_SIZE', 'VK_IMAGE_CREATE_', 'VK_COMPONENT_SWIZZLE_IDENTITY',
    'VK_VIDEO_SESSION_CREATE_', 'VK_PHYSICAL_DEVICE_TYPE_', 'VK_SEMAPHORE_TYPE_',
    'VK_QUERY_TYPE_RESULT_STATUS_ONLY_KHR', 'VK_QUERY_RESULT_', 'VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_',
    'STD_VIDEO_H264_PROFILE_IDC_', 'STD_VIDEO_H265_PROFILE_IDC_', 'STD_VIDEO_VP9_PROFILE_', 'STD_VIDEO_AV1_PROFILE_',
    'STD_VIDEO_H264_LEVEL_IDC_', 'STD_VIDEO_H265_LEVEL_IDC_', 'STD_VIDEO_VP9_LEVEL_', 'STD_VIDEO_AV1_LEVEL_',
    'STD_VIDEO_H264_', 'STD_VIDEO_H265_', 'STD_VIDEO_VP9_', 'STD_VIDEO_AV1_', 'STD_VIDEO_DECODE_',
    # Ray tracing, for :gpu.
    'VK_ACCELERATION_STRUCTURE_', 'VK_GEOMETRY_', 'VK_BUILD_ACCELERATION_STRUCTURE_', 'VK_RAY_TRACING_SHADER_GROUP_',
    'VK_SHADER_STAGE_', 'VK_DESCRIPTOR_TYPE_', 'VK_PIPELINE_BIND_POINT_', 'VK_INDEX_TYPE_UINT32',
    'VK_FORMAT_R32G32B32_SFLOAT', 'VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT', 'VK_KHR_ACCELERATION_STRUCTURE_',
    'VK_KHR_RAY_TRACING_PIPELINE_', 'VK_KHR_DEFERRED_HOST_OPERATIONS_', 'VK_KHR_RAY_QUERY_', 'VK_SHADER_UNUSED_KHR',
    'VK_COPY_ACCELERATION_STRUCTURE_MODE_', 'VK_QUERY_TYPE_ACCELERATION_STRUCTURE_COMPACTED_SIZE_KHR',
    'VK_DESCRIPTOR_POOL_CREATE_', 'VK_PIPELINE_STAGE_ALL_COMMANDS_BIT', 'VK_TRUE', 'VK_FALSE',
]

# The functions bound, with where their address comes from: the loader's
# vkGetInstanceProcAddr with no instance (global), an instance, or a device.
FUNCTIONS = {
    'loader': ['vkGetInstanceProcAddr'],
    'global': 'vkCreateInstance vkEnumerateInstanceVersion vkEnumerateInstanceExtensionProperties'.split(),
    'instance': '''vkDestroyInstance vkEnumeratePhysicalDevices vkGetPhysicalDeviceProperties2
        vkGetPhysicalDeviceQueueFamilyProperties2 vkGetPhysicalDeviceMemoryProperties vkEnumerateDeviceExtensionProperties
        vkGetPhysicalDeviceFeatures2 vkGetPhysicalDeviceVideoCapabilitiesKHR vkGetPhysicalDeviceVideoFormatPropertiesKHR
        vkCreateDevice vkGetDeviceProcAddr'''.split(),
    'device': '''vkDestroyDevice vkGetDeviceQueue vkDeviceWaitIdle vkQueueWaitIdle vkQueueSubmit
        vkGetBufferDeviceAddress vkCmdCopyBuffer
        vkCreateShaderModule vkDestroyShaderModule vkCreatePipelineLayout vkDestroyPipelineLayout vkDestroyPipeline
        vkCreateDescriptorSetLayout vkDestroyDescriptorSetLayout vkCreateDescriptorPool vkDestroyDescriptorPool
        vkAllocateDescriptorSets vkUpdateDescriptorSets vkCmdBindPipeline vkCmdBindDescriptorSets vkCmdPushConstants
        vkCreateCommandPool vkDestroyCommandPool vkResetCommandPool vkAllocateCommandBuffers vkFreeCommandBuffers
        vkBeginCommandBuffer vkEndCommandBuffer vkResetCommandBuffer
        vkCreateFence vkDestroyFence vkWaitForFences vkResetFences vkCreateSemaphore vkDestroySemaphore
        vkCreateBuffer vkDestroyBuffer vkGetBufferMemoryRequirements vkAllocateMemory vkFreeMemory
        vkBindBufferMemory vkMapMemory vkUnmapMemory vkFlushMappedMemoryRanges vkInvalidateMappedMemoryRanges
        vkCreateImage vkDestroyImage vkGetImageMemoryRequirements vkBindImageMemory vkCreateImageView vkDestroyImageView
        vkCmdPipelineBarrier2 vkCmdCopyImageToBuffer
        vkCreateQueryPool vkDestroyQueryPool vkGetQueryPoolResults vkCmdResetQueryPool vkCmdBeginQuery vkCmdEndQuery'''.split(),
    'video': '''vkCreateVideoSessionKHR vkDestroyVideoSessionKHR vkGetVideoSessionMemoryRequirementsKHR vkBindVideoSessionMemoryKHR
        vkCreateVideoSessionParametersKHR vkUpdateVideoSessionParametersKHR vkDestroyVideoSessionParametersKHR
        vkCmdBeginVideoCodingKHR vkCmdEndVideoCodingKHR vkCmdControlVideoCodingKHR vkCmdDecodeVideoKHR'''.split(),
    'rayTracing': '''vkCreateAccelerationStructureKHR vkDestroyAccelerationStructureKHR vkGetAccelerationStructureBuildSizesKHR
        vkCmdBuildAccelerationStructuresKHR vkGetAccelerationStructureDeviceAddressKHR
        vkCmdWriteAccelerationStructuresPropertiesKHR vkCmdCopyAccelerationStructureKHR
        vkCreateRayTracingPipelinesKHR vkGetRayTracingShaderGroupHandlesKHR vkCmdTraceRaysKHR'''.split(),
}

# The device-level groups, each a record resolved for one device: the core
# functions every device has, then one per extension set, which a device that
# did not enable it has no address for.
DEVICE_GROUPS = [
    ('device', 'VkDeviceFunctions', 'Every core device-level function the toolkit calls'),
    ('video', 'VkVideoFunctions', 'The Vulkan Video functions, on a device that enabled `VK_KHR_video_queue` and\n/// `VK_KHR_video_decode_queue`'),
    ('rayTracing', 'VkRayTracingFunctions', 'The ray-tracing functions, on a device that enabled\n/// `VK_KHR_acceleration_structure` and `VK_KHR_ray_tracing_pipeline`'),
]

# ---------------------------------------------------------------------------
source = ''
for h in HEADERS:
    with open(os.path.join(INCLUDE, h)) as f:
        source += f.read() + '\n'
source = re.sub(r'/\*.*?\*/', '', source, flags=re.S)
source = re.sub(r'//[^\n]*', '', source)
version = re.search(r'#define VK_HEADER_VERSION (\d+)', source).group(1)

defines = {}
for m in re.finditer(r'#define\s+(\w+)\s+([^\n]+)', source):
    name, value = m.group(1), m.group(2).strip()
    v = re.sub(r'\(|\)', '', value).strip().rstrip('U').rstrip('ULL')
    if re.fullmatch(r'-?(0x[0-9A-Fa-f]+|\d+)U?L*', value.strip()):
        defines[name] = int(value.strip().rstrip('ULul'), 0)
    elif re.fullmatch(r'"[^"]*"', value):
        defines[name] = value
    elif re.fullmatch(r'\(?~0U\)?', value) or value in ('(~0U)', '(~0ULL)', '(~0U-1)', '(~0U-2)'):
        defines[name] = {'(~0U)': 0xFFFFFFFF, '(~0ULL)': -1, '(~0U-1)': 0xFFFFFFFE, '(~0U-2)': 0xFFFFFFFD}.get(value, 0xFFFFFFFF)
    elif value.startswith('VK_MAKE_API_VERSION'):
        a = [int(x) for x in re.findall(r'\d+', value)]
        if len(a) == 4:
            defines[name] = (a[0] << 29) | (a[1] << 22) | (a[2] << 12) | a[3]
    elif value.startswith('VK_MAKE_VIDEO_STD_VERSION'):
        a = [int(x) for x in re.findall(r'\d+', value)]
        defines[name] = (a[0] << 22) | (a[1] << 12) | a[2]
    elif value in defines:
        defines[name] = defines[value]

enums = {}
enum_types = set()
for m in re.finditer(r'typedef enum (\w+)\s*\{(.*?)\}\s*(\w+);', source, re.S):
    enum_types.add(m.group(3))
    # A value inside `#ifdef VK_ENABLE_BETA_EXTENSIONS` shares its comma-split
    # piece with the directive, and the value after `#endif` with that: drop
    # the directives, or both are lost.
    body = re.sub(r'^\s*#[^\n]*$', '', m.group(2), flags=re.M)
    for line in body.split(','):
        line = line.strip()
        if '=' not in line:
            continue
        n, v = [x.strip() for x in line.split('=', 1)]
        try:
            enums[n] = int(v, 0)
        except ValueError:
            if v in enums:
                enums[n] = enums[v]
            elif v.startswith('VK_MAKE_VIDEO_STD_VERSION'):
                pass
# 64-bit flag enums: static const VkFlags64 NAME = VALUE;
for m in re.finditer(r'static const \w+ (\w+) = (0x[0-9A-Fa-f]+|\d+)ULL;', source):
    enums[m.group(1)] = int(m.group(2), 0)

typedefs = {}
for m in re.finditer(r'typedef\s+(\w+)\s+(\w+);', source):
    typedefs[m.group(2)] = m.group(1)
handles = set(re.findall(r'VK_DEFINE_HANDLE\((\w+)\)', source))
nd_handles = set(re.findall(r'VK_DEFINE_NON_DISPATCHABLE_HANDLE\((\w+)\)', source))

structs = {}
for m in re.finditer(r'typedef (struct|union) (\w+)\s*\{(.*?)\}\s*(\w+);', source, re.S):
    structs[m.group(4)] = (m.group(1), m.group(3))

PRIM = {
    'uint8_t': (1, 'JAVA_BYTE'), 'int8_t': (1, 'JAVA_BYTE'), 'char': (1, 'JAVA_BYTE'),
    'uint16_t': (2, 'JAVA_SHORT'), 'int16_t': (2, 'JAVA_SHORT'),
    'uint32_t': (4, 'JAVA_INT'), 'int32_t': (4, 'JAVA_INT'), 'float': (4, 'JAVA_FLOAT'),
    'uint64_t': (8, 'JAVA_LONG'), 'int64_t': (8, 'JAVA_LONG'), 'size_t': (8, 'JAVA_LONG'),
}

def resolve(t):
    """(size, align, java layout expr, kind) of a non-pointer type name."""
    seen = 0
    while t in typedefs and t not in PRIM and t not in structs and seen < 10:
        t = typedefs[t]
        seen += 1
    if t in PRIM:
        s, j = PRIM[t]
        return s, s, j, 'prim'
    if t in handles:
        return 8, 8, 'ADDRESS', 'prim'
    if t in nd_handles:
        return 8, 8, 'JAVA_LONG', 'prim'
    if t in enum_types or t.startswith('Std') and t not in structs:
        return 4, 4, 'JAVA_INT', 'prim'
    if t in structs:
        lay = layout_of(t)
        return lay['size'], lay['align'], t + '.LAYOUT', 'struct'
    raise SystemExit('unknown type ' + t)

def dims(text):
    out = []
    for d in re.findall(r'\[(\w+)\]', text):
        out.append(int(d) if d.isdigit() else (defines[d] if d in defines else enums[d]))
    return out

layouts = {}

def layout_of(name):
    if name in layouts:
        return layouts[name]
    kind, body = structs[name]
    fields = []
    for decl in [d.strip() for d in body.split(';') if d.strip()]:
        bit = re.match(r'(\w+)\s+(\w+)\s*:\s*(\d+)$', decl)
        if bit:
            fields.append(('bit', bit.group(1), bit.group(2), int(bit.group(3))))
            continue
        decl = decl.replace('const ', '').replace(' const', '')
        m = re.match(r'(\w+)\s*(\**)\s*(\w+)\s*((?:\[\w+\])*)$', decl)
        if not m:
            raise SystemExit('cannot read ' + name + ': ' + decl)
        fields.append(('field', m.group(1), m.group(3), bool(m.group(2)), dims(m.group(4))))
    members = []  # (name, offset, size, align, javaExpr) ; bitfield entries separately
    bits = []
    offset = 0
    align = 1
    word_at = None
    word_used = 32
    for f in fields:
        if f[0] == 'bit':
            _, t, n, w = f
            if word_at is None or word_used + w > 32:
                offset = (offset + 3) // 4 * 4
                word_at = offset
                word_used = 0
                members.append(('_bits%d' % word_at, word_at, 4, 4, 'JAVA_INT', []))
                offset += 4
                align = max(align, 4)
            bits.append((n, word_at, word_used, w))
            word_used += w
            continue
        word_at = None
        word_used = 32
        _, t, n, pointer, d = f
        if pointer:
            s, a, j, k = 8, 8, 'ADDRESS', 'prim'
        else:
            s, a, j, k = resolve(t)
        count = 1
        for x in d:
            count *= x
        if kind == 'union':
            members.append((n, 0, s * count, a, j, d))
            offset = max(offset, s * count)
        else:
            offset = (offset + a - 1) // a * a
            members.append((n, offset, s * count, a, j, d))
            offset += s * count
        align = max(align, a)
    size = (offset + align - 1) // align * align
    layouts[name] = {'size': size, 'align': align, 'members': members, 'bits': bits, 'kind': kind}
    return layouts[name]

order = []
def visit(name):
    if name in order:
        return
    lay = layout_of(name)
    for m in lay['members']:
        if m[4].endswith('.LAYOUT'):
            visit(m[4][:-7])
    order.append(name)
for s in STRUCTS:
    if s not in structs:
        raise SystemExit('no struct ' + s + ' in the headers')
    visit(s)

def java_member(m):
    n, off, size, a, j, d = m
    expr = j
    for x in reversed(d):
        expr = 'MemoryLayout.sequenceLayout(%d, %s)' % (x, expr)
    return '%s.withName("%s")' % (expr, n)

def struct_class(name):
    lay = layouts[name]
    lines = ['    /// `%s`: %d bytes, aligned to %d.' % (name, lay['size'], lay['align']),
             '    public static final class %s {' % name, '']
    parts = []
    at = 0
    for m in sorted(lay['members'], key=lambda m: m[1]) if lay['kind'] == 'struct' else lay['members']:
        if lay['kind'] == 'struct' and m[1] > at:
            parts.append('MemoryLayout.paddingLayout(%d)' % (m[1] - at))
        parts.append(java_member(m))
        at = m[1] + m[2] if lay['kind'] == 'struct' else at
    if lay['kind'] == 'struct' and lay['size'] > at:
        parts.append('MemoryLayout.paddingLayout(%d)' % (lay['size'] - at))
    maker = 'MemoryLayout.structLayout' if lay['kind'] == 'struct' else 'MemoryLayout.unionLayout'
    lines.append('        public static final %s LAYOUT = %s(' % ('StructLayout' if lay['kind'] == 'struct' else 'UnionLayout', maker))
    lines.append('                        ' + ',\n                        '.join(parts) + ')')
    lines.append('                .withName("%s");' % name)
    lines.append('')
    for m in lay['members']:
        if m[0].startswith('_bits'):
            continue
        lines.append('        public static final long %s = %d;' % (m[0], m[1]))
    for n, word, shift, width in lay['bits']:
        lines.append('        /// `%s`: %d bit%s from bit %d of the word at byte %d.' % (n, width, '' if width == 1 else 's', shift, word))
        lines.append('        public static final int SHIFT_%s = %d;' % (n, shift))
        lines.append('        public static final int MASK_%s = 0x%x;' % (n, ((1 << width) - 1) << shift))
        lines.append('        public static final long WORD_%s = %d;' % (n, word))
    lines += ['', '        private %s() {}' % name, '    }', '']
    return '\n'.join(lines)

def header_of(package):
    return '''// Generated by tools/natives/generate_vulkan.py from the Vulkan headers %s. Do not edit.
package %s;
''' % (version, package)

header = header_of(PACKAGE)

body = [header, 'import static java.lang.foreign.ValueLayout.ADDRESS;',
        'import static java.lang.foreign.ValueLayout.JAVA_BYTE;',
        'import static java.lang.foreign.ValueLayout.JAVA_FLOAT;',
        'import static java.lang.foreign.ValueLayout.JAVA_INT;',
        'import static java.lang.foreign.ValueLayout.JAVA_LONG;',
        'import static java.lang.foreign.ValueLayout.JAVA_SHORT;', '',
        'import java.lang.foreign.MemoryLayout;', 'import java.lang.foreign.StructLayout;',
        'import java.lang.foreign.UnionLayout;', 'import java.util.List;', '',
        '/// The layouts of the Vulkan structs the toolkit fills and reads, one',
        '/// nested class each: its `LAYOUT` and the byte offset of every',
        '/// field. A bitfield is a `SHIFT_`, `MASK_` and `WORD_` of its 32-bit word.',
        '/// Generated; `VulkanLayoutTest` holds every offset to the C compiler\'s.',
        '@SuppressWarnings({"MemberName", "ConstantName", "unused"})',
        'public final class VkStructs {', '', '    private VkStructs() {}', '']
for name in order:
    body.append(struct_class(name))
body.append('    /// Every struct, for the layout check.')
body.append('    public static final List<MemoryLayout> ALL = List.of(')
body.append(',\n'.join('            %s.LAYOUT' % n for n in order) + ');')
body.append('}')
os.makedirs(OUT, exist_ok=True)
open(os.path.join(OUT, 'VkStructs.java'), 'w').write('\n'.join(body) + '\n')

# Constants: a structure type only for a struct bound here.
def structure_type(name):
    words = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', name[2:])
    words = re.sub(r'(?<=[A-Z])(?=[A-Z][a-z])', '_', words)
    return 'VK_STRUCTURE_TYPE_' + words.upper()

# Matched without underscores: the enum's names break digits off as the
# struct's do not (VkImageMemoryBarrier2, VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER_2).
by_letters = {n[len('VK_STRUCTURE_TYPE_'):].replace('_', ''): n for n in enums if n.startswith('VK_STRUCTURE_TYPE_')}
bound_types = {by_letters[n[2:].upper()] for n in order if n.startswith('Vk') and n[2:].upper() in by_letters}
picked = {}
for n, v in enums.items():
    if n.startswith('VK_STRUCTURE_TYPE_') and n not in bound_types:
        continue
    if any(n.startswith(p) for p in CONSTANT_PREFIXES) and not n.endswith('_MAX_ENUM') and not n.endswith('_MAX_ENUM_KHR'):
        picked[n] = v
for n, v in defines.items():
    if any(n.startswith(p) for p in CONSTANT_PREFIXES) and (n.startswith('VK_STD_') or not n.endswith('_SPEC_VERSION')):
        picked[n] = v
lines = [header, '/// The Vulkan enum values and defines the toolkit names. Generated.',
         '@SuppressWarnings({"ConstantName", "unused"})', 'public final class VkConstants {', '', '    private VkConstants() {}', '']
for n in sorted(picked):
    v = picked[n]
    if isinstance(v, str):
        lines.append('    public static final String %s = %s;' % (n, v))
    elif v > 0x7FFFFFFF or v < -0x80000000:
        lines.append('    public static final long %s = %dL;' % (n, v))
    else:
        lines.append('    public static final int %s = %d;' % (n, v))
lines.append('}')
open(os.path.join(OUT, 'VkConstants.java'), 'w').write('\n'.join(lines) + '\n')

def wrap(text, width=88):
    words, out, cur = text.split(' '), [], ''
    for w in words:
        if cur and len(cur) + 1 + len(w) > width:
            out.append(cur)
            cur = w
        else:
            cur = (cur + ' ' + w) if cur else w
    if cur:
        out.append(cur)
    return out

# Functions.
protos = {}
for m in re.finditer(r'typedef (\w+) \(VKAPI_PTR \*PFN_(\w+)\)\((.*?)\);', source, re.S):
    protos[m.group(2)] = (m.group(1), m.group(3))

def ptype(decl):
    decl = decl.strip()
    if decl == 'void':
        return None
    # `const T* const* p` is a pointer to pointers: the stars, once the
    # qualifiers are gone, with no space between them.
    decl = re.sub(r'\*\s+\*', '**', decl.replace('const ', '').replace(' const', ''))
    m = re.match(r'(\w+)\s*(\**)\s*(\w+)(\[\d+\])?$', decl)
    t, stars, name, arr = m.group(1), m.group(2), m.group(3), m.group(4)
    if stars or arr:
        return ('ADDRESS', 'MemorySegment', name)
    s, a, j, k = resolve(t)
    jt = {'JAVA_INT': 'int', 'JAVA_LONG': 'long', 'JAVA_FLOAT': 'float', 'ADDRESS': 'MemorySegment',
          'JAVA_SHORT': 'short', 'JAVA_BYTE': 'byte'}[j]
    return (j, jt, name)

lines = [header_of(PACKAGE + '.calls'), 'import static java.lang.foreign.ValueLayout.ADDRESS;',
         'import static java.lang.foreign.ValueLayout.JAVA_FLOAT;',
         'import static java.lang.foreign.ValueLayout.JAVA_INT;',
         'import static java.lang.foreign.ValueLayout.JAVA_LONG;', '',
         'import java.lang.foreign.FunctionDescriptor;', 'import java.lang.foreign.MemorySegment;',
         'import java.lang.invoke.MethodHandle;', 'import java.util.List;', '',
         'import dev.goldberry.natives.Downcalls;', 'import %s.VulkanLinker;' % PACKAGE, '',
         '/// The Vulkan functions the toolkit calls, one holder each: the descriptor,',
         '/// linked once through [Downcalls], and a call through the address the',
         '/// loader gave for an instance or a device. Generated.',
         '@SuppressWarnings({"MethodName", "unused"})',
         'public final class VkCalls {', '', '    private VkCalls() {}', '']
descs = []
for scope in ('loader', 'global', 'instance', 'device', 'video', 'rayTracing'):
    for fn in FUNCTIONS[scope]:
        ret, params = protos[fn]
        ps = [ptype(p) for p in params.split(',')] if params.strip() != 'void' else []
        ps = [p for p in ps if p]
        rl = None if ret == 'void' else ('JAVA_INT' if ret in ('VkResult', 'VkBool32', 'uint32_t') else
                                         'JAVA_LONG' if ret in ('uint64_t', 'VkDeviceAddress') else 'ADDRESS')
        rt = 'void' if rl is None else {'JAVA_INT': 'int', 'JAVA_LONG': 'long', 'ADDRESS': 'MemorySegment'}[rl]
        lay = ', '.join(p[0] for p in ps)
        fd = ('FunctionDescriptor.ofVoid(%s)' % lay) if rl is None else ('FunctionDescriptor.of(%s%s)' % (rl, (', ' + lay) if lay else ''))
        descs.append((fn, fd))
        cls = fn[2].upper() + fn[3:] if fn.startswith('vk') else fn
        args = ', '.join('%s %s' % (p[1], p[2]) for p in ps)
        names = ', '.join(['address'] + [p[2] for p in ps])
        for doc in wrap('`%s %s(%s)`, %s-level.' % (ret, fn, ' '.join(params.split()), scope)):
            lines.append('    /// ' + doc)
        lines.append('    public static final class %s {' % cls)
        lines.append('')
        lines.append('        public static final String NAME = "%s";' % fn)
        lines.append('        public static final FunctionDescriptor DESCRIPTOR = %s;' % fd)
        lines.append('        private static final MethodHandle HANDLE = Downcalls.link(DESCRIPTOR);')
        lines.append('')
        lines.append('        private final MemorySegment address;')
        lines.append('')
        lines.append('        public %s(MemorySegment address) {' % cls)
        lines.append('            this.address = VulkanLinker.require(address, NAME);')
        lines.append('        }')
        lines.append('')
        lines.append('        public %s call(%s) {' % (rt, args))
        lines.append('            try {')
        if rl is None:
            lines.append('                HANDLE.invokeExact(%s);' % names)
        else:
            lines.append('                return (%s) HANDLE.invokeExact(%s);' % (rt, names))
        lines.append('            } catch (Throwable t) {')
        lines.append('                throw VulkanLinker.failure(NAME, t);')
        lines.append('            }')
        lines.append('        }')
        lines.append('    }')
        lines.append('')
lines.append('    /// Every descriptor, for the native image\'s foreign metadata.')
lines.append('    public static final List<FunctionDescriptor> DESCRIPTORS = List.of(')
lines.append(',\n'.join('            %s.DESCRIPTOR' % (fn[2].upper() + fn[3:]) for fn, _ in descs) + ');')
lines.append('}')
os.makedirs(CALLS_OUT, exist_ok=True)
open(os.path.join(CALLS_OUT, 'VkCalls.java'), 'w').write('\n'.join(lines) + '\n')

# Each group of device-level functions as one record, resolved through a
# function that finds an address by name (vkGetDeviceProcAddr).
def component(fn):
    return fn[2].lower() + fn[3:]
for group, record, doc in DEVICE_GROUPS:
    names = FUNCTIONS[group]
    lines = [header, 'import java.lang.foreign.MemorySegment;', 'import java.util.function.Function;', '',
             'import %s.calls.VkCalls;' % PACKAGE, '',
             '/// %s, resolved for one device. Generated.' % doc,
             'public record %s(' % record]
    lines.append(',\n'.join('        VkCalls.%s %s' % (fn[2].upper() + fn[3:], component(fn)) for fn in names) + ') {')
    lines.append('')
    lines.append('    /// Every function, its address found by `procAddr`.')
    lines.append('    ///')
    lines.append('    /// @throws VulkanException when the device has no address for one of them')
    lines.append('    public static %s resolve(Function<String, MemorySegment> procAddr) {' % record)
    lines.append('        return new %s(' % record)
    lines.append(',\n'.join('                new VkCalls.%s(procAddr.apply(VkCalls.%s.NAME))' % (fn[2].upper() + fn[3:], fn[2].upper() + fn[3:]) for fn in names) + ');')
    lines.append('    }')
    lines.append('}')
    open(os.path.join(OUT, record + '.java'), 'w').write('\n'.join(lines) + '\n')

# The C probe.
os.makedirs(os.path.dirname(PROBE), exist_ok=True)
c = ['/* Generated by tools/natives/generate_vulkan.py. Prints the layout of every struct',
     ' * VkStructs states, as the C compiler lays it out, for VulkanLayoutTest:',
     ' *   cc -I<include> vulkan_layout.c -o vulkan_layout && ./vulkan_layout */',
     '#include <stddef.h>', '#include <stdio.h>', '#include <vulkan/vulkan.h>',
     '#include <vk_video/vulkan_video_codec_h264std_decode.h>', '#include <vk_video/vulkan_video_codec_h265std_decode.h>',
     '#include <vk_video/vulkan_video_codec_vp9std_decode.h>', '#include <vk_video/vulkan_video_codec_av1std_decode.h>',
     '', 'int main(void) {', '    printf("headers=%d\\n", VK_HEADER_VERSION);']
for name in order:
    c.append('    printf("struct.%s.sizeof=%%zu\\n", sizeof(%s));' % (name, name))
    c.append('    printf("struct.%s.alignof=%%zu\\n", _Alignof(%s));' % (name, name))
    for m in layouts[name]['members']:
        if m[0].startswith('_bits'):
            continue
        c.append('    printf("field.%s.%s.offset=%%zu\\n", offsetof(%s, %s));' % (name, m[0], name, m[0]))
c += ['    return 0;', '}']
open(PROBE, 'w').write('\n'.join(c) + '\n')
print('VkStructs: %d structs; VkConstants: %d; VkCalls: %d functions; headers %s' % (len(order), len(picked), len(descs), version))
