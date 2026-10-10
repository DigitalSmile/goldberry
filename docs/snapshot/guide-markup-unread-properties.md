# A property nothing reads

Two pieces. The first goes at the end of `### Strict by default` in
`book/src/guide/markup.md`, after the paragraph on lenient registries and
before `### \`bind=\` is a path and nothing else`. The second replaces the
`> [!WARNING]` box under the `row` attribute table in
`book/src/layout/row-and-column.md`.

## 1. `guide/markup.md`, end of `### Strict by default`

---

A property is held to the same rule. A widget's factory asks its node for the
properties it understands. Once the whole document is built, the inflater
compares what was asked for with what the document wrote, and a property
nothing asked for is refused. The message names the node, where it is, and
what that node does read:

```text
row at 2:3 ignores gap=8; it reads id, class, tooltip, context-menu, name (line 2, column 3)
```

`gap` is a stylesheet property, so that row wants `#toolbar { gap: 8px }`. A
parent may read its children's properties as well as its own: `line-chart`
reads the `x` and `y` of each `point`, and those count.

This holds for a lenient inflater too. An unbound name is something a preview
expects, and a property no widget reads is not. A document that cannot be fixed
at once is built with `inflater.unread(UnreadPolicy.WARN)`, which logs one line
per property, or `UnreadPolicy.IGNORE`, which does not look.

---

## 2. `layout/row-and-column.md`, the box under the `row` table

---

> [!NOTE]
> `gap` is a stylesheet property, and `row gap=8` is refused at inflation because nothing reads it. Write `#toolbar { gap: 8px }` instead.

---
