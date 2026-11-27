package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Actual vanilla clipping versus the compiled DNV spawn codec; no game/world bootstrap. */
public class StormBulletSpawnTest {
    public static class Kind { }
    public static class Owner {public int getId(){return 71;}}
    public static class World {public Owner owner=new Owner();public Owner getEntity(int id){return id==71?owner:null;}}
    public static class Parent {
        public Vec3 motion=Vec3.ZERO;public float gravity=.05F;public Owner owner;public final World world;
        public Parent(Kind kind,World level){world=level;}
        public Vec3 getDeltaMovement(){return motion;}
        public void setDeltaMovement(double x,double y,double z){motion=new Vec3(x,y,z);}
        public void setCustomGravity(float value){gravity=value;}
        public Owner getOwner(){return owner;}public void setOwner(Owner value){owner=value;}
        public World level(){return world;}
    }

    @Test void diagonalUpAndDownShotsKeepTheServerDirectionAndSpeedFromTheFirstClientTick() throws Exception {
        Class<?> type=compile();
        for(Vec3 ray:new Vec3[]{new Vec3(0,.18,1),new Vec3(.4,-.22,1),new Vec3(-1,.7,.5)}) {
            Vec3 velocity=ray.normalize().scale(36);
            var vanilla=new ClientboundAddEntityPacket(1,UUID.randomUUID(),0,90,0,0,0,null,0,velocity,0);
            Vec3 clipped=new Vec3(vanilla.getXa(),vanilla.getYa(),vanilla.getZa());
            assertTrue(clipped.length()<7);assertNotEquals(velocity.normalize(),clipped.normalize());
            World world=new World();Parent server=(Parent)type.getConstructor(Kind.class,World.class).newInstance(null,world);
            Parent client=(Parent)type.getConstructor(Kind.class,World.class).newInstance(null,world);
            server.motion=velocity;server.owner=world.owner;client.motion=clipped;
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {
                type.getMethod("writeSpawnData",FriendlyByteBuf.class).invoke(server,buffer);
                type.getMethod("readSpawnData",FriendlyByteBuf.class).invoke(client,buffer);
                assertEquals(0,buffer.readableBytes());assertEquals(velocity,client.motion);
                assertEquals(0,server.gravity);assertEquals(server.gravity,client.gravity);assertSame(server.owner,client.owner);
            } finally {buffer.release();}
        }
    }

    @Test void configuredGravityIsSyncedAndAnAbsentOwnerIsSafe()throws Exception {
        Class<?> type=compile();World world=new World();
        Parent server=(Parent)type.getConstructor(Kind.class,World.class).newInstance(null,world);
        Parent client=(Parent)type.getConstructor(Kind.class,World.class).newInstance(null,world);
        type.getMethod("setCustomGravity",float.class).invoke(server,.013F);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            type.getMethod("writeSpawnData",FriendlyByteBuf.class).invoke(server,buffer);
            type.getMethod("readSpawnData",FriendlyByteBuf.class).invoke(client,buffer);
            assertEquals(.013F,client.gravity);assertNull(client.owner);
        } finally {buffer.release();}
    }

    private static Class<?> compile()throws Exception {
        String source="com/liucodex/doggiesnewvehicle/entity/StormBulletEntity",subject=source+"SpawnProbe";
        ClassNode node=new ClassNode();
        try(var input=StormBulletSpawnTest.class.getClassLoader().getResourceAsStream(source+".class")){new ClassReader(input).accept(node,0);}
        node.methods.removeIf(m->!Set.of("<init>","setCustomGravity","writeSpawnData","readSpawnData").contains(m.name));
        node.interfaces.clear();node.innerClasses.clear();node.nestHostClass=null;node.nestMembers=null;
        Map<String,String> names=new HashMap<>();names.put(source,subject);
        names.put("com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity",Type.getInternalName(Parent.class));
        names.put("net/minecraft/world/entity/EntityType",Type.getInternalName(Kind.class));
        names.put("net/minecraft/world/level/Level",Type.getInternalName(World.class));
        names.put("net/minecraft/world/entity/Entity",Type.getInternalName(Owner.class));
        ClassWriter writer=new ClassWriter(0);node.accept(new ClassRemapper(writer,new SimpleRemapper(names)));byte[] bytes=writer.toByteArray();
        return new ClassLoader(StormBulletSpawnTest.class.getClassLoader()) {
            Class<?> define(){return defineClass(subject.replace('/','.'),bytes,0,bytes.length);}
        }.define();
    }
}
