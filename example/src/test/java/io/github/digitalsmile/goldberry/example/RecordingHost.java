package io.github.digitalsmile.goldberry.example;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A host that remembers every widget put **over** its window — what a screen
/// that opens a dialog hands to [Host#fill].
///
/// A dialog is not in the tree that opened it: it goes to the host, which is
/// why a sheet's specimen dialog is invisible to a test that only walks the
/// element tree. This is [TourTestHost] with that one call recorded, made as a
/// proxy rather than a copy of thirty methods, so the answers to everything else
/// stay the tour host's and cannot drift from it.
final class RecordingHost {

    /// What was filled over the window, oldest first.
    final List<Widget> filled = new ArrayList<>();

    /// The host to build a tree with.
    final Host host;

    RecordingHost() {
        var delegate = new TourTestHost(List.of());
        this.host = (Host) Proxy.newProxyInstance(
                Host.class.getClassLoader(), new Class<?>[] {Host.class}, (proxy, method, args) -> {
                    if (method.getName().equals("fill") && args != null && args.length == 1) {
                        filled.add((Widget) args[0]);
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /// The last widget filled over the window, or a failure saying nothing was.
    Widget last() {
        if (filled.isEmpty()) {
            throw new AssertionError("nothing was put over the window");
        }
        return filled.getLast();
    }
}
