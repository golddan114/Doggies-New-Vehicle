package com.liucodex.doggiesnewvehicle.artillery.client;

import com.liucodex.doggiesnewvehicle.DoggiesNewVehicle;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtilleryEntities;
import com.liucodex.doggiesnewvehicle.client.EndfieldSniperTurretRenderer;
import com.liucodex.doggiesnewvehicle.client.JingzheSoundManager;
import com.liucodex.doggiesnewvehicle.client.JingzheTurretRenderer;
import com.liucodex.doggiesnewvehicle.client.StormBulletRenderer;
import com.liucodex.doggiesnewvehicle.client.StormTurretRenderer;
import com.liucodex.doggiesnewvehicle.client.mesh.TurretMeshReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client registrations and tick work owned by artillery. */
public final class DnvArtilleryClientEvents {
    private DnvArtilleryClientEvents() { }

    @Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() { }

        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(com.liucodex.doggiesnewvehicle.artillery.walker.HexbotContent.HEXBOT.get(),
                    com.liucodex.doggiesnewvehicle.client.walker.HexbotRenderer::new);
            com.liucodex.doggiesnewvehicle.artillery.DnvDragonTurrets.TYPES.values().forEach(type->
                event.registerEntityRenderer(type.get(),com.liucodex.doggiesnewvehicle.client.DnvDragonTurretRenderer::new));
            net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                    com.liucodex.doggiesnewvehicle.artillery.silo.NuclearResidualFire.FIRE.get(),net.minecraft.client.renderer.RenderType.cutout());
            event.registerEntityRenderer(DnvArtilleryEntities.NUCLEAR_SILO.get(),com.liucodex.doggiesnewvehicle.client.NuclearRenderer.Silo::new);
            event.registerEntityRenderer(DnvArtilleryEntities.NUCLEAR_MISSILE.get(),com.liucodex.doggiesnewvehicle.client.NuclearRenderer.Missile::new);
            event.registerEntityRenderer(DnvArtilleryEntities.DOGEGG_SILO.get(),com.liucodex.doggiesnewvehicle.client.NuclearRenderer.Silo::new);
            event.registerEntityRenderer(DnvArtilleryEntities.DOGEGG_MISSILE.get(),com.liucodex.doggiesnewvehicle.client.NuclearRenderer.Missile::new);
            event.registerEntityRenderer(DnvArtilleryEntities.VACUUM_EFFECT.get(),com.liucodex.doggiesnewvehicle.client.VacuumEffectRenderer::new);
            event.registerEntityRenderer(DnvArtilleryEntities.CHANGFENG.get(),
                    EndfieldSniperTurretRenderer::new);
            event.registerEntityRenderer(DnvArtilleryEntities.STORM.get(),
                    StormTurretRenderer::new);
            event.registerEntityRenderer(DnvArtilleryEntities.JINGZHE.get(),
                    JingzheTurretRenderer::new);
            event.registerEntityRenderer(DnvArtilleryEntities.STORM_BULLET.get(),
                    StormBulletRenderer::new);
        }

        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new com.liucodex.doggiesnewvehicle.client.walker.HexbotModel());
            event.registerReloadListener(new TurretMeshReloadListener());
        }
    }

    @Mod.EventBusSubscriber(modid = DoggiesNewVehicle.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() { }

        @SubscribeEvent public static void acceptedFire(com.atsuishio.superbwarfare.api.event.ClientVehicleFireEvent event){
            var client=net.minecraft.client.Minecraft.getInstance();
            if(event.getVehicle() instanceof com.liucodex.doggiesnewvehicle.api.combat.DnvNativeShotAdmission host
                && host.deferNativeClientSound()
                && event.getShooter()==client.player && client.player!=null
                && client.player.getVehicle()==event.getVehicle()
                && (client.options.getCameraType().isFirstPerson()
                    || com.atsuishio.superbwarfare.event.ClientEventHandler.zoomVehicle)) {
                // Use the accepted gun, not the selection that may have changed during transit.
                var name=event.getWeaponName();
                if(name==null||name.isEmpty())return;
                var gun=event.getVehicle().getGunData(name);
                if(gun==null||client.level==null)return;
                var sound=gun.get(com.atsuishio.superbwarfare.data.gun.GunProp.SOUND_INFO).getFire1P();
                if(sound==null)return;
                // Remote crew keeps its physical body at the station. First-person audio
                // belongs at the current listener, while native 3P remains at the accepted muzzle.
                var at=client.gameRenderer.getMainCamera().getPosition();
                client.level.playLocalSound(at.x,at.y,at.z,sound,net.minecraft.sounds.SoundSource.PLAYERS,1,1,false);
            }
        }

        @SubscribeEvent public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event){
            var data=com.liucodex.doggiesnewvehicle.artillery.silo.NuclearContainerData.read(event.getItemStack());
            if(data.getString("EntityType").equals(net.minecraft.world.entity.EntityType.getKey(DnvArtilleryEntities.NUCLEAR_SILO.get()).toString()))
                event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.doggiesnewvehicle.nuclear_silo_deployer"));
            if(data.getString("EntityType").equals(net.minecraft.world.entity.EntityType.getKey(DnvArtilleryEntities.DOGEGG_SILO.get()).toString()))
                event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.doggiesnewvehicle.dogegg_silo_deployer"));
        }

        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) JingzheSoundManager.tick();
        }
    }
}
