package dev.mapselect.command.admin;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.game.DeadPlayerStatus;
import dev.mapselect.game.KinsWatheSafePreparation;
import dev.mapselect.modifier.LoversManager;
import dev.mapselect.permissions.GexpressPermissions;
import dev.mapselect.role.GexpressRoleShop;
import dev.mapselect.weather.MapWeatherComponent;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class DiagnosticsCommand {
	private DiagnosticsCommand() {}

	public static LiteralArgumentBuilder<ServerCommandSource> buildTree() {
		return CommandManager.literal("diagnostics")
			.requires(GexpressPermissions::canUseDiagnostics)
			.executes(DiagnosticsCommand::runSummary)
			.then(CommandManager.literal("summary").executes(DiagnosticsCommand::runSummary))
			.then(CommandManager.literal("player")
				.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
					.executes(ctx -> runPlayers(ctx, GameProfileArgumentType.getProfileArgument(ctx, "players")))));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> buildDebugTree() {
		return CommandManager.literal("debugplayer")
			.requires(GexpressPermissions::canUseDiagnostics)
			.then(CommandManager.argument("players", GameProfileArgumentType.gameProfile())
				.executes(ctx -> runPlayers(ctx, GameProfileArgumentType.getProfileArgument(ctx, "players"))));
	}

	private static int runSummary(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource src = ctx.getSource();
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(src.getWorld());
		MapWeatherComponent weather = MapWeatherComponent.KEY.getNullable(src.getWorld());
		String status = game == null ? "unknown" : game.getGameStatus().name();
		String mode = game == null || game.getGameMode() == null ? "unknown" : game.getGameMode().toString();
		String map = weather == null || weather.getCurrentMapName() == null ? "none" : weather.getCurrentMapName();
		int online = src.getServer().getPlayerManager().getCurrentPlayerCount();
		int pairs = LoversManager.pairViews(src.getWorld()).size();

		src.sendFeedback(() -> Text.literal("G'Express diagnostics").formatted(Formatting.GOLD), false);
		send(src, "Game", "status=" + status + ", mode=" + mode + ", safePrep="
			+ KinsWatheSafePreparation.isActive(src.getWorld()) + ", currentMap=" + map);
		send(src, "Players", "online=" + online + ", loversPairs=" + pairs);
		send(src, "Config", "version=" + GexpressConfig.loadedConfigVersion() + "/"
			+ GexpressConfig.CURRENT_CONFIG_VERSION + ", customRoleCounts="
			+ GexpressConfig.useCustomRoleCounts() + ", maxKillers=" + GexpressConfig.getMaxKillerAmount()
			+ ", maxModifiers=" + GexpressConfig.getMaxModifiersPerPlayer());
		for (LoversManager.PairView pair : LoversManager.pairViews(src.getWorld())) {
			send(src, "Lovers", pair.firstName() + " <-> " + pair.secondName());
		}
		return 1;
	}

	private static int runPlayers(CommandContext<ServerCommandSource> ctx, Collection<GameProfile> profiles) {
		ServerCommandSource src = ctx.getSource();
		int count = 0;
		for (GameProfile profile : profiles) {
			if (profile == null || profile.getId() == null) continue;
			ServerPlayerEntity player = src.getServer().getPlayerManager().getPlayer(profile.getId());
			if (player == null) {
				send(src, profile.getName(), "offline");
				continue;
			}
			dumpPlayer(src, player);
			count++;
		}
		return count;
	}

	private static void dumpPlayer(ServerCommandSource src, ServerPlayerEntity player) {
		GameWorldComponent game = GameWorldComponent.KEY.getNullable(player.getWorld());
		Role role = game == null ? null : game.getRole(player);
		PlayerShopComponent shop = PlayerShopComponent.KEY.getNullable(player);
		List<ShopEntry> resolvedShop = GexpressRoleShop.resolve(player);
		UUID partner = LoversManager.partnerId(player.getUuid());

		send(src, player.getName().getString(), "role=" + roleId(role)
			+ ", modifiers=" + modifierIds(player)
			+ ", dead=" + DeadPlayerStatus.isDeadRoundParticipant(player)
			+ ", safePrep=" + KinsWatheSafePreparation.isActive(player.getWorld()));
		send(src, "  economy", "watheGold=" + (shop == null ? "unknown" : shop.balance)
			+ ", customShop=" + GexpressRoleShop.hasCustomShop(player)
			+ ", shopEntries=" + resolvedShop.size()
			+ ", moneyHud=" + GexpressRoleShop.showsMoneyHud(player));
		send(src, "  lovers", partner == null ? "none" : partner.toString());
		send(src, "  tags", GexpressPermissions.effectiveTagInfos(player).stream()
			.map(GexpressPermissions.TagInfo::id).toList().toString());
	}

	private static String roleId(Role role) {
		return role == null || role.identifier() == null ? "none" : role.identifier().toString();
	}

	private static List<String> modifierIds(ServerPlayerEntity player) {
		WorldModifierComponent component = WorldModifierComponent.KEY.getNullable(player.getWorld());
		if (component == null) return List.of();
		List<Modifier> modifiers = component.getModifiers(player.getUuid());
		if (modifiers == null || modifiers.isEmpty()) return List.of();
		return modifiers.stream()
			.filter(modifier -> modifier != null && modifier.identifier() != null)
			.map(modifier -> modifier.identifier().toString())
			.toList();
	}

	private static void send(ServerCommandSource src, String label, String value) {
		src.sendFeedback(() -> Text.literal(label + ": ").formatted(Formatting.GRAY)
			.append(Text.literal(value).formatted(Formatting.WHITE)), false);
	}
}
