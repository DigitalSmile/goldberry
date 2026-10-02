package dev.goldberry.natives.desktop.macos;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.Upcalls;

/// The application's menu bar on macOS: `NSApp.mainMenu`, built from a tree of
/// [Item]s, with the standard application menu in front of it.
///
/// ```java
/// MacMenuBar.get().ifPresent(bar -> bar.install("Deploy Orc", headings, tag -> run(tag)));
/// ```
///
/// Every command row is an `NSMenuItem` whose target is one object of this
/// process's own class and whose action reports the row's **tag**: the number
/// the caller gave it. So one upcall serves every row of every menu, and what
/// a tag means stays on the Java side, where the tree that was installed is.
///
/// The application menu — the one named after the application, first on the
/// bar — is AppKit's own: About, Hide, Hide Others, Show All and Quit, each
/// sent to the application object, which is what every Mac application has
/// there. Quit is `terminate:`, which SDL turns into a quit event.
///
/// UNVERIFIED: written against AppKit's documentation and the runtime's C API,
/// not yet run on a Mac.
public final class MacMenuBar {

    private static final Logger LOG = Logs.of(MacMenuBar.class);

    /// `NSEventModifierFlagShift`, `Control`, `Option` and `Command`.
    public static final long SHIFT = 1L << 17;

    public static final long CONTROL = 1L << 18;
    public static final long OPTION = 1L << 19;
    public static final long COMMAND = 1L << 20;

    /// `void (*)(id self, SEL _cmd, id sender)`.
    private static final FunctionDescriptor ACTION =
            Upcalls.describe(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

    private static final String CHOOSE = "goldberryChoose:";

    private static @Nullable MacMenuBar instance;

    private final ObjC objc;
    private @Nullable MemorySegment target;
    private @Nullable MemorySegment previous;
    private IntConsumer chosen = tag -> {};

    /// One row of a menu, already in AppKit's terms.
    ///
    /// @param kind          what it is
    /// @param title         its text; ignored for a separator
    /// @param keyEquivalent the character AppKit matches, lower case unless
    ///                      Shift is meant, or empty for none
    /// @param modifiers     the modifier mask that goes with it
    /// @param enabled       false greys it out
    /// @param checked       whether it shows a tick
    /// @param tag           what [#install]'s listener is told when it is chosen
    /// @param children      a submenu's rows
    public record Item(
            Kind kind,
            String title,
            String keyEquivalent,
            long modifiers,
            boolean enabled,
            boolean checked,
            int tag,
            List<Item> children) {

        public Item {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(keyEquivalent, "keyEquivalent");
            children = List.copyOf(children);
        }

        /// What a row is.
        public enum Kind {
            /// A row that does something when chosen.
            COMMAND,
            /// A row that opens [Item#children].
            SUBMENU,
            /// A dividing line.
            SEPARATOR
        }
    }

    private MacMenuBar(ObjC objc) {
        this.objc = objc;
    }

    /// The menu bar, or empty off macOS.
    public static synchronized Optional<MacMenuBar> get() {
        if (instance == null) {
            instance = ObjC.get().map(MacMenuBar::new).orElse(null);
        }
        return Optional.ofNullable(instance);
    }

    /// Replaces the application's main menu with `appName`'s application menu
    /// followed by `headings`, each a [Item.Kind#SUBMENU].
    ///
    /// The menu that was there — SDL's, the first time — is kept and put back
    /// by [#uninstall].
    ///
    /// @param chosen told the tag of a command row the user chooses, on the
    ///        main thread
    /// @return false where AppKit would not take it
    public boolean install(String appName, List<Item> headings, IntConsumer chosen) {
        Objects.requireNonNull(appName, "appName");
        Objects.requireNonNull(headings, "headings");
        this.chosen = Objects.requireNonNull(chosen, "chosen");
        try {
            return objc.autoreleased(() -> {
                var application = objc.send(objc.cls("NSApplication"), "sharedApplication");
                if (previous == null) {
                    var current = objc.send(application, "mainMenu");
                    if (current.address() != 0) {
                        previous = objc.send(current, "retain");
                    }
                }
                var bar = menu("");
                add(bar, submenuItem(appName, applicationMenu(appName)));
                for (var heading : headings) {
                    add(bar, item(heading));
                }
                objc.sendVoid(application, "setMainMenu:", bar);
                objc.sendVoid(bar, "release");
                return true;
            });
        } catch (RuntimeException e) {
            LOG.warn("the menu bar could not be put in the application's main menu: {}", e.toString());
            return false;
        }
    }

    /// Puts back the main menu [#install] replaced.
    public void uninstall() {
        try {
            objc.autoreleased(() -> {
                if (previous != null) {
                    var application = objc.send(objc.cls("NSApplication"), "sharedApplication");
                    objc.sendVoid(application, "setMainMenu:", previous);
                    objc.sendVoid(previous, "release");
                    previous = null;
                }
                return null;
            });
        } catch (RuntimeException e) {
            LOG.debug("the previous main menu could not be put back: {}", e.toString());
        }
    }

    /// The application menu: About, Hide, Hide Others, Show All, Quit.
    private MemorySegment applicationMenu(String appName) {
        var menu = menu(appName);
        add(menu, standard("About " + appName, "orderFrontStandardAboutPanel:", "", 0));
        add(menu, objc.send(objc.send(objc.cls("NSMenuItem"), "separatorItem"), "retain"));
        add(menu, standard("Hide " + appName, "hide:", "h", COMMAND));
        add(menu, standard("Hide Others", "hideOtherApplications:", "h", COMMAND | OPTION));
        add(menu, standard("Show All", "unhideAllApplications:", "", 0));
        add(menu, objc.send(objc.send(objc.cls("NSMenuItem"), "separatorItem"), "retain"));
        add(menu, standard("Quit " + appName, "terminate:", "q", COMMAND));
        return menu;
    }

    /// A row of the application menu, sent up the responder chain to the
    /// application object: no target, and AppKit's own enabling.
    private MemorySegment standard(String title, String action, String key, long modifiers) {
        var row = objc.send(
                objc.send(objc.cls("NSMenuItem"), "alloc"),
                "initWithTitle:action:keyEquivalent:",
                objc.string(title),
                objc.sel(action),
                objc.string(key));
        if (modifiers != 0) {
            objc.sendVoid(row, "setKeyEquivalentModifierMask:", modifiers);
        }
        return row;
    }

    /// One of the caller's rows, retained once for [#add] to hand over.
    private MemorySegment item(Item item) {
        return switch (item.kind()) {
            case SEPARATOR -> objc.send(objc.send(objc.cls("NSMenuItem"), "separatorItem"), "retain");
            case SUBMENU -> {
                var menu = menu(item.title());
                for (var child : item.children()) {
                    add(menu, item(child));
                }
                var row = submenuItem(item.title(), menu);
                objc.sendVoid(row, "setEnabled:", item.enabled());
                yield row;
            }
            case COMMAND -> {
                var row = objc.send(
                        objc.send(objc.cls("NSMenuItem"), "alloc"),
                        "initWithTitle:action:keyEquivalent:",
                        objc.string(item.title()),
                        objc.sel(CHOOSE),
                        objc.string(item.keyEquivalent()));
                objc.sendVoid(row, "setTarget:", target());
                objc.sendVoid(row, "setTag:", (long) item.tag());
                objc.sendVoid(row, "setKeyEquivalentModifierMask:", item.modifiers());
                objc.sendVoid(row, "setEnabled:", item.enabled());
                objc.sendVoid(row, "setState:", item.checked() ? 1L : 0L);
                yield row;
            }
        };
    }

    /// A row titled `title` that opens `menu`, which it takes over.
    private MemorySegment submenuItem(String title, MemorySegment menu) {
        var row = objc.send(
                objc.send(objc.cls("NSMenuItem"), "alloc"),
                "initWithTitle:action:keyEquivalent:",
                objc.string(title),
                MemorySegment.NULL,
                objc.string(""));
        objc.sendVoid(row, "setSubmenu:", menu);
        objc.sendVoid(menu, "release");
        return row;
    }

    /// An empty menu, retained. Its rows are enabled as [Item#enabled] says
    /// rather than by AppKit asking each target.
    private MemorySegment menu(String title) {
        var menu = objc.send(objc.send(objc.cls("NSMenu"), "alloc"), "initWithTitle:", objc.string(title));
        objc.sendVoid(menu, "setAutoenablesItems:", false);
        return menu;
    }

    /// Adds `row` to `menu` and drops this side's reference to it.
    private void add(MemorySegment menu, MemorySegment row) {
        objc.sendVoid(menu, "addItem:", row);
        objc.sendVoid(row, "release");
    }

    /// The object every command row is sent to, made once.
    private MemorySegment target() {
        if (target == null) {
            var made = objc.defineClass(
                    "GoldberryMenuTarget", "NSObject", null, Map.of(CHOOSE, new ObjC.Method(actionStub(), "v@:@")));
            target = objc.send(objc.send(made, "alloc"), "init");
        }
        return target;
    }

    /// `goldberryChoose:`. **Called from Objective-C; must not throw.**
    @SuppressWarnings("unused")
    private static void choose(MemorySegment self, MemorySegment command, MemorySegment sender) {
        var bar = instance;
        if (bar == null) {
            return;
        }
        try {
            bar.chosen.accept((int) bar.objc.sendLong(sender, "tag"));
        } catch (Throwable t) {
            LOG.warn("a menu command failed", t);
        }
    }

    @SuppressWarnings("restricted")
    private static MemorySegment actionStub() {
        try {
            var target = MethodHandles.lookup()
                    .findStatic(
                            MacMenuBar.class,
                            "choose",
                            MethodType.methodType(
                                    void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            return Linker.nativeLinker().upcallStub(target, ACTION, Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("no method for a menu command", e);
        }
    }
}
