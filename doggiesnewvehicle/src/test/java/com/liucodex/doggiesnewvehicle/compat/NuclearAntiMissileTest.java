package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Execute the production scoped adapter against explicit owner/world/sight native ports. */
class NuclearAntiMissileTest {
    @Test void armedHostileMissilesUseNativeSightWhileFriendlyUnknownAndRackedUnitsRefuse()throws Exception{
        var type=adapter();var tower=new Tower();var missile=new Missile();tower.world=missile.world=new Object();
        tower.owner=new Body();missile.owner=new Body();
        assertTrue(hostile(type,tower,missile));assertTrue(threat(type,tower,missile,.2));assertEquals(1,tower.rays);
        missile.owner.allied=true;assertFalse(hostile(type,tower,missile));assertFalse(threat(type,tower,missile,.2));
        assertEquals(1,tower.rays);missile.owner.allied=false;tower.visible=false;assertFalse(threat(type,tower,missile,.2));
        tower.visible=true;missile.owner=tower.owner;assertFalse(hostile(type,tower,missile));
        missile.owner=null;assertFalse(hostile(type,tower,missile));missile.owner=new Body();
        missile.armed=false;assertFalse(hostile(type,tower,missile));missile.armed=true;
        missile.removed=true;assertFalse(hostile(type,tower,missile));missile.removed=false;
        missile.ground=true;assertFalse(threat(type,tower,missile,.2));missile.ground=false;
        assertFalse(threat(type,tower,missile,9));assertFalse(threat(type,tower,missile,Double.NaN));
        assertFalse(threat(type,tower,missile,-1));missile.world=new Object();assertFalse(hostile(type,tower,missile));
        assertEquals(0,missile.hurts);assertEquals(0,missile.discards);
    }
    @Test void tdmAndMissingDefenderFollowThePinnedNativeProjectileAffiliationRule()throws Exception{
        var type=adapter();var tower=new Tower();var missile=new Missile();tower.world=missile.world=new Object();
        missile.owner=new Body();assertFalse(hostile(type,tower,missile));tower.owner=new Body();
        missile.owner.allied=true;missile.owner.tdm=true;assertFalse(hostile(type,tower,missile));
        missile.owner.team=new Team();assertTrue(hostile(type,tower,missile));
        missile.owner=tower.owner;missile.owner.tdm=true;missile.owner.team=new Team();assertFalse(hostile(type,tower,missile));
    }
    @Test void otherEntitiesDoNotEnterTheAdapterOrCancelEitherNativeQuery()throws Exception{
        var type=adapter();var tower=new Tower();var other=new Body();tower.world=other.world=new Object();
        tower.owner=new Body();
        assertFalse((boolean)type.getMethod("hostile",Tower.class,Body.class).invoke(null,tower,other));
        assertFalse((boolean)type.getMethod("threatening",Tower.class,Body.class,double.class,Vec3.class)
                .invoke(null,tower,other,.2,Vec3.ZERO));
        for(String mixin:List.of("NuclearHostileMixin","NuclearThreatMixin")){
            String name="com/liucodex/doggiesnewvehicle/compat/mixin/"+mixin;
            var source=new ClassNode();
            try(var stream=getClass().getClassLoader().getResourceAsStream(name+".class")){
                new ClassReader(stream.readAllBytes()).accept(source,0);
            }
            var handler=source.methods.stream().filter(m->m.name.startsWith("dnv$nuclear")).findFirst().orElseThrow();
            if((handler.access&Opcodes.ACC_STATIC)==0)handler.desc="(Lcom/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity;"+handler.desc.substring(1);
            handler.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC;
            handler.visibleAnnotations=null;handler.invisibleAnnotations=null;
            String probe=name+"Probe";var writer=new ClassWriter(0);
            writer.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,probe,null,"java/lang/Object",null);
            var mapping=Map.of("com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity",Type.getInternalName(Tower.class),
                    "com/liucodex/doggiesnewvehicle/api/combat/DnvNuclearInterceptionTarget",Type.getInternalName(Threat.class),
                    "net/minecraft/world/entity/Entity",Type.getInternalName(Body.class),
                    "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable",Type.getInternalName(Gate.class),
                    "com/liucodex/doggiesnewvehicle/compat/NuclearAntiMissile",Type.getInternalName(type));
            handler.accept(new ClassRemapper(writer,new SimpleRemapper(mapping)));writer.visitEnd();
            class Loader extends ClassLoader{Loader(){super(type.getClassLoader());}Class<?> define(){return defineClass(probe.replace('/','.'),writer.toByteArray(),0,writer.toByteArray().length);}}
            var actual=new Loader().define();var gate=new Gate();
            if(mixin.equals("NuclearHostileMixin"))actual.getDeclaredMethod(handler.name,Tower.class,Body.class,Gate.class).invoke(null,tower,other,gate);
            else actual.getDeclaredMethod(handler.name,Tower.class,Body.class,double.class,Vec3.class,Gate.class).invoke(null,tower,other,.2,Vec3.ZERO,gate);
            assertFalse(gate.cancelled,mixin);assertEquals(0,tower.rays);
        }
    }
    public static class Gate{public boolean cancelled;public void setReturnValue(Object value){cancelled=true;}}
    public static class Team{}
    public static class Body {public Object world;public boolean allied,tdm;public Team team;
        public Object level(){return world;}public Team getTeam(){return team;}public boolean isAlliedTo(Body other){return allied;}
        public boolean isRemoved(){return false;}public boolean onGround(){return false;}
        public float getBbWidth(){return 3;}public float getBbHeight(){return 7;}}
    public static class Living extends Body{}
    public static class Tower extends Body {public Body owner;public boolean visible=true;public int rays;
        public Body getOwner(){return owner;}public boolean checkNoClip(Body target,Vec3 origin){rays++;return visible;}}
    public interface Threat {boolean armed();Body interceptionOwner();}
    public static class Missile extends Body implements Threat {public Body owner;public boolean armed=true,removed,ground;public int hurts,discards;
        public Body interceptionOwner(){return owner;}public boolean armed(){return armed;}public boolean isRemoved(){return removed;}
        public boolean onGround(){return ground;}public float getBbWidth(){return 3;}public float getBbHeight(){return 7;}}
    public static class Store {public static final Companion Companion=new Companion();}
    public static class Companion {public boolean enabledTDM(Body body){return body.tdm;}}
    private static Class<?> adapter()throws Exception{
        String name="com/liucodex/doggiesnewvehicle/compat/NuclearAntiMissile",probe=name+"Probe";
        var mapping=new HashMap<String,String>();mapping.put(name,probe);
        Map.of("com/atsuishio/superbwarfare/entity/vehicle/base/AutoAimableEntity",Tower.class,
                "com/liucodex/doggiesnewvehicle/api/combat/DnvNuclearInterceptionTarget",Threat.class,
                "com/atsuishio/superbwarfare/world/saveddata/TDMSavedData",Store.class,
                "com/atsuishio/superbwarfare/world/saveddata/TDMSavedData$Companion",Companion.class,
                "net/minecraft/world/entity/Entity",Body.class,"net/minecraft/world/entity/LivingEntity",Body.class,
                "net/minecraft/world/level/Level",Object.class,"net/minecraft/world/scores/Team",Team.class,
                "net/minecraft/world/scores/PlayerTeam",Team.class)
                .forEach((source,type)->mapping.put(source,Type.getInternalName(type)));
        try(var stream=NuclearAntiMissileTest.class.getClassLoader().getResourceAsStream(name+".class")){
            var writer=new ClassWriter(0);new ClassReader(stream.readAllBytes()).accept(new ClassRemapper(writer,new SimpleRemapper(mapping)),0);
            return new ExactQueuedShotFixture.Loader(Map.of(probe,writer.toByteArray())).loadClass(probe.replace('/','.'));
        }
    }
    private static boolean hostile(Class<?> type,Tower tower,Missile missile)throws Exception{
        return (boolean)type.getMethod("hostile",Tower.class,Body.class).invoke(null,tower,missile);
    }
    private static boolean threat(Class<?> type,Tower tower,Missile missile,double size)throws Exception{
        return (boolean)type.getMethod("threatening",Tower.class,Body.class,double.class,Vec3.class).invoke(null,tower,missile,size,Vec3.ZERO);
    }
}
