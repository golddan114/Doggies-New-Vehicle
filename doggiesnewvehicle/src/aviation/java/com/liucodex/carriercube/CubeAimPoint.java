package com.liucodex.carriercube;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

record CubeAimPoint(int aircraftId, Vec3 point) {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CubePrototype.ID,"hover_sight"), ()->"1", "1"::equals, "1"::equals);

    static void register() {
        CHANNEL.messageBuilder(CubeAimPoint.class,0,NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg,buf)->{
                    buf.writeVarInt(msg.aircraftId);
                    buf.writeDouble(msg.point.x);buf.writeDouble(msg.point.y);buf.writeDouble(msg.point.z);
                })
                .decoder(buf->new CubeAimPoint(buf.readVarInt(),new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble())))
                .consumerMainThread((msg,ctx)->{
                    var player=ctx.get().getSender();
                    if (player!=null && player.getVehicle() instanceof CubeVehicle jet && jet.getId()==msg.aircraftId)
                        jet.acceptSight(player,msg.point);
                    ctx.get().setPacketHandled(true);
                }).add();
    }

    void send() { CHANNEL.sendToServer(this); }
}
