package dev.mapselect.mixin;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.role.GexpressRoleShop;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Server-side counterpart to ShopScreenMixin. Wathe's {@code PlayerShopComponent#tryBuy}
 * looks up the purchased item via {@code GameConstants.SHOP_ENTRIES.get(index)}; the index
 * comes from a StoreBuyPayload that the client sent from its own (possibly Bomb-Specialist-
 * specific) shop list. We redirect both GETSTATIC reads in tryBuy - the length check and the
 * index lookup - so the server resolves the same list the client displayed.
 */
@Mixin(value = PlayerShopComponent.class, remap = false, priority = 2000)
public abstract class ShopPurchaseMixin {

	@Shadow @Final private PlayerEntity player;
	@Shadow public int balance;
	@Shadow public abstract void sync();

	@Inject(method = "tryBuy", at = @At("HEAD"), cancellable = true)
	private void gexpress$buyMutedExternalNote(int index, CallbackInfo ci) {
		if (index != GexpressRoleShop.MUTED_EXTERNAL_NOTE_INDEX
				|| !GexpressRoleShop.usesExternalMutedNoteWidget(this.player)) {
			return;
		}
		ShopEntry entry = GexpressRoleShop.mutedNoteEntry();
		if (this.balance >= entry.price() && entry.onBuy(this.player)) {
			this.balance -= entry.price();
			sync();
		}
		ci.cancel();
	}

	@Redirect(
		method = "tryBuy",
		at = @At(
			value = "FIELD",
			target = "Ldev/doctor4t/wathe/game/GameConstants;SHOP_ENTRIES:Ljava/util/List;",
			opcode = Opcodes.GETSTATIC
		)
	)
	private List<ShopEntry> gexpress$bombSpecialistShop() {
		return GexpressRoleShop.resolve(this.player);
	}

	@Redirect(
		method = "tryBuy",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/player/ItemCooldownManager;isCoolingDown(Lnet/minecraft/item/Item;)Z",
			remap = true
		)
	)
	private boolean gexpress$allowPurchasingCooldownItems(ItemCooldownManager cooldowns, Item item) {
		return false;
	}
}
