package com.liucodex.doggiesnewvehicle.entity;

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

/**
 * A normal Superb Warfare projectile with a Storm-specific renderer.
 *
 * <p>Keeping the projectile behaviour inherited preserves the base mod's hit,
 * damage and networking logic. The client renderer hides the round; the
 * turret retains its independent muzzle flash.</p>
 */
public final class StormBulletEntity extends ProjectileEntity implements IEntityAdditionalSpawnData {
    private float spawnGravity;

    public StormBulletEntity(EntityType<? extends StormBulletEntity> type, Level level) {
        super(type, level);
        setCustomGravity(0.0F);
    }

    @Override public void setCustomGravity(float gravity) {
        super.setCustomGravity(gravity);
        spawnGravity = gravity;
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    // Vanilla spawn motion clamps each component to 3.9; these rounds travel
    // at 36 blocks/tick. The first client tick must already have the real ray.
    @Override public void writeSpawnData(FriendlyByteBuf buffer) {
        var motion = getDeltaMovement();
        buffer.writeDouble(motion.x);
        buffer.writeDouble(motion.y);
        buffer.writeDouble(motion.z);
        buffer.writeFloat(spawnGravity);
        buffer.writeInt(getOwner() == null ? 0 : getOwner().getId());
    }

    @Override public void readSpawnData(FriendlyByteBuf buffer) {
        setDeltaMovement(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        setCustomGravity(buffer.readFloat());
        int owner = buffer.readInt();
        if (owner != 0) setOwner(level().getEntity(owner));
    }
}
