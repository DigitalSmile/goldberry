package dev.goldberry.media.bitstream.hevc;

import dev.goldberry.media.bitstream.BitReader;

/// The general part of a `profile_tier_level()` (H.265 §7.3.3): the profile,
/// tier and level the stream conforms to. The sub-layers' own are read past.
///
/// @param profileSpace         `general_profile_space`
/// @param tier                 `general_tier_flag`: the high tier
/// @param profileIdc           `general_profile_idc`: 1 Main, 2 Main 10, 3 Main
///                             Still Picture, 4 the range extensions
/// @param compatibility        `general_profile_compatibility_flag[j]`, bit `j`
///                             of the value for flag `j`
/// @param progressiveSource    `general_progressive_source_flag`
/// @param interlacedSource     `general_interlaced_source_flag`
/// @param nonPackedConstraint  `general_non_packed_constraint_flag`
/// @param frameOnlyConstraint  `general_frame_only_constraint_flag`
/// @param levelIdc             `general_level_idc`: thirty times the level
public record ProfileTierLevel(
        int profileSpace,
        boolean tier,
        int profileIdc,
        int compatibility,
        boolean progressiveSource,
        boolean interlacedSource,
        boolean nonPackedConstraint,
        boolean frameOnlyConstraint,
        int levelIdc) {

    /// `profile_tier_level(1, maxSubLayersMinus1)`.
    static ProfileTierLevel read(BitReader r, int maxSubLayersMinus1) {
        var space = r.bits(2);
        var tier = r.flag();
        var profile = r.bits(5);
        var compatibility = r.bits(32);
        var progressive = r.flag();
        var interlaced = r.flag();
        var nonPacked = r.flag();
        var frameOnly = r.flag();
        // The 43 constraint bits and general_inbld_flag or its reserved bit.
        r.skip(44);
        var level = r.bits(8);
        var profilePresent = new boolean[maxSubLayersMinus1];
        var levelPresent = new boolean[maxSubLayersMinus1];
        for (var i = 0; i < maxSubLayersMinus1; i++) {
            profilePresent[i] = r.flag();
            levelPresent[i] = r.flag();
        }
        if (maxSubLayersMinus1 > 0) {
            r.skip(2L * (8 - maxSubLayersMinus1)); // reserved_zero_2bits
        }
        for (var i = 0; i < maxSubLayersMinus1; i++) {
            if (profilePresent[i]) {
                r.skip(88);
            }
            if (levelPresent[i]) {
                r.skip(8);
            }
        }
        return new ProfileTierLevel(
                space, tier, profile, compatibility, progressive, interlaced, nonPacked, frameOnly, level);
    }
}
