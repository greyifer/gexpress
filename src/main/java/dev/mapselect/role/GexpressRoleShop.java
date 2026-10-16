package dev.mapselect.role;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectModifiers;
import dev.mapselect.registry.MapSelectBlocks;
import dev.mapselect.modifier.ModifierUtils;
import dev.mapselect.modifier.PestControlManager;
import dev.mapselect.role.copycat.CopycatManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for role-specific shop lists. The Wathe shop is one static list, so
 * both client display and server purchase handling must resolve the same list for each role.
 */
public final class GexpressRoleShop {
	private static final Identifier BOMB_SPECIALIST_ID = Identifier.of(MapSelect.MOD_ID, "bomb_specialist");
	private static final Identifier GODFATHER_ID = Identifier.of(MapSelect.MOD_ID, "godfather");
	private static final Identifier MAFIOSO_ID = Identifier.of(MapSelect.MOD_ID, "mafioso");
	private static final Identifier JANITOR_ID = Identifier.of(MapSelect.MOD_ID, "janitor");
	private static final Identifier PICKPOCKET_ID = Identifier.of(MapSelect.MOD_ID, "pickpocket");
	private static final Identifier BURGLAR_ID = Identifier.of(MapSelect.MOD_ID, "burglar");
	private static final Identifier SPY_ID = Identifier.of(MapSelect.MOD_ID, "spy");
	public static final int MUTED_EXTERNAL_NOTE_INDEX = 120;
	public static final int PEST_CONTROL_EXTERNAL_TRAP_INDEX = 121;

	private GexpressRoleShop() {}

	public static List<ShopEntry> resolve(PlayerEntity player) {
		boolean pestControl = PestControlManager.has(player);
		if (CopycatManager.isBorrowingAbility(player)) {
			List<ShopEntry> borrowed = pestControl ? List.of(ratTrapEntry()) : List.of();
			return isMuted(player) ? withMutedNote(borrowed) : borrowed;
		}
		Role role = roleOf(player);
		boolean muted = isMuted(player);
		if (role == null) {
			List<ShopEntry> entries = pestControl ? List.of(ratTrapEntry()) : GameConstants.SHOP_ENTRIES;
			return muted ? withMutedNote(entries) : entries;
		}
		Identifier id = role.identifier();
		if (muted && isExternalRole(id) && !pestControl) return List.of();
		List<ShopEntry> entries;
		if (BOMB_SPECIALIST_ID.equals(id)) entries = bombSpecialistList();
		else if (GODFATHER_ID.equals(id)) entries = godfatherList();
		else if (MAFIOSO_ID.equals(id)) entries = mafiosoList();
		else if (JANITOR_ID.equals(id)) entries = janitorList();
		else if (PICKPOCKET_ID.equals(id)) entries = pickpocketList();
		else if (BURGLAR_ID.equals(id)) entries = burglarList();
		else if (muted && !role.canUseKiller()) entries = mutedNotesList();
		else if (pestControl && !role.canUseKiller()) entries = List.of();
		else entries = GameConstants.SHOP_ENTRIES;
		if (pestControl) entries = withRatTrap(entries);
		return muted ? withMutedNote(entries) : entries;
	}

	public static boolean hasCustomShop(PlayerEntity player) {
		if (isMuted(player) || PestControlManager.has(player)) return true;
		Role role = roleOf(player);
		if (role == null) return false;
		return roleHasCustomShop(role.identifier());
	}

	public static boolean canUseKillerEconomy(PlayerEntity player) {
		if (CopycatManager.isBorrowingAbility(player)) return isMuted(player) || PestControlManager.has(player);
		if (PestControlManager.has(player)) return true;
		if (isMuted(player) && !usesExternalMutedNoteWidget(player)) return true;
		return canUseKillerEconomy(roleOf(player));
	}

	public static boolean canUseKillerEconomy(Role role) {
		if (role == null) return false;
		return role.canUseKiller() || roleHasCustomShop(role.identifier());
	}

	public static boolean showsMoneyHud(PlayerEntity player) {
		if (CopycatManager.isBorrowingAbility(player)) return isMuted(player) || PestControlManager.has(player);
		if (isMuted(player) || PestControlManager.has(player)) return true;
		Role role = roleOf(player);
		if (role == null) return false;
		return showsMoneyHud(role);
	}

	public static boolean showsMoneyHud(Role role) {
		if (role == null) return false;
		Identifier id = role.identifier();
		return canUseKillerEconomy(role) || SPY_ID.equals(id);
	}

	public static boolean usesExternalMutedNoteWidget(PlayerEntity player) {
		Role role = roleOf(player);
		return isMuted(player)
			&& role != null && rendersSeparateExternalShop(role.identifier());
	}

	public static boolean usesExternalPestControlWidget(PlayerEntity player) {
		Role role = roleOf(player);
		return PestControlManager.has(player) && role != null
			&& rendersSeparateExternalShop(role.identifier());
	}

	public static boolean usesExternalShopWidgets(PlayerEntity player) {
		return usesExternalMutedNoteWidget(player) || usesExternalPestControlWidget(player);
	}

	public static boolean hasMutedModifier(PlayerEntity player) {
		return isMuted(player);
	}

	public static ShopEntry mutedNoteEntry() {
		return customShopEntry(mutedNoteStack(), GexpressConfig.getMutedNotePrice(), ShopEntry.Type.TOOL);
	}

	public static ShopEntry ratTrapEntry() {
		return customShopEntry(new ItemStack(MapSelectBlocks.RAT_TRAP_ITEM),
			PestControlManager.RAT_TRAP_PRICE, ShopEntry.Type.TOOL);
	}

	private static boolean roleHasCustomShop(Identifier id) {
		return BOMB_SPECIALIST_ID.equals(id)
			|| GODFATHER_ID.equals(id)
			|| MAFIOSO_ID.equals(id)
			|| JANITOR_ID.equals(id)
			|| PICKPOCKET_ID.equals(id)
			|| BURGLAR_ID.equals(id);
	}

	private static boolean isExternalRole(Identifier id) {
		return id != null && !MapSelect.MOD_ID.equals(id.getNamespace()) && !"wathe".equals(id.getNamespace());
	}

	/** External roles that draw a second shop row instead of replacing Wathe's shop list. */
	private static boolean rendersSeparateExternalShop(Identifier id) {
		if (id == null) return false;
		if ("noellesroles".equals(id.getNamespace())) {
			return switch (id.getPath()) {
				case "trapper", "bartender", "noisemaker", "jester", "mimic", "executioner" -> true;
				default -> false;
			};
		}
		if ("kinswathe".equals(id.getNamespace())) {
			return switch (id.getPath()) {
				case "cook", "dreamer", "hacker", "licensed_villain", "physician" -> true;
				default -> false;
			};
		}
		return false;
	}

	public static List<ShopEntry> bombSpecialistList() {
		return List.of(
			new ShopEntry(new ItemStack(MapSelectItems.C4), GexpressConfig.getC4Price(), ShopEntry.Type.WEAPON),
			new ShopEntry(WatheItems.GRENADE.getDefaultStack(), GexpressConfig.getGrenadePrice(), ShopEntry.Type.WEAPON),
			new ShopEntry(WatheItems.FIRECRACKER.getDefaultStack(), GexpressConfig.getBombSpecialistFirecrackerPrice(), ShopEntry.Type.WEAPON),
			new ShopEntry(WatheItems.LOCKPICK.getDefaultStack(), GexpressConfig.getBombSpecialistLockpickPrice(), ShopEntry.Type.TOOL),
			new ShopEntry(WatheItems.CROWBAR.getDefaultStack(), GexpressConfig.getBombSpecialistCrowbarPrice(), ShopEntry.Type.TOOL),
			customShopEntry(mutedNoteStack(), GexpressConfig.getMutedNotePrice(), ShopEntry.Type.TOOL)
		);
	}

	public static List<ShopEntry> godfatherList() {
		return List.of(
			customShopEntry(new ItemStack(MapSelectItems.BULLET), GexpressConfig.getGodfatherBulletPrice(), ShopEntry.Type.WEAPON)
		);
	}

	public static List<ShopEntry> mafiosoList() {
		return List.of(
			customShopEntry(WatheItems.KNIFE.getDefaultStack(), GexpressConfig.getMafiosoKnifePrice(), ShopEntry.Type.WEAPON),
			customShopEntry(WatheItems.REVOLVER.getDefaultStack(), GexpressConfig.getMafiosoRevolverPrice(), ShopEntry.Type.WEAPON),
			customShopEntry(WatheItems.GRENADE.getDefaultStack(), GexpressConfig.getGrenadePrice(), ShopEntry.Type.WEAPON)
		);
	}

	public static List<ShopEntry> janitorList() {
		return List.of(
			customShopEntry(WatheItems.POISON_VIAL.getDefaultStack(), GexpressConfig.getJanitorPoisonVialPrice(), ShopEntry.Type.POISON),
			customShopEntry(itemStack("wathe", "scorpion"), GexpressConfig.getJanitorScorpionPrice(), ShopEntry.Type.WEAPON)
		);
	}

	public static List<ShopEntry> pickpocketList() {
		return List.of(
			customShopEntry(mutedNoteStack(), GexpressConfig.getMutedNotePrice(), ShopEntry.Type.TOOL)
		);
	}

	public static List<ShopEntry> burglarList() {
		return List.of(
			customShopEntry(itemStack("wathe", "crowbar"), GexpressConfig.getBurglarCrowbarPrice(), ShopEntry.Type.TOOL),
			customShopEntry(itemStack("wathe", "lockpick"), GexpressConfig.getBurglarLockpickPrice(), ShopEntry.Type.TOOL)
		);
	}

	private static List<ShopEntry> mutedNotesList() {
		return List.of(mutedNoteEntry());
	}

	private static List<ShopEntry> withMutedNote(List<ShopEntry> entries) {
		if (entries == null || entries.isEmpty()) return mutedNotesList();
		List<ShopEntry> out = new ArrayList<>(entries.size() + 1);
		boolean replaced = false;
		for (ShopEntry entry : entries) {
			if (entry != null && entry.stack().isOf(WatheItems.NOTE)) {
				out.add(customShopEntry(mutedNoteStack(), entry.price(), entry.type()));
				replaced = true;
			} else {
				out.add(entry);
			}
		}
		if (replaced) return List.copyOf(out);
		out.add(mutedNoteEntry());
		return List.copyOf(out);
	}

	private static List<ShopEntry> withRatTrap(List<ShopEntry> entries) {
		List<ShopEntry> out = new ArrayList<>((entries == null ? 0 : entries.size()) + 1);
		if (entries != null) {
			for (ShopEntry entry : entries) {
				if (entry != null && entry.stack().isOf(MapSelectBlocks.RAT_TRAP_ITEM)) continue;
				out.add(entry);
			}
		}
		out.add(ratTrapEntry());
		return List.copyOf(out);
	}

	private static ItemStack mutedNoteStack() {
		ItemStack stack = WatheItems.NOTE.getDefaultStack();
		stack.setCount(4);
		return stack;
	}

	private static ItemStack itemStack(String namespace, String path) {
		Item item = Registries.ITEM.get(Identifier.of(namespace, path));
		return item.getDefaultStack();
	}

	private static ShopEntry customShopEntry(ItemStack stack, int price, ShopEntry.Type type) {
		return new ShopEntry(stack, price, type) {
			@Override
			public boolean onBuy(PlayerEntity player) {
				return ShopEntry.insertStackInFreeSlot(player, this.stack().copy());
			}
		};
	}

	private static Role roleOf(PlayerEntity player) {
		if (player == null || player.getWorld() == null) return null;
		GameWorldComponent gwc = GameWorldComponent.KEY.getNullable(player.getWorld());
		return gwc == null ? null : gwc.getRole(player);
	}

	private static boolean isMuted(PlayerEntity player) {
		return ModifierUtils.has(player, MapSelectModifiers.MUTED_ID);
	}
}
