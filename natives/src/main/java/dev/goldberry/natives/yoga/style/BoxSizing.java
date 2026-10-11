package dev.goldberry.natives.yoga.style;

/// What a node's width and height measure — `YGBoxSizing`, CSS's
/// `box-sizing`.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum BoxSizing implements YogaEnum {

    /// The size includes the padding and the border: a 100-wide box is 100
    /// wide, and its content is narrower.
    BORDER_BOX(0, "YGBoxSizingBorderBox"),

    /// The size is the content's: the padding and the border are added to it.
    CONTENT_BOX(1, "YGBoxSizingContentBox");

    private final int nativeValue;
    private final String nativeName;

    BoxSizing(int nativeValue, String nativeName) {
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
