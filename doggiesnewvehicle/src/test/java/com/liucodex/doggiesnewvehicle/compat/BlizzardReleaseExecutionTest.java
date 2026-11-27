package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the production native-packet bridge on both platform context shapes. */
class BlizzardReleaseExecutionTest {
    @Test void existingReleasePacketReachesOnlyBlizzardAndNeverCancelsTheNativeHandler() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/api/combat/DnvNativeShotAdmission",Admission.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Body.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/server/level/ServerPlayer",Operator.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/player/Player",Body.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraftforge/network/NetworkEvent$Context",ForgeContext.class);
        NuclearRecoveryExecutionTest.map(names,"net/neoforged/neoforge/network/handling/IPayloadContext",NeoContext.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/compat/mixin/DnvBlizzardFireReleaseMixin",
            Set.of("dnv$releaseColdBeam"),names,Packet.class,false);
        var instance=(Packet)type.getConstructor().newInstance();
        var method=Arrays.stream(type.getMethods()).filter(m->m.getName().equals("dnv$releaseColdBeam")).findFirst().orElseThrow();
        var player=new Operator();var tank=new Blizzard();player.vehicle=tank;
        Object context=method.getParameterTypes()[0]==Supplier.class?(Supplier<ForgeContext>)()->new ForgeContext(player):(NeoContext)()->player;
        var callback=new CallbackInfo("handler",true);
        instance.type=0;method.invoke(instance,context,callback);assertEquals(0,tank.releases);
        instance.type=1;method.invoke(instance,context,callback);assertEquals(1,tank.releases);assertSame(player,tank.actor);
        assertFalse(callback.isCancelled());
        player.vehicle=new Body();method.invoke(instance,context,callback);assertEquals(1,tank.releases);
        Object absent=method.getParameterTypes()[0]==Supplier.class?(Supplier<ForgeContext>)()->new ForgeContext(null):(NeoContext)()->null;
        method.invoke(instance,absent,callback);assertEquals(1,tank.releases);
    }
    public static class Packet { public int type=1;public int getType(){return type;} }
    public static class Body { }
    public static class Operator extends Body { public Body vehicle;public Body getVehicle(){return vehicle;} }
    public interface Admission {void releaseNativeFire(Body value);}
    public static class Blizzard extends Body implements Admission {public int releases;public Body actor;public void releaseNativeFire(Body value){releases++;actor=value;} }
    public record ForgeContext(Operator sender) {public Operator getSender(){return sender;} }
    public interface NeoContext {Body player();}
}
