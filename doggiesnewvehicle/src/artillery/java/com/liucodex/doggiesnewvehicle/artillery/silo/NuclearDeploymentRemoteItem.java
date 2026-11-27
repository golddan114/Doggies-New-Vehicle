package com.liucodex.doggiesnewvehicle.artillery.silo;

import com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Pairing is a reference to one native box, never a permission grant or a second deployment state. */
public final class NuclearDeploymentRemoteItem extends Item {
    public static final int RANGE=32;
    private static final String BINDING="DnvNuclearDeploymentRemote";
    private static final String BOX_ID="DnvNuclearDeploymentId";

    public NuclearDeploymentRemoteItem(Properties properties){super(properties);}

    /** Called by the same native-box interaction event that handles the crowbar. */
    static void bind(ServerLevel level,BlockPos pos,ContainerBlockEntity box,ServerPlayer player,ItemStack held){
        if(!operator(level,player)||!level.hasChunkAt(pos)||level.getBlockEntity(pos)!=box
                ||!NuclearContainerDeployment.isSiloType(box.entityType)
                ||player.distanceToSqr(Vec3.atCenterOf(pos))>64
                ||!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,held)){
            message(player,"denied");return;
        }
        if(box.entityTag!=null&&box.entityTag.getCompound("NuclearSilo").getBoolean("Unfolding")){
            message(player,"working");return;
        }
        CompoundTag identity=box.getPersistentData();
        UUID id=identity.hasUUID(BOX_ID)?identity.getUUID(BOX_ID):UUID.randomUUID();
        identity.putUUID(BOX_ID,id);box.setChanged();
        var binding=new CompoundTag();
        binding.putInt("Schema",1);binding.putUUID("Target",id);binding.putUUID("Actor",player.getUUID());
        binding.putString("Dimension",level.dimension().location().toString());binding.putLong("Position",pos.asLong());
        writeBinding(held,binding);
        message(player,"bound",pos.getX(),pos.getY(),pos.getZ(),RANGE);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level world,Player player,InteractionHand hand){
        ItemStack held=player.getItemInHand(hand);
        if(world instanceof ServerLevel level&&player instanceof ServerPlayer serverPlayer){
            if(player.isShiftKeyDown()){
                writeBinding(held,null);message(serverPlayer,"cleared");
            }else if(!player.getCooldowns().isOnCooldown(this)){
                activate(level,serverPlayer,held);
                player.getCooldowns().addCooldown(this,10);
            }
        }
        return InteractionResultHolder.sidedSuccess(held,world.isClientSide);
    }

    private static void activate(ServerLevel level,ServerPlayer player,ItemStack held){
        CompoundTag binding=readBinding(held);
        if(binding==null){message(player,"unbound");return;}
        if(!operator(level,player)||!binding.getUUID("Actor").equals(player.getUUID())){
            message(player,"denied");return;
        }
        if(!binding.getString("Dimension").equals(level.dimension().location().toString())){
            message(player,"dimension");return;
        }
        BlockPos pos=BlockPos.of(binding.getLong("Position"));
        if(player.distanceToSqr(Vec3.atCenterOf(pos))>RANGE*RANGE){message(player,"range",RANGE);return;}
        if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)){
            message(player,"missing");return;
        }
        // Never load a remote chunk just to resolve a stale binding.
        if(!level.hasChunkAt(pos)){message(player,"unloaded");return;}
        if(!(level.getBlockEntity(pos) instanceof ContainerBlockEntity box)
                ||!NuclearContainerDeployment.isSiloType(box.entityType)
                ||!box.getPersistentData().hasUUID(BOX_ID)
                ||!box.getPersistentData().getUUID(BOX_ID).equals(binding.getUUID("Target"))){
            message(player,"missing");return;
        }
        if(!level.mayInteract(player,pos)||!player.mayUseItemAt(pos,Direction.UP,held)){
            message(player,"denied");return;
        }
        // The original owner still validates inventory owner, lease, blocks, protection and living obstructions.
        NuclearContainerDeployment.begin(level,pos,box,player);
    }

    private static boolean operator(ServerLevel level,ServerPlayer player){
        return player.level()==level&&player.isAlive()&&!player.isSpectator()&&player.getAbilities().mayBuild;
    }
    private static CompoundTag readBinding(ItemStack stack){
        CompoundTag data=stack.getTag();
        if(data==null||!data.contains(BINDING,Tag.TAG_COMPOUND))return null;
        CompoundTag binding=data.getCompound(BINDING);
        if(binding.getInt("Schema")!=1||!binding.hasUUID("Target")||!binding.hasUUID("Actor")
                ||!binding.contains("Position",Tag.TAG_LONG)||binding.getString("Dimension").isBlank()
                ||binding.getString("Dimension").length()>256)return null;
        return binding;
    }
    private static void writeBinding(ItemStack stack,CompoundTag binding){
        CompoundTag original=stack.getTag();
        CompoundTag data=original==null?new CompoundTag():original.copy();
        if(binding==null)data.remove(BINDING);else data.put(BINDING,binding);
        stack.setTag(data);
    }
    private static void message(ServerPlayer player,String key,Object... arguments){
        player.displayClientMessage(Component.translatable("message.doggiesnewvehicle.nuclear.remote_"+key,arguments),true);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag){
        tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.nuclear_deployment_remote",RANGE));
        CompoundTag binding=readBinding(stack);
        if(binding!=null){BlockPos pos=BlockPos.of(binding.getLong("Position"));
            tooltip.add(Component.translatable("tooltip.doggiesnewvehicle.nuclear_remote_bound",
                    binding.getString("Dimension"),pos.getX(),pos.getY(),pos.getZ()));}
    }
}
