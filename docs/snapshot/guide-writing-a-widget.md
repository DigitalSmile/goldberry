# Guide addition held back until the release

For `book/src/guide/writing-a-widget.md`, section `## The three shapes`. The
paragraph below goes after the one that ends "`setState` throws rather than
leaking quietly.", before the `BuildContext` paragraph. No heading changes.

---

A state reaches the same `BuildContext` its builds are handed through
`context()`, from `initState` until `dispose`. The host is known when the
state is mounted, so something timed that starts when the widget appears is
set in `initState` and cancelled in `dispose`, and the build stays a
description:

```java
@Override protected void initState() {
    opening = context().host().map(host -> host.after(Duration.ofMillis(400), this::deal));
}

@Override protected void dispose() {
    opening.ifPresent(EventLoop.Timer::cancel);
}
```
