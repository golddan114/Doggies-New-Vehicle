package com.liucodex.carriercube;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

final class CubeRadarIcons {
    private static final Map<String,ItemStack> ICONS=new LinkedHashMap<>();
    static {
        add("minecraft:ancient_city","echo_shard");
        add("minecraft:bastion_remnant","gilded_blackstone");
        add("minecraft:buried_treasure","heart_of_the_sea");
        add("minecraft:desert_pyramid","gold_ingot");
        add("minecraft:end_city","shulker_shell");
        add("minecraft:fortress","blaze_rod");
        add("minecraft:igloo","snowball");
        add("minecraft:jungle_pyramid","tripwire_hook");
        add("minecraft:mansion","totem_of_undying");
        add("minecraft:mineshaft","iron_pickaxe");
        add("minecraft:mineshaft_mesa","golden_pickaxe");
        add("minecraft:monument","prismarine_shard");
        add("minecraft:nether_fossil","bone");
        add("minecraft:ocean_ruin_cold","prismarine_crystals");
        add("minecraft:ocean_ruin_warm","nautilus_shell");
        add("minecraft:pillager_outpost","crossbow");
        add("minecraft:shipwreck","oak_boat");
        add("minecraft:shipwreck_beached","spruce_boat");
        add("minecraft:stronghold","stone_bricks");
        add("minecraft:swamp_hut","cauldron");
        add("minecraft:trail_ruins","brush");
        add("minecraft:ruined_portal","obsidian");
        add("minecraft:ruined_portal_desert","sandstone");
        add("minecraft:ruined_portal_jungle","jungle_leaves");
        add("minecraft:ruined_portal_mountain","stone");
        add("minecraft:ruined_portal_nether","netherrack");
        add("minecraft:ruined_portal_ocean","kelp");
        add("minecraft:ruined_portal_swamp","lily_pad");
        add("minecraft:village_desert","cactus");
        add("minecraft:village_plains","bell");
        add("minecraft:village_savanna","acacia_sapling");
        add("minecraft:village_snowy","snow_block");
        add("minecraft:village_taiga","spruce_sapling");
        add("dnvcarriercube:slime_chunk","slime_ball");
        add("dnvcarriercube:portal_room","ender_eye");
    }
    private static void add(String id,String item) {ICONS.put(id,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft",item))));}
    static ItemStack icon(String id){return ICONS.getOrDefault(id,Items.MAP.getDefaultInstance());}
    static Component name(String id) {return ICONS.containsKey(id)?Component.translatable("radar.dnvcarriercube."+id.substring(id.indexOf(':')+1)):Component.literal(id);}
}
