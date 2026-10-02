package dev.goldberry.media.picture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;

/// How a [MediaPlayer] decides the [PictureForm] from the views attached to it.
/// Nothing is opened, so no FFmpeg: what a playback
/// does with the form is in `VideoPlaybackTest`.
@DisplayName("MediaPlayer's picture form")
class PictureFormTest {

    private final MediaPlayer player = MediaPlayer.builder()
            .sink(() -> new VirtualSink(AudioFormat.DEFAULT, true))
            .build();

    @AfterEach
    void close() {
        player.close();
    }

    @Test
    @DisplayName("converts with no view attached")
    void convertedByDefault() {
        assertEquals(PictureForm.CONVERTED, player.pictureForm());
        assertTrue(player.shownPicture().isEmpty());
    }

    @Test
    @DisplayName("prepares planes only while every view attached asks for them")
    void planesOnlyWhenEveryViewAsks() {
        var gpu = player.attachView(PictureForm.PLANES);
        assertEquals(PictureForm.PLANES, player.pictureForm());
        var second = player.attachView(PictureForm.PLANES);
        assertEquals(PictureForm.PLANES, player.pictureForm());

        var cpu = player.attachView(PictureForm.CONVERTED);
        assertEquals(PictureForm.CONVERTED, player.pictureForm(), "a CPU view is never starved");
        cpu.close();
        assertEquals(PictureForm.PLANES, player.pictureForm());

        gpu.close();
        second.close();
        assertEquals(PictureForm.CONVERTED, player.pictureForm(), "the last view gone, the default again");
    }

    @Test
    @DisplayName("follows a view that changes what it draws, as one that loses its GPU does")
    void viewChangesItsForm() {
        var view = player.attachView(PictureForm.PLANES);
        view.setForm(PictureForm.CONVERTED);
        assertEquals(PictureForm.CONVERTED, view.form());
        assertEquals(PictureForm.CONVERTED, player.pictureForm());
        view.setForm(PictureForm.PLANES);
        assertEquals(PictureForm.PLANES, player.pictureForm());
    }

    @Test
    @DisplayName("an attachment closed twice closes once, and changes nothing after")
    void closeIsIdempotent() {
        var view = player.attachView(PictureForm.PLANES);
        var other = player.attachView(PictureForm.PLANES);
        view.close();
        view.close();
        assertEquals(PictureForm.PLANES, player.pictureForm(), "the second close removed nothing more");
        view.setForm(PictureForm.CONVERTED);
        assertEquals(PictureForm.PLANES, player.pictureForm(), "a closed view has no say");
        assertTrue(view.toString().contains("closed"));
        other.close();
    }
}
