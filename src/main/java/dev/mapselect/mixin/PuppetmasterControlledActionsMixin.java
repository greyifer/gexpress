package dev.mapselect.mixin;

import dev.mapselect.game.GexpressAbilityGuards;
import dev.mapselect.role.puppetmaster.PuppetmasterManager;
import dev.mapselect.role.timemaster.TimeMasterManager;
import dev.mapselect.role.vulture.VultureManager;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.RenameItemC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class PuppetmasterControlledActionsMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
	private void gexpress$redirectPlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
	private void gexpress$redirectInteractBlock(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
	private void gexpress$redirectInteractItem(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
	private void gexpress$redirectInteractEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onHandSwing", at = @At("HEAD"), cancellable = true)
	private void gexpress$redirectHandSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
	private void gexpress$redirectHotbar(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockClickSlot(ClickSlotC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onPickFromInventory", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockPickFromInventory(PickFromInventoryC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockCreativeInventory(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockButtonClick(ButtonClickC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onCraftRequest", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockCraftRequest(CraftRequestC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onClientCommand", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockCommandExecution(CommandExecutionC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player) && !isAllowedLockedCommand(packet.command())) ci.cancel();
	}

	@Inject(method = "onChatCommandSigned", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockSignedCommand(ChatCommandSignedC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player) && !isAllowedLockedCommand(packet.command())) ci.cancel();
	}

	@Inject(method = "onBookUpdate", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockBookUpdate(BookUpdateC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onRenameItem", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockRenameItem(RenameItemC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onSelectMerchantTrade", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockMerchantTrade(SelectMerchantTradeC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onSlotChangedState", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockSlotState(SlotChangedStateC2SPacket packet, CallbackInfo ci) {
		if (isLocked(player)) ci.cancel();
	}

	@Inject(method = "onCustomPayload", at = @At("HEAD"), cancellable = true)
	private void gexpress$blockCustomPayload(CustomPayloadC2SPacket packet, CallbackInfo ci) {
		if (GexpressAbilityGuards.shouldBlockAbilityPayload(player, packet.payload())) {
			ci.cancel();
			return;
		}
		if (isLocked(player)) ci.cancel();
	}

	private static boolean isLocked(ServerPlayerEntity player) {
		return PuppetmasterManager.isControlled(player) || VultureManager.isStashed(player)
			|| TimeMasterManager.isFrozen(player);
	}

	private static boolean isAllowedLockedCommand(String command) {
		return "g roles pelican leave".equals(command) || "g pelican leave".equals(command);
	}
}
