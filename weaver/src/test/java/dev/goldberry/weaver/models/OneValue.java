package dev.goldberry.weaver.models;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;

/// The smallest model there is: one bound field and one action that moves it.
///
/// The unit the benchmark prices a press in. Anything larger measures the model
/// as well as the binding.
@Model
public final class OneValue implements Clicker {

    @Bind("one.clicks") private int clicks;

    @Override
    @Action("one.click")
    public void click() {
        clicks++;
    }

    @Override
    public int clicks() {
        return clicks;
    }
}
