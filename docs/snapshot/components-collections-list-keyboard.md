# A list's rows take the arrows, and the focused row stays built

Held back until the release (ADR-0642). Two pieces; each says where it goes.

## 1. `book/src/components/collections.md`, `## `list``, the paragraph that begins "**Rows are virtualized by row height.**"

Append, after "A list that is not virtualized builds every row.":

The row the keyboard is on is built too, however far the list is scrolled from
it, so the next arrow key carries on from where the focus was.

## 2. The same section, the **Keyboard** table

Replace the `Up`, `Down` and `Home`, `End` rows with:

| `Up`, `Down` | move focus between rows, without selecting, and scroll the row into view. They wrap at the ends |
| `Home`, `End` | the first and last item, scrolled into view |
