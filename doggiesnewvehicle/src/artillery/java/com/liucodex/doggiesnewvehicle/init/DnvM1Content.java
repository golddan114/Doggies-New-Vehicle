package com.liucodex.doggiesnewvehicle.init;

import com.liucodex.doggiesnewvehicle.entity.DnvM1YunlongEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Independent registration so the exact production host can also run in the isolated server harness. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle")
public final class DnvM1Content {
  public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "doggievehicle");
  public static final RegistryObject<EntityType<DnvM1YunlongEntity>> M1 = TYPES.register("m1_yunlong", () ->
      EntityType.Builder.<DnvM1YunlongEntity>of(DnvM1YunlongEntity::new, MobCategory.MISC)
          .sized(com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile.M1.width(),
              com.liucodex.doggiesnewvehicle.entity.DnvDragonTurretProfile.M1.height())
          .clientTrackingRange(12).updateInterval(2).build(DnvM1YunlongEntity.TYPE_ID));

  private DnvM1Content() {}
  @SubscribeEvent public static void register(RegisterCommandsEvent event) { register(event.getDispatcher()); }
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("dnv").then(Commands.literal("m1_yunlong")
        .requires(source -> source.hasPermission(2))
        .then(Commands.literal("create").then(Commands.argument("position", BlockPosArgument.blockPos()).executes(context -> {
          var player = context.getSource().getPlayerOrException();
          var level = player.serverLevel();
          BlockPos pos = BlockPosArgument.getBlockPos(context, "position");
          if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)
              || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)
              || player.distanceToSqr(pos.getX() + .5, pos.getY(), pos.getZ() + .5) > 64) return 0;
          var entity = M1.get().create(level);
          if (entity == null || !entity.initializeOwner(player.getUUID())) return 0;
          entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
          if (!level.addFreshEntity(entity)) return 0;
          player.sendSystemMessage(Component.translatable("message.doggiesnewvehicle.m1_command.deployed",
              entity.getUUID().toString(), Component.translatable(
                  "message.doggiesnewvehicle.m1_command.readiness." + entity.fireReadinessReason())));
          return 1;
        })))));
  }
}
