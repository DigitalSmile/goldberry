package dev.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

import dev.goldberry.Goldberry;
import dev.goldberry.RendererRequirement;
import dev.goldberry.Window;
import dev.goldberry.junit.HeadlessRuntime;

@ExtendWith(HeadlessRuntime.class)
class ShowcaseStartupTest {

    @Test
    void registersTheTrayOnceAfterTheFirstPresentation() {
        RendererRequirement.enforce();

        var messages = launchAndRecord();
        var firstPresent = messages.stream()
                .filter(message -> message.startsWith("frame ") && message.contains(", present "))
                .findFirst()
                .orElseThrow();
        assertTrue(messages.indexOf("tray shown") > messages.indexOf(firstPresent));
        assertEquals(1, messages.stream().filter("tray shown"::equals).count());
    }

    @Test
    void skipsTheTrayIfTheLoopStopsBeforePainting() {
        RendererRequirement.enforce();
        Goldberry.ui().execute(Goldberry::stop);

        var messages = launchAndRecord();
        assertTrue(messages.stream().noneMatch(message -> message.startsWith("tray ")));
    }

    private static List<String> launchAndRecord() {
        var window = (Logger) LoggerFactory.getLogger(Window.class);
        var showcase = (Logger) LoggerFactory.getLogger(Showcase.class);
        var oldLevel = window.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.list = new CopyOnWriteArrayList<>();
        appender.start();
        window.setLevel(Level.TRACE);
        window.addAppender(appender);
        showcase.addAppender(appender);
        try {
            Goldberry.launch(new Showcase(), new String[] {"--frames=3", "--size=900x700"});
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
        } finally {
            showcase.detachAppender(appender);
            window.detachAppender(appender);
            window.setLevel(oldLevel);
            appender.stop();
        }
    }
}
