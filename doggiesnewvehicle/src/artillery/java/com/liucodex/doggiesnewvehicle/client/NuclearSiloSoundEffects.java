package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds;
import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded client observations only; the facility remains the sole mechanical state writer. */
@Mod.EventBusSubscriber(modid="doggiesnewvehicle",value=Dist.CLIENT)
public final class NuclearSiloSoundEffects {
    private static final Map<Integer,Observer> ACTIVE=new LinkedHashMap<>();
    private NuclearSiloSoundEffects(){ }
    @SubscribeEvent public static void loaded(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide||!(event.getEntity() instanceof NuclearSiloEntity silo))return;
        var old=ACTIVE.remove(silo.getId());if(old!=null)old.stop();
        if(ACTIVE.size()<64)ACTIVE.put(silo.getId(),new Observer(silo));
    }
    public static void reset(){ACTIVE.values().forEach(Observer::stop);ACTIVE.clear();}
    public static void reload(){ACTIVE.values().forEach(Observer::stop);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){reset();return;}if(mc.isPaused())return;
        var iterator=ACTIVE.values().iterator();
        while(iterator.hasNext()){
            var observer=iterator.next();var silo=observer.silo;
            if(silo.isRemoved()||silo.level()!=mc.level){observer.stop();iterator.remove();continue;}
            observer.tick();
        }
    }
    private static SiloCycle.Phase phase(NuclearSiloEntity silo){
        try{return SiloCycle.Phase.valueOf(silo.visualSnapshot().getString("Phase"));}
        catch(IllegalArgumentException malformed){return SiloCycle.Phase.EMPTY;}
    }
    private static float frame(NuclearSiloEntity silo,SiloCycle.Phase phase){
        var state=silo.visualSnapshot();
        double remaining=NuclearMechanicalTimeline.remaining(state.getInt("Remaining"),state.getLong("Observed"),
                silo.level().getGameTime(),silo.mechanicsRunning());
        return NuclearMechanicalTimeline.frame(phase,remaining,NuclearRules.DEFAULT);
    }
    private static final class Observer {
        private final NuclearSiloEntity silo;
        private final NuclearSoundCues cues=new NuclearSoundCues();
        private MechanicalSound sound;
        private IdleSound idle;
        private Observer(NuclearSiloEntity silo){this.silo=silo;}
        private void stopIdle(){if(idle!=null){idle.finish();Minecraft.getInstance().getSoundManager().stop(idle);idle=null;}}
        private void stop(){stopIdle();if(sound!=null){sound.finish();Minecraft.getInstance().getSoundManager().stop(sound);sound=null;}}
        private void tick(){
            var phase=phase(silo);float frame=frame(silo,phase);var cue=cues.observe(phase,frame);
            var mc=Minecraft.getInstance();
            if(!silo.mechanicsRunning()||silo.distanceToSqr(mc.player)>64*64){stop();return;}
            if(sound!=null&&!sound.cue.active(phase,frame))stop();
            if(cue!=null){stop();sound=new MechanicalSound(silo,cue);mc.getSoundManager().play(sound);}
            if(sound==null&&shouldIdle(silo,phase)){
                if(idle==null||idle.isStopped()){stopIdle();idle=new IdleSound(silo);mc.getSoundManager().play(idle);}
            }else stopIdle();
        }
    }
    private static boolean shouldIdle(NuclearSiloEntity silo,SiloCycle.Phase phase){
        return !silo.isRemoved()&&silo.mechanicsRunning()&&silo.getEnergy()>0
                &&(phase==SiloCycle.Phase.EMPTY||phase==SiloCycle.Phase.RELOADING||phase==SiloCycle.Phase.READY);
    }
    private static final class IdleSound extends AbstractTickableSoundInstance {
        private final NuclearSiloEntity silo;
        private IdleSound(NuclearSiloEntity silo){
            super(DnvArtillerySounds.JINGZHE_IDLE.get(),SoundSource.BLOCKS,RandomSource.create());
            this.silo=silo;looping=true;delay=0;volume=.65F;pitch=1F;
            attenuation=net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR;
            x=silo.getX();y=silo.getY();z=silo.getZ();
        }
        @Override public void tick(){
            var mc=Minecraft.getInstance();
            if(mc.player==null||mc.level!=silo.level()||silo.distanceToSqr(mc.player)>64*64||!shouldIdle(silo,phase(silo)))stop();
        }
        private void finish(){stop();}
    }
    private static final class MechanicalSound extends AbstractTickableSoundInstance {
        private final NuclearSiloEntity silo;
        private final NuclearSoundCues.Cue cue;
        private MechanicalSound(NuclearSiloEntity silo,NuclearSoundCues.Cue cue){
            super(switch(cue){case TURNTABLE->DnvArtillerySounds.NUCLEAR_TURNTABLE.get();
                case COVER->DnvArtillerySounds.NUCLEAR_COVER.get();case LIFT->DnvArtillerySounds.NUCLEAR_LIFT.get();},SoundSource.BLOCKS,RandomSource.create());
            this.silo=silo;this.cue=cue;looping=false;volume=1F;pitch=1F;x=silo.getX();y=silo.getY();z=silo.getZ();
        }
        @Override public void tick(){
            var mc=Minecraft.getInstance();var phase=phase(silo);
            if(mc.player==null||mc.level!=silo.level()||silo.isRemoved()||!silo.mechanicsRunning()
                    ||silo.distanceToSqr(mc.player)>64*64||!cue.active(phase,frame(silo,phase)))stop();
        }
        private void finish(){stop();}
    }
}
