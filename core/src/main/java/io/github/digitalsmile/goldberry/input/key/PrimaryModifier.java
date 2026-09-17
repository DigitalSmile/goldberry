package io.github.digitalsmile.goldberry.input.key;

import java.util.Locale;

/// Which modifier this desktop uses for its accelerators — `Cmd` on macOS,
/// `Ctrl` everywhere else.
///
/// ## Why this is a modifier you ask for and not a translation
///
/// `docs/design-system.md` §2.3 wants accelerators expressed against a platform
/// primary modifier "via one `Shortcut` abstraction". [Shortcut] refused, and
/// its reason stands: a toolkit that silently turned every `Ctrl` into `Cmd` on
/// macOS would make `Ctrl+C` mean two different things depending on where it
/// ran, and a terminal emulator, a text editor with Emacs bindings and anything
/// else that means the *control key* would be broken by the translation.
///
/// Both are satisfied by naming the thing. `Primary+S` is a shortcut that says
/// "whatever this desktop uses for Save", and `Ctrl+S` is a shortcut that says
/// `Ctrl`; neither is guessed from the other ([ADR-0378]).
///
/// ## The override
///
/// `-Dgoldberry.input.primary=ctrl|meta` decides it, for a test and for an
/// application that has a reason. Read once, because the answer cannot change
/// while the process runs — nobody moves a window to a different operating
/// system.
public final class PrimaryModifier {

    /// The system property that decides it: `ctrl` or `meta`.
    public static final String PROPERTY = "goldberry.input.primary";

    private static final Mod CURRENT = resolve(System.getProperty("os.name", ""), System.getProperty(PROPERTY));

    private PrimaryModifier() {}

    /// The modifier accelerators are written against here.
    public static Mod current() {
        return CURRENT;
    }

    /// The same decision from raw values, so the mapping is testable without
    /// three machines — which is [io.github.digitalsmile.goldberry.natives.NativePlatform]'s
    /// rule for the same problem.
    ///
    /// @param osName   the value of `os.name`
    /// @param override the value of [#PROPERTY], or null
    static Mod resolve(String osName, String override) {
        if (override != null) {
            return switch (override.toLowerCase(Locale.ROOT)) {
                case "meta", "cmd", "command", "super" -> Mod.META;
                default -> Mod.CTRL;
            };
        }
        var name = osName.toLowerCase(Locale.ROOT);
        return name.contains("mac") || name.contains("darwin") ? Mod.META : Mod.CTRL;
    }
}
