package com.liucodex.doggiesnewvehicle.neoforge;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class NeoTransportTest {
    private record Intent(UUID transaction, long expectedRevision, String command) {}
    private static DnvNeoChannel channel() {
        return DnvNeoChannel.create(ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "test/" + UUID.randomUUID()),
                () -> "7", "7"::equals, "7"::equals);
    }
    private static void register(DnvNeoChannel channel, int id, DnvNeoChannel.Direction direction) {
        channel.registerMessage(id, Intent.class,
                (intent, buffer) -> { buffer.writeUUID(intent.transaction()); buffer.writeLong(intent.expectedRevision()); buffer.writeUtf(intent.command(), 32); },
                buffer -> new Intent(buffer.readUUID(), buffer.readLong(), buffer.readUtf(32)),
                (intent, context) -> {}, Optional.of(direction));
    }
    @Test void channelRejectsSelfIncompatibleProtocolBeforeRegistration() {
        var id = ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "test/invalid");
        assertThrows(IllegalArgumentException.class, () -> DnvNeoChannel.create(id, () -> "7", version -> false, version -> true));
        assertThrows(IllegalArgumentException.class, () -> DnvNeoChannel.create(id, () -> "7", version -> true, version -> false));
    }
    @Test void duplicateChannelAndMessageIdsFailClosed() {
        var id = ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle", "test/duplicate");
        DnvNeoChannel.create(id, () -> "7", version -> true, version -> true);
        assertThrows(IllegalStateException.class, () -> DnvNeoChannel.create(id, () -> "7", version -> true, version -> true));
        var channel = channel(); register(channel, 0, DnvNeoChannel.Direction.PLAY_TO_SERVER);
        assertThrows(IllegalStateException.class, () -> register(channel, 1, DnvNeoChannel.Direction.PLAY_TO_SERVER));
        assertThrows(IllegalStateException.class, () -> channel.registerMessage(0, String.class,
                (message, buffer) -> buffer.writeUtf(message), buffer -> buffer.readUtf(), (message, context) -> {},
                Optional.of(DnvNeoChannel.Direction.PLAY_TO_SERVER)));
    }
    @Test void wrongDirectionNeverTouchesTheNetwork() {
        var channel = channel(); register(channel, 0, DnvNeoChannel.Direction.PLAY_TO_CLIENT);
        assertThrows(IllegalArgumentException.class, () -> channel.sendToServer(new Intent(UUID.randomUUID(), 9, "COMMIT")));
        var serverChannel = channel(); register(serverChannel, 0, DnvNeoChannel.Direction.PLAY_TO_SERVER);
        assertThrows(IllegalArgumentException.class, () -> serverChannel.send(packet -> fail("Wrong direction sent"), new Intent(UUID.randomUUID(), 9, "COMMIT")));
        assertThrows(IllegalArgumentException.class, () -> serverChannel.sendToServer("unknown"));
    }
    @SuppressWarnings("unchecked")
    @Test void registryPayloadCodecRetainsIdentityRevisionAndBounds() throws Exception {
        var channel = channel(); register(channel, 3, DnvNeoChannel.Direction.PLAY_TO_SERVER);
        var field = DnvNeoChannel.class.getDeclaredField("messages"); field.setAccessible(true);
        Object entry = ((Map<?, ?>) field.get(channel)).get(Intent.class);
        var codecAccessor = entry.getClass().getDeclaredMethod("codec"); codecAccessor.setAccessible(true);
        var typeAccessor = entry.getClass().getDeclaredMethod("type"); typeAccessor.setAccessible(true);
        var codec = (StreamCodec<RegistryFriendlyByteBuf, DnvNeoChannel.Envelope>) codecAccessor.invoke(entry);
        var type = (CustomPacketPayload.Type<DnvNeoChannel.Envelope>) typeAccessor.invoke(entry);
        var expected = new Intent(UUID.randomUUID(), 78, "COMMIT");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, new DnvNeoChannel.Envelope(type, expected));
            var decoded = codec.decode(buffer);
            assertEquals(type, decoded.type()); assertEquals(expected, decoded.message()); assertFalse(buffer.isReadable());
            assertTrue(type.id().getPath().endsWith("/3"));
            assertThrows(IndexOutOfBoundsException.class, () -> codec.decode(buffer));
            buffer.clear();
            assertThrows(RuntimeException.class, () -> codec.encode(buffer,
                    new DnvNeoChannel.Envelope(type, new Intent(UUID.randomUUID(), 1, "x".repeat(33)))));
        } finally { buffer.release(); }
    }
}
