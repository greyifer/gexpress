package dev.mapselect.role.copycat;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.network.ability.AbilityCooldownPayload;
import dev.mapselect.network.ability.AbilityCooldownSync;
import dev.mapselect.network.role.copycat.CopycatActionPayload;
import dev.mapselect.network.role.copycat.CopycatStatePayload;
import dev.mapselect.network.role.copycat.CopycatStatePayload.StoredAbility;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.AbilityTargeting;
import dev.mapselect.role.NeutralWinManager;
import dev.mapselect.role.PassiveMoney;
import dev.mapselect.role.pelican.PelicanManager;
import dev.mapselect.testing.GexpressTestState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CopycatManager {
	private static final int MAX_STORED_ABILITIES = 3;
	private static final String BORROWED_WEAPON_KEY = "gexpress_copycat_borrowed_weapon";
	private static final String BORROWED_WEAPON_OWNER_KEY = "gexpress_copycat_borrowed_owner";
	private static final Map<UUID, ActiveCopy> activeCopies = new HashMap<>();
	private static final Map<UUID, List<StoredAbility>> storedCopies = new HashMap<>();
	private static final Map<UUID, Integer> selectedCopies = new HashMap<>();
	private static final Map<UUID, Set<Identifier>> copiedRoles = new HashMap<>();

	private CopycatManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(CopycatActionPayload.ID, CopycatActionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CopycatStatePayload.ID, CopycatStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CopycatActionPayload.ID,
			(payload, context) -> context.server().execute(() -> {
				switch (payload.action()) {
					case ACTIVATE_STORED -> activateStored(context.player(), payload.index());
					case SELECT_STORED -> selectStored(context.player(), payload.index());
					case STORE_TARGET -> tryStore(context.player(), payload.targetId());
					case CANCEL_ACTIVE -> cancelActive(context.player());
					case STORE -> tryStore(context.player());
				}
			}));
		ServerTickEvents.END_WORLD_TICK.register(CopycatManager::tick);
		GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearAll());
		GameEvents.ON_FINISH_FINALIZE.register((world, game) -> clearAll());
	}

	private static void tryStore(ServerPlayerEntity copycat) {
		tryStore(copycat, null);
	}

	private static void tryStore(ServerPlayerEntity copycat, UUID requestedTargetId) {
		if (copycat == null || !(copycat.getWorld() instanceof ServerWorld world)) return;
		if (!canUseHere(world, copycat) || !isCopycat(copycat) || PelicanManager.isStashed(copycat)
				|| (!GexpressTestState.isRoleTester(copycat) && !GameFunctions.isPlayerAliveAndSurvival(copycat))) {
			return;
		}
		if (activeCopies.containsKey(copycat.getUuid())) {
			copycat.sendMessage(Text.literal("You are already using a copied ability.").formatted(Formatting.GRAY), true);
			return;
		}
		List<StoredAbility> stored = storedCopies.computeIfAbsent(copycat.getUuid(), ignored -> new ArrayList<>());
		if (stored.size() >= MAX_STORED_ABILITIES) {
			copycat.sendMessage(Text.literal("You can only store 3 abilities. Open your inventory to choose one.")
				.formatted(Formatting.GRAY), true);
			return;
		}
		boolean testing = GexpressTestState.isRoleTester(copycat);
		ServerPlayerEntity target = requestedTargetId == null
			? AbilityTargeting.findLookTarget(copycat, world.getPlayers(),
				GexpressConfig.getCopycatCopyRange(), 0.0D, true,
				candidate -> isValidStoreTarget(copycat, candidate, testing))
			: world.getServer().getPlayerManager().getPlayer(requestedTargetId);
		if (requestedTargetId != null && !isValidStoreTarget(copycat, target, testing)) target = null;
		if (target == null) {
			copycat.sendMessage(Text.literal(requestedTargetId == null
				? "No ability close enough to copy."
				: "That ability is not available to copy.").formatted(Formatting.GRAY), true);
			return;
		}
		Role targetRole = currentRole(target);
		if (targetRole == null || targetRole.identifier() == null || !canCopyRole(targetRole.identifier())) {
			copycat.sendMessage(Text.literal("That ability cannot be copied.").formatted(Formatting.GRAY), true);
			return;
		}
		Set<Identifier> used = copiedRoles.computeIfAbsent(copycat.getUuid(), id -> new HashSet<>());
		if (used.contains(targetRole.identifier())) {
			copycat.sendMessage(Text.literal("You already copied that ability this round.").formatted(Formatting.GRAY), true);
			return;
		}
		used.add(targetRole.identifier());
		stored.add(new StoredAbility(targetRole.identifier(), target.getUuid(), target.getGameProfile().getName()));
		selectedCopies.put(copycat.getUuid(), stored.size() - 1);
		syncState(copycat, null, 0L);
		AbilityCooldownSync.clear(copycat, AbilityCooldownPayload.COPYCAT_COPY);
		copycat.sendMessage(Text.literal("Stored " + target.getName().getString() + "'s ability. Open your inventory to select it, then use your secondary ability key.")
			.formatted(Formatting.LIGHT_PURPLE), true);
	}

	private static boolean isValidStoreTarget(ServerPlayerEntity copycat, ServerPlayerEntity target, boolean testing) {
		return target != null
			&& target != copycat
			&& target.getWorld() == copycat.getWorld()
			&& !PelicanManager.isStashed(target)
			&& (testing ? !target.isSpectator() : DeadPlayerStatus.isLivingRoundParticipant(target));
	}

	private static void selectStored(ServerPlayerEntity copycat, int index) {
		if (copycat == null) return;
		List<StoredAbility> stored = storedCopies.getOrDefault(copycat.getUuid(), List.of());
		if (stored.isEmpty()) {
			selectedCopies.remove(copycat.getUuid());
			syncState(copycat, null, 0L);
			return;
		}
		selectedCopies.put(copycat.getUuid(), clampIndex(index, stored.size()));
		syncState(copycat, null, 0L);
	}

	private static void activateStored(ServerPlayerEntity copycat, int requestedIndex) {
		if (copycat == null || !(copycat.getWorld() instanceof ServerWorld world)) return;
		if (!canUseHere(world, copycat) || !isCopycat(copycat) || PelicanManager.isStashed(copycat)
				|| (!GexpressTestState.isRoleTester(copycat) && !GameFunctions.isPlayerAliveAndSurvival(copycat))) {
			return;
		}
		if (activeCopies.containsKey(copycat.getUuid())) {
			copycat.sendMessage(Text.literal("You are already using a copied ability.").formatted(Formatting.GRAY), true);
			return;
		}
		List<StoredAbility> stored = storedCopies.get(copycat.getUuid());
		if (stored == null || stored.isEmpty()) {
			copycat.sendMessage(Text.literal("No stored ability.").formatted(Formatting.GRAY), true);
			syncState(copycat, null, 0L);
			return;
		}
		int index = clampIndex(requestedIndex >= 0 ? requestedIndex : selectedCopies.getOrDefault(copycat.getUuid(), 0), stored.size());
		StoredAbility storedAbility = stored.get(index);
		Identifier storedRole = storedAbility.roleId();
		selectedCopies.put(copycat.getUuid(), index);
		long duration = (long) GexpressConfig.getCopycatCopyDurationSeconds() * 20L;
		BorrowedWeapon borrowedWeapon = borrowedWeaponForRole(storedRole);
		activeCopies.put(copycat.getUuid(), new ActiveCopy(storedRole, world.getTime() + duration, index,
			borrowedWeapon, false));
		grantBorrowedWeapon(copycat, borrowedWeapon);
		grantBorrowedShopItem(copycat, storedRole);
		syncState(copycat, storedRole, duration);
		AbilityCooldownSync.send(copycat, AbilityCooldownPayload.COPYCAT_COPY, duration, duration, true);
		copycat.sendMessage(Text.literal("Activated stored ability for "
			+ GexpressConfig.getCopycatCopyDurationSeconds() + "s.").formatted(Formatting.LIGHT_PURPLE), true);
	}

	private static void cancelActive(ServerPlayerEntity copycat) {
		if (copycat == null || !(copycat.getWorld() instanceof ServerWorld)) return;
		ActiveCopy copy = activeCopies.remove(copycat.getUuid());
		if (copy == null) return;
		removeBorrowedWeapon(copycat, copy.borrowedWeapon());
		consumeStoredAbility(copycat.getUuid(), copy.storedIndex());
		syncState(copycat, null, 0L);
		AbilityCooldownSync.clear(copycat, AbilityCooldownPayload.COPYCAT_COPY);
		copycat.sendMessage(Text.literal("Cancelled copied ability.").formatted(Formatting.GRAY), true);
	}

	private static void tick(ServerWorld world) {
		if (world.getRegistryKey() != World.OVERWORLD || activeCopies.isEmpty()) return;
		long now = world.getTime();
		for (UUID playerId : Set.copyOf(activeCopies.keySet())) {
			ActiveCopy copy = activeCopies.get(playerId);
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerId);
			if (copy == null) continue;
			if (now < copy.untilTick()) {
				if (player != null && player.getWorld() == world) tickBorrowedWeapon(player, playerId, copy);
				continue;
			}
			activeCopies.remove(playerId);
			consumeStoredAbility(playerId, copy.storedIndex());
			if (player == null || player.getWorld() != world) continue;
			removeBorrowedWeapon(player, copy.borrowedWeapon());
			syncState(player, null, 0L);
			AbilityCooldownSync.clear(player, AbilityCooldownPayload.COPYCAT_COPY);
			player.sendMessage(Text.literal("Your copied ability faded.").formatted(Formatting.GRAY), true);
		}
	}

	public static boolean isBorrowingAbility(PlayerEntity player) {
		if (player == null) return false;
		if (!player.getWorld().isClient) return activeCopies.containsKey(player.getUuid());
		try {
			Class<?> state = Class.forName("dev.mapselect.client.role.copycat.ClientCopycatState");
			return Boolean.TRUE.equals(state.getMethod("isBorrowingAbility").invoke(null));
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static Identifier copiedRoleId(PlayerEntity player) {
		if (player == null) return null;
		if (!player.getWorld().isClient) {
			ActiveCopy copy = activeCopies.get(player.getUuid());
			if (copy == null) return null;
			long now = player.getWorld().getTime();
			return now >= copy.untilTick() ? null : copy.copiedRole();
		}
		try {
			Class<?> state = Class.forName("dev.mapselect.client.role.copycat.ClientCopycatState");
			Object value = state.getMethod("copiedRoleId").invoke(null);
			return value instanceof Identifier id ? id : null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static boolean isCopyingRole(PlayerEntity player, Identifier roleId) {
		Identifier copied = copiedRoleId(player);
		return copied != null && copied.equals(roleId);
	}

	public static boolean handleMurderTick(ServerWorld world, GameWorldComponent game) {
		if (world == null || game == null || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) return false;
		java.util.List<ServerPlayerEntity> alive = world.getPlayers(GameFunctions::isPlayerAliveAndSurvival);
		java.util.List<ServerPlayerEntity> copycats = alive.stream()
			.filter(CopycatManager::isCopycat)
			.toList();
		if (copycats.isEmpty()) return false;

		PassiveMoney.grant(world, game);

		GameFunctions.WinStatus winStatus = GameFunctions.WinStatus.NONE;
		if (!GameTimeComponent.KEY.get(world).hasTime()) {
			winStatus = GameFunctions.WinStatus.TIME;
		} else if (alive.size() == 1 && alive.getFirst() == copycats.getFirst()) {
			ServerPlayerEntity winner = alive.getFirst();
			game.setLooseEndWinner(winner.getUuid());
			NeutralWinManager.announce(world, winner, "announcement.win.gexpress.copycat",
				MapSelectRoles.COPYCAT == null ? 0x9B7BEA : MapSelectRoles.COPYCAT.color());
			winStatus = GameFunctions.WinStatus.LOOSE_END;
		}

		if (winStatus != GameFunctions.WinStatus.NONE) {
			GameRoundEndComponent.KEY.get(world).setRoundEndData(world.getPlayers(), winStatus);
			GameFunctions.stopGame(world);
		}
		return true;
	}

	private static boolean canCopyRole(Identifier roleId) {
		if (roleId == null) return false;
		if (MapSelectRoles.COPYCAT_ID.equals(roleId)) return false;
		if (MapSelectRoles.GODFATHER_ID.equals(roleId) || MapSelectRoles.MAFIOSO_ID.equals(roleId)
				|| MapSelectRoles.JANITOR_ID.equals(roleId) || MapSelectRoles.PICKPOCKET_ID.equals(roleId)
				|| MapSelectRoles.BURGLAR_ID.equals(roleId)) {
			return false;
		}
		if (WatheRoles.CIVILIAN.identifier().equals(roleId)
				|| WatheRoles.LOOSE_END.identifier().equals(roleId)
				|| WatheRoles.DISCOVERY_CIVILIAN.identifier().equals(roleId)) {
			return false;
		}
		return true;
	}

	private static boolean isCopycat(ServerPlayerEntity player) {
		Role role = currentRole(player);
		return role != null && MapSelectRoles.COPYCAT_ID.equals(role.identifier());
	}

	private static Role currentRole(ServerPlayerEntity player) {
		GameWorldComponent game = player == null ? null : GameWorldComponent.KEY.getNullable(player.getWorld());
		return game == null ? null : game.getRole(player);
	}

	private static boolean canUseHere(World world, ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(world);
		return (game != null && game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE)
			|| GexpressTestState.isRoleTester(player);
	}

	private static void syncState(ServerPlayerEntity player, Identifier roleId, long remainingTicks) {
		if (player != null && ServerPlayNetworking.canSend(player, CopycatStatePayload.ID)) {
			List<StoredAbility> stored = storedCopies.getOrDefault(player.getUuid(), List.of());
			int selected = selectedCopies.getOrDefault(player.getUuid(), 0);
			ServerPlayNetworking.send(player, new CopycatStatePayload(roleId != null, roleId, remainingTicks, stored, selected));
		}
	}

	private static void consumeStoredAbility(UUID playerId, int index) {
		if (playerId == null) return;
		List<StoredAbility> stored = storedCopies.get(playerId);
		if (stored == null || stored.isEmpty()) {
			selectedCopies.remove(playerId);
			return;
		}
		if (index >= 0 && index < stored.size()) stored.remove(index);
		if (stored.isEmpty()) {
			storedCopies.remove(playerId);
			selectedCopies.remove(playerId);
		} else {
			selectedCopies.put(playerId, clampIndex(index, stored.size()));
		}
	}

	private static void grantBorrowedShopItem(ServerPlayerEntity copycat, Identifier roleId) {
		ItemStack stack = borrowedShopItem(roleId);
		if (copycat == null || stack.isEmpty()) return;
		ItemStack grant = stack.copy();
		if (!copycat.giveItemStack(grant)) copycat.dropItem(grant, false);
		copycat.sendMessage(Text.literal("Borrowed shop item: " + stack.getName().getString() + ".")
			.formatted(Formatting.LIGHT_PURPLE), true);
	}

	private static BorrowedWeapon borrowedWeaponForRole(Identifier roleId) {
		if (WatheRoles.KILLER.identifier().equals(roleId)) return BorrowedWeapon.KNIFE;
		if (WatheRoles.VIGILANTE.identifier().equals(roleId)) return BorrowedWeapon.REVOLVER;
		return BorrowedWeapon.NONE;
	}

	private static void grantBorrowedWeapon(ServerPlayerEntity copycat, BorrowedWeapon weapon) {
		if (copycat == null || weapon == BorrowedWeapon.NONE) return;
		ItemStack stack = weapon.stack();
		NbtCompound tag = new NbtCompound();
		tag.putString(BORROWED_WEAPON_KEY, weapon.id);
		tag.putString(BORROWED_WEAPON_OWNER_KEY, copycat.getUuidAsString());
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
		if (!copycat.giveItemStack(stack)) copycat.dropItem(stack, false);
		copycat.playerScreenHandler.syncState();
		copycat.sendMessage(Text.literal("Borrowed weapon: " + stack.getName().getString() + ".")
			.formatted(Formatting.LIGHT_PURPLE), true);
	}

	private static void tickBorrowedWeapon(ServerPlayerEntity player, UUID playerId, ActiveCopy copy) {
		if (copy.borrowedWeapon() != BorrowedWeapon.REVOLVER || copy.borrowedRevolverSpent()) return;
		if (!player.getItemCooldownManager().isCoolingDown(WatheItems.REVOLVER)) return;
		removeBorrowedWeapon(player, BorrowedWeapon.REVOLVER);
		activeCopies.put(playerId, copy.withBorrowedRevolverSpent());
		player.sendMessage(Text.literal("Borrowed vigilante revolver spent.").formatted(Formatting.GRAY), true);
	}

	private static void removeBorrowedWeapon(ServerPlayerEntity player, BorrowedWeapon weapon) {
		if (player == null || weapon == BorrowedWeapon.NONE) return;
		boolean changed = false;
		for (int slot = 0; slot < player.getInventory().size(); slot++) {
			if (!isBorrowedWeapon(player, player.getInventory().getStack(slot), weapon)) continue;
			player.getInventory().setStack(slot, ItemStack.EMPTY);
			changed = true;
		}
		if (changed) player.playerScreenHandler.syncState();
	}

	private static boolean isBorrowedWeapon(ServerPlayerEntity player, ItemStack stack, BorrowedWeapon weapon) {
		if (stack == null || stack.isEmpty() || weapon == BorrowedWeapon.NONE || !stack.isOf(weapon.item)) return false;
		NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		NbtCompound tag = customData.copyNbt();
		return weapon.id.equals(tag.getString(BORROWED_WEAPON_KEY))
			&& player.getUuidAsString().equals(tag.getString(BORROWED_WEAPON_OWNER_KEY));
	}

	private static ItemStack borrowedShopItem(Identifier roleId) {
		if (roleId == null) return ItemStack.EMPTY;
		if (MapSelectRoles.BOMB_SPECIALIST_ID.equals(roleId)) return new ItemStack(MapSelectItems.C4);
		String namespace = roleId.getNamespace();
		String path = roleId.getPath();
		if ("noellesroles".equals(namespace)) {
			return switch (path) {
				case "trapper" -> itemStack("noellesroles", "role_mine");
				case "bartender" -> itemStack("noellesroles", "defense_vial");
				case "noisemaker" -> WatheItems.FIRECRACKER.getDefaultStack();
				default -> ItemStack.EMPTY;
			};
		}
		if ("starexpress".equals(namespace) && "muzzler".equals(path)) return itemStack("starexpress", "tape");
		if ("kinswathe".equals(namespace)) {
			return switch (path) {
				case "cook" -> itemStack("kinswathe", "pan");
				case "drugmaker" -> itemStack("kinswathe", "poison_injector");
				case "hunter" -> itemStack("kinswathe", "hunting_knife");
				case "kidnapper" -> itemStack("kinswathe", "knockout_drug");
				case "physician" -> itemStack("kinswathe", "pill");
				case "technician" -> itemStack("kinswathe", "capture_device");
				default -> ItemStack.EMPTY;
			};
		}
		if ("stupid_express".equals(namespace) && "arsonist".equals(path)) return itemStack("stupid_express", "jerry_can");
		return ItemStack.EMPTY;
	}

	private static ItemStack itemStack(String namespace, String path) {
		Item item = Registries.ITEM.get(Identifier.of(namespace, path));
		return item.getDefaultStack();
	}

	private static void clearAll() {
		activeCopies.clear();
		storedCopies.clear();
		selectedCopies.clear();
		copiedRoles.clear();
	}

	private static int clampIndex(int index, int size) {
		if (size <= 0) return 0;
		return Math.max(0, Math.min(size - 1, index));
	}

	private enum BorrowedWeapon {
		NONE("", null),
		KNIFE("knife", WatheItems.KNIFE),
		REVOLVER("revolver", WatheItems.REVOLVER);

		private final String id;
		private final Item item;

		BorrowedWeapon(String id, Item item) {
			this.id = id;
			this.item = item;
		}

		private ItemStack stack() {
			return item == null ? ItemStack.EMPTY : item.getDefaultStack();
		}
	}

	private record ActiveCopy(Identifier copiedRole, long untilTick, int storedIndex,
			BorrowedWeapon borrowedWeapon, boolean borrowedRevolverSpent) {
		private ActiveCopy withBorrowedRevolverSpent() {
			return new ActiveCopy(copiedRole, untilTick, storedIndex, borrowedWeapon, true);
		}
	}
}
