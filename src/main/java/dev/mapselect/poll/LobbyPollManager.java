package dev.mapselect.poll;

import cat.rezelyn.watheextended.api.config.ServerConfig;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.mapselect.command.GexpressFeedback;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.network.poll.LobbyPollBuilderOpenPayload;
import dev.mapselect.network.poll.LobbyPollCreatePayload;
import dev.mapselect.network.poll.LobbyPollStatusPayload;
import dev.mapselect.network.poll.LobbyPollVotePayload;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.RoleModifierTuningBridge;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LobbyPollManager {
	private static final Map<UUID, Long> lastCreatedTick = new HashMap<>();
	private static ActivePoll activePoll;
	private static int syncTicker;

	private LobbyPollManager() {}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(LobbyPollCreatePayload.ID, LobbyPollCreatePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(LobbyPollVotePayload.ID, LobbyPollVotePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LobbyPollBuilderOpenPayload.ID, LobbyPollBuilderOpenPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(LobbyPollStatusPayload.ID, LobbyPollStatusPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(LobbyPollCreatePayload.ID,
			(payload, context) -> context.server().execute(() -> create(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(LobbyPollVotePayload.ID,
			(payload, context) -> context.server().execute(() -> vote(context.player(), payload.approve())));
		ServerTickEvents.END_SERVER_TICK.register(LobbyPollManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			server.execute(() -> {
				if (activePoll != null) {
					activePoll.votes.remove(handler.player.getUuid());
					broadcastStatus(server);
				}
			}));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
			server.execute(() -> sendStatus(handler.player)));
	}

	public static int openBuilder(ServerCommandSource source) {
		ServerPlayerEntity player;
		try {
			player = source.getPlayerOrThrow();
		} catch (Exception ignored) {
			GexpressFeedback.send(source, () -> Text.literal("Only players can open lobby polls.")
				.formatted(Formatting.RED), false);
			return 0;
		}
		if (!isLobby(player)) {
			GexpressFeedback.send(source, () -> Text.literal("Lobby polls can only be created before a round starts.")
				.formatted(Formatting.RED), false);
			return 0;
		}
		if (activePoll != null) {
			sendStatus(player);
			GexpressFeedback.send(source, () -> Text.literal("A lobby poll is already active.")
				.formatted(Formatting.YELLOW), false);
			return 0;
		}
		long remaining = bypassesPollCooldown(player) ? 0L : cooldownRemaining(player);
		if (remaining > 0L) {
			long seconds = Math.max(1L, (remaining + 19L) / 20L);
			GexpressFeedback.send(source, () -> Text.literal("You can make another poll in " + seconds + "s.")
				.formatted(Formatting.YELLOW), false);
			return 0;
		}
		if (!ServerPlayNetworking.canSend(player, LobbyPollBuilderOpenPayload.ID)) {
			GexpressFeedback.send(source, () -> Text.literal("Your client does not support lobby poll menus.")
				.formatted(Formatting.RED), false);
			return 0;
		}
		ServerPlayNetworking.send(player, new LobbyPollBuilderOpenPayload());
		return 1;
	}

	public static int voteCommand(ServerCommandSource source, boolean approve) {
		ServerPlayerEntity player;
		try {
			player = source.getPlayerOrThrow();
		} catch (Exception ignored) {
			GexpressFeedback.send(source, () -> Text.literal("Only players can vote in lobby polls.")
				.formatted(Formatting.RED), false);
			return 0;
		}
		return vote(player, approve) ? 1 : 0;
	}

	private static void create(ServerPlayerEntity creator, LobbyPollCreatePayload payload) {
		if (creator == null) return;
		if (!isLobby(creator)) {
			creator.sendMessage(Text.literal("Lobby polls can only be created before a round starts.")
				.formatted(Formatting.RED), false);
			return;
		}
		if (activePoll != null) {
			creator.sendMessage(Text.literal("A lobby poll is already active.").formatted(Formatting.YELLOW), false);
			return;
		}
		boolean bypassesCooldown = bypassesPollCooldown(creator);
		long remaining = bypassesCooldown ? 0L : cooldownRemaining(creator);
		if (remaining > 0L) {
			long seconds = Math.max(1L, (remaining + 19L) / 20L);
			creator.sendMessage(Text.literal("You can make another poll in " + seconds + "s.")
				.formatted(Formatting.YELLOW), false);
			return;
		}
		PollChange change = PollChange.from(payload);
		if (change == null || !change.exists()) {
			creator.sendMessage(Text.literal("That poll target is not a known role or modifier.")
				.formatted(Formatting.RED), false);
			return;
		}

		MinecraftServer server = creator.getServer();
		if (server == null) return;
		long now = now(server);
		long duration = (long) GexpressConfig.getLobbyPollDurationSeconds() * 20L;
		activePoll = new ActivePoll(creator.getUuid(), change, now + duration);
		activePoll.votes.put(creator.getUuid(), true);
		if (!bypassesCooldown) {
			lastCreatedTick.put(creator.getUuid(), now);
		}
		broadcast(server, Text.literal(creator.getName().getString() + " started a lobby poll: ")
			.formatted(Formatting.GOLD).append(Text.literal(change.title()).formatted(Formatting.WHITE)));
		broadcastStatus(server);
	}

	private static boolean vote(ServerPlayerEntity player, boolean approve) {
		if (player == null) return false;
		if (activePoll == null) {
			player.sendMessage(Text.literal("There is no active lobby poll.").formatted(Formatting.GRAY), false);
			return false;
		}
		if (!isLobby(player)) {
			player.sendMessage(Text.literal("Lobby poll voting is closed once the round starts.")
				.formatted(Formatting.RED), false);
			return false;
		}
		activePoll.votes.put(player.getUuid(), approve);
		MinecraftServer server = player.getServer();
		if (server != null) broadcastStatus(server);
		player.sendMessage(Text.literal("Vote recorded: " + (approve ? "Yes" : "No") + ".")
			.formatted(approve ? Formatting.GREEN : Formatting.RED), false);
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (activePoll == null) return;
		if (!isAnyLobby(server) || now(server) >= activePoll.endsAtTick) {
			finish(server);
			return;
		}
		if (++syncTicker >= 20) {
			syncTicker = 0;
			broadcastStatus(server);
		}
	}

	private static void finish(MinecraftServer server) {
		ActivePoll poll = activePoll;
		activePoll = null;
		syncTicker = 0;
		if (poll == null) return;
		int onlinePlayers = playerCount(server);
		int yesVotes = poll.yesVotes();
		boolean passed = yesVotes > onlinePlayers / 2;
		if (passed) {
			poll.change.apply(server);
			broadcast(server, Text.literal("Lobby poll passed: ").formatted(Formatting.GREEN)
				.append(Text.literal(poll.change.title()).formatted(Formatting.WHITE)));
		} else {
			broadcast(server, Text.literal("Lobby poll failed: ").formatted(Formatting.RED)
				.append(Text.literal(poll.change.title()).formatted(Formatting.WHITE)));
		}
		broadcastStatus(server);
	}

	private static long cooldownRemaining(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null) return 0L;
		long now = now(player.getServer());
		long cooldown = (long) GexpressConfig.getLobbyPollCooldownSeconds() * 20L;
		long last = lastCreatedTick.getOrDefault(player.getUuid(), Long.MIN_VALUE / 2L);
		return Math.max(0L, last + cooldown - now);
	}

	private static boolean bypassesPollCooldown(ServerPlayerEntity player) {
		if (player == null) return false;
		if (player.hasPermissionLevel(2) || GexpressPermissions.isDev(player)
				|| GexpressPermissions.isOwner(player) || GexpressPermissions.isStaff(player)) {
			return true;
		}
		return GexpressPermissions.effectiveTagInfos(player).stream()
			.anyMatch(tag -> tag.priority() >= PlayerTag.STAFF.priority());
	}

	private static void broadcastStatus(MinecraftServer server) {
		LobbyPollStatusPayload payload = statusPayload(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (ServerPlayNetworking.canSend(player, LobbyPollStatusPayload.ID)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void sendStatus(ServerPlayerEntity player) {
		if (player == null || player.getServer() == null
				|| !ServerPlayNetworking.canSend(player, LobbyPollStatusPayload.ID)) return;
		ServerPlayNetworking.send(player, statusPayload(player.getServer()));
	}

	private static LobbyPollStatusPayload statusPayload(MinecraftServer server) {
		if (activePoll == null) {
			return new LobbyPollStatusPayload(false, "", "", 0, 0, playerCount(server), 0);
		}
		return new LobbyPollStatusPayload(true, activePoll.change.title(), activePoll.change.details(),
			activePoll.yesVotes(), activePoll.noVotes(), playerCount(server),
			(int) Math.max(0L, activePoll.endsAtTick - now(server)));
	}

	private static void broadcast(MinecraftServer server, Text text) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			player.sendMessage(text, false);
		}
	}

	private static boolean isLobby(ServerPlayerEntity player) {
		if (player == null) return false;
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		if (game == null) return true;
		GameWorldComponent.GameStatus status = game.getGameStatus();
		return status != GameWorldComponent.GameStatus.STARTING
			&& status != GameWorldComponent.GameStatus.ACTIVE
			&& status != GameWorldComponent.GameStatus.STOPPING;
	}

	private static boolean isAnyLobby(MinecraftServer server) {
		return server.getPlayerManager().getPlayerList().stream().allMatch(LobbyPollManager::isLobby);
	}

	private static int playerCount(MinecraftServer server) {
		return server == null ? 0 : server.getPlayerManager().getCurrentPlayerCount();
	}

	private static long now(MinecraftServer server) {
		return server.getOverworld().getTime();
	}

	private static String label(String id) {
		String path = id;
		int colon = path.indexOf(':');
		if (colon >= 0 && colon + 1 < path.length()) path = path.substring(colon + 1);
		String[] parts = path.replace('-', '_').split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1) out.append(part.substring(1));
		}
		return out.isEmpty() ? id : out.toString();
	}

	private record PollChange(String targetType, String targetId, String setting, int value) {
		private static final Set<String> SETTINGS = Set.of("enabled", "chance", "amount");

		private static PollChange from(LobbyPollCreatePayload payload) {
			if (payload == null) return null;
			String targetType = payload.targetType().toLowerCase(Locale.ROOT).strip();
			String targetId = payload.targetId().strip();
			String setting = payload.setting().toLowerCase(Locale.ROOT).strip();
			if ((!targetType.equals("role") && !targetType.equals("modifier")) || !SETTINGS.contains(setting)
					|| Identifier.tryParse(targetId) == null) {
				return null;
			}
			int value = switch (setting) {
				case "enabled" -> payload.value() > 0 ? 1 : 0;
				case "chance" -> Math.max(RoleModifierTuningConfig.CHANCE_MIN,
					Math.min(RoleModifierTuningConfig.CHANCE_MAX, payload.value()));
				default -> Math.max(RoleModifierTuningConfig.MAX_MIN,
					Math.min(RoleModifierTuningConfig.MAX_MAX, payload.value()));
			};
			return new PollChange(targetType, targetId, setting, value);
		}

		private boolean exists() {
			if (targetType.equals("role")) {
				for (Role role : WatheRoles.ROLES) {
					if (role != null && role.identifier() != null
							&& targetId.equals(role.identifier().toString())) return true;
				}
				return false;
			}
			for (Modifier modifier : HMLModifiers.MODIFIERS) {
				if (modifier != null && modifier.identifier() != null
						&& targetId.equals(modifier.identifier().toString())) return true;
			}
			return false;
		}

		private String targetLabel() {
			String target = (targetType.equals("role") ? "Role " : "Modifier ") + label(targetId);
			return target;
		}

		private String title() {
			String target = targetLabel();
			return switch (setting) {
				case "enabled" -> target + " should be " + (value > 0 ? "enabled" : "disabled");
				case "chance" -> target + " chance should be " + value + "%";
				default -> target + " amount should be " + value;
			};
		}

		private String details() {
			String target = targetLabel();
			return switch (setting) {
				case "enabled" -> target + " will be " + (value > 0 ? "enabled" : "disabled") + " if this passes.";
				case "chance" -> target + " chance will be set to " + value + "% if this passes.";
				default -> target + " amount will be set to " + value + " if this passes.";
			};
		}

		private void apply(MinecraftServer server) {
			if (setting.equals("enabled")) {
				boolean enabled = value > 0;
				if (targetType.equals("role")) {
					RoleModifierTuningConfig.setRoleMax(targetId, enabled
						? RoleModifierTuningConfig.DEFAULT_MAX : RoleModifierTuningConfig.MAX_MIN);
					if (RoleModifierTuningConfig.getRoleChance(targetId) <= RoleModifierTuningConfig.CHANCE_MIN) {
						RoleModifierTuningConfig.setRoleChance(targetId, RoleModifierTuningConfig.DEFAULT_CHANCE);
					}
					setWatheExtendedEnabled(server, "hml.disabled", targetId, enabled);
				} else {
					RoleModifierTuningConfig.setModifierMax(targetId, enabled
						? RoleModifierTuningConfig.DEFAULT_MAX : RoleModifierTuningConfig.MAX_MIN);
					if (RoleModifierTuningConfig.getModifierChance(targetId) <= RoleModifierTuningConfig.CHANCE_MIN) {
						RoleModifierTuningConfig.setModifierChance(targetId, RoleModifierTuningConfig.DEFAULT_CHANCE);
					}
					setWatheExtendedEnabled(server, "hml.disabledModifiers", targetId, enabled);
				}
				RoleModifierTuningConfig.save();
				RoleModifierTuningBridge.applyConfiguredMaxima();
				return;
			}

			if (targetType.equals("role")) {
				if (setting.equals("chance")) {
					RoleModifierTuningConfig.setRoleChance(targetId, value);
				} else {
					RoleModifierTuningConfig.setRoleMax(targetId, setting.equals("enabled") && value > 0
						? RoleModifierTuningConfig.DEFAULT_MAX : value);
				}
			} else if (setting.equals("chance")) {
				RoleModifierTuningConfig.setModifierChance(targetId, value);
			} else {
				RoleModifierTuningConfig.setModifierMax(targetId, setting.equals("enabled") && value > 0
					? RoleModifierTuningConfig.DEFAULT_MAX : value);
			}
			RoleModifierTuningConfig.save();
			RoleModifierTuningBridge.applyConfiguredMaxima();
		}

		private static void setWatheExtendedEnabled(MinecraftServer server, String key, String targetId, boolean enabled) {
			if (server == null) return;
			NbtCompound snapshot = ServerConfig.buildSnapshot(server.getOverworld());
			LinkedHashSet<String> disabled = new LinkedHashSet<>();
			String csv = snapshot.contains(key) ? snapshot.getString(key) : "";
			if (!csv.isBlank()) {
				for (String raw : csv.split(",", -1)) {
					String id = raw.strip();
					if (!id.isEmpty()) disabled.add(id);
				}
			}
			if (enabled) {
				disabled.remove(targetId);
			} else {
				disabled.add(targetId);
			}
			ServerConfig.applyChanges(Map.of(key, String.join(",", disabled)), server.getOverworld());
			ServerConfig.broadcastToAll(server);
		}
	}

	private static final class ActivePoll {
		private final UUID creator;
		private final PollChange change;
		private final long endsAtTick;
		private final Map<UUID, Boolean> votes = new HashMap<>();

		private ActivePoll(UUID creator, PollChange change, long endsAtTick) {
			this.creator = creator;
			this.change = change;
			this.endsAtTick = endsAtTick;
		}

		private int yesVotes() {
			int count = 0;
			for (Boolean vote : votes.values()) {
				if (Boolean.TRUE.equals(vote)) count++;
			}
			return count;
		}

		private int noVotes() {
			int count = 0;
			for (Boolean vote : votes.values()) {
				if (Boolean.FALSE.equals(vote)) count++;
			}
			return count;
		}
	}
}
