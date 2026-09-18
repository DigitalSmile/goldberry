package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;
import java.io.IOException;
import java.util.List;

/// A model whose methods carry everything javac writes *beside* the code.
///
/// `Signature`, `RuntimeVisibleAnnotations`, `MethodParameters` and `Exceptions`
/// are all attributes of the method rather than instructions in it, so a weaver
/// that rebuilt a method from its name, its descriptor, its flags and its code
/// array dropped every one of them and nothing in the class file said so. The
/// members here exist to carry one each.
@Model
public final class Attributed {

    @Bind("attr.count")
    private int count;

    /// Generic and declared `throws`, so javac writes a `Signature` and an
    /// `Exceptions`; compiled with `-parameters`, so it writes a
    /// `MethodParameters` naming `items`. Not one of the three is reachable from
    /// the code array.
    public <T extends Comparable<T>> List<T> sorted(List<T> items) throws IOException {
        if (items == null) {
            throw new IOException("nothing to sort");
        }
        return items.stream().sorted().toList();
    }

    /// The write that makes this a class the weaver rewrites at all, carrying a
    /// `RuntimeVisibleAnnotations` of its own.
    @Action("attr.bump")
    public void bump(String by) {
        count += Integer.parseInt(by);
    }

    public int count() {
        return count;
    }

    /// A plain class — no marker of any kind — that assigns to the model beside
    /// it.
    ///
    /// The other half of the same rebuild: a class that is not a model at all
    /// still has every method rewritten, because one of them writes to one
    /// (ADR-0134). Its attributes travelled exactly the same road.
    public static final class Helper {

        private final Attributed values;

        public Helper(Attributed values) {
            this.values = values;
        }

        public <T> T reset(T token) throws IOException {
            if (token == null) {
                throw new IOException("no token");
            }
            values.count = 0;
            return token;
        }
    }
}
