package com.liucodex.doggiesnewvehicle.artillery;

import com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretEntity;
import com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.RegistryObject;

/** Registers five new identities; the sixth author replaces the existing m1_yunlong. */
public final class DnvDragonTurrets {
    public static final Map<String,RegistryObject<EntityType<DnvDragonTurretEntity>>> TYPES=new LinkedHashMap<>();
    public static final Map<String,RegistryObject<Item>> BLUEPRINTS=new LinkedHashMap<>();
    static {
        for(var p:DnvDragonTurretProfile.ALL.values()) {
            if(p==DnvDragonTurretProfile.M1)continue;
            TYPES.put(p.entity,DnvArtilleryEntities.TYPES.register(p.entity,()->EntityType.Builder
                .<DnvDragonTurretEntity>of(DnvDragonTurretEntity::new,MobCategory.MISC)
                .sized(p.width(),p.height()).clientTrackingRange(12).updateInterval(1).fireImmune()
                .build("doggievehicle:"+p.entity)));
            BLUEPRINTS.put(p.entity,DnvArtilleryItems.ITEMS.register(p.entity+"_blueprint",()->
                new com.atsuishio.superbwarfare.item.material.BlueprintItem(Rarity.EPIC)));
        }
        for(var model:DnvDragonTurretProfile.MODELS)for(String role:new String[]{"fire_1p","fire_3p","fire_far","fire_veryfar","yaw","pitch","reload","select"}) {
            String key="dragon_"+model+"_"+role;
            DnvArtillerySounds.SOUND_EVENTS.register(key,()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("doggiesnewvehicle",key)));
        }
    }
    public static void initialize(){}
    private DnvDragonTurrets(){}
}
