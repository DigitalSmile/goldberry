package io.github.digitalsmile.goldberry.widgets.controls.selectlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;

/// `select-list` after the move — [ADR-0417].
///
/// These tests exist to hold down a claim rather than a behaviour, which is
/// unusual and is the point. ADR-0182 filed this move as costing "a change to
/// every stylesheet and every golden", and that was wrong: a CSS type is a
/// **string a widget returns**, so the only thing a package move can break is
/// Java. If the two tests below ever start failing together, somebody has made
/// the CSS type a function of the class — and the reason the move was avoided
/// for two hundred records will have become true after the fact.
class SelectListTest {

    private static final List<Widget> ROWS = List.of(new Option("nord-dark", "Nord Dark"));

    @Nested
    @DisplayName("the CSS type, which is what the move was said to cost")
    class CssType {

        /// The literal. Not `getSimpleName()`, not a name derived from the
        /// package, not anything the compiler knows about where this class lives.
        @Test
        @DisplayName("is the string `select-list`, written in one file")
        void isALiteral() {
            assertEquals("select-list", new SelectList(ROWS).cssType());
        }

        /// The assertion that says *why* the move was free. The class now lives in
        /// `…controls.selectlist` and the type is still `select-list` — two
        /// different strings, related by nothing a build could enforce.
        @Test
        @DisplayName("owes nothing to the package the class is in")
        void isNotThePackage() {
            var type = new SelectList(ROWS).cssType();
            var pkg = SelectList.class.getPackageName();

            assertEquals("io.github.digitalsmile.goldberry.widgets.controls.selectlist", pkg);
            assertNotEquals(pkg, type);
            assertEquals("select-list", type, "the package moved and this did not");
        }
    }

    @Nested
    @DisplayName("the cascade, which is the thing a stylesheet would have had to change")
    class Cascade {

        /// The real proof. A rule written `select-list` reaches the widget through
        /// the same selector it always did, from its new package — so `controls.css`
        /// needed no edit and neither did anybody's application sheet.
        @Test
        @DisplayName("a `select-list` rule still matches the moved class")
        void ruleStillMatches() {
            var sheets = List.of(
                    Controls.baseStylesheet(), Theme.NORD_DARK.load(), Stylesheet.parse(CascadeLayer.APPLICATION, """
                            select-list { gap: 7px }
                            """));

            var box = new WidgetRenderer(sheets, TestFont.get()).render(new ElementTree(new SelectList(ROWS)));

            assertEquals(Length.points(7), box.gap());
        }

        /// And the base stylesheet's own rules land, which is what says the panel
        /// still looks like a panel. `select-list` has a background in
        /// `controls.css`; a type that had stopped matching would resolve to
        /// nothing and paint transparent.
        @Test
        @DisplayName("and so do `controls.css`'s own")
        void baseStylesheetStillReachesIt() {
            var sheets = Controls.stylesheets(Theme.NORD_DARK);

            var box = new WidgetRenderer(sheets, TestFont.get()).render(new ElementTree(new SelectList(ROWS)));

            assertNotEquals(0, box.background(), "the panel's surface comes from `select-list`'s own rule");
        }
    }

    @Nested
    @DisplayName("what the record still is")
    class Shape {

        @Test
        @DisplayName("a list with no typeahead is a list with a null one")
        void oneArgumentConstructor() {
            assertEquals(new SelectList(ROWS, null), new SelectList(ROWS));
        }

        @Test
        @DisplayName("the rows are copied, so a caller's list cannot change the panel")
        void rowsAreCopied() {
            var mutable = new ArrayList<Widget>(ROWS);
            var list = new SelectList(mutable);

            mutable.clear();

            assertEquals(1, list.children().size());
        }
    }
}
