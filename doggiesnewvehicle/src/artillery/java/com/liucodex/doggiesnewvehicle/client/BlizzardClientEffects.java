package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.armored.BlizzardRig;
import com.liucodex.doggiesnewvehicle.armored.DnvBlizzardEntity;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Loaded-entity and passenger effects: bounded state, emission budget, and no server/world writes. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", value = Dist.CLIENT)
public final class BlizzardClientEffects {
    private static final Map<Integer, Effect> ACTIVE = new LinkedHashMap<>();
    private static int clock;
    private static int particleCursor;
    private static final ClientEntityCycle<DnvBlizzardEntity> LOADED = new ClientEntityCycle<>();
    private static net.minecraft.client.multiplayer.ClientLevel discoveryWorld;
    private BlizzardClientEffects() { }
    static void track(DnvBlizzardEntity tank) {
        Effect existing = ACTIVE.get(tank.getId());
        if (existing != null && existing.tank != tank) {
            existing.stop(); ACTIVE.remove(tank.getId()); existing = null;
        }
        if (existing == null && ACTIVE.size() >= 32) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var farthest = ACTIVE.values().stream().max(java.util.Comparator.comparingDouble(e -> e.tank.distanceToSqr(player))).orElse(null);
                if (farthest != null && farthest.tank.distanceToSqr(player) > tank.distanceToSqr(player)) {
                    farthest.stop(); ACTIVE.remove(farthest.tank.getId());
                }
            }
        }
        if (existing == null && ACTIVE.size() < 32) {
            existing = new Effect(tank); ACTIVE.put(tank.getId(), existing);
        }
    }
    @SubscribeEvent public static void loaded(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof DnvBlizzardEntity tank) {
            if(discoveryWorld!=tank.level()){reset();discoveryWorld=(net.minecraft.client.multiplayer.ClientLevel)tank.level();}
            LOADED.put(tank.getId(),tank);track(tank);
        }
    }
    @SubscribeEvent public static void unloaded(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if(event.getLevel().isClientSide&&event.getEntity() instanceof DnvBlizzardEntity tank){
            LOADED.remove(tank.getId(),tank);
            var effect=ACTIVE.get(tank.getId());
            if(effect!=null&&effect.tank==tank){effect.stop();ACTIVE.remove(tank.getId());}
        }
    }
    public static void reset() {
        reload(); ACTIVE.clear(); LOADED.clear(); discoveryWorld = null;
    }
    public static void reload() { ACTIVE.values().forEach(Effect::stop); particleCursor = 0; }
    static void appendLights(java.util.List<DnvDynamicLightField.Source> sources,net.minecraft.world.level.Level world){
        for(var effect:ACTIVE.values()){
            var tank=effect.tank;
            if(tank.isRemoved()||tank.level()!=world||!tank.powered())continue;
            // Average the four existing rear exhaust anchors: one light per tail compartment.
            Vec3 point=Vec3.ZERO;int count=0;
            for(var anchor:BlizzardRig.INSTANCE.anchors.entrySet())if(anchor.getValue().role().equals("axial_exhaust")){
                point=point.add(tank.anchorPoint(anchor.getKey(),1));count++;
            }
            if(count>0){point=point.scale(1D/count);sources.add(new DnvDynamicLightField.Source(point.x,point.y,point.z,12));}
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { reset(); return; }
        if(discoveryWorld!=mc.level){reset();discoveryWorld=mc.level;}
        if(mc.player==null){reload();return;}
        if (mc.isPaused()) return;
        // Only event-registered Blizzard vehicles: at most 32 visits, including off-screen returns.
        LOADED.visit(32,tank->{
            if(tank.isRemoved()||tank.level()!=mc.level)LOADED.remove(tank.getId(),tank);
            else if(tank.distanceToSqr(mc.player)<=64*64)track(tank);
        });
        if (mc.player.getVehicle() instanceof DnvBlizzardEntity tank) track(tank);
        clock++;
        var iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            var tank = effect.tank;
            if (tank.isRemoved() || tank.level() != mc.level) {
                effect.stop(); iterator.remove(); continue;
            }
            if (tank.distanceToSqr(mc.player) > 64 * 64) { effect.stop(); continue; }
            effect.sounds(mc);
        }
        int budget = 12;
        Effect[] effects = ACTIVE.values().toArray(Effect[]::new);
        int start = effects.length == 0 ? 0 : Math.floorMod(particleCursor, effects.length);
        for (int i = 0; i < effects.length && budget > 0; i++) {
            int index = (start + i) % effects.length;
            var effect = effects[index];
            if (!effect.tank.presentationPowered() || !effect.tank.hovering() || effect.tank.distanceToSqr(mc.player) >= 24 * 24
                    || Math.floorMod(clock + effect.tank.getId(), 10) != 0) continue;
            budget -= effect.particles(budget);
            particleCursor = index + 1;
        }
    }
    private static final class Effect {
        private final DnvBlizzardEntity tank;
        private int gasCooldown;
        private boolean powered;
        private double thrust;
        private HoverSound idle, jet;
        private ChargeSound charge;
        private boolean charging;
        private Effect(DnvBlizzardEntity tank) { this.tank = tank; }
        private void stop() {
            stopHover();
            if (charge != null) Minecraft.getInstance().getSoundManager().stop(charge);
            charge = null; charging = false;
        }
        private void stopHover() {
            var manager = Minecraft.getInstance().getSoundManager();
            if (idle != null) manager.stop(idle);
            if (jet != null) manager.stop(jet);
            idle = jet = null;
        }
        private void sounds(Minecraft mc) {
            boolean now = tank.presentationPowered() && tank.hovering();
            double next = BlizzardVisualMotion.thrust(tank.presentationVelocity().horizontalDistance(), tank.presentationVelocity().y, true);
            if (gasCooldown > 0) gasCooldown--;
            if (now && gasCooldown == 0 && (!powered || next - thrust > .22)) {
                tank.level().playLocalSound(tank.getX(), tank.getY(), tank.getZ(), SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, .28F, 1.25F, false);
                gasCooldown = 30;
            }
            if (tank.presentationPowered()) {
                if (idle == null || idle.isStopped()) { idle = new HoverSound(tank, false); mc.getSoundManager().play(idle); }
                if (now) {
                    if (jet == null || jet.isStopped()) { jet = new HoverSound(tank, true); mc.getSoundManager().play(jet); }
                } else if (jet != null) { mc.getSoundManager().stop(jet); jet = null; }
            } else stopHover();
            boolean chargeNow = tank.mode2() && tank.coldPhase() == 1 && tank.isAlive() && !tank.isWreck();
            if (chargeNow && !charging) { charge = new ChargeSound(tank); mc.getSoundManager().play(charge); }
            if (!chargeNow && charge != null) { mc.getSoundManager().stop(charge); charge = null; }
            charging = chargeNow;
            powered = now; thrust = next;
        }
        private int particles(int budget) {
            int used = 0;
            double bob = 0;
            for (var anchor : BlizzardRig.INSTANCE.anchors.entrySet()) {
                if (!anchor.getValue().role().equals("lift_exhaust") || used >= budget) continue;
                Vec3 start = tank.anchorPoint(anchor.getKey(), 1).add(0, bob, 0);
                tank.level().addParticle(ParticleTypes.CLOUD, start.x, start.y - .12, start.z,
                        tank.presentationVelocity().x * .05, -.025, tank.presentationVelocity().z * .05);
                used++;
                // Short, loaded-only ground probe: no dust in mid-air or through floors.
                Vec3 end = start.add(0, -1.15, 0);
                if (used >= budget || !tank.level().hasChunkAt(net.minecraft.core.BlockPos.containing(end))) continue;
                var hit = tank.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tank));
                if (hit.getType() == HitResult.Type.MISS) continue;
                var state = tank.level().getBlockState(hit.getBlockPos());
                if (state.isAir()) continue;
                Vec3 point = hit.getLocation();
                tank.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        point.x, point.y + .05, point.z, (point.x - tank.getX()) * .035, .045, (point.z - tank.getZ()) * .035);
                used++;
            }
            return used;
        }
    }
    private static final class ChargeSound extends AbstractTickableSoundInstance {
        private final DnvBlizzardEntity tank;
        private ChargeSound(DnvBlizzardEntity tank) {
            super(DnvArtillerySounds.BLIZZARD_CHARGE.get(), SoundSource.BLOCKS, RandomSource.create());
            this.tank = tank; looping = false; volume = .9F; pitch = 1F;
            attenuation = SoundInstance.Attenuation.LINEAR; position();
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (tank.isRemoved() || !tank.isAlive() || tank.isWreck() || mc.level != tank.level() || mc.player == null
                    || !tank.mode2() || tank.coldPhase() != 1 || tank.distanceToSqr(mc.player) > 64 * 64) { stop(); return; }
            position();
        }
        private void position() {
            Vec3 muzzle = tank.getShootPos("ColdBeam", 1);
            x = muzzle.x; y = muzzle.y; z = muzzle.z;
        }
    }
    private static final class HoverSound extends AbstractTickableSoundInstance {
        private final DnvBlizzardEntity tank;
        private final boolean jet;
        private HoverSound(DnvBlizzardEntity tank, boolean jet) {
            super(jet ? DnvArtillerySounds.BLIZZARD_HOVER.get() : DnvArtillerySounds.BLIZZARD_IDLE.get(), SoundSource.BLOCKS, RandomSource.create());
            this.tank = tank; this.jet = jet; looping = true; delay = 0; pitch = 1F;
            attenuation = SoundInstance.Attenuation.LINEAR; position();
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            if (tank.isRemoved() || mc.level != tank.level() || mc.player == null || !tank.presentationPowered()
                    || jet && !tank.hovering() || tank.distanceToSqr(mc.player) > 64 * 64) { stop(); return; }
            position();
        }
        private void position() {
            x = tank.getX(); y = tank.getY(); z = tank.getZ();
            volume = jet ? (float) Math.min(1.15, .60 + tank.presentationVelocity().length() * 1.1) : .75F;
        }
    }
}
