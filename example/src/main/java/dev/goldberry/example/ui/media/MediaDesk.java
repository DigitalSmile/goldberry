package dev.goldberry.example.ui.media;

import dev.goldberry.example.media.ShowcaseMedia;

/// What the media cards ask their screen to do.
///
/// The cards are values that say what they show; the screen holds what more than
/// one of them shares (the source open now, the line under the picker) and does
/// what a press asks. The calls that touch nothing but the player go to the player
/// directly.
///
/// Read more: [A player](https://goldberry.dev/docs/components/media.html#a-player).
interface MediaDesk {

    /// Opens the bundled sample whose key is `key`, and plays it.
    void open(String key);

    /// Asks for a file from disk, and plays it.
    void openFile();

    /// Pauses, and says the source can be changed.
    void closeSource();

    /// Sets the speed, and says why when the player refuses it.
    void setSpeed(float speed);

    /// Steps `pictures` on, or back when negative.
    void step(int pictures);

    /// Loads a bundled subtitle file over what plays.
    void loadSubtitles(ShowcaseMedia.SubtitleFile file);

    /// Asks for a subtitle file from disk, and loads it.
    void openSubtitleFile();

    /// Switches hardware decoding, and reopens what plays where it was.
    void setHardware(boolean on);

    /// Switches the application's own decoder.
    void setJavaDecoder(boolean on);
}
