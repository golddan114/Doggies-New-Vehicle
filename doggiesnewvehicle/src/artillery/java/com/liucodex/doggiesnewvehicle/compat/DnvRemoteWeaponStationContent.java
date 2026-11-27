package com.liucodex.doggiesnewvehicle.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Minecraft station adapter. Forge registration is independent of SBW and of the connection owner. */
@Mod.EventBusSubscriber(modid = "doggiesnewvehicle", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DnvRemoteWeaponStationContent {
  public static final ResourceLocation ID = new ResourceLocation("doggiesnewvehicle", "remote_weapon_station");
  public static StationBlock BLOCK;
  public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
  public static BlockItem ITEM;

  private static BlockItem stationItem() { return new BlockItem(BLOCK, new Item.Properties().stacksTo(1)) {
    @Override protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
        ItemStack stack, BlockState state) {
      // Pick-block/creative/loot copies are content items, never carriers of a world station identity.
      return false;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.remote_weapon_station"));
      if (stack.getTag() != null && stack.getTag().contains(DnvM1RemoteCrew.BINDING))
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.remote_weapon_station.bound",
            DnvM1RemoteCrew.bindingDisplayName(stack.getTag().getCompound(DnvM1RemoteCrew.BINDING))));
    }
  }; }
  private static BlockEntityType<StationBlockEntity> type;

  private DnvRemoteWeaponStationContent() {}

  @SubscribeEvent public static void register(RegisterEvent event) {
    event.register(Registries.BLOCK, helper -> helper.register(ID, BLOCK = new StationBlock()));
    event.register(Registries.ITEM, helper -> helper.register(ID, ITEM = stationItem()));
    event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
      type = BlockEntityType.Builder.of(StationBlockEntity::new, BLOCK).build(null);
      helper.register(ID, type);
    });
  }


  public static final class StationBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 11, 15);
    private StationBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F).noOcclusion().pushReaction(PushReaction.BLOCK));
      registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
      return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StationBlockEntity(pos, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      if (placer instanceof ServerPlayer player) {
        var reason = DnvRemoteWeaponServices.create(player, pos);
        if (reason == com.liucodex.doggiesnewvehicle.core.DnvRemoteWeaponLinks.Reason.NONE)
          DnvM1RemoteCrew.placed(player, pos, stack);
        else player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.remote_station.reason." + reason), true);
      }
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit) {
      if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
        DnvM1RemoteCrew.enter(serverPlayer, pos);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
      if (!state.is(next.getBlock()) && level instanceof ServerLevel serverLevel
          && level.getBlockEntity(pos) instanceof StationBlockEntity anchor) {
        DnvRemoteWeaponServices.stationRemoved(serverLevel, anchor);
      }
      super.onRemove(state, level, pos, next, moving);
    }
  }

  /** Identity alone lives in ForgeData. Owner/link/revision remain exclusively in SavedData. No ticker. */
  public static final class StationBlockEntity extends BlockEntity {
    private StationBlockEntity(BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public void setRemoved() { invalidateRuntime(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { invalidateRuntime(); super.onChunkUnloaded(); }
    @Override public void load(CompoundTag tag) { invalidateRuntime(); super.load(tag); }
    private void invalidateRuntime() {
      if (level instanceof ServerLevel serverLevel) DnvRemoteWeaponServices.anchorUnavailable(serverLevel, this);
    }
  }
}
