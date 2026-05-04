package dev.mapselect.mixin.client;

import cat.rezelyn.watheextended.client.screen.guidebook.RoleItemsRegistry;
import cat.rezelyn.watheextended.client.screen.guidebook.RoleItemsRegistry.RoleItem;
import dev.mapselect.config.GexpressConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * RoleItemsRegistry has a private REGISTRY map with no public add hook, so we can't register
 * a supplier for our role from the outside. Intercept getItemsForRole for gexpress:bomb_specialist
 * and return the exact shop list the user wants (c4, grenade, firecracker, lockpick, crowbar, note)
 * regardless of the includeGlobal flag so the global killer shop doesn't get appended.
 */
@Mixin(value = RoleItemsRegistry.class, remap = false)
public abstract class BombSpecialistShopMixin {
	@Inject(method = "getItemsForRole", at = @At("HEAD"), cancellable = true)
	private static void gexpress$bombSpecialistShop(String roleId, boolean includeGlobal,
			CallbackInfoReturnable<List<RoleItem>> cir) {
		if ("gexpress:bomb_specialist".equals(roleId)) {
			cir.setReturnValue(List.of(
			new RoleItem("c4", "item.gexpress.c4", GexpressConfig.getC4Price()),
			new RoleItem("grenade", "item.wathe.grenade", GexpressConfig.getGrenadePrice()),
			new RoleItem("firecracker", "item.wathe.firecracker", 10),
			new RoleItem("lockpick", "item.wathe.lockpick", 50),
			new RoleItem("crowbar", "item.wathe.crowbar", 25),
			new RoleItem("note", "item.wathe.note", 10)
			));
			return;
		}
		if ("gexpress:godfather".equals(roleId)) {
			cir.setReturnValue(List.of(
				new RoleItem("revolver", "item.wathe.revolver", 0),
				new RoleItem("bullet", "item.gexpress.bullet", GexpressConfig.getGodfatherBulletPrice())
			));
			return;
		}
		if ("gexpress:mafioso".equals(roleId)) {
			cir.setReturnValue(List.of(
				new RoleItem("knife", "item.wathe.knife", 200),
				new RoleItem("revolver", "item.wathe.revolver", 350),
				new RoleItem("grenade", "item.wathe.grenade", GexpressConfig.getGrenadePrice())
			));
			return;
		}
		if ("gexpress:janitor".equals(roleId)) {
			cir.setReturnValue(List.of(
				new RoleItem("poison_vial", "item.wathe.poison_vial", 100)
			));
		}
	}

	/**
	 * GuidebookPageContent.resolve only populates the items list from this registry if the role
	 * has an explicit registration. Without this, a plain `getItemsForRole` override produces
	 * items but the "no items" fallback wipes them out before render. Force true for our role.
	 */
	@Inject(method = "hasExplicitRegistration", at = @At("HEAD"), cancellable = true)
	private static void gexpress$bombSpecialistIsRegistered(String roleId,
			CallbackInfoReturnable<Boolean> cir) {
		if ("gexpress:bomb_specialist".equals(roleId)
				|| "gexpress:godfather".equals(roleId)
				|| "gexpress:mafioso".equals(roleId)
				|| "gexpress:janitor".equals(roleId)) {
			cir.setReturnValue(Boolean.TRUE);
		}
	}
}
