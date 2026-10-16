package dev.mapselect.mixin;

import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block.TrainDoorBlock;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.mapselect.item.CounterfeitMasterKeyItem;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={SmallDoorBlock.class,TrainDoorBlock.class})
public abstract class CounterfeitMasterKeyDoorMixin {
    @Inject(method="onUse",at=@At("HEAD"),cancellable=true)
    private void gexpress$threeUseKey(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit,CallbackInfoReturnable<ActionResult> cir){
        var stack=player.getMainHandStack();if(!(stack.getItem() instanceof CounterfeitMasterKeyItem))return;
        BlockPos lower=state.get(SmallDoorBlock.HALF)==DoubleBlockHalf.LOWER?pos:pos.down();
        if(!(world.getBlockEntity(lower) instanceof SmallDoorBlockEntity door)||door.isBlasted())return;
        if(!(state.getBlock() instanceof TrainDoorBlock) && door.isJammed() && !door.isOpen()){cir.setReturnValue(ActionResult.FAIL);return;}
        if(world.isClient){cir.setReturnValue(ActionResult.SUCCESS);return;}
        SmallDoorBlock.toggleDoor(state,world,door,lower);
        world.playSound(null,lower,WatheSounds.ITEM_KEY_DOOR,SoundCategory.BLOCKS,1f,1f);
        int uses=stack.getDamage()+1;
        if(uses>=3){player.sendEquipmentBreakStatus(stack.getItem(),EquipmentSlot.MAINHAND);stack.decrement(1);}else stack.setDamage(uses);
        player.getInventory().markDirty();cir.setReturnValue(ActionResult.CONSUME);
    }
}
