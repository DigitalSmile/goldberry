package io.github.digitalsmile.goldberry.render.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.Backend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;

/// The SPI's own half of G9: what a backend that has never heard of file dialogs
/// reports, and that the headless one has real ones.
class FileDialogsTest {

    @Test
    @DisplayName("none() answers a failure rather than a cancel")
    void noneFails() {
        var answers = new ArrayList<FileChoice>();

        FileDialogs.none().show(null, FileDialogSpec.openFile(), answers::add);

        assertEquals(1, answers.size());
        var failed = assertInstanceOf(FileChoice.Failed.class, answers.getFirst());
        assertFalse(failed.message().isBlank(), "a caller whose export did nothing needs something to say");
    }

    @Test
    @DisplayName("none() reports itself unsupported, which is what a menu asks")
    void noneIsUnsupported() {
        assertFalse(FileDialogs.none().supported());
    }

    @Test
    @DisplayName("is what a backend that has not been told about dialogs reports")
    void isTheDefault() {
        var bare = new Backend() {

            @Override
            public String name() {
                return "bare";
            }

            @Override
            public io.github.digitalsmile.goldberry.render.window.BackendWindow createWindow(
                    io.github.digitalsmile.goldberry.render.window.WindowSpec spec) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.List<io.github.digitalsmile.goldberry.render.window.BackendWindow> windows() {
                return java.util.List.of();
            }

            @Override
            public int pumpEvents(
                    io.github.digitalsmile.goldberry.render.event.EventSink sink, java.time.Duration timeout) {
                return 0;
            }

            @Override
            public void wakeup() {}

            @Override
            public void close() {}
        };

        assertFalse(bare.fileDialogs().supported());
    }

    @Test
    @DisplayName("the headless backend has real ones, and the same instance every time")
    void headlessHasThem() {
        try (var backend = new HeadlessBackend()) {
            assertSame(backend.fileDialogs(), backend.fileDialogs());
            assertEquals(true, backend.fileDialogs().supported());
        }
    }
}
