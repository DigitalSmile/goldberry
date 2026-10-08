<!-- Destination: book/src/guide/input.md, `## Accelerators`, after the paragraph
     that ends "…so an unmount cannot take a binding somebody else made." -->

A held key repeats, and an accelerator fires on each repeat unless its
binding says otherwise. A toggle says so, or holding `Escape` opens and closes
a menu at the platform's repeat rate:

```java
host.shortcut(Shortcut.of(Key.ESCAPE), menu::toggle, Repeat.IGNORE);   // once per press
host.shortcut(Mod.CTRL.and(Key.Z), history::undo);                     // repeats while held
```

`Repeat.FIRE` is what every other form binds. A repeat that an `IGNORE`
binding declines is still its key: it does not reach focus navigation or the
focused widget. In an offscreen session, `session.hold(Key.ESCAPE, 3)`
presses the key, repeats it three times and releases it.
