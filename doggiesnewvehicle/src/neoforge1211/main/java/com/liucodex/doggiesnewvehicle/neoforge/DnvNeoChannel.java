package com.liucodex.doggiesnewvehicle.neoforge;

import java.util.*;
import java.util.function.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.PacketDistributor;

/** Platform transport only; the owning modules retain codecs, authorization and state. */
public final class DnvNeoChannel {
    public enum Direction { PLAY_TO_SERVER, PLAY_TO_CLIENT }
    public record Context(IPayloadContext payload) {
        public ServerPlayer getSender() {
            return payload.player() instanceof ServerPlayer player ? player : null;
        }
        public void enqueueWork(Runnable work) { payload.enqueueWork(work); }
    }
    public record Envelope(CustomPacketPayload.Type<Envelope> type, Object message)
            implements CustomPacketPayload {}
    private record Entry(CustomPacketPayload.Type<Envelope> type, Direction direction,
            StreamCodec<RegistryFriendlyByteBuf, Envelope> codec) {}
    private static final List<DnvNeoChannel> CHANNELS = new ArrayList<>();
    private final ResourceLocation id;
    private final String version;
    private final Map<Class<?>, Entry> messages = new LinkedHashMap<>();
    private final Set<Integer> ids = new HashSet<>();
    private final List<Consumer<RegisterPayloadHandlersEvent>> registrations = new ArrayList<>();
    private boolean registered;

    private DnvNeoChannel(ResourceLocation id, String version) {
        this.id = id;
        this.version = version;
    }
    public static synchronized DnvNeoChannel create(ResourceLocation id, Supplier<String> version,
            Predicate<String> clientVersion, Predicate<String> serverVersion) {
        String protocol = version.get();
        if (!clientVersion.test(protocol) || !serverVersion.test(protocol)) {
            throw new IllegalArgumentException("Channel rejects its own protocol: " + id);
        }
        if (CHANNELS.stream().anyMatch(channel -> channel.id.equals(id))) {
            throw new IllegalStateException("Duplicate channel: " + id);
        }
        DnvNeoChannel channel = new DnvNeoChannel(id, protocol);
        CHANNELS.add(channel);
        return channel;
    }
    public static void install(IEventBus modBus) {
        modBus.addListener(DnvNeoChannel::registerPayloads);
    }
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        for (DnvNeoChannel channel : List.copyOf(CHANNELS)) {
            if (channel.registered) throw new IllegalStateException("Duplicate payload registration");
            channel.registrations.forEach(registration -> registration.accept(event));
            channel.registered = true;
        }
    }
    public <M> void registerMessage(int index, Class<M> messageClass,
            BiConsumer<M, FriendlyByteBuf> encode, Function<FriendlyByteBuf, M> decode,
            BiConsumer<M, Supplier<Context>> handler, Optional<Direction> direction) {
        if (registered || messages.containsKey(messageClass) || !ids.add(index)) {
            throw new IllegalStateException("Duplicate or late packet in " + id + ": " + index);
        }
        Direction flow = direction.orElseThrow();
        var type = new CustomPacketPayload.Type<Envelope>(
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "/" + index));
        StreamCodec<RegistryFriendlyByteBuf, Envelope> codec = StreamCodec.of(
                (buffer, envelope) -> encode.accept(messageClass.cast(envelope.message()), buffer),
                buffer -> new Envelope(type, decode.apply(buffer)));
        messages.put(messageClass, new Entry(type, flow, codec));
        IPayloadHandler<Envelope> receiver = (envelope, context) ->
                handler.accept(messageClass.cast(envelope.message()), () -> new Context(context));
        registrations.add(event -> {
            // Required payloads: both endpoints must advertise this channel's exact protocol.
            var registrar = event.registrar(version);
            if (flow == Direction.PLAY_TO_SERVER) registrar.playToServer(type, codec, receiver);
            else registrar.playToClient(type, codec, receiver);
        });
    }
    private Envelope wrap(Object message, Direction direction) {
        Entry entry = messages.get(message.getClass());
        if (entry == null || entry.direction() != direction) {
            throw new IllegalArgumentException("Unregistered packet or wrong direction in " + id);
        }
        return new Envelope(entry.type(), message);
    }
    public void sendToServer(Object message) {
        PacketDistributor.sendToServer(wrap(message, Direction.PLAY_TO_SERVER));
    }
    public void send(Consumer<CustomPacketPayload> target, Object message) {
        target.accept(wrap(message, Direction.PLAY_TO_CLIENT));
    }
}
