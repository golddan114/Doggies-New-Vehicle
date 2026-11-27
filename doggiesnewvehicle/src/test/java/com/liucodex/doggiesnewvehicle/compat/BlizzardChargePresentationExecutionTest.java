package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Execute the actual charge HUD and sound consumers; drawing/audio devices are explicit ports. */
class BlizzardChargePresentationExecutionTest {
    @Test void narrowColumnFillsUpBlueAndDrainsRedAtEveryScaleWithoutWritingState() throws Exception {
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/gui/GuiGraphics",Canvas.class);
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/client/BlizzardChargeHud",Set.of("draw"),names,null,false);
        var draw=type.getMethod("draw",Canvas.class,int.class,int.class,int.class,float.class);
        var preview=new java.awt.image.BufferedImage(400,140,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int phase:new int[]{1,2,3})for(float progress:new float[]{0,.25F,.5F,1,Float.NaN,-1,2}) {
            var c=new Canvas();draw.invoke(null,c,80,60,phase,progress);
            int expected=Math.round(38*(Float.isFinite(progress)?Math.max(0,Math.min(1,progress)):0));
            int color=phase==2?0xFFE55F62:0xFF46BFEA;
            long filled=c.pixels.entrySet().stream().filter(e->e.getValue()==color&&e.getKey().x==111).count();
            assertEquals(expected,filled);assertTrue(c.pixels.keySet().stream().allMatch(p->p.x>=110&&p.x<117&&p.y>=40&&p.y<82));
            if(expected>0)assertEquals(color,c.pixels.get(new Pixel(111,79)));
            if(expected<38)assertEquals(0xD016242C,c.pixels.get(new Pixel(111,40+2)));
        }
        for(int i=0;i<6;i++){
            var c=new Canvas();draw.invoke(null,c,10+i*60,65,i<3?1:2,new float[]{.25F,.5F,1,1,.5F,0}[i]);
            c.pixels.forEach((p,v)->preview.setRGB(p.x,p.y,v));
        }
        var file=java.nio.file.Path.of("build/reports/blizzard-charge-hud.png");java.nio.file.Files.createDirectories(file.getParent());
        javax.imageio.ImageIO.write(preview,"png",file.toFile());
    }
    @Test void chargeCuePlaysOncePerEntryStopsOnDecayCooldownAndCleanup() throws Exception {
        var names=soundNames();String base="com/liucodex/doggiesnewvehicle/client/BlizzardClientEffects";
        NuclearRecoveryExecutionTest.map(names,base+"$HoverSound",Sound.class);
        NuclearRecoveryExecutionTest.map(names,base+"$ChargeSound",Sound.class);
        var type=NuclearRecoveryExecutionTest.extract(base+"$Effect",Set.of("sounds","stop","stopHover"),names,EffectHost.class,false);
        var effect=(EffectHost)type.getConstructor().newInstance();var update=type.getMethod("sounds",MC.class);
        MC.INSTANCE=new MC();effect.tank=new Tank();effect.tank.phase=1;
        for(int i=0;i<59;i++)update.invoke(effect,MC.INSTANCE);
        assertEquals(1,MC.INSTANCE.manager.plays);assertEquals(0,MC.INSTANCE.manager.stops);
        effect.tank.phase=3;update.invoke(effect,MC.INSTANCE);assertEquals(1,MC.INSTANCE.manager.stops);
        effect.tank.phase=1;update.invoke(effect,MC.INSTANCE);assertEquals(2,MC.INSTANCE.manager.plays);
        effect.tank.phase=2;for(int i=0;i<40;i++)update.invoke(effect,MC.INSTANCE);assertEquals(2,MC.INSTANCE.manager.stops);
        effect.tank.phase=1;update.invoke(effect,MC.INSTANCE);type.getMethod("stop").invoke(effect);type.getMethod("stop").invoke(effect);
        assertEquals(3,MC.INSTANCE.manager.plays);assertEquals(3,MC.INSTANCE.manager.stops);
    }
    @Test void actualChargeSoundTracksMuzzleAndStopsAtEveryInvalidLifecycleBoundary() throws Exception {
        var names=soundNames();
        var type=NuclearRecoveryExecutionTest.extract("com/liucodex/doggiesnewvehicle/client/BlizzardClientEffects$ChargeSound",Set.of("tick","position"),names,ChargeHost.class,false);
        for(int reason=0;reason<8;reason++){
            MC.INSTANCE=new MC();var sound=(ChargeHost)type.getConstructor().newInstance();sound.tank=new Tank();sound.tank.phase=1;
            type.getMethod("tick").invoke(sound);assertFalse(sound.stopped);assertEquals(2,sound.x);assertEquals(3,sound.y);assertEquals(4,sound.z);
            if(reason==0)sound.tank.phase=2;if(reason==1)sound.tank.phase=3;if(reason==2)sound.tank.removed=true;
            if(reason==3)sound.tank.alive=false;if(reason==4)sound.tank.wreck=true;if(reason==5)MC.INSTANCE.level=new World();
            if(reason==6)MC.INSTANCE.player=null;if(reason==7)sound.tank.distance=5000;
            type.getMethod("tick").invoke(sound);assertTrue(sound.stopped,"cause "+reason);
        }
    }
    private static Map<String,String> soundNames(){
        var names=new HashMap<String,String>();
        NuclearRecoveryExecutionTest.map(names,"com/liucodex/doggiesnewvehicle/armored/DnvBlizzardEntity",Tank.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/Minecraft",MC.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/player/LocalPlayer",Object.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/entity/Entity",Object.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/multiplayer/ClientLevel",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/world/level/Level",World.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/sounds/SoundManager",Manager.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/client/resources/sounds/SoundInstance",Sound.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/sounds/SoundEvents",Events.class);
        NuclearRecoveryExecutionTest.map(names,"net/minecraft/sounds/SoundEvent",Object.class);
        return names;
    }
    record Pixel(int x,int y){}
    public static class Canvas {final Map<Pixel,Integer> pixels=new HashMap<>();
        public void fill(int x1,int y1,int x2,int y2,int z,int color){assertEquals(100,z);for(int y=y1;y<y2;y++)for(int x=x1;x<x2;x++)pixels.put(new Pixel(x,y),color);}}
    public static class World {public void playLocalSound(double x,double y,double z,Object sound,net.minecraft.sounds.SoundSource source,float volume,float pitch,boolean delay){}}
    public static class Events {public static Object FIRE_EXTINGUISH=new Object();}
    public static class MC {public static MC INSTANCE=new MC();public World level=new World();public Object player=new Object();public Manager manager=new Manager();public static MC getInstance(){return INSTANCE;}public Manager getSoundManager(){return manager;}}
    public static class Manager {int plays,stops;public void play(Sound sound){plays++;}public void stop(Sound sound){if(!sound.stopped){sound.stopped=true;stops++;}}}
    public static class Sound {public boolean stopped;public double x,y,z;public Sound(){}public Sound(Tank tank){}public Sound(Tank tank,boolean jet){}public boolean isStopped(){return stopped;}public void stop(){stopped=true;}}
    public static class ChargeHost extends Sound {public Tank tank;}
    public static class EffectHost {public Tank tank;public int gasCooldown;public boolean powered,charging;public double thrust;public Sound idle,jet,charge;}
    public static class Tank {public int phase;public boolean removed,wreck,alive=true;public double distance;
        public boolean presentationPowered(){return false;}public boolean hovering(){return false;}public boolean mode2(){return true;}public int coldPhase(){return phase;}
        public boolean isRemoved(){return removed;}public boolean isAlive(){return alive;}public boolean isWreck(){return wreck;}public World level(){return world;}
        final World world=MC.INSTANCE.level;public double distanceToSqr(Object player){return distance;}public Vec3 getShootPos(String weapon,float partial){return new Vec3(2,3,4);}
        public Vec3 presentationVelocity(){return Vec3.ZERO;}public double getX(){return 0;}public double getY(){return 0;}public double getZ(){return 0;}}
}
