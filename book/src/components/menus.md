# Menus and the tray

<p class="gb-lede">A menu bar in the window, menus that open as platform windows of their own, context menus named on any widget, and a tray icon the desktop draws from the same menu value.</p>

A menu is a widget: a `menu` holds `item`s and `separator`s, and an `item`
holding `item`s is a submenu. Opening one is not a widget's job. A `menubar`
opens its own headings, and anything else goes through `Menus.open(host, …)`,
because opening needs a `Host` and a widget must not have one.

## `menubar`

A horizontal bar of headings, each an `item` whose children are its menu.

<div class="gb-shot"><img class="gb-light" src="../images/menubar-light.webp" width="640" alt="A menu bar with File, View and Help"><img class="gb-dark" src="../images/menubar-dark.webp" width="640" alt="A menu bar with File, View and Help"><p>The bar at rest. A menu opens on press.</p></div>

<div class="gb-tabs">

```kdl
menubar id="app-menu" {
  item "File" {
    item press="app.new" accelerator="Ctrl+N" "New"
    item press="app.open" accelerator="Ctrl+O" "Open…"
    separator
    item press="app.quit" accelerator="Ctrl+Q" "Quit"
  }
  item "View" {
    item press="app.toggle-hud" accelerator="Ctrl+F" checked=#false "Frame rate"
    item "Theme" {
      item press="app.light" "Light"
      item press="app.dark" "Dark"
    }
  }
  item "Help" {
    item "Nothing here yet" disabled=#true
  }
}
```

```java
new MenuBar(
        new Item("File").submenu(
                new Item("New", actions::create).accelerator("Ctrl+N"),
                new Item("Open…", actions::open).accelerator("Ctrl+O"),
                new Separator(),
                new Item("Quit", window::quit).accelerator("Ctrl+Q")
        ),
        new Item("View").submenu(
                new Item("Frame rate", window::toggleHud).accelerator("Ctrl+F").checked(hudShown),
                new Item("Theme").submenu(
                        new Item("Light", () -> actions.pick("light")),
                        new Item("Dark", () -> actions.pick("dark"))
                )
        ),
        new Item("Help").submenu(
                new Item("Nothing here yet").disabled(true)
        )
).id("app-menu");
```

</div>

The bar owns its menus. It opens a heading's menu as a popup against the
heading, swaps to the neighbour when the pointer runs along the bar with a menu
down, and binds every accelerator its rows name while it is mounted. A menu bar
is a widget and not a property of the window, so a screen may have a second one.

**Attributes**

| Attribute | Type | Default | What it does |
|---|---|---|---|
| `id`, `class` | string | | The usual. |

The children are `item`s. One that has children is a heading. A child that is
not an `item` is placed in the row unchanged.

**Styling**

The CSS type is `menubar`. Each heading is a `menu-title`, which matches
`:hover`, `:focus-visible`, `:active` and `:disabled`, and carries the class
`open` while its menu is down. The menus themselves are `menu`.

**Keyboard**

| Key | Does |
|---|---|
| `F10`, or a tap on `Alt` | opens the first heading, or closes the bar when a menu is open |
| `Left`, `Right` | walk the headings, and swap menus while one is open |
| `Enter`, `Space`, `Down` | open the focused heading |
| `Escape` | closes one menu, then the bar |

A bare `Alt` is a gesture rather than a shortcut, which is why it has a
registration of its own. There are no mnemonics.

## `menu`

A column of rows: the value a context menu, a tray and a heading all hold.

<div class="gb-shot"><img class="gb-light" src="../images/menu-light.webp" width="640" alt="A menu card with Rename, Duplicate, a separator, and Delete"><img class="gb-dark" src="../images/menu-dark.webp" width="640" alt="A menu card with Rename, Duplicate, a separator, and Delete"><p>Three items and a separator.</p></div>

<div class="gb-tabs">

```kdl
menu id="row-menu" {
  item press="app.rename" "Rename"
  item press="app.duplicate" "Duplicate"
  separator
  item press="app.delete" "Delete"
}
```

```java
var rowMenu = new Menu(
        new Item("Rename", actions::rename),
        new Item("Duplicate", actions::duplicate),
        new Separator(),
        new Item("Delete", actions::delete)
);

Menus.open(host, "more-button", rowMenu).ifPresent(open -> this.menu = open);
```

</div>

A document declares a menu. `Menus.open(host, anchorId, menu)` measures it,
places it under the node with that id, flips it above when it would run off the
bottom of the screen, and opens it in a window of its own, so it may hang past
the window's edge. The answer is empty when nothing with that id has been
painted or the platform has no popup windows. Choosing a command closes the
whole stack. A menu too tall for the screen scrolls.

**Attributes**

| Attribute | Type | Default | What it does |
|---|---|---|---|
| `id`, `class` | string | | The usual. |

The children are `item`s and `separator`s.

**Styling**

The CSS type is `menu`. A row that has an open submenu carries the class
`open`. When any row has an icon or is checkable, every row reserves an
`item-lead` column so the labels line up. A row that leads to a submenu ends in
an `item-chevron`. The row height is `--gb-menu-item-height`.

**Keyboard**

| Key | Does |
|---|---|
| `Up`, `Down` | move between rows |
| `Enter`, `Space` | run the row, or open its submenu at once |
| `Right` | opens the submenu, or on a plain row moves to the next heading of a bar |
| `Left` | closes a submenu, or moves to the previous heading of a bar |
| `Escape` | closes this menu and no more |

The pointer opens a submenu after 150 ms of resting on its row, so a pointer
crossing three rows on the way somewhere does not drop one out.

### `item`

One row: a label, an optional icon, an accelerator to show, a tick, and either
a command or a submenu.

<div class="gb-shot"><img class="gb-light" src="../images/item-light.webp" width="622" alt="One menu item: a palette icon, Switch the light, a tick, and the accelerator Ctrl+T"><img class="gb-dark" src="../images/item-dark.webp" width="622" alt="One menu item: a palette icon, Switch the light, a tick, and the accelerator Ctrl+T"><p>An icon, a label, a check and an accelerator.</p></div>

<div class="gb-tabs">

```kdl
item icon="palette" press="app.toggle-theme" accelerator="Ctrl+T" checked=#true "Switch the light"
```

```java
new Item("Switch the light", actions::toggleTheme)
        .icon(paletteIcon)
        .accelerator("Ctrl+T")
        .checked(true);
```

</div>

**Attributes**

| Attribute | Type | Default | What it does |
|---|---|---|---|
| argument | string | `""` | The label. A row with neither a label nor an icon is refused. |
| `press` | action name | none | The command. |
| `icon` | icon name | none | An icon in the lead column. |
| `accelerator` | string | none | Text shown right-aligned, and bound while a `menubar` holds the row. |
| `checked` | boolean | absent | `#true` shows a tick, `#false` is a checkable row that is off, and no attribute is a row that is not checkable. |
| `disabled` | boolean | `#false` | Greys the row and refuses it. |
| `id`, `class` | string | | The usual. |

A nested `item` is a submenu. That is the only thing an item can contain, and
there is no `submenu` node. A row cannot be both a command and a heading.

In Java, `new Item(label, onPress)` is a command, `new Item(label)` has
nothing behind it, and `submenu(Widget...)`, `icon`, `accelerator`,
`checked(boolean)`, `checkable()` and `disabled(boolean)` set the rest.

**Styling**

The CSS type is `item`. It matches `:hover`, `:focus-visible`, `:active` and
`:disabled`, and carries the class `open` while its submenu is. Its parts are
`item-lead` and `item-chevron`. The accelerator text has no part of its own.

### `separator`

A rule between groups of rows.

<div class="gb-shot"><img class="gb-light" src="../images/separator-light.webp" width="640" alt="A thin horizontal rule"><img class="gb-dark" src="../images/separator-dark.webp" width="640" alt="A thin horizontal rule"><p>A rule between items.</p></div>

<div class="gb-tabs">

```kdl
separator
```

```java
new Separator();
```

</div>

It takes only `id` and `class`. The CSS type is `separator`, a 1px line in
`--gb-border`.

## Accelerators

An accelerator is written as text, `Ctrl+S`, `Shift+Ctrl+Z`, `Primary+Q`. The
row shows the text as written. A `menubar` parses every accelerator under it
with `Shortcut.of` and binds it on the window while the bar is mounted, so the
key works with every menu shut. When the bar unmounts it gives back the keys
it took and no others.

`Primary` names the desktop's own modifier, `Cmd` on macOS and `Ctrl`
elsewhere, so one document fits both. `Ctrl`, `Control`, `Alt`, `Option`,
`Meta`, `Cmd`, `Super` and `Win` are the other spellings.

> [!WARNING]
> Only a `menubar` binds. A menu that is only ever opened, a context menu or a
> `Menus.open` call, shows its accelerators and registers none of them. Bind
> those with `host.shortcut(…)` yourself. A row that is disabled, has a
> submenu, or has no `press` is skipped.

## The macOS menu bar

On macOS a `menubar` is the application's menu bar, at the top of the screen,
and draws nothing in the window. Its headings follow the application menu,
which AppKit provides: About, Hide, Hide Others, Show All and Quit, under the
application's title. The same document is a bar in the window on Linux and
Windows.

The platform fires the accelerators itself there, so the bar binds none in the
window, and F10 and a bare `Alt` do nothing. In the menu bar, and only there,
`Ctrl` with no `Cmd` beside it is read as `Cmd`. A menu written once as
`Ctrl+O` shows ⌘O on a Mac. `Primary+O` needs no reading at all.

| In the `menubar` | In the macOS menu bar |
|---|---|
| `item` with `item`s | A menu, or a submenu inside one |
| `item` with `press` | A command, with its accelerator as a key equivalent |
| `checked=` | A tick |
| `disabled=#true` | A greyed row |
| `separator` | A dividing line |
| Anything else | Left out |

An application without a `menubar` can set the bar from Java with
`host.applicationMenu(List<AppMenuItem>)`, which answers whether the platform
shows it. An empty list puts back what was there. There is no Window menu of
AppKit's own. An application that wants Minimize and Zoom writes one.

## Context menus

Any widget names its context menu, and the application says what the name
means.

<div class="gb-tabs">

```kdl
panel context-menu="content" {
  text "Right-click anywhere in here."
}
```

```java
@Override public void start(Host host) {
    Menus.contextMenus(host, Map.of("content", contentMenu()));
}
```

</div>

A right-click walks up from the widget under the pointer to the first
ancestor with a `context-menu` and opens that menu at the pointer. The `Menu`
key, and `Shift+F10`, open the same menu at the focused widget instead. A name
the application never registered is logged and ignored. A right-click on a
list row selects it first.

In Java the attribute is `.contextMenu("content")` on any widget, or
`.itemMenu(item -> "row-menu")` on a `list`.

## The tray icon

A tray icon is a menu somebody else draws. The application hands the desktop a
tooltip, a picture and an ordinary `Menu`, and the desktop's shell themes it,
spaces it and clicks it. There is no markup node for it.

```java
private Optional<BackendTray> tray = Optional.empty();

@Override public void start(Host host) {
    var menu = new Menu(
            new Item("Switch the light", actions::toggleTheme),
            new Separator(),
            new Item("Screens").submenu(
                    new Item("Basic", () -> actions.pickScreen("basic")),
                    new Item("Panels", () -> actions.pickScreen("panels"))
            ),
            new Separator(),
            new Item("Quit", () -> host.window().close())
    );
    tray = Trays.show(host, TrayIcon.of("Goldberry — showcase", menu).icons(onLightShell, onDarkShell));
}

@Override public void stop() {
    tray.ifPresent(BackendTray::close);
}
```

| Call | What it does |
|---|---|
| `TrayIcon.of(tooltip, menu)` | A tray with the platform's default picture. |
| `.icon(PixelBuffer)` | One picture, in physical pixels, 32×32 or 64×64. |
| `.icons(forLightShell, forDarkShell)` | Two pictures, named for the panel each sits on. The tray follows the desktop's theme while it is up, and shows the light-shell one where the desktop says nothing. |
| `.tooltip(String)` | The hover text. |
| `Trays.show(host, tray)` | Puts it up. Empty when this session has no notification area. |
| `BackendTray.close()` | Takes it down. The tray outlives the process if you forget, so close it in `stop()`. |

Rows map as a desktop expects: a checkable `item` is a checkbox row, a nested
`item` is a submenu, a disabled one is disabled. Icons and accelerators on rows
are dropped with a warning, because the shell draws the rows and takes neither.
The menu cannot be changed in place. Close the tray and show a new one.

> [!NOTE]
> The tray is SDL3's. Nothing in it is painted by Goldberry: the menu is a GTK
> menu under AppIndicator on Linux, an `NSMenu` on macOS and a Win32 popup on
> Windows.
