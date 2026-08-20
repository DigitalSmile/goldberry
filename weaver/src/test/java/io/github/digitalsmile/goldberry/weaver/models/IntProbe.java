package io.github.digitalsmile.goldberry.weaver.models;

/// Reads one `int` field of one model class, unboxed.
///
/// What a runtime-generated reader would implement. Public, and its method with
/// it: a hidden class defined as a nestmate of the model is in the model's
/// runtime package but is a different class, and an interface it could not see
/// would not verify.
public interface IntProbe {

    int read(Object model);
}
