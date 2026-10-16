package dev.mapselect.mixin;

import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.mapselect.item.KeycardItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SmallDoorBlock.class, remap = false)
public abstract class SmallDoorKeycardMixin {
	@Inject(method = "method_55766", at = @At("HEAD"), cancellable = true, remap = false)
	private void gexpress$handleKeycard(BlockState state, World world, BlockPos pos, PlayerEntity player,
			BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
		ItemStack stack = player.getMainHandStack();
		if (!KeycardItem.isKeycard(stack)) return;
		if (world.isClient) {
			cir.setReturnValue(ActionResult.SUCCESS);
			return;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

		DoorBlockEntity door = KeycardItem.findDoor(world, pos);
		if (!(door instanceof SmallDoorBlockEntity smallDoor)) return;

		if (KeycardItem.canAssign(serverPlayer)) {
			String keyName = KeycardItem.keyName(stack);
			KeycardItem.Tier tier = KeycardItem.tier(stack);
			if (keyName.isBlank()) {
				serverPlayer.sendMessage(Text.literal("This keycard has no access label.").formatted(Formatting.RED), true);
			} else if (tier != null) {
				KeycardItem.assignDoorForLink(serverPlayer, world, door, keyName, tier);
			}
			cir.setReturnValue(ActionResult.CONSUME);
			return;
		}

		if (door.isBlasted() || door.isJammed()) return;
		String keyName = KeycardItem.sanitizeKeyName(door.getKeyName());
		if (keyName.isBlank()) return;

		world.playSound(null, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
			WatheSounds.BLOCK_DOOR_LOCKED, SoundCategory.BLOCKS, 1.0F, 1.0F);
		serverPlayer.sendMessage(Text.literal("Keycard declined").formatted(Formatting.RED), true);
		cir.setReturnValue(ActionResult.CONSUME);
	}
}
