package dev.goldberry.media.platform.vulkan;

import java.util.Objects;

/// What a track's configuration says a Vulkan decoder needs of the device,
/// before a packet is read: the profile, and the coded size its session is made
/// at.
///
/// @param profile the profile the session decodes
/// @param width   the coded width
/// @param height  the coded height
public record VideoClaim(VideoProfile profile, int width, int height) {

    public VideoClaim {
        Objects.requireNonNull(profile, "profile");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a " + width + "×" + height + " picture");
        }
    }

    /// Whether `device` decodes this: the profile, and the size within its
    /// limits.
    public boolean decodesOn(VideoDevice device) {
        return VideoCapabilities.query(device, profile)
                .filter(c -> c.fits(width, height))
                .isPresent();
    }
}
