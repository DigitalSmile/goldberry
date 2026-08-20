package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;

/// Eight bound fields, one action, and the action moves exactly one of them.
///
/// The shape that separates the two schemes. A woven write costs what one field
/// costs, however many the model has — the setter it was rewritten into knows
/// which field it is. A sweep costs what *all* of them cost, because it has no
/// way to know which one moved without looking at each. This is where that shows
/// up as a number (ADR-0155).
@Model
public final class EightValues implements Clicker {

    @Bind("eight.a") private int a;
    @Bind("eight.b") private int b;
    @Bind("eight.c") private int c;
    @Bind("eight.d") private int d;
    @Bind("eight.e") private int e;
    @Bind("eight.f") private int f;
    @Bind("eight.g") private int g;
    @Bind("eight.h") private int h;

    @Override
    @Action("eight.click")
    public void click() {
        a++;
    }

    @Override
    public int clicks() {
        return a + b + c + d + e + f + g + h;
    }
}
