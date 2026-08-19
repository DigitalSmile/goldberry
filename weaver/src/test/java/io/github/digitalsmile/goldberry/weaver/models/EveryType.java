package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;

/// One field of every shape the weaver has to emit a comparison for.
///
/// The setter's "did it change?" test is per-type — `if_icmpne` for the small
/// integrals, `lcmp` for a long, `Float.compare`/`Double.compare` so that `NaN`
/// and `-0.0` answer the way a boxed comparison would — and each of those is a
/// different few bytes that either verify or do not.
@Model
public final class EveryType {

    @Bind("t.z") private boolean z;
    @Bind("t.b") private byte b;
    @Bind("t.c") private char c;
    @Bind("t.s") private short s;
    @Bind("t.i") private int i;
    @Bind("t.j") private long j;
    @Bind("t.f") private float f;
    @Bind("t.d") private double d;
    @Bind("t.o") private String o;

    @Action("t.set")
    private void set() {
        z = true;
        b = 1;
        c = 'x';
        s = 2;
        i = 3;
        j = 4L;
        f = 5.5f;
        d = 6.5;
        o = "seven";
    }

    @Action("t.nan")
    private void nan() {
        f = Float.NaN;
        d = Double.NaN;
    }

    @Action("t.negativeZero")
    private void negativeZero() {
        f = -0.0f;
        d = -0.0;
    }
}
