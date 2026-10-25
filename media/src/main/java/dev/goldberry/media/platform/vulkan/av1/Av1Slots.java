package dev.goldberry.media.platform.vulkan.av1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// AV1's eight reference slots mapped onto the session's picture buffer: a
/// frame is decoded into a buffer slot no reference holds, and the reference
/// slots its `refresh_frame_flags` name hold it from then on.
final class Av1Slots {

    /// AV1's reference slots, `NUM_REF_FRAMES`.
    static final int REFERENCES = 8;

    /// The buffer slots: the references and the frame being decoded.
    static final int SLOTS = REFERENCES + 1;

    /// What a reference slot no frame is in maps to.
    static final int NONE = -1;

    private final int[] slotOf = new int[REFERENCES];

    Av1Slots() {
        clear();
    }

    /// The buffer slot reference slot `reference` maps to, or [#NONE].
    int slotOf(int reference) {
        return slotOf[reference];
    }

    /// A buffer slot no reference holds.
    int free() {
        for (var slot = 0; slot < SLOTS; slot++) {
            var taken = false;
            for (var held : slotOf) {
                taken |= held == slot;
            }
            if (!taken) {
                return slot;
            }
        }
        throw new IllegalStateException("every slot holds a reference");
    }

    /// `referenceNameSlotIndices`: the buffer slot of each of the seven
    /// references `refFrameIdx` names, `LAST_FRAME` first; [#NONE] each for an
    /// intra frame, whose list is empty.
    int[] named(List<Integer> refFrameIdx) {
        var named = new int[REFERENCES - 1];
        Arrays.fill(named, NONE);
        for (var i = 0; i < refFrameIdx.size() && i < named.length; i++) {
            named[i] = slotOf[refFrameIdx.get(i)];
        }
        return named;
    }

    /// The buffer slots of `named`, each once, in order.
    static List<Integer> distinct(int[] named) {
        var slots = new ArrayList<Integer>();
        for (var slot : named) {
            if (slot != NONE && !slots.contains(slot)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    /// The frame in buffer slot `slot` is now in every reference slot
    /// `refreshFrameFlags` names.
    void refresh(int refreshFrameFlags, int slot) {
        for (var i = 0; i < REFERENCES; i++) {
            if ((refreshFrameFlags >> i & 1) != 0) {
                slotOf[i] = slot;
            }
        }
    }

    /// A key frame shown again (§7.21): it is loaded into every slot.
    void loadEverywhere(int reference) {
        Arrays.fill(slotOf, slotOf[reference]);
    }

    /// No reference holds anything.
    void clear() {
        Arrays.fill(slotOf, NONE);
    }
}
