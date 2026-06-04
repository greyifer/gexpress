package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.client.screen.WeIcons;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.AbilityCooldownPayload;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.silent.SilentShadowComponent;
import dev.mapselect.role.timemaster.TimeMasterComponent;
import dev.mapselect.role.warlock.WarlockComponent;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientAbilityCooldownHud {
	private static final int BASE_ICON_SIZE = 12;
	private static final int BASE_ICON_BAR_GAP = 5;
	private static final int BASE_BAR_WIDTH = 91;
	private static final int BASE_BAR_HEIGHT = 6;
	private static final int BASE_BAR_SPACING = 14;
	private static final int BASE_HOTBAR_SIDE_GAP = 7;
	private static final int BASE_HOTBAR_TOP_GAP = 3;
	private static final int HOTBAR_HEIGHT = 22;
	private static final int TIMER_WIDTH = 50;
	private static final int FRAME_DARK = 0xFF140801;
	private static final int FRAME_BROWN = 0xFF5B3108;
	private static final int FRAME_GOLD = 0xFFC48921;
	private static final int TRACK_DARK = 0xFF1B0B03;
	private static final int TRACK_BROWN = 0xFF2B1405;
	private static final int FILL_GOLD = 0xFFE3A12E;
	private static final int FILL_LIGHT = 0xFFFFD869;
	private static final int FILL_DARK = 0xFF8B4A11;
	private static final Identifier ICON_MEDIC_SHIELD = hudIcon("ability_medic_shield");
	private static final Identifier ICON_SHADOW_MARCH = hudIcon("ability_shadow_march");
	private static final Identifier ICON_WARLOCK_MARK = hudIcon("ability_warlock_mark");
	private static final Identifier ICON_HEX_KILL = hudIcon("ability_hex_kill");
	private static final Identifier ICON_JUGGERNAUT_WEAPONS = hudIcon("ability_hex_kill");
	private static final Identifier ICON_MASQUERADE = hudIcon("ability_masquerade");
	private static final Identifier ICON_DANCING_CARTS = hudIcon("ability_dancing_carts");
	private static final Identifier ICON_TIME_REWIND = hudIcon("ability_time_rewind");
	private static final Identifier ICON_TIME_FREEZE = hudIcon("ability_time_freeze");
	private static final Identifier ICON_PUPPET_STRINGS = hudIcon("ability_puppet_strings");
	private static final Identifier ICON_PELICAN_SWALLOW = hudIcon("ability_pelican_swallow");
	private static final Identifier ICON_SCATTER = hudIcon("ability_scatter");
	private static final Identifier ICON_TRACKER = hudIcon("ability_track");
	private static final Identifier ICON_SEER = ICON_TRACKER;
	private static final Identifier ICON_CUPID = ICON_MASQUERADE;
	private static final Identifier ICON_COVENANT_BITE = ICON_HEX_KILL;
	private static final Identifier ICON_COVENANT_BAT = ICON_MASQUERADE;
	private static final Identifier ICON_ALTRUIST = hudIcon("ability_revive");
	private static final Identifier ICON_BORROWED_SKIN = hudIcon("ability_borrowed_skin");
	private static final Identifier ICON_SPY_BUG = hudIcon("ability_bug");
	private static final Identifier ICON_MAFIA = hudIcon("ability_recruit_mafioso");
	private static final Identifier ICON_JANITOR_CLEAN = hudIcon("ability_janitor");
	private static final Identifier ICON_PICKPOCKET = hudIcon("ability_pickpocket");
	private static final Identifier ICON_COPYCAT = ICON_MASQUERADE;
	private static final Map<String, SyncedCooldown> SYNCED = new HashMap<>();
	private static final Map<String, Integer> EXTERNAL_TOTALS = new HashMap<>();
	private static Object syncedWorld;

	private ClientAbilityCooldownHud() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(AbilityCooldownPayload.ID, (payload, context) ->
			context.client().execute(() -> apply(context.client(), payload)));
		HudRenderCallback.EVENT.register(ClientAbilityCooldownHud::render);
	}

	private static void apply(MinecraftClient client, AbilityCooldownPayload payload) {
		checkSyncedWorld(client);
		if (client == null || client.world == null || payload.key().isEmpty()
				|| (payload.remainingTicks() <= 0 && payload.totalTicks() <= 0 && payload.maxUses() <= 0)) {
			SYNCED.remove(payload.key());
			return;
		}
		if (!payload.draining() && isCreativeRolePreview(client)) {
			SYNCED.remove(payload.key());
			return;
		}
		SYNCED.put(payload.key(), new SyncedCooldown(
			client.world.getTime() + payload.remainingTicks(),
			payload.totalTicks(),
			payload.draining(),
			payload.usesRemaining(),
			payload.maxUses()
		));
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (ClientHudVisibility.shouldHide(client) || client.player == null || client.world == null) return;
		boolean alive = isLocalPlayerAlive(client);
		if (!alive) return;
		checkSyncedWorld(client);

		List<AbilityBar> bars = alive ? new ArrayList<>(barsFor(client)) : new ArrayList<>();
		if (bars.isEmpty()) return;

		float scale = GexpressConfig.getAbilityHudScalePercent() / 100.0F;
		int iconSize = scaled(BASE_ICON_SIZE, scale);
		int iconBarGap = scaled(BASE_ICON_BAR_GAP, scale);
		int barWidth = scaled(BASE_BAR_WIDTH, scale);
		int barHeight = Math.max(4, scaled(BASE_BAR_HEIGHT, scale));
		int barSpacing = Math.max(scaled(BASE_BAR_SPACING, scale), iconSize + 2);
		int hotbarSideGap = scaled(BASE_HOTBAR_SIDE_GAP, scale);
		int hotbarTopGap = scaled(BASE_HOTBAR_TOP_GAP, scale);

		int hotbarRight = context.getScaledWindowWidth() / 2 + 91;
		int x = hotbarRight + hotbarSideGap + iconSize + iconBarGap + GexpressConfig.getAbilityHudOffsetX();
		x = clamp(x, 2 + iconSize + iconBarGap, context.getScaledWindowWidth() - barWidth - TIMER_WIDTH - 4);

		int hotbarTop = context.getScaledWindowHeight() - HOTBAR_HEIGHT;
		int totalIconHeight = iconSize + (bars.size() - 1) * barSpacing;
		int iconStartY = hotbarTop - hotbarTopGap - totalIconHeight + GexpressConfig.getAbilityHudOffsetY();
		iconStartY = clamp(iconStartY, 2, context.getScaledWindowHeight() - totalIconHeight - 2);
		int y = iconStartY + (iconSize - barHeight) / 2;
		TextRenderer text = client.textRenderer;
		for (int i = 0; i < bars.size(); i++) {
			drawBar(context, text, bars.get(i), x, y + i * barSpacing, iconSize, iconBarGap, barWidth, barHeight);
		}
	}

	private static List<AbilityBar> barsFor(MinecraftClient client) {
		Role role = localRole(client);
		if (role == null) return List.of();
		Identifier actualRoleId = role.identifier();
		UUID playerId = client.player.getUuid();
		boolean creativePreview = isCreativeRolePreview(client);
		List<AbilityBar> bars = new ArrayList<>();

		if (MapSelectRoles.COPYCAT_ID.equals(actualRoleId) && !ClientCopycatState.isBorrowingAbility()) {
			addCopycatBars(bars);
			return bars;
		}

		Identifier roleId = ClientCopycatState.effectiveRoleId(client, actualRoleId);
		if (MapSelectRoles.MEDIC_ID.equals(roleId)) {
			MedicShieldComponent comp = MedicShieldComponent.KEY.getNullable(client.world);
			long remaining = creativePreview || comp == null ? 0L : comp.cooldownRemainingTicks(playerId);
			bars.add(cooldown(ICON_MEDIC_SHIELD, remaining, GexpressConfig.getMedicShieldCooldownSeconds() * 20L,
				0xFF4FD889, 0xFF216B42));
		} else if (MapSelectRoles.THE_SILENT_ID.equals(roleId)) {
			SilentShadowComponent comp = SilentShadowComponent.KEY.getNullable(client.world);
			long active = comp == null ? 0L : comp.activeRemainingTicks(playerId);
			if (active > 0L) {
				bars.add(draining(ICON_SHADOW_MARCH, active, GexpressConfig.getSilentShadowDurationSeconds() * 20L,
					0xFFB16CFF, 0xFF4D236E));
			} else {
				long remaining = creativePreview || comp == null ? 0L : comp.cooldownRemainingTicks(playerId);
				bars.add(cooldown(ICON_SHADOW_MARCH, remaining, GexpressConfig.getSilentShadowCooldownSeconds() * 20L,
					0xFFB16CFF, 0xFF4D236E));
			}
		} else if (MapSelectRoles.WARLOCK_ID.equals(roleId)) {
			WarlockComponent comp = WarlockComponent.KEY.getNullable(client.world);
			long mark = creativePreview || comp == null ? 0L : comp.markCooldownRemainingTicks(playerId);
			long kill = creativePreview || comp == null ? 0L : comp.killCooldownRemainingTicks(playerId);
			bars.add(cooldown(ICON_WARLOCK_MARK, mark, GexpressConfig.getWarlockMarkCooldownSeconds() * 20L,
				0xFFD276FF, 0xFF602375));
			bars.add(cooldown(ICON_HEX_KILL, kill, GexpressConfig.getWarlockKillCooldownSeconds() * 20L,
				0xFFFF4A55, 0xFF7C151F, "", true));
		} else if (MapSelectRoles.JUGGERNAUT_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.JUGGERNAUT_WEAPONS, ICON_JUGGERNAUT_WEAPONS,
				GexpressConfig.getJuggernautInitialCooldownSeconds() * 20L, 0xFFFF7A42, 0xFF7A2418));
			if (SYNCED.containsKey(AbilityCooldownPayload.JUGGERNAUT_SHIELD)) {
				bars.add(syncedOrReady(AbilityCooldownPayload.JUGGERNAUT_SHIELD, ICON_MEDIC_SHIELD,
					GexpressConfig.getJuggernautShieldRechargeSeconds() * 20L, 0xFF9DEBFF, 0xFF225B72));
				}
		} else if (MapSelectRoles.TIME_MASTER_ID.equals(roleId)) {
			TimeMasterComponent comp = TimeMasterComponent.KEY.getNullable(client.world);
			long rewindRemaining = creativePreview || comp == null ? 0L : comp.cooldownRemainingTicks(playerId);
			int rewinds = comp == null ? GexpressConfig.getTimeMasterMaxUses() : comp.usesRemaining(playerId);
			bars.add(creativePreview
				? cooldown(ICON_TIME_REWIND, rewindRemaining, GexpressConfig.getTimeMasterCooldownSeconds() * 20L,
					0xFF57D4E6, 0xFF176B77, "inf")
				: usesCooldown(ICON_TIME_REWIND, rewindRemaining, GexpressConfig.getTimeMasterCooldownSeconds() * 20L,
					0xFF57D4E6, 0xFF176B77, GexpressConfig.getTimeMasterMaxUses(), rewinds));
			long freezeRemaining = creativePreview || comp == null ? 0L : comp.freezeCooldownRemainingTicks(playerId);
			int freezes = comp == null ? GexpressConfig.getTimeMasterFreezeMaxUses() : comp.freezeUsesRemaining(playerId);
			bars.add(creativePreview
				? cooldown(ICON_TIME_FREEZE, freezeRemaining, GexpressConfig.getTimeMasterFreezeCooldownSeconds() * 20L,
					0xFF9DEBFF, 0xFF225B72, "inf")
				: usesCooldown(ICON_TIME_FREEZE, freezeRemaining, GexpressConfig.getTimeMasterFreezeCooldownSeconds() * 20L,
					0xFF9DEBFF, 0xFF225B72, GexpressConfig.getTimeMasterFreezeMaxUses(), freezes, true));
		} else if (MapSelectRoles.TRICKSTER_ID.equals(roleId)) {
			long remaining = ClientTricksterState.remainingTicks();
			bars.add(remaining > 0L
				? draining(ICON_MASQUERADE, remaining, GexpressConfig.getTricksterSwapDurationSeconds() * 20L,
					0xFF4BE4B1, 0xFF1B775A)
				: syncedOrReady(AbilityCooldownPayload.HARLEQUIN_MASQUERADE, ICON_MASQUERADE,
					GexpressConfig.getTricksterMasqueradeCooldownSeconds() * 20L, 0xFF4BE4B1, 0xFF1B775A));
			bars.add(syncedOrReadyUses(AbilityCooldownPayload.HARLEQUIN_DANCING_CARTS, ICON_DANCING_CARTS,
				GexpressConfig.getTricksterDancingCartsCooldownSeconds() * 20L, 0xFFFFC857, 0xFF7A4D16,
				GexpressConfig.getTricksterDancingCartsMaxUses(), true));
		} else if (MapSelectRoles.PUPPETMASTER_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.PUPPETMASTER_CONTROL, ICON_PUPPET_STRINGS,
				GexpressConfig.getPuppetmasterControlCooldownSeconds() * 20L, 0xFFFF5368, 0xFF741323));
		} else if (MapSelectRoles.SCATTER_BRAIN_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.SCATTER_BRAIN_SCATTER, ICON_SCATTER,
				GexpressConfig.getScatterBrainCooldownSeconds() * 20L, 0xFFFF8A4C, 0xFF8B2E16));
		} else if (MapSelectRoles.SKINCRAWLER_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.SKINCRAWLER_STEAL, ICON_MASQUERADE,
				GexpressConfig.getSkincrawlerCooldownSeconds() * 20L, 0xFFB05A66, 0xFF4D161D));
		} else if (MapSelectRoles.SPY_ID.equals(roleId)) {
			long active = ClientSpyState.activeRemainingTicks();
			if (active > 0L) {
				bars.add(draining(ICON_TRACKER, active, GexpressConfig.getSpyBugDurationSeconds() * 20L,
					0xFF77C7FF, 0xFF234F7A));
			} else {
				bars.add(moneyCooldown(ICON_TRACKER, 0L, 1L, 0xFF77C7FF, 0xFF234F7A,
					GexpressConfig.getSpyBugCost(), moneyBalance(client.player)));
			}
		} else if (MapSelectRoles.VULTURE_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.PELICAN_SWALLOW, ICON_PELICAN_SWALLOW,
				GexpressConfig.getPelicanEatCooldownSeconds() * 20L, 0xFFC5DF5C, 0xFF607421));
		} else if (MapSelectRoles.TRACKER_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.TRACKER_TRACK, ICON_TRACKER,
				GexpressConfig.getTrackerCooldownSeconds() * 20L, 0xFF58B7FF, 0xFF1F4D7A));
		} else if (MapSelectRoles.SEER_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.SEER_COMPARE, ICON_SEER,
				GexpressConfig.getSeerCompareCooldownSeconds() * 20L, 0xFFD94B66, 0xFF6E1D31));
		} else if (MapSelectRoles.CUPID_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.CUPID_LINK, ICON_CUPID,
				GexpressConfig.getCupidPairCooldownSeconds() * 20L, 0xFFFF7DBB, 0xFF7A2351));
		} else if (MapSelectRoles.ALTRUIST_ID.equals(roleId)) {
			bars.add(cooldown(ICON_ALTRUIST, 0L, 1L, 0xFFFFE5A3, 0xFF80642B));
		} else if (MapSelectRoles.DRACULA_ID.equals(roleId) || MapSelectRoles.VAMPIRE_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.COVENANT_BITE, ICON_COVENANT_BITE,
				GexpressConfig.getCovenantBiteCooldownSeconds() * 20L, 0xFFFF5B6F, 0xFF5F0814));
			if (MapSelectRoles.DRACULA_ID.equals(roleId) && ClientCovenantState.hasActiveState()) {
				long batTicks = Math.max(0L, ClientCovenantState.batRemainingTicks());
				long batMax = Math.max(1L, ClientCovenantState.batMaxTicks());
				if (ClientCovenantState.batForm()) {
					bars.add(draining(ICON_COVENANT_BAT, batTicks, batMax, 0xFFB88BFF, 0xFF4A2868, true));
				} else {
					bars.add(cooldown(ICON_COVENANT_BAT, Math.max(0L, batMax - batTicks), batMax,
						0xFFB88BFF, 0xFF4A2868, "", true));
				}
			}
		} else if (MapSelectRoles.GODFATHER_ID.equals(roleId)) {
			if (SYNCED.containsKey(AbilityCooldownPayload.GODFATHER_RECRUIT_MAFIOSO)) {
				bars.add(syncedOrReady(AbilityCooldownPayload.GODFATHER_RECRUIT_MAFIOSO, ICON_MAFIA,
					GexpressConfig.getMafiaReplacementCooldownSeconds() * 20L, 0xFFD0D0D0, 0xFF4F4F4F));
			}
			if (SYNCED.containsKey(AbilityCooldownPayload.GODFATHER_RECRUIT_JANITOR)) {
				bars.add(syncedOrReady(AbilityCooldownPayload.GODFATHER_RECRUIT_JANITOR, ICON_JANITOR_CLEAN,
					GexpressConfig.getMafiaReplacementCooldownSeconds() * 20L, 0xFFD0D0D0, 0xFF4F4F4F));
			}
			if (SYNCED.containsKey(AbilityCooldownPayload.GODFATHER_RECRUIT_PICKPOCKET)) {
				bars.add(syncedOrReady(AbilityCooldownPayload.GODFATHER_RECRUIT_PICKPOCKET, ICON_PICKPOCKET,
					GexpressConfig.getMafiaReplacementCooldownSeconds() * 20L, 0xFFD0D0D0, 0xFF4F4F4F));
			}
			if (SYNCED.containsKey(AbilityCooldownPayload.GODFATHER_RECRUIT_BURGLAR)) {
				bars.add(syncedOrReady(AbilityCooldownPayload.GODFATHER_RECRUIT_BURGLAR, ICON_MAFIA,
					GexpressConfig.getMafiaReplacementCooldownSeconds() * 20L, 0xFFD0D0D0, 0xFF4F4F4F));
			}
		} else if (MapSelectRoles.JANITOR_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.JANITOR_CLEAN, ICON_JANITOR_CLEAN,
				GexpressConfig.getJanitorCleanCooldownSeconds() * 20L, 0xFFD0D0D0, 0xFF4F4F4F));
		} else if (MapSelectRoles.PICKPOCKET_ID.equals(roleId)) {
			bars.add(syncedOrReady(AbilityCooldownPayload.PICKPOCKET_STEAL, ICON_PICKPOCKET,
				GexpressConfig.getPickpocketMaxHoldSeconds() * 20L, 0xFFD0D0D0, 0xFF4F4F4F));
		}
		if (bars.isEmpty()) {
			addExternalAbilityBar(client, roleId, role, bars);
		}
		return bars;
	}

	private static void addCopycatBars(List<AbilityBar> bars) {
		bars.add(syncedOrReady(AbilityCooldownPayload.COPYCAT_COPY, ICON_COPYCAT,
			GexpressConfig.getCopycatCopyCooldownSeconds() * 20L, 0xFFC9B5FF, 0xFF4E3D7A));

		Identifier selectedRole = ClientCopycatState.storedRoleId();
		if (selectedRole == null) {
			bars.add(empty(ICON_COPYCAT, 0xFF776C8B, 0xFF2D2638, true));
			return;
		}
		int color = 0xFFC9B5FF;
		bars.add(cooldown(copycatStoredIcon(selectedRole), 0L,
			GexpressConfig.getCopycatCopyDurationSeconds() * 20L, color, darken(color, 92), "stored", true));
	}

	private static Identifier copycatStoredIcon(Identifier roleId) {
		if (MapSelectRoles.MEDIC_ID.equals(roleId)) return ICON_MEDIC_SHIELD;
		if (MapSelectRoles.THE_SILENT_ID.equals(roleId)) return ICON_SHADOW_MARCH;
		if (MapSelectRoles.WARLOCK_ID.equals(roleId)) return ICON_WARLOCK_MARK;
		if (MapSelectRoles.TRICKSTER_ID.equals(roleId)) return ICON_MASQUERADE;
		if (MapSelectRoles.TIME_MASTER_ID.equals(roleId)) return ICON_TIME_REWIND;
		if (MapSelectRoles.PUPPETMASTER_ID.equals(roleId)) return ICON_PUPPET_STRINGS;
		if (MapSelectRoles.SCATTER_BRAIN_ID.equals(roleId)) return ICON_SCATTER;
		if (MapSelectRoles.SKINCRAWLER_ID.equals(roleId)) return ICON_BORROWED_SKIN;
		if (MapSelectRoles.SPY_ID.equals(roleId)) return ICON_SPY_BUG;
		if (MapSelectRoles.VULTURE_ID.equals(roleId)) return ICON_PELICAN_SWALLOW;
		if (MapSelectRoles.TRACKER_ID.equals(roleId)) return ICON_TRACKER;
		if (MapSelectRoles.SEER_ID.equals(roleId)) return ICON_SEER;
		if (MapSelectRoles.CUPID_ID.equals(roleId)) return ICON_CUPID;
		if (MapSelectRoles.ALTRUIST_ID.equals(roleId)) return ICON_ALTRUIST;
		if (MapSelectRoles.DRACULA_ID.equals(roleId) || MapSelectRoles.VAMPIRE_ID.equals(roleId)) {
			return ICON_COVENANT_BITE;
		}
		if (MapSelectRoles.GODFATHER_ID.equals(roleId)) return ICON_MAFIA;
		if (MapSelectRoles.JANITOR_ID.equals(roleId)) return ICON_JANITOR_CLEAN;
		if (MapSelectRoles.PICKPOCKET_ID.equals(roleId)) return ICON_PICKPOCKET;
		return ICON_COPYCAT;
	}

	private static void addExternalAbilityBar(MinecraftClient client, Identifier roleId, Role role,
			List<AbilityBar> bars) {
		ExternalAbility external = externalAbility(roleId);
		if (external == null || client == null || client.player == null) return;
		int remaining = readExternalCooldown(client.player, external.componentId());
		if (remaining < 0) return;
		int total = externalTotal(external.componentId() + ":" + roleId, remaining,
			externalDefaultTotalTicks(roleId, external));
		int color = 0xFF000000 | (role.color() & 0x00FFFFFF);
		int cost = externalMoneyCost(client, roleId, external);
		bars.add(moneyCooldown(external.icon(), remaining, total, color, darken(color, 78),
			cost, moneyBalance(client.player)));
	}

	private static ExternalAbility externalAbility(Identifier roleId) {
		if (roleId == null) return null;
		String namespace = roleId.getNamespace();
		String path = roleId.getPath();
		if ("kinswathe".equals(namespace) && any(path,
				"bellringer", "bodymaker", "cleaner", "detective", "hunter", "judge", "robot")) {
			return new ExternalAbility(Identifier.of("kinswathe", "ability"), ICON_MASQUERADE, 60L * 20L);
		}
		if ("noellesroles".equals(namespace) && any(path,
				"recaller", "phantom", "vulture", "infected", "swapper", "voodoo", "guesser")) {
			return new ExternalAbility(Identifier.of("noellesroles", "ability"), ICON_MASQUERADE, 60L * 20L);
		}
		if ("starexpress".equals(namespace) && "starstruck".equals(path)) {
			return new ExternalAbility(Identifier.of("starexpress", "ability"), ICON_MASQUERADE, 90L * 20L);
		}
		if ("stupid_express".equals(namespace) && any(path, "thief", "necromancer")) {
			return new ExternalAbility(Identifier.of("stupid_express", "cooldown"), ICON_PICKPOCKET, 90L * 20L);
		}
		return null;
	}

	private static int readExternalCooldown(PlayerEntity player, Identifier componentId) {
		try {
			Object component = externalComponent(player, componentId);
			if (component == null) return -1;
			Integer value = readIntMember(component, "cooldown", "getCooldown", "cooldownTicks");
			return value == null ? -1 : Math.max(0, value);
		} catch (Throwable ignored) {
			return -1;
		}
	}

	private static int readExternalCost(PlayerEntity player, Identifier componentId) {
		try {
			Object component = externalComponent(player, componentId);
			if (component == null) return 0;
			Integer value = readIntMember(component,
				"cost", "getCost",
				"price", "getPrice",
				"moneyCost", "getMoneyCost",
				"abilityCost", "getAbilityCost",
				"requiredMoney", "getRequiredMoney");
			return value == null ? 0 : Math.max(0, Math.min(9999, value));
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static int externalMoneyCost(MinecraftClient client, Identifier roleId, ExternalAbility external) {
		int fallback = readExternalCost(client == null ? null : client.player,
			external == null ? null : external.componentId());
		if (roleId == null || client == null || client.world == null) return fallback;
		String namespace = roleId.getNamespace();
		String path = roleId.getPath();
		if ("kinswathe".equals(namespace)) {
			return switch (path) {
				case "bellringer" -> readWorldConfigInt(client, Identifier.of("kinswathe", "config"),
					"BellringerAbilityPrice", 200);
				case "cleaner" -> readWorldConfigInt(client, Identifier.of("kinswathe", "config"),
					"CleanerAbilityPrice", 200);
				case "detective" -> readWorldConfigInt(client, Identifier.of("kinswathe", "config"),
					"DetectiveAbilityPrice", 200);
				case "hunter" -> readWorldConfigInt(client, Identifier.of("kinswathe", "config"),
					"HunterAbilityPrice", 125);
				case "judge" -> readWorldConfigInt(client, Identifier.of("kinswathe", "config"),
					"JudgeAbilityPrice", 300);
				default -> fallback;
			};
		}
		if ("noellesroles".equals(namespace) && "recaller".equals(path)) {
			return recallerMarkerPlaced(client.player) ? 100 : fallback;
		}
		return fallback;
	}

	private static int externalDefaultTotalTicks(Identifier roleId, ExternalAbility external) {
		if (roleId == null || external == null) return 20;
		String namespace = roleId.getNamespace();
		String path = roleId.getPath();
		if ("kinswathe".equals(namespace)) {
			int seconds = switch (path) {
				case "bellringer" -> 120;
				case "bodymaker" -> 90;
				case "cleaner" -> 150;
				case "detective" -> 90;
				case "hunter" -> 5;
				case "judge" -> 180;
				case "robot" -> 90;
				default -> (int) (external.defaultTotalTicks() / 20L);
			};
			return Math.max(1, seconds * 20);
		}
		if ("noellesroles".equals(namespace)) {
			int seconds = switch (path) {
				case "recaller" -> recallerMarkerPlaced(MinecraftClient.getInstance().player) ? 30 : 10;
				case "phantom" -> 90;
				case "vulture" -> 20;
				case "infected" -> 80;
				case "swapper" -> 60;
				case "voodoo" -> 30;
				case "guesser" -> 120;
				default -> (int) (external.defaultTotalTicks() / 20L);
			};
			return Math.max(1, seconds * 20);
		}
		if ("starexpress".equals(namespace) && "starstruck".equals(path)) return 90 * 20;
		if ("stupid_express".equals(namespace)) {
			if ("necromancer".equals(path)) return 180 * 20;
			if ("thief".equals(path)) return 90 * 20;
		}
		return Math.max(1, (int) external.defaultTotalTicks());
	}

	private static int readWorldConfigInt(MinecraftClient client, Identifier componentId, String field, int fallback) {
		try {
			if (client == null || client.world == null || componentId == null) return fallback;
			ComponentKey<?> key = ComponentRegistry.get(componentId);
			if (key == null || !key.isProvidedBy(client.world)) return fallback;
			Object component = key.getNullable(client.world);
			Integer value = readIntMember(component, field, "get" + field);
			return value == null ? fallback : Math.max(0, Math.min(9999, value));
		} catch (Throwable ignored) {
			return fallback;
		}
	}

	private static boolean recallerMarkerPlaced(PlayerEntity player) {
		try {
			Object component = externalComponent(player, Identifier.of("noellesroles", "recaller"));
			Boolean placed = readBooleanMember(component, "placed", "isPlaced", "getPlaced");
			return placed != null && placed;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static int moneyBalance(PlayerEntity player) {
		try {
			PlayerShopComponent shop = player == null ? null : PlayerShopComponent.KEY.get(player);
			return shop == null ? 0 : Math.max(0, Math.min(9999, shop.balance));
		} catch (Throwable ignored) {
			return 0;
		}
	}

	private static Object externalComponent(PlayerEntity player, Identifier componentId) {
		if (player == null || componentId == null) return null;
		ComponentKey<?> key = ComponentRegistry.get(componentId);
		if (key == null || !key.isProvidedBy(player)) return null;
		return key.getNullable(player);
	}

	private static Integer readIntMember(Object component, String... names) {
		if (component == null) return null;
		Class<?> type = component.getClass();
		for (String name : names) {
			try {
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				Object value = field.get(component);
				if (value instanceof Number number) return number.intValue();
			} catch (NoSuchFieldException ignored) {
			} catch (Throwable ignored) {
				return null;
			}
			try {
				Method method = type.getMethod(name);
				Object value = method.invoke(component);
				if (value instanceof Number number) return number.intValue();
			} catch (NoSuchMethodException ignored) {
			} catch (Throwable ignored) {
				return null;
			}
		}
		return null;
	}

	private static Boolean readBooleanMember(Object component, String... names) {
		if (component == null) return null;
		Class<?> type = component.getClass();
		for (String name : names) {
			try {
				Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				Object value = field.get(component);
				if (value instanceof Boolean bool) return bool;
			} catch (NoSuchFieldException ignored) {
			} catch (Throwable ignored) {
				return null;
			}
			try {
				Method method = type.getMethod(name);
				Object value = method.invoke(component);
				if (value instanceof Boolean bool) return bool;
			} catch (NoSuchMethodException ignored) {
			} catch (Throwable ignored) {
				return null;
			}
		}
		return null;
	}

	private static int externalTotal(String key, int remaining, int fallback) {
		int total = Math.max(1, EXTERNAL_TOTALS.getOrDefault(key, Math.max(1, fallback)));
		if (remaining > total) total = remaining;
		if (remaining > 0) EXTERNAL_TOTALS.put(key, total);
		return total;
	}

	private static boolean any(String value, String... choices) {
		for (String choice : choices) {
			if (choice.equals(value)) return true;
		}
		return false;
	}

	private static AbilityBar syncedOrReady(String key, Identifier icon, long readyTotalTicks, int color, int darkColor) {
		SyncedCooldown synced = SYNCED.get(key);
		if (synced == null) return cooldown(icon, 0L, readyTotalTicks, color, darkColor);
		MinecraftClient client = MinecraftClient.getInstance();
		if (isCreativeRolePreview(client) && !synced.draining()) {
			SYNCED.remove(key);
			return cooldown(icon, 0L, readyTotalTicks, color, darkColor);
		}
		long now = client != null && client.world != null ? client.world.getTime() : 0L;
		long remaining = Math.max(0L, synced.expiresAtTick() - now);
		if (remaining <= 0L) {
			SYNCED.remove(key);
			return cooldown(icon, 0L, readyTotalTicks, color, darkColor);
		}
		return synced.draining()
			? draining(icon, remaining, synced.totalTicks(), color, darkColor)
			: cooldown(icon, remaining, synced.totalTicks(), color, darkColor);
	}

	private static AbilityBar syncedOrReadyUses(String key, Identifier icon, long readyTotalTicks, int color,
			int darkColor, int configuredMaxUses, boolean secondaryKey) {
		SyncedCooldown synced = SYNCED.get(key);
		int maxUses = Math.max(0, synced != null && synced.maxUses() > 0 ? synced.maxUses() : configuredMaxUses);
		int usesRemaining = Math.max(0, Math.min(maxUses,
			synced != null && synced.maxUses() > 0 ? synced.usesRemaining() : maxUses));
		if (synced == null) {
			return usesCooldown(icon, 0L, readyTotalTicks, color, darkColor, maxUses, usesRemaining, secondaryKey);
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (isCreativeRolePreview(client) && !synced.draining()) {
			SYNCED.remove(key);
			return usesCooldown(icon, 0L, readyTotalTicks, color, darkColor, maxUses, maxUses, secondaryKey);
		}
		long now = client != null && client.world != null ? client.world.getTime() : 0L;
		long remaining = Math.max(0L, synced.expiresAtTick() - now);
		if (remaining <= 0L && usesRemaining >= maxUses) {
			SYNCED.remove(key);
		}
		return usesCooldown(icon, remaining, synced.totalTicks() > 0 ? synced.totalTicks() : readyTotalTicks,
			color, darkColor, maxUses, usesRemaining, secondaryKey);
	}

	private static AbilityBar cooldown(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor) {
		return cooldown(icon, remainingTicks, totalTicks, color, darkColor, "");
	}

	private static AbilityBar cooldown(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor,
			String extraText) {
		return cooldown(icon, remainingTicks, totalTicks, color, darkColor, extraText, false);
	}

	private static AbilityBar cooldown(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor,
			String extraText, boolean secondaryKey) {
		long total = Math.max(1L, totalTicks);
		float progress = remainingTicks <= 0L ? 1.0F : 1.0F - Math.min(1.0F, remainingTicks / (float) total);
		return new AbilityBar(icon, remainingTicks, progress, color, darkColor, extraText == null ? "" : extraText,
			secondaryKey, 0, 0, 0, 0);
	}

	private static AbilityBar moneyCooldown(Identifier icon, long remainingTicks, long totalTicks, int color,
			int darkColor, int moneyCost, int moneyBalance) {
		long total = Math.max(1L, totalTicks);
		int cost = Math.max(0, Math.min(9999, moneyCost));
		int balance = Math.max(0, Math.min(9999, moneyBalance));
		float progress = remainingTicks <= 0L
			? (cost > 0 ? Math.min(1.0F, balance / (float) cost) : 1.0F)
			: 1.0F - Math.min(1.0F, remainingTicks / (float) total);
		return new AbilityBar(icon, remainingTicks, progress, color, darkColor, "",
			false, 0, 0, cost, balance);
	}

	private static AbilityBar draining(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor) {
		return draining(icon, remainingTicks, totalTicks, color, darkColor, false);
	}

	private static AbilityBar draining(Identifier icon, long remainingTicks, long totalTicks, int color, int darkColor,
			boolean secondaryKey) {
		long total = Math.max(1L, totalTicks);
		float progress = Math.min(1.0F, remainingTicks / (float) total);
		return new AbilityBar(icon, remainingTicks, progress, color, darkColor, "", secondaryKey, 0, 0, 0, 0);
	}

	private static AbilityBar usesCooldown(Identifier icon, long remainingTicks, long totalTicks, int color,
			int darkColor, int maxUses, int usesRemaining) {
		return usesCooldown(icon, remainingTicks, totalTicks, color, darkColor, maxUses, usesRemaining, false);
	}

	private static AbilityBar usesCooldown(Identifier icon, long remainingTicks, long totalTicks, int color,
			int darkColor, int maxUses, int usesRemaining, boolean secondaryKey) {
		long total = Math.max(1L, totalTicks);
		float progress = remainingTicks <= 0L ? 1.0F : 1.0F - Math.min(1.0F, remainingTicks / (float) total);
		int segments = Math.max(0, maxUses);
		int filled = segments <= 0 ? 0 : Math.max(0, Math.min(segments, usesRemaining));
		return new AbilityBar(icon, remainingTicks, progress, color, darkColor, "", secondaryKey, segments, filled, 0, 0);
	}

	private static AbilityBar empty(Identifier icon, int color, int darkColor, boolean secondaryKey) {
		return new AbilityBar(icon, 0L, 0.0F, color, darkColor, "", secondaryKey, 0, 0, 0, 0);
	}

	private static void drawBar(DrawContext context, TextRenderer text, AbilityBar bar, int x, int y,
			int iconSize, int iconBarGap, int barWidth, int barHeight) {
		int iconX = x - iconBarGap - iconSize;
		int iconY = y - (iconSize - barHeight) / 2;
		context.drawGuiTexture(bar.icon(), iconX, iconY, iconSize, iconSize);

		context.fill(x + 1, y + 1, x + barWidth + 1, y + barHeight + 1, 0x99000000);
		context.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, FRAME_DARK);
		context.fill(x, y, x + barWidth, y + barHeight, FRAME_BROWN);
		context.fill(x, y, x + barWidth, y + 1, FRAME_GOLD);
		context.fill(x + 1, y + 1, x + barWidth - 1, y + barHeight - 1, TRACK_DARK);
		context.fill(x + 1, y + barHeight - 2, x + barWidth - 1, y + barHeight - 1, TRACK_BROWN);

		int innerWidth = barWidth - 2;
		if (bar.segments() > 0) {
			int segments = Math.max(1, Math.min(32, bar.segments()));
			int filledSegments = Math.max(0, Math.min(segments, bar.filledSegments()));
			for (int i = 0; i < segments; i++) {
				int segmentStart = x + 1 + Math.round(i * innerWidth / (float) segments);
				int segmentEnd = x + 1 + Math.round((i + 1) * innerWidth / (float) segments);
				if (i < segments - 1) {
					context.fill(segmentEnd - 1, y + 1, segmentEnd, y + barHeight - 1, 0xAA000000);
					segmentEnd--;
				}
				if (i < filledSegments && segmentEnd > segmentStart) {
					drawFilledSpan(context, bar, segmentStart, segmentEnd, y, barHeight);
				}
			}
		} else {
			int fill = Math.round(innerWidth * Math.max(0.0F, Math.min(1.0F, bar.progress())));
			if (fill > 0) {
				int fillX = x + 1;
				drawFilledSpan(context, bar, fillX, fillX + fill, y, barHeight);
			}
		}

		String sideText = "";
		if (bar.remainingTicks() > 0L) {
			sideText = Math.max(1L, (bar.remainingTicks() + 19L) / 20L) + "s";
		}
		if (!bar.extraText().isBlank()) {
			sideText = sideText.isEmpty() ? bar.extraText() : sideText + " " + bar.extraText();
		}
		if (bar.moneyCost() > 0) {
			String moneyText = bar.moneyCost() + " " + WeIcons.COIN;
			sideText = sideText.isEmpty() ? moneyText : sideText + " " + moneyText;
		}
		String keyText = keyTextFor(bar);
		if (!keyText.isBlank()) {
			context.drawTextWithShadow(text, "[" + keyText + "]", x + barWidth + 4, y - 2, 0xFFAAAAAA);
			x += text.getWidth("[" + keyText + "]") + 7;
		}
		if (!sideText.isEmpty()) {
			int sideColor = bar.moneyCost() > 0 && bar.moneyBalance() < bar.moneyCost() && bar.remainingTicks() <= 0L
				? 0xFFFF7777
				: 0xFFFFFFFF;
			context.drawTextWithShadow(text, sideText, x + barWidth + 4, y - 2, sideColor);
		}
	}

	private static void drawFilledSpan(DrawContext context, AbilityBar bar, int fillX, int fillEnd, int y,
			int barHeight) {
		int warm = hotbarTint(bar.color(), FILL_GOLD, 170);
		int warmLight = hotbarTint(lighten(bar.color(), 36), FILL_LIGHT, 190);
		int warmDark = hotbarTint(bar.darkColor(), FILL_DARK, 190);
		context.fill(fillX, y + 1, fillEnd, y + barHeight - 1, warmDark);
		context.fill(fillX, y + 1, fillEnd, y + 2, warmLight);
		context.fill(fillX, y + 2, fillEnd, y + barHeight - 2, warm);
		context.fill(fillX, y + barHeight - 2, fillEnd, y + barHeight - 1, darken(warmDark, 18));
	}

	private static String keyTextFor(AbilityBar bar) {
		if (bar == null || bar.icon() == null) return "";
		return ClientAbilityKeys.displayName(bar.secondaryKey()
			? ClientAbilityKeys.secondaryBinding()
			: ClientAbilityKeys.primaryBinding());
	}

	private static int scaled(int value, float scale) {
		return Math.max(1, Math.round(value * scale));
	}

	private static int clamp(int value, int min, int max) {
		if (max < min) return min;
		return Math.max(min, Math.min(max, value));
	}

	private static Identifier hudIcon(String name) {
		return Identifier.of(MapSelect.MOD_ID, "hud/" + name);
	}

	private static int lighten(int color, int amount) {
		int a = color & 0xFF000000;
		int r = Math.min(255, ((color >> 16) & 0xFF) + amount);
		int g = Math.min(255, ((color >> 8) & 0xFF) + amount);
		int b = Math.min(255, (color & 0xFF) + amount);
		return a | (r << 16) | (g << 8) | b;
	}

	private static int darken(int color, int amount) {
		int a = color & 0xFF000000;
		int r = Math.max(0, ((color >> 16) & 0xFF) - amount);
		int g = Math.max(0, ((color >> 8) & 0xFF) - amount);
		int b = Math.max(0, (color & 0xFF) - amount);
		return a | (r << 16) | (g << 8) | b;
	}

	private static int hotbarTint(int color, int hotbarColor, int hotbarWeight) {
		int weight = Math.max(0, Math.min(255, hotbarWeight));
		int inverse = 255 - weight;
		int a = color & 0xFF000000;
		int r = ((((color >> 16) & 0xFF) * inverse) + (((hotbarColor >> 16) & 0xFF) * weight)) / 255;
		int g = ((((color >> 8) & 0xFF) * inverse) + (((hotbarColor >> 8) & 0xFF) * weight)) / 255;
		int b = (((color & 0xFF) * inverse) + ((hotbarColor & 0xFF) * weight)) / 255;
		return a | (r << 16) | (g << 8) | b;
	}

	private static Role localRole(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game == null ? null : game.getRole(client.player);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static boolean isLocalPlayerAlive(MinecraftClient client) {
		if (client == null || client.player == null) return false;
		if (ClientVultureState.isLocalStashed(client)) return true;
		if (isCreativeRolePreview(client)) return true;
		try {
			return GameFunctions.isPlayerAliveAndSurvival(client.player);
		} catch (Throwable ignored) {
			return !client.player.isSpectator();
		}
	}

	private static boolean isCreativeRolePreview(MinecraftClient client) {
		return ClientRoleRevealState.isCreativeRolePreview(client);
	}

	private static void checkSyncedWorld(MinecraftClient client) {
		Object world = client == null ? null : client.world;
		if (syncedWorld == world) return;
		syncedWorld = world;
		SYNCED.clear();
		EXTERNAL_TOTALS.clear();
	}

	private record AbilityBar(Identifier icon, long remainingTicks, float progress, int color, int darkColor,
	                          String extraText, boolean secondaryKey, int segments, int filledSegments,
	                          int moneyCost, int moneyBalance) {}

	private record ExternalAbility(Identifier componentId, Identifier icon, long defaultTotalTicks) {}

	private record SyncedCooldown(long expiresAtTick, int totalTicks, boolean draining,
	                              int usesRemaining, int maxUses) {}
}
