package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.network.CopycatActionPayload;
import dev.mapselect.network.CopycatStatePayload;
import dev.mapselect.network.CopycatStatePayload.StoredAbility;
import dev.mapselect.registry.MapSelectRoles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientCopycatState {
	private static boolean wasPrimaryDown;
	private static boolean wasSecondaryDown;
	private static boolean borrowingAbility;
	private static Identifier copiedRoleId;
	private static List<StoredAbility> storedAbilities = List.of();
	private static int selectedIndex;
	private static long borrowedUntilTick;
	private static boolean suppressBorrowedSecondaryUntilRelease;

	private ClientCopycatState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CopycatStatePayload.ID, (payload, context) ->
			context.client().execute(() -> applyState(context.client(), payload)));
		ClientTickEvents.END_CLIENT_TICK.register(ClientCopycatState::tick);
	}

	private static void tick(MinecraftClient client) {
		Identifier actualRole = client == null || client.player == null || client.world == null ? null : localRoleId(client);
		if (client != null && client.player != null && client.world != null && !MapSelectRoles.COPYCAT_ID.equals(actualRole)) {
			borrowingAbility = false;
			copiedRoleId = null;
			storedAbilities = List.of();
			selectedIndex = 0;
			borrowedUntilTick = 0L;
			suppressBorrowedSecondaryUntilRelease = false;
		}
		if (client == null || client.player == null || client.world == null || client.currentScreen != null
				|| ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client)
				|| !MapSelectRoles.COPYCAT_ID.equals(actualRole)
				|| !GameFunctions.isPlayerAliveAndSurvival(client.player)) {
			wasPrimaryDown = false;
			wasSecondaryDown = false;
			return;
		}
		if (isBorrowingAbility()) {
			wasPrimaryDown = false;
			wasSecondaryDown = false;
			return;
		}
		KeyBinding binding = ClientAbilityKeys.primaryBinding();
		boolean primary = binding != null && ClientAbilityKeys.isDown(client, binding);
		if (primary && !wasPrimaryDown && ClientPlayNetworking.canSend(CopycatActionPayload.ID)) {
			ClientPlayNetworking.send(CopycatActionPayload.store());
		}
		wasPrimaryDown = primary;

		KeyBinding secondaryBinding = ClientAbilityKeys.secondaryBinding();
		boolean secondary = secondaryBinding != null && ClientAbilityKeys.isDown(client, secondaryBinding);
		if (secondary && !wasSecondaryDown && storedRoleId() != null
				&& ClientPlayNetworking.canSend(CopycatActionPayload.ID)) {
			sendActivateStored(selectedIndex);
		}
		wasSecondaryDown = secondary;
	}

	public static boolean isBorrowingAbility() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!borrowingAbility || client == null || client.world == null) return false;
		if (client.world.getTime() >= borrowedUntilTick) {
			borrowingAbility = false;
			copiedRoleId = null;
			borrowedUntilTick = 0L;
			return false;
		}
		return true;
	}

	public static Identifier copiedRoleId() {
		return isBorrowingAbility() ? copiedRoleId : null;
	}

	public static Identifier storedRoleId() {
		StoredAbility ability = storedAbility();
		return ability == null ? null : ability.roleId();
	}

	public static List<Identifier> storedRoleIds() {
		List<Identifier> ids = new ArrayList<>(storedAbilities.size());
		for (StoredAbility ability : storedAbilities) ids.add(ability.roleId());
		return List.copyOf(ids);
	}

	public static List<StoredAbility> storedAbilities() {
		return List.copyOf(storedAbilities);
	}

	public static StoredAbility storedAbility() {
		if (storedAbilities.isEmpty()) return null;
		return storedAbilities.get(Math.max(0, Math.min(storedAbilities.size() - 1, selectedIndex)));
	}

	public static int selectedIndex() {
		return selectedIndex;
	}

	public static void selectStored(int index) {
		if (storedAbilities.isEmpty()) {
			selectedIndex = 0;
			return;
		}
		selectedIndex = Math.max(0, Math.min(storedAbilities.size() - 1, index));
		if (ClientPlayNetworking.canSend(CopycatActionPayload.ID)) {
			ClientPlayNetworking.send(CopycatActionPayload.selectStored(selectedIndex));
		}
	}

	public static void requestStore(UUID targetId) {
		if (targetId != null && ClientPlayNetworking.canSend(CopycatActionPayload.ID)) {
			ClientPlayNetworking.send(CopycatActionPayload.storeTarget(targetId));
		}
	}

	public static void activateSelected() {
		if (storedRoleId() != null) sendActivateStored(selectedIndex);
	}

	public static void activateStored(int index) {
		if (!storedAbilities.isEmpty()) {
			sendActivateStored(Math.max(0, Math.min(storedAbilities.size() - 1, index)));
		}
	}

	static boolean shouldSuppressBorrowedSecondary(MinecraftClient client, KeyBinding binding, boolean down) {
		if (binding == null || binding != ClientAbilityKeys.secondaryBinding()) return false;
		if (!suppressBorrowedSecondaryUntilRelease) return false;
		if (!down || !isBorrowingAbility()) {
			suppressBorrowedSecondaryUntilRelease = false;
			return false;
		}
		return true;
	}

	public static boolean shouldShowInventoryMenu(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		return MapSelectRoles.COPYCAT_ID.equals(localRoleId(client))
			&& !isBorrowingAbility()
			&& !ClientVultureState.isLocalStashed(client)
			&& ClientRoleRevealState.canUseRoleAbility(client)
			&& GameFunctions.isPlayerAliveAndSurvival(client.player);
	}

	public static Identifier effectiveRoleId(MinecraftClient client, Identifier actualRoleId) {
		Identifier copied = copiedRoleId();
		return copied == null ? actualRoleId : copied;
	}

	public static boolean isEffectiveRole(MinecraftClient client, Identifier roleId) {
		return roleId != null && roleId.equals(effectiveRoleId(client, localRoleId(client)));
	}

	private static void applyState(MinecraftClient client, CopycatStatePayload payload) {
		if (payload == null || client == null || client.world == null || !payload.active()) {
			borrowingAbility = false;
			copiedRoleId = null;
			borrowedUntilTick = 0L;
			storedAbilities = payload == null ? List.of() : new ArrayList<>(payload.storedAbilities());
			selectedIndex = payload == null ? 0 : payload.selectedIndex();
			return;
		}
		borrowingAbility = true;
		copiedRoleId = payload.copiedRoleId();
		borrowedUntilTick = client.world.getTime() + payload.remainingTicks();
		storedAbilities = new ArrayList<>(payload.storedAbilities());
		selectedIndex = payload.selectedIndex();
		suppressBorrowedSecondaryUntilRelease = true;
	}

	private static void sendActivateStored(int index) {
		if (ClientPlayNetworking.canSend(CopycatActionPayload.ID)) {
			suppressBorrowedSecondaryUntilRelease = true;
			ClientPlayNetworking.send(CopycatActionPayload.activateStored(index));
		}
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
