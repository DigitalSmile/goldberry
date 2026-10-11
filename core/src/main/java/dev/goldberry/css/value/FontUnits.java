package dev.goldberry.css.value;

/// What `ch` and `ex` are worth in the face an element is drawn in.
///
/// ```java
/// FontUnits inter = fonts.face("Inter", 400);
/// double ch = inter.ch() * 13; // the advance of "0" at 13px
/// ```
///
/// Both are per em, so one answer serves every size of a face. `ch` is the
/// advance of the digit zero and `ex` the face's x-height. Where nobody can say,
/// [#FALLBACK] answers half an em for each, which is what CSS says to assume.
///
/// The renderer hands one in through [CssLength.Context], over the book of
/// fonts it draws with, and the cascade moves it from face to face as
/// `font-family` and `font-weight` change down the tree.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#box-and-layout).
public interface FontUnits {

    /// Half an em for both, for any face.
    FontUnits FALLBACK = Fallback.INSTANCE;

    /// The advance of `0`, per em.
    double ch();

    /// The x-height, per em.
    double ex();

    /// The same source of measures, for another face. Cheap: a face is measured
    /// the first time [#ch] or [#ex] asks, not here.
    FontUnits face(String family, int weight);

    /// [#FALLBACK]'s one instance.
    enum Fallback implements FontUnits {
        INSTANCE;

        @Override
        public double ch() {
            return 0.5;
        }

        @Override
        public double ex() {
            return 0.5;
        }

        @Override
        public FontUnits face(String family, int weight) {
            return this;
        }
    }
}
