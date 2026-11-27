package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.entity.JingzheTurretEntity;
import com.liucodex.doggiesnewvehicle.artillery.DnvArtillerySounds;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Keeps Jingzhe's idle loop attached to the entity instead of replaying packets. */
public final class JingzheSoundManager {
    private static final double DISCOVERY_RANGE = 20.0D;
    private static final double RETENTION_RANGE = 64.0D;
    private static final int DISCOVERY_INTERVAL = 20;
    private static final int DISCOVERY_PHASE = 15;
    private static final Map<Integer, IdleSound> IDLE_SOUNDS = new HashMap<>();

    private JingzheSoundManager() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stopAll(minecraft);
            return;
        }

        IDLE_SOUNDS.entrySet().removeIf(entry -> entry.getValue().isStopped());
        if (Math.floorMod(minecraft.player.tickCount, DISCOVERY_INTERVAL) != DISCOVERY_PHASE) {
            return;
        }
        for (JingzheTurretEntity turret : minecraft.level.getEntitiesOfClass(
                JingzheTurretEntity.class,
                minecraft.player.getBoundingBox().inflate(DISCOVERY_RANGE))) {
            if (!shouldIdle(turret) || IDLE_SOUNDS.containsKey(turret.getId())) {
                continue;
            }
            IdleSound sound = new IdleSound(turret);
            IDLE_SOUNDS.put(turret.getId(), sound);
            minecraft.getSoundManager().play(sound);
        }
    }

    private static boolean shouldIdle(JingzheTurretEntity turret) {
        return !turret.isRemoved()
                && turret.getHealth() > 0.0F
                && turret.isTurretActivated()
                && turret.hasOperationalPower()
                && turret.isCombatDeployed();
    }

    private static void stopAll(Minecraft minecraft) {
        for (IdleSound sound : IDLE_SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        IDLE_SOUNDS.clear();
    }

    private static final class IdleSound extends AbstractTickableSoundInstance {
        private final JingzheTurretEntity turret;

        private IdleSound(JingzheTurretEntity turret) {
            super(DnvArtillerySounds.JINGZHE_IDLE.get(), SoundSource.BLOCKS, RandomSource.create());
            this.turret = turret;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.65F;
            this.pitch = 1.0F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            updatePosition();
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (!shouldIdle(turret)
                    || minecraft.player == null
                    || turret.distanceToSqr(minecraft.player)
                            > RETENTION_RANGE * RETENTION_RANGE) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            this.x = turret.getX();
            this.y = turret.getY();
            this.z = turret.getZ();
        }
    }
}
