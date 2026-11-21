package dev.goldberry.natives.desktop.shortcuts;

/// A key a global shortcut can be on, with what each platform calls it.
///
/// The constants are named as `:core`'s `Key` names them, so the toolkit maps
/// one to the other by name, and a test holds the two lists together.
///
/// | Column       | Where it comes from                                            |
/// |--------------|----------------------------------------------------------------|
/// | `keysym`     | `X11/keysymdef.h`, which XKB and the portal's triggers share   |
/// | `keysymName` | the same header's names without `XK_`                          |
/// | `windowsVk`  | `WinUser.h`'s `VK_*`                                           |
/// | `macKeyCode` | `HIToolbox/Events.h`'s `kVK_*`, or -1 where a Mac has no such key |
///
/// A keysym is a symbol, so on X11 and Wayland the shortcut follows the
/// user's layout. The Windows and macOS codes are the US layout's virtual
/// keys, which is what `RegisterHotKey` and `RegisterEventHotKey` take: the
/// letters land where the layout puts them on Windows and where the US layout
/// does on macOS, as every other Carbon hotkey does.
public enum HotkeyKey {
    ENTER(0xff0d, "Return", 0x0D, 0x24),
    ESCAPE(0xff1b, "Escape", 0x1B, 0x35),
    BACKSPACE(0xff08, "BackSpace", 0x08, 0x33),
    TAB(0xff09, "Tab", 0x09, 0x30),
    SPACE(0x0020, "space", 0x20, 0x31),
    DELETE(0xffff, "Delete", 0x2E, 0x75),

    LEFT(0xff51, "Left", 0x25, 0x7B),
    RIGHT(0xff53, "Right", 0x27, 0x7C),
    UP(0xff52, "Up", 0x26, 0x7E),
    DOWN(0xff54, "Down", 0x28, 0x7D),
    HOME(0xff50, "Home", 0x24, 0x73),
    END(0xff57, "End", 0x23, 0x77),
    PAGE_UP(0xff55, "Prior", 0x21, 0x74),
    PAGE_DOWN(0xff56, "Next", 0x22, 0x79),
    /// The Mac's Help key sits where Insert is on a PC keyboard.
    INSERT(0xff63, "Insert", 0x2D, 0x72),
    /// No Mac keyboard has one.
    MENU(0xff67, "Menu", 0x5D, -1),

    F1(0xffbe, "F1", 0x70, 0x7A),
    F2(0xffbf, "F2", 0x71, 0x78),
    F3(0xffc0, "F3", 0x72, 0x63),
    F4(0xffc1, "F4", 0x73, 0x76),
    F5(0xffc2, "F5", 0x74, 0x60),
    F6(0xffc3, "F6", 0x75, 0x61),
    F7(0xffc4, "F7", 0x76, 0x62),
    F8(0xffc5, "F8", 0x77, 0x64),
    F9(0xffc6, "F9", 0x78, 0x65),
    F10(0xffc7, "F10", 0x79, 0x6D),
    F11(0xffc8, "F11", 0x7A, 0x67),
    F12(0xffc9, "F12", 0x7B, 0x6F),

    A('a', "a", 'A', 0x00),
    B('b', "b", 'B', 0x0B),
    C('c', "c", 'C', 0x08),
    D('d', "d", 'D', 0x02),
    E('e', "e", 'E', 0x0E),
    F('f', "f", 'F', 0x03),
    G('g', "g", 'G', 0x05),
    H('h', "h", 'H', 0x04),
    I('i', "i", 'I', 0x22),
    J('j', "j", 'J', 0x26),
    K('k', "k", 'K', 0x28),
    L('l', "l", 'L', 0x25),
    M('m', "m", 'M', 0x2E),
    N('n', "n", 'N', 0x2D),
    O('o', "o", 'O', 0x1F),
    P('p', "p", 'P', 0x23),
    Q('q', "q", 'Q', 0x0C),
    R('r', "r", 'R', 0x0F),
    S('s', "s", 'S', 0x01),
    T('t', "t", 'T', 0x11),
    U('u', "u", 'U', 0x20),
    V('v', "v", 'V', 0x09),
    W('w', "w", 'W', 0x0D),
    X('x', "x", 'X', 0x07),
    Y('y', "y", 'Y', 0x10),
    Z('z', "z", 'Z', 0x06),

    DIGIT_0('0', "0", '0', 0x1D),
    DIGIT_1('1', "1", '1', 0x12),
    DIGIT_2('2', "2", '2', 0x13),
    DIGIT_3('3', "3", '3', 0x14),
    DIGIT_4('4', "4", '4', 0x15),
    DIGIT_5('5', "5", '5', 0x17),
    DIGIT_6('6', "6", '6', 0x16),
    DIGIT_7('7', "7", '7', 0x1A),
    DIGIT_8('8', "8", '8', 0x1C),
    DIGIT_9('9', "9", '9', 0x19),

    COMMA(',', "comma", 0xBC, 0x2B),
    PERIOD('.', "period", 0xBE, 0x2F),
    SLASH('/', "slash", 0xBF, 0x2C),
    MINUS('-', "minus", 0xBD, 0x1B),
    EQUALS('=', "equal", 0xBB, 0x18);

    private final int keysym;
    private final String keysymName;
    private final int windowsVk;
    private final int macKeyCode;

    HotkeyKey(int keysym, String keysymName, int windowsVk, int macKeyCode) {
        this.keysym = keysym;
        this.keysymName = keysymName;
        this.windowsVk = windowsVk;
        this.macKeyCode = macKeyCode;
    }

    /// The X keysym, which `XKeysymToKeycode` turns into this keyboard's code.
    public int keysym() {
        return keysym;
    }

    /// The keysym's name, as an XDG trigger spells the key.
    public String keysymName() {
        return keysymName;
    }

    /// The Windows virtual-key code.
    public int windowsVk() {
        return windowsVk;
    }

    /// The macOS virtual key code, or -1 where a Mac keyboard has no such key.
    public int macKeyCode() {
        return macKeyCode;
    }
}
