package dev.goldberry.natives.desktop.windows;

import java.lang.foreign.MemorySegment;

/// Movable global memory, the `HGLOBAL` a data object hands its bytes over in.
///
/// An interface so the objects that hand it out can be called through their
/// vtables anywhere: [Win32GlobalMemory] is the real one, and a test gives one
/// over the Java heap's native arenas.
public interface GlobalMemory {

    /// A block holding `bytes`, or [MemorySegment#NULL] when there is no
    /// memory for it. Whoever it is handed to frees it.
    MemorySegment allocate(byte[] bytes);

    /// What `handle` holds, all of it: the system may round a block up, so this
    /// can be longer than what was put in.
    byte[] read(MemorySegment handle);

    /// Frees a block from [#allocate] that was never handed over.
    void free(MemorySegment handle);

    /// `ReleaseStgMedium`: gives back what an `STGMEDIUM` holds, as its
    /// `pUnkForRelease` says to.
    void releaseMedium(MemorySegment medium);
}
