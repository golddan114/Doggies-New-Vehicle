package com.liucodex.doggiesnewvehicle.artillery;

import com.atsuishio.superbwarfare.api.event.RegisterContainersEvent;
import com.atsuishio.superbwarfare.item.container.ContainerBlockItem;
import com.liucodex.doggiesnewvehicle.armored.ApocalypseContent;
import com.liucodex.doggiesnewvehicle.armored.BlizzardContent;
import com.liucodex.doggiesnewvehicle.armored.Project612Content;
import com.liucodex.doggiesnewvehicle.init.DnvM1Content;
import com.liucodex.doggiesnewvehicle.init.ModCreativeTabs;
import net.minecraftforge.eventbus.api.IEventBus;

/** Installs player artillery and the heavy tank through the same native SBW container boundary. */
public final class DnvArtilleryContent {
    private DnvArtilleryContent() { }

    public static void register(IEventBus modBus) {
        DnvDragonTurrets.initialize();
        com.liucodex.doggiesnewvehicle.artillery.walker.HexbotContent.TYPES.register(modBus);
        ModCreativeTabs.installArtilleryItems((category, output) -> {
            switch (category) {
                case VEHICLES -> {
                    output.accept(ContainerBlockItem.createInstance(DnvArtilleryEntities.CHANGFENG.get()));
                    output.accept(ContainerBlockItem.createInstance(DnvArtilleryEntities.STORM.get()));
                    output.accept(ContainerBlockItem.createInstance(DnvArtilleryEntities.JINGZHE.get()));
                    DnvDragonTurrets.TYPES.values().forEach(type -> output.accept(ContainerBlockItem.createInstance(type.get())));
                    output.accept(ContainerBlockItem.createInstance(DnvM1Content.M1.get()));
                    output.accept(ContainerBlockItem.createInstance(ApocalypseContent.TANK.get()));
                    output.accept(ContainerBlockItem.createInstance(BlizzardContent.TANK.get()));
                    output.accept(ContainerBlockItem.createInstance(Project612Content.TANK.get()));
                    output.accept(ContainerBlockItem.createInstance(com.liucodex.doggiesnewvehicle.artillery.walker.HexbotContent.HEXBOT.get()));
                }
                case DEPLOYERS -> {
                    output.accept(DnvArtilleryItems.NUCLEAR_SILO_DEPLOYER.get());
                    output.accept(DnvArtilleryItems.DOGEGG_SILO_DEPLOYER.get());
                    output.accept(DnvArtilleryItems.APOCALYPSE_AI_DEPLOYER.get());
                }
                case BLUEPRINTS -> {
                    output.accept(DnvArtilleryItems.CHANGFENG_BLUEPRINT.get());
                    output.accept(DnvArtilleryItems.STORM_BLUEPRINT.get());
                    output.accept(DnvArtilleryItems.JINGZHE_BLUEPRINT.get());
                    DnvDragonTurrets.BLUEPRINTS.values().forEach(item -> output.accept(item.get()));
                    output.accept(DnvArtilleryItems.M1_BLUEPRINT.get());
                    output.accept(DnvArtilleryItems.APOCALYPSE_BLUEPRINT.get());
                    output.accept(DnvArtilleryItems.BLIZZARD_BLUEPRINT.get());
                }
                case AMMUNITION -> {
                    output.accept(DnvArtilleryItems.CRYO_MISSILE.get());
                    output.accept(DnvArtilleryItems.NUCLEAR_MISSILE.get());
                    output.accept(DnvArtilleryItems.DOGEGG_MISSILE.get());
                }
                case MATERIALS -> {
                    output.accept(DnvArtilleryItems.EXPLOSIVE_CORE.get());
                    output.accept(DnvArtilleryItems.ABSORPTION_CORE.get());
                    output.accept(DnvArtilleryItems.WARHEAD_CORE.get());
                    output.accept(DnvArtilleryItems.DOG_CORE.get());
                    output.accept(DnvArtilleryItems.DOG_MILK_CARTON.get());
                    output.accept(DnvArtilleryItems.WILD_DOG_MILK.get());
                }
                case TOOLS -> {
                    output.accept(com.liucodex.doggiesnewvehicle.compat.DnvRemoteWeaponStationContent.ITEM);
                    output.accept(DnvArtilleryItems.NUCLEAR_DEPLOYMENT_REMOTE.get());
                    output.accept(DnvArtilleryItems.NUCLEAR_PREPARATION_REMOTE.get());
                }
                default -> { }
            }
        });
        DnvArtilleryEntities.TYPES.register(modBus);
        com.liucodex.doggiesnewvehicle.api.combat.DnvContainerTickBoundary.install((level,pos,state,box)->
                box instanceof com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity nativeBox
                        &&com.liucodex.doggiesnewvehicle.artillery.silo.NuclearContainerDeployment.tick(level,pos,state,nativeBox));
        DnvM1Content.TYPES.register(modBus);
        ApocalypseContent.TYPES.register(modBus);
        BlizzardContent.TYPES.register(modBus);
        Project612Content.TYPES.register(modBus);
        DnvArtilleryItems.ITEMS.register(modBus);
        DnvArtilleryBlueprintLoot.SERIALIZERS.register(modBus);
        DnvArtillerySounds.SOUND_EVENTS.register(modBus);
        com.liucodex.doggiesnewvehicle.artillery.silo.NuclearResidualFire.BLOCKS.register(modBus);
        com.liucodex.doggiesnewvehicle.artillery.silo.NuclearEffects.EFFECTS.register(modBus);
        com.liucodex.doggiesnewvehicle.artillery.silo.SiloChunkTickets.register(modBus);
        modBus.addListener(DnvArtilleryContent::registerContainers);
    }

    private static void registerContainers(RegisterContainersEvent event) {
        event.add(DnvArtilleryEntities.CHANGFENG);
        event.add(DnvArtilleryEntities.STORM);
        event.add(DnvArtilleryEntities.JINGZHE);
        event.add(DnvM1Content.M1);
        DnvDragonTurrets.TYPES.values().forEach(event::add);
        event.add(ApocalypseContent.TANK);
        event.add(ApocalypseContent.ALLIED_TANK);
        event.add(BlizzardContent.TANK);
        event.add(Project612Content.TANK);
        event.add(com.liucodex.doggiesnewvehicle.artillery.walker.HexbotContent.HEXBOT);
        event.add(DnvArtilleryEntities.NUCLEAR_SILO);
        event.add(DnvArtilleryEntities.DOGEGG_SILO);
    }
}
