package dev.goldberry.media.platform.vulkan.hevc;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.Arena;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.bitstream.hevc.ShortTermRefPicSet;
import dev.goldberry.media.platform.vulkan.ffm.Structs;
import dev.goldberry.media.platform.vulkan.ffm.VkConstants;
import dev.goldberry.media.platform.vulkan.ffm.VkStructs.StdVideoH265ShortTermRefPicSet;

/// HEVC's parameter sets as Vulkan's structs: the arithmetic the mapping does.
@DisplayName("HEVC's parameter sets for Vulkan")
class H265ParametersTest {

    @ParameterizedTest(name = "general_level_idc {0} is level {1}")
    @CsvSource({"30, 1_0", "63, 2_1", "93, 3_1", "120, 4_0", "153, 5_1", "186, 6_2"})
    @DisplayName("a level is thirty times its number")
    void levels(int levelIdc, String name) throws ReflectiveOperationException {
        var expected =
                VkConstants.class.getField("STD_VIDEO_H265_LEVEL_IDC_" + name).getInt(null);
        assertEquals(expected, H265Parameters.level(levelIdc));
    }

    @Test
    @DisplayName("a short-term set's deltas go over as coded: each the step from the one before, less one")
    void shortTermDeltas() {
        var set = new ShortTermRefPicSet(false, 0, false, 0, 0, 0, List.of(-1, -3, -8), 0b011, List.of(2, 4), 0b10, 0);
        try (var arena = Arena.ofConfined()) {
            var s = Structs.allocate(arena, StdVideoH265ShortTermRefPicSet.LAYOUT);
            H265Parameters.shortTermSet(s, set);
            assertEquals(3, s.get(JAVA_BYTE, StdVideoH265ShortTermRefPicSet.num_negative_pics));
            assertEquals(2, s.get(JAVA_BYTE, StdVideoH265ShortTermRefPicSet.num_positive_pics));
            var s0 = StdVideoH265ShortTermRefPicSet.delta_poc_s0_minus1;
            assertEquals(0, s.get(JAVA_SHORT, s0));
            assertEquals(1, s.get(JAVA_SHORT, s0 + 2));
            assertEquals(4, s.get(JAVA_SHORT, s0 + 4));
            var s1 = StdVideoH265ShortTermRefPicSet.delta_poc_s1_minus1;
            assertEquals(1, s.get(JAVA_SHORT, s1));
            assertEquals(1, s.get(JAVA_SHORT, s1 + 2));
            assertEquals(0b011, s.get(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.used_by_curr_pic_s0_flag));
            assertEquals(0b10, s.get(JAVA_SHORT, StdVideoH265ShortTermRefPicSet.used_by_curr_pic_s1_flag));
        }
    }
}
