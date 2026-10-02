package dev.goldberry.natives.yoga.style;

/// What happens to content that does not fit — `YGOverflow`.
///
/// Yoga only decides *sizing* from this; nothing here clips. [#HIDDEN] and
/// [#SCROLL] tell Yoga a child may exceed its parent without the parent growing,
/// and the paint layer is what actually clips. A node whose overflow is
/// [#VISIBLE] is allowed to make its parent's content box larger.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum Overflow implements YogaEnum {
    VISIBLE(0, "YGOverflowVisible"),

    HIDDEN(1, "YGOverflowHidden"),

    /// Sizes as [#HIDDEN] does. The difference is entirely above Yoga: the
    /// `scroll` widget reads this to decide whether to offer scrollbars.
    SCROLL(2, "YGOverflowScroll");

    private final int nativeValue;
    private final String nativeName;

    Overflow(int nativeValue, String nativeName) {
        this.nativeValue = nativeValue;
        this.nativeName = nativeName;
    }

    @Override
    public int nativeValue() {
        return nativeValue;
    }

    @Override
    public String nativeName() {
        return nativeName;
    }
}
