package com.liucodex.doggiesnewvehicle.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class DnvMuzzleMixinApplicationTest {
  private static final String SERVICE = "com.liucodex.doggiesnewvehicle.compat.transform.ExactMuzzleMixinService";
  @TempDir Path temp;

  @Test void dragonSoundRedirectAppliesOnceAndKeepsTheNativeFirePacket() throws Exception {
    try(var engine=loader()) {
      byte[][] result=transform(engine,"event/ClientEventHandler","DragonClientPredictionMixin","CLIENT",false);
      var after=new ClassNode();new ClassReader(result[1]).accept(after,0);
      var shoot=after.methods.stream().filter(m->m.name.equals("clientShootVehicle")).findFirst().orElseThrow();
      var calls=Arrays.stream(shoot.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
      assertEquals(1,calls.stream().filter(c->c.name.equals("sendPacketToServer")).count());
      assertEquals(1,calls.stream().filter(c->c.name.contains("dnv$acceptedLocalSound")).count());
      assertEquals(0,calls.stream().filter(c->c.name.equals("playVehicleClientSounds")).count());
    }
  }

  @ParameterizedTest
  @CsvSource({"net/minecraft/server/level/ServerLevel,DnvServerLevelEntityManagerAccess,entityManager,dnv$getEntityManager",
      "net/minecraft/world/level/entity/PersistentEntitySectionManager,DnvPersistentEntitySectionsAccess,sectionStorage,dnv$getSectionStorage",
      "net/minecraft/world/level/entity/EntitySection,DnvEntitySectionEntitiesAccess,storage,dnv$getStorage",
      "net/minecraft/world/level/entity/EntitySectionStorage,DnvEntitySectionKeysAccess,sectionIds,dnv$getSectionIds"})
  void sectionPagerAccessorsApplyToTheActualMinecraftClasses(
      String target, String mixin, String field, String accessor) throws Exception {
    try (var loader = loader()) {
      byte[][] result = (byte[][]) loader.loadClass(SERVICE)
          .getMethod("transform", String.class, String.class, String.class, boolean.class)
          .invoke(null, target, mixin, "SERVER", false);
      assertFalse(Arrays.equals(result[0], result[1]));
      ClassNode after = new ClassNode();
      new ClassReader(result[1]).accept(after, 0);
      var method = after.methods.stream().filter(m -> m.name.equals(accessor)).findFirst().orElseThrow();
      assertEquals(1, Arrays.stream(method.instructions.toArray())
          .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
          .filter(read -> read.name.equals(field)).count());
    }
  }

  @ParameterizedTest @ValueSource(strings={"CLIENT","SERVER"})
  void compoundRaytracePatchesBothNativeHitMethods(String side) throws Exception {
    try(var loader=loader()){
      byte[][] result=(byte[][])loader.loadClass(SERVICE).getMethod("transform",String.class,String.class,String.class,boolean.class)
          .invoke(null,"net/minecraft/world/entity/projectile/ProjectileUtil","CompoundEntityRaytraceMixin",side,false);
      var after=new ClassNode();new ClassReader(result[1]).accept(after,0);
      assertEquals(2,after.methods.stream().filter(m->!m.name.contains("dnv$")).flatMap(m->Arrays.stream(m.instructions.toArray()))
          .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).filter(c->c.name.contains("dnv$")).count());
    }
  }

  @ParameterizedTest @ValueSource(strings={"CLIENT","SERVER"})
  void compoundCollisionPatchesActualMinecraftBoundaryOnBothSides(String side) throws Exception {
    try(var loader=loader()){
      byte[][] result=(byte[][])loader.loadClass(SERVICE).getMethod("transform",String.class,String.class,String.class,boolean.class)
          .invoke(null,"net/minecraft/world/entity/Entity","CompoundEntityCollisionMixin",side,false);
      assertFalse(Arrays.equals(result[0],result[1]));
      var after=new ClassNode();new ClassReader(result[1]).accept(after,0);
      assertEquals(1,after.methods.stream().filter(m->!m.name.contains("dnv$")).flatMap(m->Arrays.stream(m.instructions.toArray()))
          .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).filter(c->c.name.contains("dnv$compound")).count());
    }
  }

  @ParameterizedTest
  @CsvSource({"entity/vehicle/base/VehicleEntity,VehicleQueuedShotMixin,SERVER,3",
      "entity/projectile/ProjectileEntity,Dsx1TrailProjectileMixin,SERVER,2",
      "entity/projectile/SmallCannonShellEntity,SmallCannonImpactMixin,SERVER,3",
      "client/overlay/VehicleHudOverlay,BlizzardEnergyHudMixin,CLIENT,3",
      "client/overlay/VehicleCrosshairOverlay,BlizzardCrosshairMixin,CLIENT,10",
      "block/entity/ContainerBlockEntity$Companion,NuclearContainerTickMixin,SERVER,1",
      "entity/vehicle/base/AutoAimableEntity,NuclearThreatMixin,SERVER,1",
      "entity/vehicle/base/AutoAimableEntity,NuclearThreatMixin,CLIENT,1",
      "entity/vehicle/ai/TowerAI$TeamResolver,NuclearHostileMixin,SERVER,1",
      "entity/vehicle/ai/TowerAI$TeamResolver,NuclearHostileMixin,CLIENT,1",
      "entity/vehicle/base/VehicleEntity,VehicleEnergyBudgetMixin,SERVER,2",
      "capability/energy/VehicleEnergyStorage,VehicleEnergyInputMixin,SERVER,6",
      "entity/vehicle/damage/DamageModifier,DnvVehicleArmorMixin,SERVER,3",
      "entity/vehicle/damage/DamageModifier,DnvVehicleArmorMixin,CLIENT,3",
      "data/gun/GunData,ApocalypseAmmoSelectionMixin,SERVER,1",
      "data/gun/GunData,ApocalypseAmmoSelectionMixin,CLIENT,1",
      "entity/vehicle/utils/VehicleEngineUtils,ApocalypseAutonomousDriveMixin,SERVER,1",
      "entity/vehicle/utils/VehicleEngineUtils,ApocalypseAutonomousDriveMixin,CLIENT,1",
      "tools/SeekTool$Builder,ApocalypseSeekFilterMixin,SERVER,2",
      "tools/SeekTool$Builder,ApocalypseSeekFilterMixin,CLIENT,2",
      "item/gun/GunItem,GunShotAdmissionMixin,SERVER,5",
      "item/misc/ArtilleryIndicatorItem,ArtilleryIndicatorItemMixin,SERVER,2",
      "item/misc/ArtilleryIndicatorItem,ArtilleryIndicatorItemMixin,CLIENT,2",
      "network/message/send/ArtilleryIndicatorFireMessage,ArtilleryIndicatorFireMessageMixin,SERVER,1",
      "network/message/send/FireKeyMessage,DnvBlizzardFireReleaseMixin,SERVER,1",
      "event/GunEventHandler,M1ReloadSoundMixin,SERVER,1",
      "event/GunEventHandler,M1ReloadSoundMixin,CLIENT,1",
      "tools/VectorTool,HullTargetProjectionMixin,CLIENT,1",
      "tools/VectorTool,HullTargetProjectionMixin,SERVER,1",
      "tools/SeekTool,HullTargetSeekMixin,CLIENT,2",
      "tools/SeekTool,HullTargetSeekMixin,SERVER,2",
      "tools/SeekTool$Builder,HullTargetSightMixin,SERVER,1",
      "tools/SeekTool$Builder,HullTargetSightMixin,CLIENT,1",
      "entity/projectile/JavelinMissileEntity,HullTargetMissileMixin,SERVER,5",
      "entity/projectile/JavelinMissileEntity,HullTargetMissileMixin,CLIENT,5",
      "entity/projectile/MissileProjectile,DnvMissileChunkMixin,SERVER,2",
      "entity/projectile/MissileProjectile,DnvMissileChunkMixin,CLIENT,2",
      "entity/projectile/JavelinMissileEntity,DnvVlsFlightMixin,SERVER,9",
      "entity/projectile/JavelinMissileEntity,DnvVlsFlightMixin,CLIENT,9",
      "entity/vehicle/base/VehicleEntity,VehicleWreckLifetimeMixin,SERVER,1",
      "entity/vehicle/base/VehicleEntity,VehicleWreckLifetimeMixin,CLIENT,1",
      "client/overlay/VehicleTeamOverlay,VehicleWreckHealthOverlayMixin,CLIENT,2",
      "tools/CustomExplosion$Builder,ProjectileBlastEffectsMixin,CLIENT,3",
      "tools/CustomExplosion$Builder,ProjectileBlastEffectsMixin,SERVER,3",
      "tools/CustomExplosion,ExplosionCameraShakeMixin,CLIENT,2",
      "tools/CustomExplosion,ExplosionBlockDamageMixin,CLIENT,2",
      "tools/CustomExplosion,ExplosionBlockDamageMixin,SERVER,2",
      "entity/projectile/CannonShellEntity,ProjectileBlockDamageMixin,SERVER,1",
      "entity/projectile/SmallCannonShellEntity,ProjectileBlockDamageMixin,SERVER,1",
      "entity/projectile/MediumRocketEntity,ProjectileBlockDamageMixin,SERVER,1",
      "entity/projectile/FastThrowableProjectile,ProjectileBlockDamageMixin,SERVER,1",
      "entity/projectile/FastThrowableProjectile,DnvTurretShellLifecycleMixin,SERVER,6",
      "entity/projectile/CannonShellEntity,DnvCannonShellChunkMixin,SERVER,3",
      "entity/projectile/ProjectileEntity,DnvBulletStepMixin,SERVER,3",
      "entity/projectile/CannonShellEntity,DnvFastShellTailMixin,SERVER,1",
      "entity/projectile/SmallCannonShellEntity,DnvFastShellTailMixin,SERVER,1",
      "entity/projectile/SmallRocketEntity,DnvFastShellTailMixin,SERVER,1",
      "entity/projectile/RpgRocketStandardEntity,DnvFastShellTailMixin,SERVER,1",
      "entity/projectile/DestroyableProjectile,DnvFastShellTailMixin,SERVER,1",
      "entity/vehicle/utils/VehicleMotionUtils,VehicleBlockDamageMixin,SERVER,1",
      "tools/CustomExplosion,ExplosionCameraShakeMixin,SERVER,2",
      "tools/ParticleTool,ExplosionParticleShakeMixin,CLIENT,1",
      "tools/ParticleTool,ExplosionParticleShakeMixin,SERVER,1",
      "entity/vehicle/TurretWreckEntity,TurretWreckLifetimeMixin,SERVER,1",
      "entity/vehicle/TurretWreckEntity,TurretWreckLifetimeMixin,CLIENT,1"})
  void actualMixinPatchesEveryRequiredTarget(String target, String mixin, String side, int hooks) throws Exception {
    try (var loader = loader()) {
      byte[][] result = transform(loader, target, mixin, side, false);
      assertFalse(Arrays.equals(result[0], result[1]));
      ClassNode after = new ClassNode();
      new ClassReader(result[1]).accept(after, 0);
      long count = after.methods.stream().filter(method -> !method.name.contains("dnv$")).flatMap(method -> Arrays.stream(method.instructions.toArray()))
          .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).filter(call -> call.name.contains("dnv$")
              && !call.name.contains("dnv$beginReticle") && !call.name.contains("dnv$chargeColumn")).count();
      assertEquals(hooks, count);
      if (mixin.equals("BlizzardCrosshairMixin")) {
        ClassNode before = new ClassNode();new ClassReader(result[0]).accept(before,0);
        long returns=before.methods.stream().filter(m->m.name.equals("render"))
            .flatMap(m->Arrays.stream(m.instructions.toArray())).filter(i->i.getOpcode()==org.objectweb.asm.Opcodes.RETURN).count();
        var calls=after.methods.stream().filter(m->m.name.equals("render")).flatMap(m->Arrays.stream(m.instructions.toArray()))
            .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(1,calls.stream().filter(c->c.name.contains("dnv$beginReticle")).count());
        assertEquals(returns,calls.stream().filter(c->c.name.contains("dnv$chargeColumn")).count());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"CLIENT", "SERVER"})
  void retiredAc130MuzzleOverrideIsAbsentOnBothSides(String side) throws Exception {
    assertFalse(Files.exists(Path.of(
        "src/main/java/com/liucodex/doggiesnewvehicle/compat/mixin/VehicleMuzzleMixin.java")), side);
    String config = Files.readString(Path.of("src/main/resources/mixins.doggiesnewvehicle.json"));
    assertFalse(config.contains("\"VehicleMuzzleMixin\""), side);
    assertTrue(config.contains("\"VehicleQueuedShotMixin\""), side);
    assertTrue(config.contains("\"GunShotAdmissionMixin\""), side);
  }

  @Test void remoteBodySaveInjectsIntoTheRealVanillaEntitySaveMethod() throws Exception {
    try (var loader = loader()) {
      byte[][] result = (byte[][]) loader.loadClass(SERVICE)
          .getMethod("transform", String.class, String.class, String.class, boolean.class)
          .invoke(null, "net/minecraft/world/entity/Entity", "M1RemoteBodySaveMixin", "SERVER", false);
      ClassNode node = new ClassNode(); new ClassReader(result[1]).accept(node, 0);
      var save = node.methods.stream().filter(method -> method.name.equals("saveWithoutId")).findFirst().orElseThrow();
      assertEquals(1, Arrays.stream(save.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
          .map(MethodInsnNode.class::cast).filter(call -> call.name.contains("dnv$saveRemoteBody")).count());
      // Exact ForgeGradle 1.20.1 mapping, checked independently of the development method name.
      String mappings = Files.readString(Path.of("build/createSrgToMcp/output.srg"));
      assertTrue(mappings.contains("MD: net/minecraft/world/entity/Entity/m_20240_ (Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag; net/minecraft/world/entity/Entity/saveWithoutId"));
    }
  }
  @Test void environmentLightEntersTheRealNativeEngineAndPreservesItsEnabledSectionGuard()throws Exception{
    try(var engine=loader()){
      var result=(byte[][])engine.loadClass(SERVICE).getMethod("transform",String.class,String.class,String.class,boolean.class)
          .invoke(null,"net/minecraft/world/level/lighting/BlockLightEngine","BlizzardDynamicLightMixin","CLIENT",false);
      ClassNode after=new ClassNode();new ClassReader(result[1]).accept(after,0);
      var emission=after.methods.stream().filter(m->m.name.equals("getEmission")).findFirst().orElseThrow();
      var calls=Arrays.stream(emission.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
      assertEquals(1,calls.stream().filter(call->call.name.contains("dnv$sourceEmission")).count());
      assertEquals(1,calls.stream().filter(call->call.name.equals("lightOnInSection")).count());
      var redirect=after.methods.stream().filter(m->m.name.contains("dnv$sourceEmission")).findFirst().orElseThrow();
      assertEquals(1,Arrays.stream(redirect.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
          .map(MethodInsnNode.class::cast).filter(call->call.name.equals("getLightEmission")).count());
    }
  }

  @Test void directPercentInjectsOnceIntoEveryNativeLivingArmorReturnOnBothSides() throws Exception {
    for (String side : List.of("CLIENT", "SERVER")) try (var engine = loader()) {
      var result = (byte[][])engine.loadClass(SERVICE).getMethod("transform", String.class, String.class, String.class, boolean.class)
          .invoke(null, "net/minecraft/world/entity/LivingEntity", "DnvLivingPercentDamageMixin", side, false);
      ClassNode before = new ClassNode(), after = new ClassNode();
      new ClassReader(result[0]).accept(before, 0); new ClassReader(result[1]).accept(after, 0);
      var original = before.methods.stream().filter(m -> m.name.equals("getDamageAfterArmorAbsorb")).findFirst().orElseThrow();
      var transformed = after.methods.stream().filter(m -> m.name.equals(original.name) && m.desc.equals(original.desc)).findFirst().orElseThrow();
      long returns = Arrays.stream(original.instructions.toArray()).filter(i -> i.getOpcode() == org.objectweb.asm.Opcodes.FRETURN).count();
      assertTrue(returns > 0);
      assertEquals(returns, Arrays.stream(transformed.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
          .map(MethodInsnNode.class::cast).filter(call -> call.name.contains("dnv$percent")).count());
    }
  }

  @Test void incompatibleNativeEmissionEntryFailsRequiredInjection()throws Exception{
    try(var engine=loader()){
      var failure=assertThrows(InvocationTargetException.class,()->engine.loadClass(SERVICE)
          .getMethod("transform",String.class,String.class,String.class,boolean.class)
          .invoke(null,"net/minecraft/world/level/lighting/BlockLightEngine","BlizzardDynamicLightMixin","CLIENT",true));
      StringBuilder causes=new StringBuilder();
      for(Throwable cause=failure;cause!=null;cause=cause.getCause())causes.append(cause).append('\n');
      assertTrue(causes.toString().contains("dnv$sourceEmission"),causes::toString);
    }
  }

  @Test void retiredClientModelMixinsAreNotRegistered() throws Exception {
    String config = Files.readString(Path.of("src/main/resources/mixins.doggiesnewvehicle.json"));
    assertFalse(config.contains("VehicleMuzzleModelMixin"));
    assertFalse(config.contains("VehicleMuzzleModelBakeMixin"));
    assertFalse(config.contains("VehicleMuzzleMixin"));
    String platformConfig = Files.readString(Path.of("src/platform/resources/mixins.dnvplatform.json"));
    assertTrue(platformConfig.contains("TacticalMapScreenMixin"));
  }

  @Test void hullExposureInjectsIntoTheActualVanillaSamplerOnBothSides() throws Exception {
    for (String side : List.of("CLIENT", "SERVER")) try (var loader = loader()) {
      byte[][] result = (byte[][]) loader.loadClass(SERVICE)
          .getMethod("transform", String.class, String.class, String.class, boolean.class)
          .invoke(null, "net/minecraft/world/level/Explosion", "HullExplosionExposureMixin", side, false);
      ClassNode after = new ClassNode(); new ClassReader(result[1]).accept(after, 0);
      var sampler = after.methods.stream().filter(m -> m.name.equals("getSeenPercent")).findFirst().orElseThrow();
      assertEquals(1, Arrays.stream(sampler.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
          .map(MethodInsnNode.class::cast).filter(call -> call.name.contains("dnv$hullExposure")).count());
    }
  }

  @Test void incompatibleAdmissionParameterFailsRequiredInjection() throws Exception {
    try (var loader = loader()) {
      var failure = assertThrows(InvocationTargetException.class,
          () -> transform(loader, "item/gun/GunItem", "GunShotAdmissionMixin", "SERVER", true));
      StringBuilder causes = new StringBuilder();
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) causes.append(cause).append('\n');
      assertTrue(causes.toString().contains("dnv$worldAdmission"), causes::toString);
    }
  }

  static byte[][] transform(ClassLoader loader, String target, String mixin, String side, boolean invalid) throws Exception {
    return (byte[][]) loader.loadClass(SERVICE).getMethod("transform", String.class, String.class, String.class, boolean.class)
        .invoke(null, "com/atsuishio/superbwarfare/" + target, mixin, side, invalid);
  }

  URLClassLoader loader() throws Exception {
    Path mixin = Path.of(org.spongepowered.asm.mixin.Mixin.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path service = temp.resolve(UUID.randomUUID() + ".txt");
    Files.writeString(service, SERVICE + "\n");
    return new EngineLoader(new URL[] {mixin.toUri().toURL(), getClass().getProtectionDomain().getCodeSource().getLocation()}, service.toUri().toURL());
  }

  private static final class EngineLoader extends URLClassLoader {
    private final URL service;
    EngineLoader(URL[] urls, URL service) { super(urls, DnvMuzzleMixinApplicationTest.class.getClassLoader()); this.service = service; }
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.startsWith("org.spongepowered.") && !name.equals(SERVICE) && !name.startsWith(SERVICE + "$")) return super.loadClass(name, resolve);
      synchronized (getClassLoadingLock(name)) {
        Class<?> type = findLoadedClass(name);
        if (type == null) type = findClass(name);
        if (resolve) resolveClass(type);
        return type;
      }
    }
    @Override public Enumeration<URL> getResources(String name) throws java.io.IOException {
      if (name.equals("META-INF/services/org.spongepowered.asm.service.IMixinServiceBootstrap")) return Collections.emptyEnumeration();
      if (name.equals("META-INF/services/org.spongepowered.asm.service.IMixinService") || name.equals("META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService")) return Collections.enumeration(List.of(service));
      return super.getResources(name);
    }
  }
}
