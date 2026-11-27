package com.liucodex.doggiesnewvehicle.armored;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class BlizzardTvCodecTest {
    @Test void cameraStateAndControlsPreserveExactSessionAndEntityIdentities() {
        var state = new BlizzardTvNetwork.State(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100000, true);
        var stateBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BlizzardTvNetwork.encodeState(state, stateBuffer);
            assertEquals(state, BlizzardTvNetwork.decodeState(stateBuffer));
            assertEquals(0, stateBuffer.readableBytes());
        } finally { stateBuffer.release(); }
        for (boolean exit : new boolean[] {false, true}) {
            var control = new BlizzardTvNetwork.Control(state.missile(), state.session(), -179.5F, -84, exit);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                BlizzardTvNetwork.encodeControl(control, buffer);
                assertEquals(control, BlizzardTvNetwork.decodeControl(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    @Test void truncatedInputCannotDecodeACompleteControl() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            assertThrows(IndexOutOfBoundsException.class, () -> BlizzardTvNetwork.decodeControl(buffer));
        } finally { buffer.release(); }
    }
}
