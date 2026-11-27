package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Executes the compiled shared gate used by Noise retained targets and mothership fire authorization. */
public class BossPlayerModeExecutionTest {
    public static class Actor {
        public boolean spectator;public String team;public List<Actor> passengers=new ArrayList<>();
        public Stream<Actor> getSelfAndPassengers(){return Stream.concat(Stream.of(this),passengers.stream());}
        public boolean isAlliedTo(Actor target){return team!=null&&team.equals(target.team);}
        public boolean isSpectator(){return spectator;}
    }
    public static class Player extends Actor {public boolean creative;public boolean isCreative(){return creative;}}
    @Test void creativeSpectatorPassengersAndMidLockModeChangesAreRejectedWithoutAnAdminImmunityRule() throws Exception {
        Class<?> gate=compile();Actor boss=new Actor();boss.team="boss";Player player=new Player();
        var allow=gate.getMethod("bossRelationsAllow",Actor.class,Actor.class);
        assertEquals(true,allow.invoke(null,boss,player));
        player.creative=true;assertEquals(false,allow.invoke(null,boss,player));
        player.creative=false;player.spectator=true;assertEquals(false,allow.invoke(null,boss,player));
        player.spectator=false;assertEquals(true,allow.invoke(null,boss,player));
        Actor vehicle=new Actor();vehicle.passengers.add(player);
        assertEquals(true,allow.invoke(null,boss,vehicle));player.creative=true;
        assertEquals(false,allow.invoke(null,boss,vehicle));
        player.creative=false;player.team="boss";assertEquals(false,allow.invoke(null,boss,vehicle));
        var protectedMode=gate.getMethod("isProtectedPlayerTarget",Actor.class);
        assertEquals(false,protectedMode.invoke(null,player),"team semantics are separate from creative protection");
        player.creative=true;assertEquals(true,protectedMode.invoke(null,player));
        assertEquals(false,allow.invoke(null,boss,boss));assertEquals(false,allow.invoke(null,boss,null));
    }
    private static Class<?> compile() throws Exception {
        String name="com/liucodex/doggiesnewvehicle/compat/DnvCombatPolicyRuntime",target=name+"ExecutedBossRelations";
        ClassNode node=new ClassNode();
        try(var in=BossPlayerModeExecutionTest.class.getClassLoader().getResourceAsStream(name+".class")){new ClassReader(in).accept(node,0);}
        node.methods.removeIf(m->!m.name.equals("bossRelationsAllow")&&!m.name.startsWith("lambda$bossRelationsAllow$")
                &&!m.name.equals("isProtectedPlayerTarget")&&!m.name.startsWith("lambda$isProtectedPlayerTarget$"));
        node.fields.clear();node.innerClasses.clear();node.nestMembers=null;node.nestHostClass=null;
        ClassWriter writer=new ClassWriter(0);
        node.accept(new ClassRemapper(writer,new SimpleRemapper(Map.of(name,target,
                "net/minecraft/world/entity/Entity",Type.getInternalName(Actor.class),
                "net/minecraft/world/entity/player/Player",Type.getInternalName(Player.class)))));
        byte[] bytes=writer.toByteArray();return new ClassLoader(BossPlayerModeExecutionTest.class.getClassLoader()) {
            Class<?> define(){return defineClass(target.replace('/','.'),bytes,0,bytes.length);}
        }.define();
    }
}
