package com.liucodex.doggiesnewvehicle.network;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** One-way bounded visual samples. Native entities continue to own movement and hits. */
@Mod.EventBusSubscriber(modid="doggiesnewvehicle",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class Dsx1TrailNetwork {
    private static SimpleChannel channel;
    private static volatile Consumer<Segment> sink;
    private Dsx1TrailNetwork(){}
    public record Segment(ResourceLocation dimension,UUID trail,int sequence,long tick,Vec3 from,Vec3 to){
        public Segment {
            if(dimension==null||dimension.toString().length()>256||trail==null||sequence<0
                    ||sequence>=128||tick<0||!validSegment(from,to))throw new IllegalArgumentException("Invalid trail sample");
        }
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
        channel=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle","dsx1_trails"),()->"1","1"::equals,"1"::equals);
        channel.registerMessage(0,Segment.class,Dsx1TrailNetwork::encode,Dsx1TrailNetwork::decode,(s,supplier)->{
            var context=supplier.get();context.enqueueWork(()->{var current=sink;if(current!=null)current.accept(s);});
            context.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void installClient(Consumer<Segment> consumer){sink=java.util.Objects.requireNonNull(consumer);}
    public static void send(ServerLevel level,UUID trail,int sequence,Vec3 from,Vec3 to){
        if(channel==null||!validSegment(from,to))return;
        var sample=new Segment(level.dimension().location(),trail,sequence,level.getGameTime(),from,to);
        // A fixed maximum of 128 samples per projectile, sent only to nearby viewers in this level.
        for(var player:level.players())if(distanceSquared(player.position(),from,to)<=256*256)
            channel.send(PacketDistributor.PLAYER.with(()->player),sample);
    }
    public static boolean validSegment(Vec3 from,Vec3 to){
        return com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotVisual.validSegment(from,to);
    }
    private static double distanceSquared(Vec3 point,Vec3 from,Vec3 to){
        Vec3 delta=to.subtract(from);double length=delta.lengthSqr();
        double t=length<1e-12?0:Math.max(0,Math.min(1,point.subtract(from).dot(delta)/length));
        return point.distanceToSqr(from.add(delta.scale(t)));
    }
    public static void encode(Segment s,FriendlyByteBuf b){
        b.writeResourceLocation(s.dimension);b.writeUUID(s.trail);b.writeVarInt(s.sequence);b.writeLong(s.tick);
        b.writeDouble(s.from.x);b.writeDouble(s.from.y);b.writeDouble(s.from.z);
        b.writeDouble(s.to.x);b.writeDouble(s.to.y);b.writeDouble(s.to.z);
    }
    public static Segment decode(FriendlyByteBuf b){return new Segment(b.readResourceLocation(),b.readUUID(),b.readVarInt(),b.readLong(),
        new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()));}
}
