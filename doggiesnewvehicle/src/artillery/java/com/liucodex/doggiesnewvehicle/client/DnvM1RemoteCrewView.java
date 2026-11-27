package com.liucodex.doggiesnewvehicle.client;

import com.liucodex.doggiesnewvehicle.armored.ApocalypseRig;
import com.liucodex.doggiesnewvehicle.armored.DnvApocalypseEntity;
import com.liucodex.doggiesnewvehicle.client.mixin.BlizzardCameraAccess;
import com.liucodex.doggiesnewvehicle.entity.DnvM1YunlongEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Entry selects native first-person vehicle view; SBW owns the camera, HUD, zoom and input. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", value = Dist.CLIENT)
public final class DnvM1RemoteCrewView {
  private static CameraType previous;
  private static ClientLevel level;
  private DnvM1RemoteCrewView() {}

  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    Minecraft client = Minecraft.getInstance();
    boolean occupied = DnvM1ClientBridge.isRemoteCrew(client.player);
    if (previous != null && (!occupied || level != client.level)) {
      client.options.setCameraType(previous);
      previous = null;
      level = null;
    }
    if (occupied && previous == null) {
      previous = client.options.getCameraType();
      level = client.level;
      client.options.setCameraType(CameraType.FIRST_PERSON);
    }
  }

  /** Native SBW returns null for the ordinary third-person seat path; anchor that fallback at the turret. */
  public static void afterSetup(net.minecraft.client.Camera camera, float partialTick) {
    var client = Minecraft.getInstance();
    if (mountedFirstPerson(client, camera, partialTick)) return;
    if (!DnvM1ClientBridge.isRemoteCrew(client.player) || camera.getEntity() != client.player
        || client.level == null || client.options.getCameraType().isFirstPerson()) return;
    boolean front = client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
    float yaw = client.player.getViewYRot(partialTick) + (front ? 180 : 0);
    float pitch = client.player.getViewXRot(partialTick) * (front ? -1 : 1);
    var rig = DnvM1ClientBridge.thirdPerson(client.player.getVehicle(), partialTick);
    var anchor = rig.position();
    var desired = thirdPersonPosition(anchor, yaw, pitch, rig.distance());
    var hit = client.level.clip(new net.minecraft.world.level.ClipContext(anchor, desired,
        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, client.player));
    if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
      var delta = desired.subtract(anchor).normalize();
      desired = hit.getLocation().subtract(delta.scale(.15));
    }
    var access = (com.liucodex.doggiesnewvehicle.client.mixin.BlizzardCameraAccess) camera;
    access.dnv$blizzardPosition(desired.x, desired.y, desired.z);
    access.dnv$blizzardRotation(yaw, pitch);
  }

  private static boolean mountedFirstPerson(Minecraft client, Camera camera, float partial) {
    var player = client.player;
    if (player == null || client.level == null || camera.getEntity() != player
        || !client.options.getCameraType().isFirstPerson()) return false;
    var vehicle = player.getVehicle();
    Vec3 desired;
    if (vehicle instanceof DnvM1YunlongEntity gun && gun.profile().seats > 0) {
      var profile = gun.profile();
      int seat = gun.getSeatIndex(player);
      if (seat < 0 || seat >= profile.seats) return false;
      desired = gun.getPosition(partial).add(profile.posed(profile.clearAdsPosition(),
          gun.getVisualAimYaw(partial), gun.getVisualAimElevation(partial)));
      if (seat == 0 && profile.model.startsWith("bofors_")
          && com.atsuishio.superbwarfare.event.ClientEventHandler.zoomVehicle) {
        var direction = gun.getShootVec(player, partial).normalize();
        ((BlizzardCameraAccess) camera).dnv$blizzardRotation((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)),
            (float) -Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1, 1))));
      }
    } else if (vehicle instanceof DnvApocalypseEntity tank && tank.getSeatIndex(player) == 0) {
      desired = ApocalypseRig.transformPoint(tank.groupTransform("Turret", partial), ApocalypseRig.INSTANCE.forwardCameraAnchor());
    } else {
      return false;
    }
    Vec3 origin = player.getEyePosition(partial);
    Vec3 eye = origin;
    if (client.level.hasChunksAt(BlockPos.containing(origin), BlockPos.containing(desired))) {
      var hit = client.level.clip(new ClipContext(origin, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
      eye = hit.getType() == HitResult.Type.MISS ? desired : stopBeforeWall(origin, desired, hit.getLocation());
    }
    ((BlizzardCameraAccess) camera).dnv$blizzardPosition(eye.x, eye.y, eye.z);
    return true;
  }

  static Vec3 stopBeforeWall(Vec3 origin, Vec3 desired, Vec3 hit) {
    var direction = desired.subtract(origin).normalize();
    return origin.add(direction.scale(Math.max(0, hit.subtract(origin).dot(direction) - .15)));
  }

  static net.minecraft.world.phys.Vec3 thirdPersonPosition(net.minecraft.world.phys.Vec3 anchor, float yaw, float pitch, double distance) {
    return anchor.subtract(net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw).normalize().scale(distance));
  }
}
