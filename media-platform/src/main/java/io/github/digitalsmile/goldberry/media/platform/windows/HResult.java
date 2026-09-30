package io.github.digitalsmile.goldberry.media.platform.windows;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/// Windows's `HRESULT` codes, and the exception a failed call raises.
///
/// A code is negative when it is a failure (the severity bit), and shown the way
/// the SDK headers name it (`winerror.h`, `mferror.h`) with its hexadecimal
/// value, so a log line can be searched for.
final class HResult {

    /// `S_OK`.
    static final int S_OK = 0;
    /// `S_FALSE`: success, but not all of it. `CoInitializeEx` answers it on a
    /// thread already initialised.
    static final int S_FALSE = 1;

    /// `E_NOTIMPL`.
    static final int E_NOTIMPL = 0x8000_4001;
    /// `E_NOINTERFACE`: `QueryInterface` for an interface the object lacks.
    static final int E_NOINTERFACE = 0x8000_4002;
    /// `E_FAIL`.
    static final int E_FAIL = 0x8000_4005;
    /// `E_INVALIDARG`.
    static final int E_INVALIDARG = 0x8007_0057;
    /// `RPC_E_CHANGED_MODE`: `CoInitializeEx` on a thread already initialised as
    /// a single-threaded apartment. Media Foundation works from one.
    static final int RPC_E_CHANGED_MODE = 0x8001_0106;

    /// `MF_E_INVALIDMEDIATYPE`: a transform that does not take the type offered.
    static final int MF_E_INVALIDMEDIATYPE = 0xC00D_36B4;
    /// `MF_E_NOTACCEPTING`: a transform holding output that has to be taken
    /// first.
    static final int MF_E_NOTACCEPTING = 0xC00D_36B5;
    /// `MF_E_NO_MORE_TYPES`: the end of a transform's list of types.
    static final int MF_E_NO_MORE_TYPES = 0xC00D_36B9;
    /// `MF_E_ATTRIBUTENOTFOUND`: an attribute the object does not have.
    static final int MF_E_ATTRIBUTENOTFOUND = 0xC00D_36E6;
    /// `MF_E_TRANSFORM_TYPE_NOT_SET`: a transform asked to work before its types
    /// are set.
    static final int MF_E_TRANSFORM_TYPE_NOT_SET = 0xC00D_6D60;
    /// `MF_E_TRANSFORM_STREAM_CHANGE`: the output type changed, as when a
    /// decoder reads a stream's size from its first parameter sets.
    static final int MF_E_TRANSFORM_STREAM_CHANGE = 0xC00D_6D61;
    /// `MF_E_TRANSFORM_NEED_MORE_INPUT`: no output until more input.
    static final int MF_E_TRANSFORM_NEED_MORE_INPUT = 0xC00D_6D72;

    private HResult() {}

    /// Whether `hr` is a failure: its severity bit is set.
    static boolean failed(int hr) {
        return hr < 0;
    }

    /// `hr` as the SDK headers name it, with its value in hexadecimal; the value
    /// alone for a code not named here.
    static String describe(int hr) {
        var hex = "0x" + String.format(Locale.ROOT, "%08X", hr);
        var name = name(hr);
        return name != null ? name + " (" + hex + ")" : hex;
    }

    /// `hr`'s name in the SDK headers, or null for one not named here.
    static @Nullable String name(int hr) {
        return switch (hr) {
            case S_OK -> "S_OK";
            case S_FALSE -> "S_FALSE";
            case E_NOTIMPL -> "E_NOTIMPL";
            case E_NOINTERFACE -> "E_NOINTERFACE";
            case 0x8000_4003 -> "E_POINTER";
            case E_FAIL -> "E_FAIL";
            case 0x8000_FFFF -> "E_UNEXPECTED";
            case 0x8007_000E -> "E_OUTOFMEMORY";
            case E_INVALIDARG -> "E_INVALIDARG";
            case 0x8004_0154 -> "REGDB_E_CLASSNOTREG";
            case 0x8004_01F0 -> "CO_E_NOTINITIALIZED";
            case RPC_E_CHANGED_MODE -> "RPC_E_CHANGED_MODE";
            case 0xC00D_36B3 -> "MF_E_INVALIDSTREAMNUMBER";
            case MF_E_INVALIDMEDIATYPE -> "MF_E_INVALIDMEDIATYPE";
            case MF_E_NOTACCEPTING -> "MF_E_NOTACCEPTING";
            case MF_E_NO_MORE_TYPES -> "MF_E_NO_MORE_TYPES";
            case 0xC00D_36BA -> "MF_E_UNSUPPORTED_SERVICE";
            case 0xC00D_36B2 -> "MF_E_INVALIDREQUEST";
            case MF_E_ATTRIBUTENOTFOUND -> "MF_E_ATTRIBUTENOTFOUND";
            case MF_E_TRANSFORM_TYPE_NOT_SET -> "MF_E_TRANSFORM_TYPE_NOT_SET";
            case MF_E_TRANSFORM_STREAM_CHANGE -> "MF_E_TRANSFORM_STREAM_CHANGE";
            case MF_E_TRANSFORM_NEED_MORE_INPUT -> "MF_E_TRANSFORM_NEED_MORE_INPUT";
            default -> null;
        };
    }

    /// Throws [Failure] when `hr` is a failure. `S_FALSE` and other successes
    /// pass.
    static void check(String function, int hr) {
        if (failed(hr)) {
            throw new Failure(function, hr);
        }
    }

    /// A call that answered a failure code.
    static final class Failure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final int hresult;

        Failure(String function, int hresult) {
            super(function + " failed: " + describe(hresult));
            this.hresult = hresult;
        }

        /// The `HRESULT` the call answered.
        int hresult() {
            return hresult;
        }
    }
}
