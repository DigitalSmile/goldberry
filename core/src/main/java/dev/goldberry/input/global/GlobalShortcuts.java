package dev.goldberry.input.global;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/// Shortcuts that work while the application is not focused: push-to-talk,
/// mute, a global "show the window".
///
/// ```java
/// var shortcuts = GlobalShortcuts.open();
/// shortcuts.bind(
///                 List.of(GlobalShortcut.of("talk", "Push to talk", "Ctrl+Alt+T")),
///                 event -> microphone.open(event.isPress()))
///         .thenAccept(grants -> settings.show(grants));
/// // when the application ends
/// shortcuts.close();
/// ```
///
/// | Desktop | How it is asked                                  | Who decides                    |
/// |---------|--------------------------------------------------|--------------------------------|
/// | Wayland | the `GlobalShortcuts` desktop portal             | the user, in a dialog, and may choose another trigger
/// |
/// | X11     | a key grab on the root window                    | whichever client grabbed it first |
/// | Windows | `RegisterHotKey`                                 | whichever application registered it first |
/// | macOS   | Carbon's `RegisterEventHotKey`                   | whichever application registered it first |
///
/// ## Granted, not assumed
///
/// [#bind] answers with a [Grant] per shortcut, once the desktop has
/// answered. On Wayland that is after the user has, so it can take a while,
/// and a shortcut the user leaves out is [Grant.Refused]. Show the answer in
/// the application's settings: a shortcut that another application holds,
/// or that the user turned down, is not one the user should be told to press.
///
/// ## Press and release
///
/// The listener hears each granted shortcut go down and come up, once each:
/// a held key's repeats are not passed on. A shortcut held when the
/// shortcuts are bound again or closed is released first.
///
/// ## Threads
///
/// Opened on the UI thread, as an application usually does, the listener and
/// the future [#bind] answers run on the UI thread. Opened anywhere else, they
/// run on the thread the desktop's answer arrives on, or on the executor
/// [#open(Executor)] was given. The calls themselves are safe from any thread.
///
/// On macOS a hotkey is registered and heard on the main thread, which is the
/// UI thread, so open them there: the bind is run on the executor.
///
/// ## In tests
///
/// [#simulated()] grants what it is asked for and presses what the test
/// tells it to, so an application's push-to-talk can be tested without a
/// desktop.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
public sealed interface GlobalShortcuts extends AutoCloseable permits SystemGlobalShortcuts, SimulatedGlobalShortcuts {

    /// The desktop's global shortcuts, on a connection of their own; close
    /// them when done. Where this desktop has no way to ask, every shortcut is
    /// [Grant.Unsupported] and [#isSupported()] says so.
    static GlobalShortcuts open() {
        return SystemGlobalShortcuts.open(SystemGlobalShortcuts.uiThread());
    }

    /// The same, delivering events and answers on `executor`.
    static GlobalShortcuts open(Executor executor) {
        return SystemGlobalShortcuts.open(executor);
    }

    /// Global shortcuts of a test's own: see [SimulatedGlobalShortcuts].
    static SimulatedGlobalShortcuts simulated() {
        return new SimulatedGlobalShortcuts();
    }

    /// How this desktop is asked: `GlobalShortcuts portal`, `X11 key grabs`,
    /// `Windows hotkeys`, `Carbon hotkeys`, `simulated`, or `none`.
    String name();

    /// Whether there is a way to ask at all. True does not promise a grant:
    /// on Wayland that is a portal on the bus, whose interface only a bind
    /// finds out about.
    boolean isSupported();

    /// Asks for `shortcuts`, in place of the ones bound before.
    ///
    /// An empty list unbinds them all.
    ///
    /// @param listener hears the granted shortcuts go down and come up
    /// @return a grant per shortcut, in the order given, once the desktop has
    ///         answered
    /// @throws IllegalArgumentException if two shortcuts share an id
    /// @throws IllegalStateException if these shortcuts are closed
    CompletableFuture<List<Grant>> bind(List<GlobalShortcut> shortcuts, Consumer<GlobalShortcutEvent> listener);

    /// Unbinds every shortcut and lets go of the desktop, releasing any that is
    /// held. Safe to call more than once.
    @Override
    void close();
}
