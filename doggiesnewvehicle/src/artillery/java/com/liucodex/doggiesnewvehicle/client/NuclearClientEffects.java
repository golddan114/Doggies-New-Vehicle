package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.artillery.silo.*;
import com.liucodex.doggiesnewvehicle.client.mesh.NuclearVisualData;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Loaded lifecycle supplies bounded smoke even when the body is off-screen. No gameplay writes. */
@Mod.EventBusSubscriber(modid="doggiesnewvehicle",value=Dist.CLIENT)
public final class NuclearClientEffects {
    private static final Map<Integer,NuclearMissileEntity> ACTIVE=new LinkedHashMap<>();
    private static int cursor;
    private NuclearClientEffects(){ }
    @SubscribeEvent public static void loaded(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide||!(event.getEntity() instanceof NuclearMissileEntity missile))return;
        ACTIVE.remove(missile.getId());
        if(ACTIVE.size()<64)ACTIVE.put(missile.getId(),missile);
    }
    public static void reset(){ACTIVE.clear();cursor=0;}
    public static void reload(){cursor=0;}
    static void appendLights(java.util.List<DnvDynamicLightField.Source> sources,net.minecraft.world.level.Level world){
        var data=NuclearVisualData.get();
        if(data.motion()==null||data.nozzles().isEmpty())return;
        for(var missile:ACTIVE.values())if(!missile.isRemoved()&&missile.level()==world&&missile.armed()){
            var rotation=new Quaternionf().rotateY((float)Math.toRadians(-missile.getYRot()))
                    .rotateX((float)Math.toRadians(missile.getXRot()+90));
            var port=(missile.dogEgg()?new Vector3f():new Vector3f(data.nozzles().get(0))).rotate(rotation);var point=missile.position();
            sources.add(new DnvDynamicLightField.Source(point.x+port.x,point.y+port.y,point.z+port.z,15));
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){reset();return;}if(mc.isPaused())return;
        var iterator=ACTIVE.values().iterator();
        while(iterator.hasNext()){
            var missile=iterator.next();
            if(missile.isRemoved()||missile.level()!=mc.level)iterator.remove();
        }
        var effects=ACTIVE.values().toArray(NuclearMissileEntity[]::new);int budget=24;
        int start=effects.length==0?0:Math.floorMod(cursor,effects.length);
        for(int i=0;i<effects.length&&budget>0;i++){
            int index=(start+i)%effects.length;var missile=effects[index];
            if(missile.armed()&&missile.distanceToSqr(mc.player)<=256*256){
                budget-=smoke(missile,budget);cursor=index+1;
            }
        }
    }
    private static int smoke(NuclearMissileEntity missile,int budget){
        var data=NuclearVisualData.get();if(data.motion()==null)return 0;
        var rotation=new Quaternionf().rotateY((float)Math.toRadians(-missile.getYRot()))
                .rotateX((float)Math.toRadians(missile.getXRot()+90));
        var position=missile.position();var velocity=missile.getDeltaMovement();
        int segments=Math.max(2,Math.min(12,(int)Math.ceil(velocity.length()))),used=0;
        Vector3f port=(missile.dogEgg()?new Vector3f():new Vector3f(data.nozzles().get(0))).rotate(rotation);
        // Main nozzle fills the swept interval; the four boosters remain attached to their own ports.
        for(int segment=0;segment<segments&&used<budget;segment++){
            double offset=segment/(double)segments;
            missile.level().addParticle(ParticleTypes.LARGE_SMOKE,position.x-velocity.x*offset+port.x,
                    position.y-velocity.y*offset+port.y,position.z-velocity.z*offset+port.z,0,.025,0);used++;
        }
        for(int i=1;!missile.dogEgg()&&i<data.nozzles().size()&&used<budget;i++){
            port.set(data.nozzles().get(i)).rotate(rotation);
            missile.level().addParticle(ParticleTypes.LARGE_SMOKE,position.x+port.x,position.y+port.y,position.z+port.z,0,.025,0);used++;
        }
        return used;
    }
}
