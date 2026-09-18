package io.github.digitalsmile.goldberry.weaver.models;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.Bind;
import io.github.digitalsmile.goldberry.bind.Model;

/// A model with an ordinary method that merges two of the author's own types.
///
/// `Base picked = flag ? new A() : new B()` leaves `A` and `B` on the stack down
/// two arms of a branch, so the verifier has to be told what they are where the
/// arms meet — and javac writes a stack map frame naming `Base`, which it knows
/// from the source. Regenerating that frame means computing the common
/// supertype of `A` and `B`, which means resolving both, which means the class
/// hierarchy resolver has to be able to see them.
///
/// Nothing in `pick` writes to a `@Bind` field, so the weaver has no business
/// rebuilding it and no business asking. The action below is the method it does
/// have business with.
@Model
public final class Merged {

    @Bind("merge.count")
    private int count;

    /// The two arms of the join, and the type they meet at.
    public abstract static class Base {

        public abstract String name();
    }

    public static final class A extends Base {

        @Override
        public String name() {
            return "a";
        }
    }

    public static final class B extends Base {

        @Override
        public String name() {
            return "b";
        }
    }

    /// The control-flow join. No `putfield` anywhere in it.
    public String pick(boolean flag) {
        Base picked = flag ? new A() : new B();
        return picked.name();
    }

    /// The same join, in a method that *does* write — so the two cases sit side
    /// by side and the difference between them is the write and nothing else.
    @Action("merge.bump")
    public void bump(boolean flag) {
        Base picked = flag ? new A() : new B();
        count += picked.name().length();
    }

    public int count() {
        return count;
    }
}
