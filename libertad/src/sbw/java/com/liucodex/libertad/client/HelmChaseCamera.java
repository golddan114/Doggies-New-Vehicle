package com.liucodex.libertad.client;

import com.liucodex.libertad.ship.LibertadEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.function.BiFunction;

/** SBW calls this offset hook after vanilla has positioned the chase camera and before its solid-block zoom limit. */
public final class HelmChaseCamera {
    private HelmChaseCamera() {}

    public static Vec3 limit(LibertadEntity ship, Vec3 offset) {
        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK || mc.player == null
                || camera.getEntity() != mc.player || mc.player.getVehicle() != ship || ship.getSeatIndex(mc.player) != 0
                || offset.x <= 0 || camera.getLookVector().y() <= 0) return offset;

        return clip(camera, offset, (start, end) -> {
            var hit = ship.level().clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.WATER, ship) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter level, BlockPos pos) {
                    return Shapes.empty();
                }
            });
            return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        });
    }

    static Vec3 clip(Camera camera, Vec3 offset, BiFunction<Vec3, Vec3, Vec3> waterHit) {
        Vec3 look = new Vec3(camera.getLookVector());
        // Reserve vertical clearance for the near plane along the whole elevated chase ray.
        Vec3 start = camera.getPosition().add(new Vec3(camera.getUpVector()).scale(offset.y))
                .add(new Vec3(camera.getLeftVector()).scale(offset.z)).add(0, -.35, 0);
        Vec3 end = start.subtract(look.scale(offset.x));
        Vec3 hit = waterHit.apply(start, end);
        return hit.equals(end) ? offset : new Vec3(Math.min(offset.x, start.distanceTo(hit)), offset.y, offset.z);
    }
}
