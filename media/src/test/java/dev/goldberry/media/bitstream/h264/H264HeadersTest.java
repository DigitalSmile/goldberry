package dev.goldberry.media.bitstream.h264;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.media.bitstream.BitReader;
import dev.goldberry.media.bitstream.HeaderTrace;
import dev.goldberry.media.bitstream.NalUnits;

/// The H.264 readers against FFmpeg's `trace_headers` of the same streams,
/// element by element, and every slice header read to the same bit.
@DisplayName("H.264 headers")
class H264HeadersTest {

    private static byte[] rbsp(byte[] nal) {
        return BitReader.unescape(nal, 1, nal.length - 1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"clip-h264-high.mp4", "clip-h264-full-709.mp4"})
    @DisplayName("the parameter sets and every slice header read as FFmpeg reads them")
    void headers(String name) {
        var units = NalUnits.of(name);
        var trace = HeaderTrace.load(name.replace('.', '-') + ".trace");
        Map<Integer, SequenceParameterSet> spss = new HashMap<>();
        Map<Integer, PictureParameterSet> ppss = new HashMap<>();
        var slices = new ArrayList<SliceHeader>();
        for (var nal : units.all()) {
            var header = NalHeader.of(nal);
            switch (header.type()) {
                case NalHeader.SPS -> {
                    var sps = SequenceParameterSet.parse(rbsp(nal));
                    spss.put(sps.id(), sps);
                }
                case NalHeader.PPS -> {
                    var pps = PictureParameterSet.parse(rbsp(nal), spss);
                    ppss.put(pps.id(), pps);
                }
                default -> {
                    if (header.isSlice()) {
                        slices.add(SliceHeader.parse(header, rbsp(nal), spss, ppss));
                    }
                }
            }
        }
        var sps = spss.values().iterator().next();
        var s = trace.first("Sequence Parameter Set");
        assertEquals(s.value("profile_idc"), sps.profileIdc());
        assertEquals(s.value("level_idc"), sps.levelIdc());
        assertEquals(s.find("chroma_format_idc").orElse(1), sps.chromaFormatIdc());
        assertEquals(s.value("log2_max_frame_num_minus4"), sps.log2MaxFrameNumMinus4());
        assertEquals(s.value("pic_order_cnt_type"), sps.picOrderCntType());
        assertEquals(s.find("log2_max_pic_order_cnt_lsb_minus4").orElse(0), sps.log2MaxPocLsbMinus4());
        assertEquals(s.value("max_num_ref_frames"), sps.maxNumRefFrames());
        assertEquals(s.value("pic_width_in_mbs_minus1"), sps.picWidthInMbsMinus1());
        assertEquals(s.value("pic_height_in_map_units_minus1"), sps.picHeightInMapUnitsMinus1());
        assertEquals(s.value("frame_mbs_only_flag") == 1, sps.frameMbsOnly());
        assertEquals(s.value("direct_8x8_inference_flag") == 1, sps.direct8x8Inference());
        assertEquals(s.find("frame_crop_bottom_offset").orElse(0), sps.cropBottom());
        sps.vui().ifPresent(vui -> {
            assertEquals(s.find("matrix_coefficients").orElse(2), vui.matrixCoefficients());
            assertEquals(s.find("max_num_reorder_frames").orElse(-1), vui.maxNumReorderFrames());
            assertEquals(s.find("max_dec_frame_buffering").orElse(-1), vui.maxDecFrameBuffering());
        });

        var pps = ppss.values().iterator().next();
        var p = trace.first("Picture Parameter Set");
        assertEquals(p.value("entropy_coding_mode_flag") == 1, pps.entropyCodingMode());
        assertEquals(p.value("num_ref_idx_l0_default_active_minus1"), pps.numRefIdxL0DefaultActiveMinus1());
        assertEquals(p.value("weighted_pred_flag") == 1, pps.weightedPred());
        assertEquals(p.value("weighted_bipred_idc"), pps.weightedBipredIdc());
        assertEquals(p.value("pic_init_qp_minus26"), pps.picInitQpMinus26());
        assertEquals(p.value("chroma_qp_index_offset"), pps.chromaQpIndexOffset());
        assertEquals(p.find("transform_8x8_mode_flag").orElse(0) == 1, pps.transform8x8Mode());
        assertEquals(
                p.find("second_chroma_qp_index_offset").orElse(pps.chromaQpIndexOffset()),
                pps.secondChromaQpIndexOffset());

        var traced = trace.all("Slice Header");
        assertEquals(traced.size(), slices.size());
        for (var i = 0; i < slices.size(); i++) {
            var t = traced.get(i);
            var slice = slices.get(i);
            var where = name + " slice " + i;
            assertEquals(t.value("slice_type") % 5, slice.sliceType(), where);
            assertEquals(t.value("frame_num"), slice.frameNum(), where);
            assertEquals(t.find("pic_order_cnt_lsb").orElse(0), slice.pocLsb(), where);
            assertEquals(t.find("idr_pic_id").orElse(0), slice.idrPicId(), where);
            assertEquals(
                    t.find("adaptive_ref_pic_marking_mode_flag").orElse(0) == 1, slice.adaptiveRefPicMarking(), where);
            // Read to the bit FFmpeg reads the next element at. The NAL header
            // is the first eight bits of the trace's positions.
            var next = t.find("cabac_init_idc").isPresent() ? "cabac_init_idc" : "slice_qp_delta";
            assertEquals(t.bit(next) - 8, slice.bits(), where + ": the header ends where " + next + " starts");
        }
    }
}
