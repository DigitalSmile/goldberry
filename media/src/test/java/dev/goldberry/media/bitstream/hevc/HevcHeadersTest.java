package dev.goldberry.media.bitstream.hevc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.bitstream.BitReader;
import dev.goldberry.media.bitstream.HeaderTrace;
import dev.goldberry.media.bitstream.NalUnits;
import dev.goldberry.media.codec.ColorDescription;

/// The HEVC readers against FFmpeg's `trace_headers` of the same streams,
/// element by element.
@DisplayName("HEVC headers")
class HevcHeadersTest {

    /// The fixtures, and their traces.
    private record Stream(NalUnits units, HeaderTrace trace) {
        static Stream of(String name) {
            return new Stream(NalUnits.of(name), HeaderTrace.load(name.replace('.', '-') + ".trace"));
        }
    }

    private static byte[] rbsp(byte[] nal) {
        return BitReader.unescape(nal, 2, nal.length - 2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-hevc.mp4", "clip-hevc-10bit.mp4", "clip-hevc-pq.mp4"})
    @DisplayName("the parameter sets read as FFmpeg reads them")
    void parameterSets(String name) {
        var stream = Stream.of(name);
        VideoParameterSet vps = null;
        SequenceParameterSet sps = null;
        PictureParameterSet pps = null;
        for (var nal : stream.units().parameterSets()) {
            switch (NalHeader.of(nal).type()) {
                case NalHeader.VPS -> vps = VideoParameterSet.parse(rbsp(nal));
                case NalHeader.SPS -> sps = SequenceParameterSet.parse(rbsp(nal));
                case NalHeader.PPS -> pps = PictureParameterSet.parse(rbsp(nal));
                default -> {}
            }
        }
        assertTrue(vps != null && sps != null && pps != null);

        var v = stream.trace().first("Video Parameter Set");
        assertEquals(v.value("vps_video_parameter_set_id"), vps.id());
        assertEquals(v.value("vps_max_sub_layers_minus1"), vps.maxSubLayersMinus1());
        assertEquals(v.value("general_profile_idc"), vps.profileTierLevel().profileIdc());
        assertEquals(v.value("general_level_idc"), vps.profileTierLevel().levelIdc());
        assertEquals(
                v.value("vps_max_dec_pic_buffering_minus1[0]"),
                vps.ordering().getFirst().maxDecPicBufferingMinus1());
        assertEquals(
                v.value("vps_max_num_reorder_pics[0]"),
                vps.ordering().getFirst().maxNumReorderPics());
        assertEquals(v.value("vps_timing_info_present_flag") == 1, vps.timingInfoPresent());

        var s = stream.trace().first("Sequence Parameter Set");
        assertEquals(s.value("sps_seq_parameter_set_id"), sps.id());
        assertEquals(s.value("chroma_format_idc"), sps.chromaFormatIdc());
        assertEquals(s.value("pic_width_in_luma_samples"), sps.width());
        assertEquals(s.value("pic_height_in_luma_samples"), sps.height());
        assertEquals(s.find("conf_win_bottom_offset").orElse(0), sps.confWinBottom());
        assertEquals(s.value("bit_depth_luma_minus8"), sps.bitDepthLumaMinus8());
        assertEquals(s.value("log2_max_pic_order_cnt_lsb_minus4"), sps.log2MaxPocLsbMinus4());
        assertEquals(s.value("sps_max_num_reorder_pics[0]"), sps.highest().maxNumReorderPics());
        assertEquals(s.value("log2_diff_max_min_luma_coding_block_size"), sps.log2DiffMaxMinLumaCodingBlockSize());
        assertEquals(s.value("max_transform_hierarchy_depth_intra"), sps.maxTransformHierarchyDepthIntra());
        assertEquals(s.value("scaling_list_enabled_flag") == 1, sps.scalingListEnabled());
        assertEquals(s.value("amp_enabled_flag") == 1, sps.ampEnabled());
        assertEquals(s.value("sample_adaptive_offset_enabled_flag") == 1, sps.saoEnabled());
        assertEquals(
                s.value("num_short_term_ref_pic_sets"),
                sps.shortTermRefPicSets().size());
        assertEquals(s.value("long_term_ref_pics_present_flag") == 1, sps.longTermRefPicsPresent());
        assertEquals(s.value("sps_temporal_mvp_enabled_flag") == 1, sps.temporalMvpEnabled());
        assertEquals(s.value("strong_intra_smoothing_enabled_flag") == 1, sps.strongIntraSmoothingEnabled());
        assertEquals(s.value("vui_parameters_present_flag") == 1, sps.vui().isPresent());
        sps.vui().ifPresent(vui -> {
            assertEquals(s.find("colour_primaries").orElse(2), vui.colourPrimaries());
            assertEquals(s.find("transfer_characteristics").orElse(2), vui.transferCharacteristics());
            assertEquals(s.find("matrix_coefficients").orElse(2), vui.matrixCoefficients());
            assertEquals(s.find("vui_time_scale").orElse(0), vui.timeScale());
            assertEquals(s.value("bitstream_restriction_flag") == 1, vui.bitstreamRestriction());
        });
        assertEquals(s.value("sps_extension_present_flag") == 1, sps.extensionPresent());

        var p = stream.trace().first("Picture Parameter Set");
        assertEquals(p.value("pps_pic_parameter_set_id"), pps.id());
        assertEquals(p.value("num_extra_slice_header_bits"), pps.numExtraSliceHeaderBits());
        assertEquals(p.value("sign_data_hiding_enabled_flag") == 1, pps.signDataHidingEnabled());
        assertEquals(p.value("init_qp_minus26"), pps.initQpMinus26());
        assertEquals(p.value("cu_qp_delta_enabled_flag") == 1, pps.cuQpDeltaEnabled());
        assertEquals(p.find("diff_cu_qp_delta_depth").orElse(0), pps.diffCuQpDeltaDepth());
        assertEquals(p.value("pps_cb_qp_offset"), pps.cbQpOffset());
        assertEquals(p.value("weighted_pred_flag") == 1, pps.weightedPred());
        assertEquals(p.value("tiles_enabled_flag") == 1, pps.tilesEnabled());
        assertEquals(p.value("entropy_coding_sync_enabled_flag") == 1, pps.entropyCodingSyncEnabled());
        assertEquals(p.value("deblocking_filter_control_present_flag") == 1, pps.deblockingFilterControlPresent());
        assertEquals(p.value("lists_modification_present_flag") == 1, pps.listsModificationPresent());
        assertEquals(p.value("log2_parallel_merge_level_minus2"), pps.log2ParallelMergeLevelMinus2());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-hevc.mp4", "clip-hevc-10bit.mp4", "clip-hevc-pq.mp4"})
    @DisplayName("every slice header reads as FFmpeg reads it, as far as its long-term pictures")
    void sliceHeaders(String name) {
        var stream = Stream.of(name);
        Map<Integer, SequenceParameterSet> spss = new HashMap<>();
        Map<Integer, PictureParameterSet> ppss = new HashMap<>();
        var slices = new ArrayList<SliceSegmentHeader>();
        for (var nal : stream.units().all()) {
            var header = NalHeader.of(nal);
            switch (header.type()) {
                case NalHeader.SPS -> {
                    var sps = SequenceParameterSet.parse(rbsp(nal));
                    spss.put(sps.id(), sps);
                }
                case NalHeader.PPS -> {
                    var pps = PictureParameterSet.parse(rbsp(nal));
                    ppss.put(pps.id(), pps);
                }
                default -> {
                    if (header.isSlice()) {
                        slices.add(SliceSegmentHeader.parse(header, rbsp(nal), spss, ppss));
                    }
                }
            }
        }
        var traced = stream.trace().all("Slice Segment Header");
        assertEquals(traced.size(), slices.size());
        for (var i = 0; i < slices.size(); i++) {
            var t = traced.get(i);
            var slice = slices.get(i);
            var where = name + " slice " + i;
            assertEquals(t.value("nal_unit_type"), slice.nal().type(), where);
            assertEquals(t.value("first_slice_segment_in_pic_flag") == 1, slice.firstSliceSegmentInPic(), where);
            assertEquals(t.value("slice_type"), slice.sliceType(), where);
            assertEquals(t.find("slice_pic_order_cnt_lsb").orElse(0), slice.pocLsb(), where);
            assertEquals(
                    t.find("short_term_ref_pic_set_sps_flag").orElse(0) == 1, slice.shortTermRefPicSetSps(), where);
            if (t.find("num_negative_pics").isPresent()) {
                var set = slice.shortTermRefPicSet().orElseThrow();
                assertEquals(t.value("num_negative_pics"), set.negative(), where);
                assertEquals(t.value("num_positive_pics"), set.positive(), where);
                // The bits the set took: from its first element to the next
                // element after it.
                var start = t.bit("num_negative_pics");
                var next = t.bits().entrySet().stream()
                        .filter(e -> e.getValue() > start
                                && !e.getKey().startsWith("delta_poc_s")
                                && !e.getKey().startsWith("used_by_curr_pic_s")
                                && !e.getKey().startsWith("num_positive_pics"))
                        .mapToLong(Map.Entry::getValue)
                        .min()
                        .orElseThrow();
                assertEquals(next - start, slice.numBitsForShortTermRefPicSet(), where);
            }
            assertEquals(t.find("num_long_term_pics").orElse(0), slice.numLongTermPics(), where);
        }
    }

    @Test
    @DisplayName("an HDR10 stream's SPS says PQ in BT.2020, and ParameterSets passes it on")
    void colour() {
        var units = NalUnits.of("clip-hevc-pq.mp4");
        var sps = units.parameterSets().stream()
                .filter(nal -> NalHeader.of(nal).type() == NalHeader.SPS)
                .map(nal -> SequenceParameterSet.parse(rbsp(nal)))
                .findFirst()
                .orElseThrow();
        var colour = sps.vui().orElseThrow().colour();
        assertEquals(ColorDescription.Primaries.BT2020, colour.primaries());
        assertEquals(ColorDescription.Transfer.PQ, colour.transfer());
        assertEquals(List.of(160, 90), List.of(sps.visibleWidth(), sps.visibleHeight()));
    }
}
