package com.liucodex.carriercube;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=CubePrototype.ID)
final class CubeFleet extends SavedData {
    private final Set<UUID> carriers=new HashSet<>();
    // Cross-dimension notices live in the overworld's instance, including when either entity is unloaded.
    private final Map<UUID,UUID> revoked=new HashMap<>(), losses=new HashMap<>();
    static CubeFleet in(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(CubeFleet::load,CubeFleet::new,"dnvcarriercube_fleet");
    }
    static CubeFleet notices(ServerLevel world){return in(world.getServer().overworld());}
    boolean canUnpack(CompoundTag entity) {
        return carriers.size()<2 && (entity==null || !entity.hasUUID("UUID") || !carriers.contains(entity.getUUID("UUID")));
    }
    boolean admits(UUID id){return carriers.contains(id) || carriers.size()<2;}
    void register(UUID id){if(carriers.add(id))setDirty();}
    void revoke(UUID plane,UUID carrier){revoked.put(plane,carrier);setDirty();}
    boolean consume(UUID plane,UUID carrier){
        if(!revoked.remove(plane,carrier))return false;
        setDirty();return true;
    }
    void lost(UUID plane,UUID ship){if(ship!=null){losses.put(plane,ship);setDirty();}}
    boolean consumeLoss(UUID plane,UUID ship){
        if(!losses.remove(plane,ship))return false;
        setDirty();return true;
    }
    static CubeFleet load(CompoundTag nbt){
        var result=new CubeFleet();
        for(var tag:nbt.getList("ships",Tag.TAG_COMPOUND))result.carriers.add(((CompoundTag)tag).getUUID("id"));
        readPairs(nbt,"revoked",result.revoked);readPairs(nbt,"losses",result.losses);
        return result;
    }
    private static void readPairs(CompoundTag tag,String key,Map<UUID,UUID> pairs){
        for(var entry:tag.getList(key,Tag.TAG_COMPOUND)){
            var row=(CompoundTag)entry;pairs.put(row.getUUID("plane"),row.getUUID("ship"));
        }
    }
    private static ListTag pairs(Map<UUID,UUID> pairs){
        var list=new ListTag();
        pairs.forEach((plane,ship)->{var row=new CompoundTag();row.putUUID("plane",plane);row.putUUID("ship",ship);list.add(row);});
        return list;
    }
    @Override public CompoundTag save(CompoundTag tag){
        var ships=new ListTag();
        for(var id:carriers){var row=new CompoundTag();row.putUUID("id",id);ships.add(row);}
        tag.put("ships",ships);tag.put("revoked",pairs(revoked));tag.put("losses",pairs(losses));return tag;
    }
    @SubscribeEvent public static void entering(EntityJoinLevelEvent event){
        if(event.getEntity() instanceof CubeVehicle v && v.carrier() && event.getLevel() instanceof ServerLevel level
                && !event.loadedFromDisk() && !in(level).admits(v.getUUID()))event.setCanceled(true);
    }
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent event){
        if(event.getEntity() instanceof CubeVehicle v && v.carrier() && v.level() instanceof ServerLevel src){
            var dest=src.getServer().getLevel(event.getDimension());
            if(dest!=null && !in(dest).admits(v.getUUID()))event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void leaving(EntityLeaveLevelEvent event){
        if(event.getEntity() instanceof CubeVehicle v && v.carrier() && event.getLevel() instanceof ServerLevel world){
            var reason=v.getRemovalReason();
            if(reason!=null && (reason.shouldDestroy() || reason==RemovalReason.CHANGED_DIMENSION)){
                var fleet=in(world);if(fleet.carriers.remove(v.getUUID()))fleet.setDirty();
            }
        }
    }
}
