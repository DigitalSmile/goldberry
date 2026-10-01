package dev.goldberry.media.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.GoldberryTestAccess;
import dev.goldberry.Host;
import dev.goldberry.bind.Subscription;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.media.FfmpegRequirement;
import dev.goldberry.media.HardwareDecoding;
import dev.goldberry.media.MediaPlayer;
import dev.goldberry.media.PlaybackState;
import dev.goldberry.media.Wav;
import dev.goldberry.media.audio.AudioFormat;
import dev.goldberry.media.audio.VirtualSink;
import dev.goldberry.media.io.MediaIO;
import dev.goldberry.media.io.MediaIOProvider;
import dev.goldberry.media.io.MemoryIO;
import dev.goldberry.media.io.Source;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;

/// `media-player`'s fullscreen (ADR-0473): the button and keys, the copy laid
/// over the window, and the window asked and given back, against a host that
/// records what it was asked and reports what a platform would.
@DisplayName("media-player fullscreen")
class MediaPlayerFullscreenTest {

    private static final int RATE = AudioFormat.DEFAULT.sampleRate();

    record Memory(byte[] data) implements MediaIOProvider {
        @Override
        public Set<String> schemes() {
            return Set.of("mem");
        }

        @Override
        public MediaIO open(Source source) {
            return new MemoryIO(data);
        }
    }

    /// A window as far as fullscreen goes, behind a [Host] made with a
    /// [Proxy]: every other method answers its default, or nothing.
    ///
    /// A proxy rather than a class because `Host` has some fifty methods, and
    /// the test host `:widgets` keeps is in its test sources, which this module
    /// does not see.
    static final class FakeWindow implements InvocationHandler {

        boolean offers = true;
        boolean fullscreen;
        final List<Boolean> asked = new ArrayList<>();
        final List<Consumer<Boolean>> listeners = new ArrayList<>();
        final List<Widget> filled = new ArrayList<>();
        int removed;

        final Host host = (Host) Proxy.newProxyInstance(Host.class.getClassLoader(), new Class<?>[] {Host.class}, this);

        /// What the platform says: the window came to fill its display, or left.
        void report(boolean full) {
            fullscreen = full;
            List.copyOf(listeners).forEach(listener -> listener.accept(full));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "canFullscreen" -> offers;
                case "isFullscreen" -> fullscreen;
                case "setFullscreen" -> {
                    asked.add((Boolean) args[0]);
                    yield null;
                }
                case "onFullscreenChanged" -> {
                    var listener = (Consumer<Boolean>) args[0];
                    listeners.add(listener);
                    yield (Subscription) () -> listeners.remove(listener);
                }
                case "fill" -> {
                    filled.add((Widget) args[0]);
                    yield GoldberryTestAccess.attachedFilling((Widget) args[0], () -> removed++);
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "FakeWindow";
                default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : nothing(method);
            };
        }

        private static Object nothing(Method method) {
            var type = method.getReturnType();
            if (type == boolean.class) {
                return false;
            }
            if (type == Optional.class) {
                return Optional.empty();
            }
            if (type == int.class || type == long.class || type == float.class || type == double.class) {
                return 0;
            }
            // `after` among them: a state that gets no timer sets none.
            return null;
        }
    }

    private MediaPlayer player;
    private final FakeWindow window = new FakeWindow();

    @BeforeEach
    void openPlaying() {
        FfmpegRequirement.enforce();
        player = MediaPlayer.builder()
                .hardwareDecoding(HardwareDecoding.OFF)
                .sink(() -> new VirtualSink(AudioFormat.DEFAULT, false))
                .ioProviders(List.of(new Memory(Wav.silence(RATE, 2, RATE * 12))))
                .decoderProviders(List.of())
                .build();
        player.open(Source.of(URI.create("mem:///clip.wav")));
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (player.status().state() != PlaybackState.PLAYING) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("never played; last " + player.status());
            }
            Thread.onSpinWait();
        }
    }

    @AfterEach
    void close() {
        if (player != null) {
            player.close();
        }
    }

    private static List<Element> walk(Element root) {
        var all = new ArrayList<Element>();
        all.add(root);
        for (var child : root.children()) {
            all.addAll(walk(child));
        }
        return all;
    }

    private static MediaPlayerBox box(ElementTree tree) {
        return walk(tree.root()).stream()
                .map(Element::widget)
                .filter(MediaPlayerBox.class::isInstance)
                .map(MediaPlayerBox.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static Optional<Button> fullscreenButton(ElementTree tree) {
        return walk(tree.root()).stream()
                .map(Element::widget)
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(button -> button.attributes().classes().contains("media-fullscreen"))
                .findFirst();
    }

    private static KeyEvent key(Key key) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
    }

    private ElementTree inline() {
        return new ElementTree(new MediaPlayerView(player).withAttributes(Attributes.NONE.id("tv")), window.host);
    }

    @Test
    @DisplayName("with no window there is no button, and F is not taken")
    void noWindowNoButton() {
        var tree = new ElementTree(new MediaPlayerView(player));
        assertTrue(fullscreenButton(tree).isEmpty(), "every golden is taken with no window, and must not change");
        var f = key(Key.F);
        box(tree).onKey(f);
        assertFalse(f.isConsumed());
    }

    @Test
    @DisplayName("a host that can says so with a button, last in the controls")
    void buttonLast() {
        var tree = inline();
        var bar = walk(tree.root()).stream()
                .map(Element::widget)
                .filter(MediaControlsBar.class::isInstance)
                .map(MediaControlsBar.class::cast)
                .findFirst()
                .orElseThrow();
        var last = assertInstanceOf(Button.class, bar.children().getLast());
        assertTrue(last.attributes().classes().contains("media-fullscreen"));
    }

    @Test
    @DisplayName("F lays a copy over the window and asks the window to fill its display")
    void enters() {
        var tree = inline();
        var f = key(Key.F);
        box(tree).onKey(f);

        assertTrue(f.isConsumed());
        assertEquals(List.of(true), window.asked);
        var copy = assertInstanceOf(MediaPlayerView.FullscreenPlayer.class, window.filled.getFirst());
        var copyBox = box(new ElementTree(copy, window.host));
        assertTrue(copyBox.classes().contains("is-fullscreen"));
        assertNull(copyBox.id(), "the player in the layout keeps its id");
        assertFalse(box(tree).classes().contains("is-fullscreen"));
    }

    @Test
    @DisplayName("Esc on the copy leaves, and gives the window back")
    void escapeLeaves() {
        var tree = inline();
        box(tree).onKey(key(Key.F));
        var copy = new ElementTree(window.filled.getFirst(), window.host);

        var escape = key(Key.ESCAPE);
        box(copy).onKey(escape);

        assertTrue(escape.isConsumed());
        assertEquals(1, window.removed);
        assertEquals(List.of(true, false), window.asked);
        assertTrue(window.listeners.isEmpty(), "the player stopped listening when it left");
    }

    @Test
    @DisplayName("F again leaves, from the player in the layout too, and the button follows")
    void toggles() {
        var tree = inline();
        box(tree).onKey(key(Key.F));
        tree.flush();
        box(tree).onKey(key(Key.F));
        tree.flush();

        assertEquals(1, window.removed);
        assertEquals(List.of(true, false), window.asked);
        box(tree).onKey(key(Key.F));
        assertEquals(2, window.filled.size(), "and enters again");
    }

    @Test
    @DisplayName("Esc outside fullscreen is left for whatever is around the player")
    void escapeIsNotTaken() {
        var escape = key(Key.ESCAPE);
        box(inline()).onKey(escape);
        assertFalse(escape.isConsumed());
        assertTrue(window.filled.isEmpty());
    }

    @Test
    @DisplayName("a window already fullscreen is not asked in, nor out when the player leaves")
    void alreadyFullscreen() {
        window.fullscreen = true;
        var tree = inline();
        box(tree).onKey(key(Key.F));
        box(tree).onKey(key(Key.F));

        assertEquals(1, window.filled.size());
        assertEquals(1, window.removed);
        assertEquals(List.of(), window.asked);
    }

    @Test
    @DisplayName("the user leaving with the platform's button takes the copy away, and asks nothing")
    void platformLeaves() {
        var tree = inline();
        box(tree).onKey(key(Key.F));
        window.report(true);
        window.report(false);

        assertEquals(1, window.removed);
        assertEquals(List.of(true), window.asked, "the window already left; asking again would be noise");
        assertTrue(window.listeners.isEmpty());
    }

    @Test
    @DisplayName("a player taken out of the tree while fullscreen leaves, and gives the window back")
    void disposeLeaves() {
        var tree = inline();
        box(tree).onKey(key(Key.F));
        tree.unmount();

        assertEquals(1, window.removed);
        assertEquals(List.of(true, false), window.asked);
    }

    @Test
    @DisplayName("a held F toggles once")
    void repeatIsIgnored() {
        var tree = inline();
        box(tree).onKey(key(Key.F));
        var held = new KeyEvent(KeyEvent.Kind.PRESSED, Key.F, Modifiers.NONE, true, null);
        box(tree).onKey(held);

        assertTrue(held.isConsumed());
        assertEquals(List.of(true), window.asked);
    }
}
