package dev.mapselect.client.screen;

import cat.rezelyn.watheextended.api.hml.ConfigHelper;
import cat.rezelyn.watheextended.api.hml.ModifiersDisplay;
import cat.rezelyn.watheextended.api.wathe.RolesDisplay;
import cat.rezelyn.watheextended.client.screen.config.OptionsCategory;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.config.RoleModifierTuningConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class GexpressGameCategory {
	private GexpressGameCategory() {}

	private static final String BOMB_SPECIALIST_ID = MapSelect.MOD_ID + ":bomb_specialist";
	private static final String MEDIC_ID = MapSelect.MOD_ID + ":medic";
	private static final String SNITCH_ID = MapSelect.MOD_ID + ":snitch";
	private static final String SEER_ID = MapSelect.MOD_ID + ":seer";
	private static final String TIME_MASTER_ID = MapSelect.MOD_ID + ":time_master";
	private static final String THE_SILENT_ID = MapSelect.MOD_ID + ":the_silent";
	private static final String WARLOCK_ID = MapSelect.MOD_ID + ":warlock";
	private static final String JUGGERNAUT_ID = MapSelect.MOD_ID + ":juggernaut";
	private static final String TRICKSTER_ID = MapSelect.MOD_ID + ":trickster";
	private static final String PUPPETMASTER_ID = MapSelect.MOD_ID + ":puppetmaster";
	private static final String BOUNTY_HUNTER_ID = MapSelect.MOD_ID + ":bounty_hunter";
	private static final String VULTURE_ID = MapSelect.MOD_ID + ":pelican";
	private static final String SCATTER_BRAIN_ID = MapSelect.MOD_ID + ":scatter_brain";
	private static final String SKINCRAWLER_ID = MapSelect.MOD_ID + ":skincrawler";
	private static final String TRACKER_ID = MapSelect.MOD_ID + ":tracker";
	private static final String SPY_ID = MapSelect.MOD_ID + ":spy";
	private static final String ALTRUIST_ID = MapSelect.MOD_ID + ":altruist";
	private static final String GODFATHER_ID = MapSelect.MOD_ID + ":godfather";
	private static final String MAFIOSO_ID = MapSelect.MOD_ID + ":mafioso";
	private static final String JANITOR_ID = MapSelect.MOD_ID + ":janitor";
	private static final Set<String> MAFIA_ROLE_IDS = Set.of(GODFATHER_ID, MAFIOSO_ID, JANITOR_ID);
	private static final String EOD_SPECIALIST_ID = MapSelect.MOD_ID + ":eod_specialist";
	private static final String SHORT_SIGHTED_ID = MapSelect.MOD_ID + ":short_sighted";
	private static final String HUNGRY_ID = MapSelect.MOD_ID + ":hungry";
	private static final String THIRSTY_ID = MapSelect.MOD_ID + ":thirsty";
	private static final String SQUEAKER_ID = MapSelect.MOD_ID + ":squeaker";
	private static final String KILLER_ID = "wathe:killer";
	private static final String VIGILANTE_ID = "wathe:vigilante";
	private static final String CIVILIAN_ID = "wathe:civilian";
	private static final String DISCOVERY_CIVILIAN_ID = "wathe:discovery_civilian";
	private static final String LOOSE_END_ID = "wathe:loose_end";

	private static final String WE_GROUP_PREFIX = "gui.watheextended.config.category.options.group.";
	private static final String WE_GAMERULES_KEY = WE_GROUP_PREFIX + "gamerules";
	private static final String WE_OPTIONS_KEY = WE_GROUP_PREFIX + "wathe_options";
	private static final String WE_ROLES_KEY = WE_GROUP_PREFIX + "roles_options";
	private static final String WE_MODIFIERS_KEY = WE_GROUP_PREFIX + "modifiers_options";
	private static final String WE_ROLES_OPTS_PREFIX = WE_ROLES_KEY + ".";
	private static final String WE_MOD_OPTS_PREFIX = WE_MODIFIERS_KEY + ".";

	public static ConfigCategory build(Screen parent, BiConsumer<String, Screen> stage) {
		ConfigCategory.Builder category = ConfigCategory.createBuilder()
			.name(Text.translatable("gui.gexpress.config.category.game"))
			.tooltip(Text.translatable("gui.gexpress.config.category.game.tooltip"));

		List<OptionGroup> weGroups = tryBuildWeGroups(parent, stage);

		Map<String, OptionGroup> weGroupByKey = new LinkedHashMap<>();
		for (OptionGroup g : weGroups) {
			String key = extractTranslationKey(g.name());
			if (key != null) weGroupByKey.put(key, g);
		}

		OptionGroup gamerules = weGroupByKey.get(WE_GAMERULES_KEY);
		if (gamerules != null) category.group(gamerules);
		OptionGroup watheOptions = weGroupByKey.get(WE_OPTIONS_KEY);
		if (watheOptions != null) category.group(watheOptions);

		Map<String, List<Option<?>>> roleKeyToWeOpts = new LinkedHashMap<>();
		List<Option<?>> globalRoleOpts = new ArrayList<>();
		OptionGroup rolesGroup = weGroupByKey.get(WE_ROLES_KEY);
		if (rolesGroup != null) parseWeGroup(rolesGroup, WE_ROLES_OPTS_PREFIX, roleKeyToWeOpts, globalRoleOpts);
		globalRoleOpts.add(buildUseCustomRoleCountsOption());
		Option<Integer> maxKillers = buildMaxKillerAmountOption();
		Option<Integer> maxVigilantes = buildMaxVigilanteAmountOption();
		Option<Integer> playersPerKiller = buildPlayersPerKillerOption();
		Option<Integer> playersPerVigilante = buildPlayersPerVigilanteOption();
		OptionVisibility.setHiddenWhen(maxKillers, () -> !GexpressConfig.useCustomRoleCounts());
		OptionVisibility.setHiddenWhen(maxVigilantes, () -> !GexpressConfig.useCustomRoleCounts());
		OptionVisibility.setHiddenWhen(playersPerKiller, GexpressConfig::useCustomRoleCounts);
		OptionVisibility.setHiddenWhen(playersPerVigilante, GexpressConfig::useCustomRoleCounts);
		globalRoleOpts.add(maxKillers);
		globalRoleOpts.add(maxVigilantes);
		globalRoleOpts.add(playersPerKiller);
		globalRoleOpts.add(playersPerVigilante);
		globalRoleOpts.add(buildMafiaMinimumPlayersOption());
		globalRoleOpts.add(buildLastDeathShieldOption());
		globalRoleOpts.add(buildGuardianAngelAllowNonInnocentsOption());
		globalRoleOpts.add(buildPassiveIncomeOption("killer", 5,
			GexpressConfig::getPassiveIncomeKiller, v -> GexpressConfig.passiveIncomeKiller = v));
		globalRoleOpts.add(buildPassiveIncomeOption("civilian", 0,
			GexpressConfig::getPassiveIncomeCivilian, v -> GexpressConfig.passiveIncomeCivilian = v));
		globalRoleOpts.add(buildPassiveIncomeOption("neutral", 5,
			GexpressConfig::getPassiveIncomeNeutral, v -> GexpressConfig.passiveIncomeNeutral = v));
		globalRoleOpts.add(buildPassiveIncomeOption("mafia", 5,
			GexpressConfig::getPassiveIncomeMafia, v -> GexpressConfig.passiveIncomeMafia = v));

		Map<String, List<Option<?>>> modKeyToWeOpts = new LinkedHashMap<>();
		List<Option<?>> globalModOpts = new ArrayList<>();
		OptionGroup modsGroup = weGroupByKey.get(WE_MODIFIERS_KEY);
		if (modsGroup != null) parseWeGroup(modsGroup, WE_MOD_OPTS_PREFIX, modKeyToWeOpts, globalModOpts);

		if (!globalRoleOpts.isEmpty()) {
			category.group(buildPassthroughGroup("gui.gexpress.config.group.roles.global", globalRoleOpts));
		}

		Map<String, RolesDisplay.RoleDisplay> roleDisplay;
		try {
			roleDisplay = RolesDisplay.get();
		} catch (Throwable t) {
			roleDisplay = Map.of();
			category.group(errorGroup("gui.gexpress.config.group.roles.error"));
		}

		for (OptionGroup sideGroup : buildRoleSideGroups(roleDisplay, roleKeyToWeOpts)) {
			category.group(sideGroup);
		}

		category.group(buildModifiersGroup(modKeyToWeOpts));

		if (!globalModOpts.isEmpty()) {
			category.group(buildPassthroughGroup("gui.gexpress.config.group.modifiers.global", globalModOpts));
		}

		return category.build();
	}

	private static List<OptionGroup> tryBuildWeGroups(Screen parent, BiConsumer<String, Screen> stage) {
		try {
			return new ArrayList<>(OptionsCategory.build(parent, stage).groups());
		} catch (Throwable t) {
			MapSelect.LOGGER.warn("[gexpress] Failed to build WE OptionsCategory", t);
			return List.of();
		}
	}

	private static void parseWeGroup(OptionGroup group, String prefix,
	                                  Map<String, List<Option<?>>> keyToOpts, List<Option<?>> global) {
		for (Option<?> opt : group.options()) {
			if (opt instanceof LabelOption) continue;
			String transKey = extractTranslationKey(opt.name());
			if (transKey == null || !transKey.startsWith(prefix)) continue;
			String suffix = transKey.substring(prefix.length());
			if (!suffix.startsWith("opt.")) continue;
			String optKey = suffix.substring("opt.".length());
			if (isRetiredHarpyAssignmentOption(prefix, optKey)) continue;
			int dotIdx = optKey.indexOf('.');
			if (dotIdx > 0) {
				String role = optKey.substring(0, dotIdx);
				keyToOpts.computeIfAbsent(role, k -> new ArrayList<>()).add(opt);
			} else {
				global.add(opt);
			}
		}
	}

	private static boolean isRetiredHarpyAssignmentOption(String prefix, String optKey) {
		if (WE_ROLES_OPTS_PREFIX.equals(prefix)) {
			return "roledividend_killer".equals(optKey) || "roledividend_vigilante".equals(optKey);
		}
		if (WE_MOD_OPTS_PREFIX.equals(prefix)) {
			return "maximum".equals(optKey) || "multiplier".equals(optKey);
		}
		return false;
	}

	private static String extractTranslationKey(Text text) {
		if (text == null) return null;
		if (text.getContent() instanceof TranslatableTextContent tc) return tc.getKey();
		return null;
	}

	private static String roleIdToWeKey(String roleId) {
		int colon = roleId.indexOf(':');
		String name = colon < 0 ? roleId : roleId.substring(colon + 1);
		return name.replace("_", "").toLowerCase();
	}

	private static List<OptionGroup> buildRoleSideGroups(Map<String, RolesDisplay.RoleDisplay> display,
	                                                      Map<String, List<Option<?>>> weOptsByRole) {
		Map<RolesDisplay.Side, List<String>> bySide = new EnumMap<>(RolesDisplay.Side.class);
		bySide.put(RolesDisplay.Side.KILLER, new ArrayList<>());
		bySide.put(RolesDisplay.Side.INNOCENT, new ArrayList<>());
		bySide.put(RolesDisplay.Side.NEUTRAL, new ArrayList<>());
		List<String> mafiaRoles = new ArrayList<>();

		for (Map.Entry<String, RolesDisplay.RoleDisplay> e : display.entrySet()) {
			if (isBuiltinNonConfigRole(e.getKey()) || isRetiredExternalVulture(e.getKey())) continue;
			if (isMafiaRoleId(e.getKey())) {
				mafiaRoles.add(e.getKey());
				continue;
			}
			RolesDisplay.RoleDisplay rd = e.getValue();
			RolesDisplay.Side side = rd != null ? rd.side() : RolesDisplay.Side.NEUTRAL;
			if (JUGGERNAUT_ID.equals(e.getKey()) || VULTURE_ID.equals(e.getKey())) side = RolesDisplay.Side.NEUTRAL;
			bySide.get(side).add(e.getKey());
		}
		if (!bySide.get(RolesDisplay.Side.KILLER).contains(KILLER_ID)) {
			bySide.get(RolesDisplay.Side.KILLER).add(0, KILLER_ID);
		}
		if (!bySide.get(RolesDisplay.Side.INNOCENT).contains(VIGILANTE_ID)) {
			bySide.get(RolesDisplay.Side.INNOCENT).add(0, VIGILANTE_ID);
		}

		List<OptionGroup> groups = new ArrayList<>();
		for (RolesDisplay.Side side : List.of(RolesDisplay.Side.KILLER, RolesDisplay.Side.INNOCENT, RolesDisplay.Side.NEUTRAL)) {
			List<String> roleIds = bySide.get(side);
			if (roleIds.isEmpty()) continue;
			groups.add(buildRoleSideGroup(side, roleIds, display, weOptsByRole));
		}
		if (!mafiaRoles.isEmpty()) {
			groups.add(buildRoleGroup(Text.translatable("gui.gexpress.config.group.roles.mafia").formatted(Formatting.DARK_GRAY),
				mafiaRoles, display, weOptsByRole));
		}
		return groups;
	}

	private static OptionGroup buildRoleSideGroup(RolesDisplay.Side side, List<String> roleIds,
	                                                Map<String, RolesDisplay.RoleDisplay> display,
	                                                Map<String, List<Option<?>>> weOptsByRole) {
		return buildRoleGroup(sideTitle(side), roleIds, display, weOptsByRole);
	}

	private static OptionGroup buildRoleGroup(Text title, List<String> roleIds,
	                                                Map<String, RolesDisplay.RoleDisplay> display,
	                                                Map<String, List<Option<?>>> weOptsByRole) {
		OptionGroup.Builder g = OptionGroup.createBuilder()
			.name(title)
			.collapsed(false);

		for (String id : roleIds) {
			RolesDisplay.RoleDisplay rd = display.get(id);

			List<Option<?>> subOptions = new ArrayList<>();
			Option<Integer> chance = buildRoleChanceOption(id);
			Option<Integer> max = buildRoleMaxOption(id);
			subOptions.add(chance);
			subOptions.add(max);
			subOptions.addAll(buildRoleSubOptions(id));
			subOptions.addAll(weOptsByRole.getOrDefault(roleIdToWeKey(id), List.of()));

			Option<Boolean> toggle = buildRoleToggle(id, rd);
			attachDropdown(g, toggle, subOptions);
		}
		return g.build();
	}

	private static Option<Boolean> buildRoleToggle(String id, RolesDisplay.RoleDisplay rd) {
		MutableText name = colorizedRoleName(rd, id);
		OptionDescription desc = buildRoleDescription(id, rd);

		return Option.<Boolean>createBuilder()
			.name(name)
			.description(desc)
			.binding(true, () -> {
				if (GexpressOptionsScreen.pendingRoleState.containsKey(id)) {
					return GexpressOptionsScreen.pendingRoleState.get(id);
				}
				return !ConfigHelper.getDisabledRoles().contains(id);
			}, v -> {
				GexpressOptionsScreen.pendingRoleState.put(id, v);
				GexpressOptionsScreen.stageChatCommand("setEnabledRole " + id + " " + v);
			})
			.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
				.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
			.build();
	}

	private static Option<Integer> buildRoleChanceOption(String id) {
		return Option.<Integer>createBuilder()
			.name(indented(Text.translatable("gui.gexpress.config.option.tuning.role_chance")))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.tuning.role_chance.tooltip")))
			.binding(RoleModifierTuningConfig.DEFAULT_CHANCE,
				() -> GexpressOptionsScreen.getRoleChance(id),
				v -> GexpressOptionsScreen.setRoleChance(id, v))
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX))
			.build();
	}

	private static Option<Boolean> buildUseCustomRoleCountsOption() {
		return Option.<Boolean>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.use_custom_role_counts"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.use_custom_role_counts.tooltip")))
			.binding(true, GexpressConfig::useCustomRoleCounts, v -> GexpressConfig.useCustomRoleCounts = v)
			.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
				.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
			.build();
	}

	private static Option<Integer> buildMaxKillerAmountOption() {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.max_killer_amount"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.max_killer_amount.tooltip")))
			.binding(64, GexpressConfig::getMaxKillerAmount, v -> GexpressConfig.maxKillerAmount = v)
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(GexpressConfig.MAX_KILLER_AMOUNT_MIN, GexpressConfig.MAX_KILLER_AMOUNT_MAX))
			.build();
	}

	private static Option<Integer> buildPlayersPerKillerOption() {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.players_per_killer"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.players_per_killer.tooltip")))
			.binding(6, GexpressConfig::getPlayersPerKiller, v -> GexpressConfig.playersPerKiller = v)
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(GexpressConfig.PLAYERS_PER_KILLER_MIN, GexpressConfig.PLAYERS_PER_KILLER_MAX))
			.build();
	}

	private static Option<Integer> buildPlayersPerVigilanteOption() {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.players_per_vigilante"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.players_per_vigilante.tooltip")))
			.binding(8, GexpressConfig::getPlayersPerVigilante, v -> GexpressConfig.playersPerVigilante = v)
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(GexpressConfig.PLAYERS_PER_VIGILANTE_MIN, GexpressConfig.PLAYERS_PER_VIGILANTE_MAX))
			.build();
	}

	private static Option<Integer> buildMaxVigilanteAmountOption() {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.max_vigilante_amount"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.max_vigilante_amount.tooltip")))
			.binding(1, GexpressConfig::getMaxVigilanteAmount, v -> GexpressConfig.maxVigilanteAmount = v)
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(GexpressConfig.MAX_VIGILANTE_AMOUNT_MIN, GexpressConfig.MAX_VIGILANTE_AMOUNT_MAX))
			.build();
	}

	private static Option<Integer> buildMafiaMinimumPlayersOption() {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.watheextended.config.option.gexpress.mafia_minimum_players"))
			.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.mafia_minimum_players.tooltip")))
			.binding(15, GexpressConfig::getMafiaMinimumPlayers, v -> GexpressConfig.mafiaMinimumPlayers = v)
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(GexpressConfig.MAFIA_MINIMUM_PLAYERS_MIN, GexpressConfig.MAFIA_MINIMUM_PLAYERS_MAX))
			.build();
	}

	private static Option<Boolean> buildLastDeathShieldOption() {
		return Option.<Boolean>createBuilder()
			.name(Text.translatable("gui.watheextended.config.option.gexpress.last_death_shield"))
			.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.last_death_shield.tooltip")))
			.binding(false, GexpressConfig::isLastDeathShieldEnabled, v -> GexpressConfig.lastDeathShieldEnabled = v)
			.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
				.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
			.build();
	}

	private static Option<Boolean> buildGuardianAngelAllowNonInnocentsOption() {
		return Option.<Boolean>createBuilder()
			.name(Text.translatable("gui.watheextended.config.option.gexpress.guardian_angel_allow_non_innocents"))
			.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.guardian_angel_allow_non_innocents.tooltip")))
			.binding(false, GexpressConfig::canGuardianAngelPickNonInnocents,
				v -> GexpressConfig.guardianAngelAllowNonInnocents = v)
			.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
				.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
			.build();
	}

	private static Option<Integer> buildPassiveIncomeOption(String key, int defaultValue, Supplier<Integer> getter,
			Consumer<Integer> setter) {
		return Option.<Integer>createBuilder()
			.name(Text.translatable("gui.gexpress.config.option.passive_income_" + key))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.passive_income_" + key + ".tooltip")))
			.binding(defaultValue, getter, setter)
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(GexpressConfig.PASSIVE_INCOME_MIN, GexpressConfig.PASSIVE_INCOME_MAX))
			.build();
	}

	private static Option<Integer> buildRoleMaxOption(String id) {
		return Option.<Integer>createBuilder()
			.name(indented(Text.translatable("gui.gexpress.config.option.tuning.role_max")))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.tuning.role_max.tooltip")))
			.binding(RoleModifierTuningConfig.DEFAULT_MAX,
				() -> GexpressOptionsScreen.getRoleMax(id),
				v -> GexpressOptionsScreen.setRoleMax(id, v))
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
			.build();
	}

	private static List<Option<?>> buildRoleSubOptions(String roleId) {
		if (BOMB_SPECIALIST_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.c4_price")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.c4_price.tooltip")))
				.binding(500, GexpressConfig::getC4Price, v -> GexpressConfig.c4Price = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.C4_PRICE_MIN, GexpressConfig.C4_PRICE_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.c4_fuse_seconds")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.c4_fuse_seconds.tooltip")))
				.binding(15, GexpressConfig::getC4FuseSeconds, v -> GexpressConfig.c4FuseSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.C4_FUSE_MIN, GexpressConfig.C4_FUSE_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.c4_first_beep_seconds")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.c4_first_beep_seconds.tooltip")))
				.binding(3, GexpressConfig::getC4FirstBeepSeconds, v -> GexpressConfig.c4FirstBeepSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.C4_FIRST_BEEP_MIN, GexpressConfig.C4_FIRST_BEEP_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.grenade_price")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.grenade_price.tooltip")))
				.binding(150, GexpressConfig::getGrenadePrice, v -> GexpressConfig.grenadePrice = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.GRENADE_PRICE_MIN, GexpressConfig.GRENADE_PRICE_MAX))
				.build());
			return out;
		}
		if (MEDIC_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.medic_shield_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.medic_shield_cooldown.tooltip")))
				.binding(90, GexpressConfig::getMedicShieldCooldownSeconds,
					v -> GexpressConfig.medicShieldCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.MEDIC_SHIELD_COOLDOWN_MIN, GexpressConfig.MEDIC_SHIELD_COOLDOWN_MAX))
				.build());
			out.add(Option.<Boolean>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.medic_shield_knife_breaks")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.medic_shield_knife_breaks.tooltip")))
				.binding(false, GexpressConfig::doesMedicShieldKnifeBreaks,
					v -> GexpressConfig.medicShieldKnifeBreaks = v)
				.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
					.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
				.build());
			return out;
		}
		if (SNITCH_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.snitch_tasks_required")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.snitch_tasks_required.tooltip")))
				.binding(3, GexpressConfig::getSnitchTasksRequired,
					v -> GexpressConfig.snitchTasksRequired = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SNITCH_TASKS_REQUIRED_MIN, GexpressConfig.SNITCH_TASKS_REQUIRED_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.snitch_warning_tasks_remaining")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.snitch_warning_tasks_remaining.tooltip")))
				.binding(1, GexpressConfig::getSnitchWarningTasksRemaining,
					v -> GexpressConfig.snitchWarningTasksRemaining = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SNITCH_WARNING_TASKS_REMAINING_MIN,
						GexpressConfig.SNITCH_WARNING_TASKS_REMAINING_MAX))
				.build());
			return out;
		}
		if (TIME_MASTER_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.time_master_rewind_seconds")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.time_master_rewind_seconds.tooltip")))
				.binding(10, GexpressConfig::getTimeMasterRewindSeconds,
					v -> GexpressConfig.timeMasterRewindSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TIME_MASTER_REWIND_SECONDS_MIN, GexpressConfig.TIME_MASTER_REWIND_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.time_master_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.time_master_cooldown.tooltip")))
				.binding(120, GexpressConfig::getTimeMasterCooldownSeconds,
					v -> GexpressConfig.timeMasterCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TIME_MASTER_COOLDOWN_SECONDS_MIN, GexpressConfig.TIME_MASTER_COOLDOWN_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.time_master_max_uses")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.time_master_max_uses.tooltip")))
				.binding(1, GexpressConfig::getTimeMasterMaxUses,
					v -> GexpressConfig.timeMasterMaxUses = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TIME_MASTER_MAX_USES_MIN, GexpressConfig.TIME_MASTER_MAX_USES_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_duration")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_duration.tooltip")))
				.binding(4, GexpressConfig::getTimeMasterFreezeDurationSeconds,
					v -> GexpressConfig.timeMasterFreezeDurationSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TIME_MASTER_FREEZE_DURATION_SECONDS_MIN, GexpressConfig.TIME_MASTER_FREEZE_DURATION_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_cooldown.tooltip")))
				.binding(30, GexpressConfig::getTimeMasterFreezeCooldownSeconds,
					v -> GexpressConfig.timeMasterFreezeCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TIME_MASTER_FREEZE_COOLDOWN_SECONDS_MIN, GexpressConfig.TIME_MASTER_FREEZE_COOLDOWN_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_max_uses")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_max_uses.tooltip")))
				.binding(3, GexpressConfig::getTimeMasterFreezeMaxUses,
					v -> GexpressConfig.timeMasterFreezeMaxUses = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TIME_MASTER_FREEZE_MAX_USES_MIN, GexpressConfig.TIME_MASTER_FREEZE_MAX_USES_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.time_master_freeze_range.tooltip")))
				.binding(8, GexpressConfig::getTimeMasterFreezeRange,
					v -> GexpressConfig.timeMasterFreezeRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TIME_MASTER_FREEZE_RANGE_MIN, GexpressConfig.TIME_MASTER_FREEZE_RANGE_MAX))
				.build());
			return out;
		}
		if (THE_SILENT_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.silent_shadow_duration")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.silent_shadow_duration.tooltip")))
				.binding(8, GexpressConfig::getSilentShadowDurationSeconds,
					v -> GexpressConfig.silentShadowDurationSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SILENT_SHADOW_DURATION_MIN, GexpressConfig.SILENT_SHADOW_DURATION_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.silent_shadow_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.silent_shadow_cooldown.tooltip")))
				.binding(35, GexpressConfig::getSilentShadowCooldownSeconds,
					v -> GexpressConfig.silentShadowCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SILENT_SHADOW_COOLDOWN_MIN, GexpressConfig.SILENT_SHADOW_COOLDOWN_MAX))
				.build());
			return out;
		}
		if (WARLOCK_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.warlock_mark_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.warlock_mark_cooldown.tooltip")))
				.binding(20, GexpressConfig::getWarlockMarkCooldownSeconds,
					v -> GexpressConfig.warlockMarkCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.WARLOCK_MARK_COOLDOWN_MIN, GexpressConfig.WARLOCK_MARK_COOLDOWN_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.warlock_kill_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.warlock_kill_cooldown.tooltip")))
				.binding(45, GexpressConfig::getWarlockKillCooldownSeconds,
					v -> GexpressConfig.warlockKillCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.WARLOCK_KILL_COOLDOWN_MIN, GexpressConfig.WARLOCK_KILL_COOLDOWN_MAX))
				.build());
			return out;
		}
		if (JUGGERNAUT_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_initial_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_initial_cooldown.tooltip")))
				.binding(60, GexpressConfig::getJuggernautInitialCooldownSeconds,
					v -> GexpressConfig.juggernautInitialCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.JUGGERNAUT_INITIAL_COOLDOWN_MIN, GexpressConfig.JUGGERNAUT_INITIAL_COOLDOWN_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_cooldown_reduction")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_cooldown_reduction.tooltip")))
				.binding(20, GexpressConfig::getJuggernautCooldownReductionSeconds,
					v -> GexpressConfig.juggernautCooldownReductionSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.JUGGERNAUT_COOLDOWN_REDUCTION_MIN, GexpressConfig.JUGGERNAUT_COOLDOWN_REDUCTION_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_minimum_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_minimum_cooldown.tooltip")))
				.binding(10, GexpressConfig::getJuggernautMinimumCooldownSeconds,
					v -> GexpressConfig.juggernautMinimumCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.JUGGERNAUT_MINIMUM_COOLDOWN_MIN, GexpressConfig.JUGGERNAUT_MINIMUM_COOLDOWN_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_shield_recharge")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.juggernaut_shield_recharge.tooltip")))
				.binding(60, GexpressConfig::getJuggernautShieldRechargeSeconds,
					v -> GexpressConfig.juggernautShieldRechargeSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.JUGGERNAUT_SHIELD_RECHARGE_MIN, GexpressConfig.JUGGERNAUT_SHIELD_RECHARGE_MAX))
				.build());
			return out;
		}
		if (TRICKSTER_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.trickster_swap_duration")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.trickster_swap_duration.tooltip")))
				.binding(25, GexpressConfig::getTricksterSwapDurationSeconds,
					v -> GexpressConfig.tricksterSwapDurationSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TRICKSTER_SWAP_DURATION_MIN, GexpressConfig.TRICKSTER_SWAP_DURATION_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.trickster_masquerade_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.trickster_masquerade_cooldown.tooltip")))
				.binding(35, GexpressConfig::getTricksterMasqueradeCooldownSeconds,
					v -> GexpressConfig.tricksterMasqueradeCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TRICKSTER_MASQUERADE_COOLDOWN_MIN,
						GexpressConfig.TRICKSTER_MASQUERADE_COOLDOWN_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.trickster_dancing_carts_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.trickster_dancing_carts_cooldown.tooltip")))
				.binding(45, GexpressConfig::getTricksterDancingCartsCooldownSeconds,
					v -> GexpressConfig.tricksterDancingCartsCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TRICKSTER_DANCING_CARTS_COOLDOWN_MIN,
						GexpressConfig.TRICKSTER_DANCING_CARTS_COOLDOWN_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.trickster_dancing_carts_max_uses")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.trickster_dancing_carts_max_uses.tooltip")))
				.binding(1, GexpressConfig::getTricksterDancingCartsMaxUses,
					v -> GexpressConfig.tricksterDancingCartsMaxUses = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TRICKSTER_DANCING_CARTS_MAX_USES_MIN,
						GexpressConfig.TRICKSTER_DANCING_CARTS_MAX_USES_MAX))
				.build());
			return out;
		}
		if (PUPPETMASTER_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_control_duration")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_control_duration.tooltip")))
				.binding(15, GexpressConfig::getPuppetmasterControlDurationSeconds,
					v -> GexpressConfig.puppetmasterControlDurationSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.PUPPETMASTER_CONTROL_DURATION_MIN, GexpressConfig.PUPPETMASTER_CONTROL_DURATION_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_control_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_control_cooldown.tooltip")))
				.binding(45, GexpressConfig::getPuppetmasterControlCooldownSeconds,
					v -> GexpressConfig.puppetmasterControlCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.PUPPETMASTER_CONTROL_COOLDOWN_MIN, GexpressConfig.PUPPETMASTER_CONTROL_COOLDOWN_MAX))
				.build());
			out.add(Option.<Boolean>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_random_target")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_random_target.tooltip")))
				.binding(false, GexpressConfig::isPuppetmasterRandomTarget,
					v -> GexpressConfig.puppetmasterRandomTarget = v)
				.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
					.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_control_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_control_range.tooltip")))
				.binding(16, GexpressConfig::getPuppetmasterControlRange,
					v -> GexpressConfig.puppetmasterControlRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.PUPPETMASTER_CONTROL_RANGE_MIN, GexpressConfig.PUPPETMASTER_CONTROL_RANGE_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_max_uses")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_max_uses.tooltip")))
				.binding(3, GexpressConfig::getPuppetmasterMaxUses,
					v -> GexpressConfig.puppetmasterMaxUses = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.PUPPETMASTER_MAX_USES_MIN, GexpressConfig.PUPPETMASTER_MAX_USES_MAX))
				.build());
			out.add(Option.<Boolean>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_self_kill")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.puppetmaster_self_kill.tooltip")))
				.binding(false, GexpressConfig::canPuppetmasterKillOwnBody,
					v -> GexpressConfig.puppetmasterCanKillOwnBody = v)
				.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
					.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
				.build());
			return out;
		}
		if (BOUNTY_HUNTER_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.bounty_hunter_interval")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.bounty_hunter_interval.tooltip")))
				.binding(60, GexpressConfig::getBountyHunterBountyIntervalSeconds,
					v -> GexpressConfig.bountyHunterBountyIntervalSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.BOUNTY_HUNTER_INTERVAL_SECONDS_MIN,
						GexpressConfig.BOUNTY_HUNTER_INTERVAL_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.bounty_hunter_reward")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.bounty_hunter_reward.tooltip")))
				.binding(200, GexpressConfig::getBountyHunterRewardGold,
					v -> GexpressConfig.bountyHunterRewardGold = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.BOUNTY_HUNTER_REWARD_GOLD_MIN,
						GexpressConfig.BOUNTY_HUNTER_REWARD_GOLD_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.bounty_hunter_fail_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.bounty_hunter_fail_cooldown.tooltip")))
				.binding(90, GexpressConfig::getBountyHunterFailCooldownSeconds,
					v -> GexpressConfig.bountyHunterFailCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.BOUNTY_HUNTER_FAIL_COOLDOWN_SECONDS_MIN,
						GexpressConfig.BOUNTY_HUNTER_FAIL_COOLDOWN_SECONDS_MAX))
				.build());
			return out;
		}
		if (GODFATHER_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.godfather_bullet_price")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.godfather_bullet_price.tooltip")))
				.binding(75, GexpressConfig::getGodfatherBulletPrice, v -> GexpressConfig.godfatherBulletPrice = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.GODFATHER_BULLET_PRICE_MIN, GexpressConfig.GODFATHER_BULLET_PRICE_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.godfather_starting_bullets")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.godfather_starting_bullets.tooltip")))
				.binding(1, GexpressConfig::getGodfatherStartingBullets, v -> GexpressConfig.godfatherStartingBullets = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.GODFATHER_STARTING_BULLETS_MIN, GexpressConfig.GODFATHER_STARTING_BULLETS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.godfather_max_loaded_bullets")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.godfather_max_loaded_bullets.tooltip")))
				.binding(3, GexpressConfig::getGodfatherMaxLoadedBullets, v -> GexpressConfig.godfatherMaxLoadedBullets = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.GODFATHER_MAX_LOADED_BULLETS_MIN, GexpressConfig.GODFATHER_MAX_LOADED_BULLETS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.godfather_starting_gold")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.godfather_starting_gold.tooltip")))
				.binding(100, GexpressConfig::getGodfatherStartingGold, v -> GexpressConfig.godfatherStartingGold = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.MAFIA_STARTING_GOLD_MIN, GexpressConfig.MAFIA_STARTING_GOLD_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.mafia_recruit_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.mafia_recruit_range.tooltip")))
				.binding(16, GexpressConfig::getMafiaRecruitRange, v -> GexpressConfig.mafiaRecruitRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.MAFIA_RECRUIT_RANGE_MIN, GexpressConfig.MAFIA_RECRUIT_RANGE_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.mafia_replacement_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.mafia_replacement_cooldown.tooltip")))
				.binding(120, GexpressConfig::getMafiaReplacementCooldownSeconds, v -> GexpressConfig.mafiaReplacementCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.MAFIA_REPLACEMENT_COOLDOWN_SECONDS_MIN, GexpressConfig.MAFIA_REPLACEMENT_COOLDOWN_SECONDS_MAX))
				.build());
			return out;
		}
		if (MAFIOSO_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.mafioso_starting_gold")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.mafioso_starting_gold.tooltip")))
				.binding(100, GexpressConfig::getMafiosoStartingGold, v -> GexpressConfig.mafiosoStartingGold = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.MAFIA_STARTING_GOLD_MIN, GexpressConfig.MAFIA_STARTING_GOLD_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.mafia_revolver_kill_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.mafia_revolver_kill_cooldown.tooltip")))
				.binding(60, GexpressConfig::getMafiaRevolverKillCooldownSeconds, v -> GexpressConfig.mafiaRevolverKillCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.MAFIA_REVOLVER_KILL_COOLDOWN_SECONDS_MIN, GexpressConfig.MAFIA_REVOLVER_KILL_COOLDOWN_SECONDS_MAX))
				.build());
			return out;
		}
		if (JANITOR_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.janitor_starting_gold")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.janitor_starting_gold.tooltip")))
				.binding(100, GexpressConfig::getJanitorStartingGold, v -> GexpressConfig.janitorStartingGold = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.MAFIA_STARTING_GOLD_MIN, GexpressConfig.MAFIA_STARTING_GOLD_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.janitor_clean_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.janitor_clean_range.tooltip")))
				.binding(4, GexpressConfig::getJanitorCleanRange, v -> GexpressConfig.janitorCleanRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.JANITOR_CLEAN_RANGE_MIN, GexpressConfig.JANITOR_CLEAN_RANGE_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.janitor_clean_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.janitor_clean_cooldown.tooltip")))
				.binding(20, GexpressConfig::getJanitorCleanCooldownSeconds, v -> GexpressConfig.janitorCleanCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.JANITOR_CLEAN_COOLDOWN_SECONDS_MIN, GexpressConfig.JANITOR_CLEAN_COOLDOWN_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.janitor_clean_cooldown_after_kill")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.janitor_clean_cooldown_after_kill.tooltip")))
				.binding(45, GexpressConfig::getJanitorCleanCooldownAfterKillSeconds,
					v -> GexpressConfig.janitorCleanCooldownAfterKillSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.JANITOR_CLEAN_COOLDOWN_AFTER_KILL_SECONDS_MIN,
						GexpressConfig.JANITOR_CLEAN_COOLDOWN_AFTER_KILL_SECONDS_MAX))
				.build());
			return out;
		}
		if (SCATTER_BRAIN_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.scatter_brain_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.scatter_brain_cooldown.tooltip")))
				.binding(60, GexpressConfig::getScatterBrainCooldownSeconds,
					v -> GexpressConfig.scatterBrainCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SCATTER_BRAIN_COOLDOWN_SECONDS_MIN,
						GexpressConfig.SCATTER_BRAIN_COOLDOWN_SECONDS_MAX))
				.build());
			return out;
		}
		if (SKINCRAWLER_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_body_age")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_body_age.tooltip")))
				.binding(90, GexpressConfig::getSkincrawlerBodyMaxAgeSeconds,
					v -> GexpressConfig.skincrawlerBodyMaxAgeSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SKINCRAWLER_BODY_MAX_AGE_SECONDS_MIN,
						GexpressConfig.SKINCRAWLER_BODY_MAX_AGE_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_cooldown.tooltip")))
				.binding(90, GexpressConfig::getSkincrawlerCooldownSeconds,
					v -> GexpressConfig.skincrawlerCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SKINCRAWLER_COOLDOWN_SECONDS_MIN,
						GexpressConfig.SKINCRAWLER_COOLDOWN_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_stun")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_stun.tooltip")))
				.binding(5, GexpressConfig::getSkincrawlerStunSeconds,
					v -> GexpressConfig.skincrawlerStunSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SKINCRAWLER_STUN_SECONDS_MIN,
						GexpressConfig.SKINCRAWLER_STUN_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.skincrawler_range.tooltip")))
				.binding(4, GexpressConfig::getSkincrawlerRange, v -> GexpressConfig.skincrawlerRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SKINCRAWLER_RANGE_MIN, GexpressConfig.SKINCRAWLER_RANGE_MAX))
				.build());
			return out;
		}
		if (VULTURE_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.pelican_eat_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.pelican_eat_cooldown.tooltip")))
				.binding(20, GexpressConfig::getPelicanEatCooldownSeconds,
					v -> GexpressConfig.pelicanEatCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.PELICAN_EAT_COOLDOWN_MIN, GexpressConfig.PELICAN_EAT_COOLDOWN_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.pelican_eat_percentage")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.pelican_eat_percentage.tooltip")))
				.binding(80, GexpressConfig::getPelicanEatPercentage,
					v -> GexpressConfig.pelicanEatPercentage = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.PELICAN_EAT_PERCENTAGE_MIN, GexpressConfig.PELICAN_EAT_PERCENTAGE_MAX))
				.build());
			return out;
		}
		if (TRACKER_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.tracker_max_targets")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.tracker_max_targets.tooltip")))
				.binding(3, GexpressConfig::getTrackerMaxTargets, v -> GexpressConfig.trackerMaxTargets = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TRACKER_MAX_TARGETS_MIN, GexpressConfig.TRACKER_MAX_TARGETS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.tracker_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.tracker_range.tooltip")))
				.binding(24, GexpressConfig::getTrackerRange, v -> GexpressConfig.trackerRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TRACKER_RANGE_MIN, GexpressConfig.TRACKER_RANGE_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.tracker_cooldown")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.tracker_cooldown.tooltip")))
				.binding(10, GexpressConfig::getTrackerCooldownSeconds, v -> GexpressConfig.trackerCooldownSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.TRACKER_COOLDOWN_SECONDS_MIN, GexpressConfig.TRACKER_COOLDOWN_SECONDS_MAX))
				.build());
			return out;
		}
		if (SPY_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.spy_bug_cost")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.spy_bug_cost.tooltip")))
				.binding(100, GexpressConfig::getSpyBugCost, v -> GexpressConfig.spyBugCost = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SPY_BUG_COST_MIN, GexpressConfig.SPY_BUG_COST_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.spy_bug_duration")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.spy_bug_duration.tooltip")))
				.binding(120, GexpressConfig::getSpyBugDurationSeconds,
					v -> GexpressConfig.spyBugDurationSeconds = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SPY_BUG_DURATION_SECONDS_MIN,
						GexpressConfig.SPY_BUG_DURATION_SECONDS_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.spy_bug_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.spy_bug_range.tooltip")))
				.binding(16, GexpressConfig::getSpyBugRange, v -> GexpressConfig.spyBugRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.SPY_BUG_RANGE_MIN, GexpressConfig.SPY_BUG_RANGE_MAX))
				.build());
			return out;
		}
		if (ALTRUIST_ID.equals(roleId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.altruist_range")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.altruist_range.tooltip")))
				.binding(4, GexpressConfig::getAltruistRange, v -> GexpressConfig.altruistRange = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.ALTRUIST_RANGE_MIN, GexpressConfig.ALTRUIST_RANGE_MAX))
				.build());
			return out;
		}
		return List.of();
	}

	private static List<Option<?>> buildModifierSubOptions(String modifierId) {
		if (EOD_SPECIALIST_ID.equals(modifierId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.wrong_wire_percent")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.wrong_wire_percent.tooltip")))
				.binding(20, GexpressConfig::getWrongWirePercent, v -> GexpressConfig.wrongWirePercent = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.WRONG_WIRE_MIN, GexpressConfig.WRONG_WIRE_MAX))
				.build());
			return out;
		}
		if (SHORT_SIGHTED_ID.equals(modifierId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(floatStringOption("gui.watheextended.config.option.gexpress.short_sighted_entity_range",
				5.0F, GexpressConfig::getShortSightedEntityRange, v -> GexpressConfig.shortSightedFogRange = v,
				GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MIN, GexpressConfig.SHORT_SIGHTED_ENTITY_RANGE_MAX));
			return out;
		}
		if (HUNGRY_ID.equals(modifierId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.hungry_food_limit")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.hungry_food_limit.tooltip")))
				.binding(2, GexpressConfig::getHungryFoodLimit, v -> GexpressConfig.hungryFoodLimit = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.HUNGRY_FOOD_LIMIT_MIN, GexpressConfig.HUNGRY_FOOD_LIMIT_MAX))
				.build());
			return out;
		}
		if (THIRSTY_ID.equals(modifierId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.thirsty_drink_limit")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.thirsty_drink_limit.tooltip")))
				.binding(2, GexpressConfig::getThirstyDrinkLimit, v -> GexpressConfig.thirstyDrinkLimit = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.THIRSTY_DRINK_LIMIT_MIN, GexpressConfig.THIRSTY_DRINK_LIMIT_MAX))
				.build());
			return out;
		}
		if (SQUEAKER_ID.equals(modifierId)) {
			List<Option<?>> out = new ArrayList<>();
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.squeaker_pitch")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.squeaker_pitch.tooltip")))
				.binding(135, GexpressConfig::getSqueakerPitchPercent,
					v -> GexpressConfig.squeakerPitchPercent = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.VOICE_PITCH_PERCENT_MIN, GexpressConfig.VOICE_PITCH_PERCENT_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.masquerade_pitch_min")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.masquerade_pitch_min.tooltip")))
				.binding(80, GexpressConfig::getMasqueradePitchMinPercent,
					v -> GexpressConfig.masqueradePitchMinPercent = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.VOICE_PITCH_PERCENT_MIN, GexpressConfig.VOICE_PITCH_PERCENT_MAX))
				.build());
			out.add(Option.<Integer>createBuilder()
				.name(indented(Text.translatable("gui.watheextended.config.option.gexpress.masquerade_pitch_max")))
				.description(OptionDescription.of(Text.translatable("gui.watheextended.config.option.gexpress.masquerade_pitch_max.tooltip")))
				.binding(145, GexpressConfig::getMasqueradePitchMaxPercent,
					v -> GexpressConfig.masqueradePitchMaxPercent = v)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt)
					.range(GexpressConfig.VOICE_PITCH_PERCENT_MIN, GexpressConfig.VOICE_PITCH_PERCENT_MAX))
				.build());
			return out;
		}
		return List.of();
	}

	private static OptionGroup buildModifiersGroup(Map<String, List<Option<?>>> weOptsByModifier) {
		OptionGroup.Builder g = OptionGroup.createBuilder()
			.name(Text.translatable("gui.gexpress.config.group.modifiers"))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.group.modifiers.tooltip")))
			.collapsed(false);

		Map<String, ModifiersDisplay.ModifierDisplay> display;
		try {
			display = ModifiersDisplay.get();
		} catch (Throwable t) {
			return errorGroup("gui.gexpress.config.group.modifiers.error");
		}

		for (Map.Entry<String, ModifiersDisplay.ModifierDisplay> e : display.entrySet()) {
			String id = e.getKey();
			ModifiersDisplay.ModifierDisplay md = e.getValue();

			List<Option<?>> subOptions = new ArrayList<>();
			Option<Integer> chance = buildModifierChanceOption(id);
			subOptions.add(chance);
			if (hasConfigurableModifierMax(id)) {
				Option<Integer> max = buildModifierMaxOption(id);
				subOptions.add(max);
			}
			subOptions.addAll(buildModifierSubOptions(id));
			subOptions.addAll(weOptsByModifier.getOrDefault(roleIdToWeKey(id), List.of()));

			Option<Boolean> toggle = buildModifierToggle(id, md);
			attachDropdown(g, toggle, subOptions);
		}
		return g.build();
	}

	private static Option<Boolean> buildModifierToggle(String id, ModifiersDisplay.ModifierDisplay md) {
		MutableText name = colorizedModifierName(md, id);
		OptionDescription desc = buildModifierDescription(id);

		return Option.<Boolean>createBuilder()
			.name(name)
			.description(desc)
			.binding(true, () -> {
				if (GexpressOptionsScreen.pendingModifierState.containsKey(id)) {
					return GexpressOptionsScreen.pendingModifierState.get(id);
				}
				return !ConfigHelper.getDisabledModifiers().contains(id);
			}, v -> {
				GexpressOptionsScreen.pendingModifierState.put(id, v);
				GexpressOptionsScreen.stageChatCommand("setEnabledModifier " + id + " " + v);
			})
			.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true)
				.formatValue(b -> Text.translatable(b ? "text.watheextended.enabled" : "text.watheextended.disabled")))
			.build();
	}

	private static Option<Integer> buildModifierChanceOption(String id) {
		return Option.<Integer>createBuilder()
			.name(indented(Text.translatable("gui.gexpress.config.option.tuning.modifier_chance")))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.tuning.modifier_chance.tooltip")))
			.binding(RoleModifierTuningConfig.DEFAULT_CHANCE,
				() -> GexpressOptionsScreen.getModifierChance(id),
				v -> GexpressOptionsScreen.setModifierChance(id, v))
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(RoleModifierTuningConfig.CHANCE_MIN, RoleModifierTuningConfig.CHANCE_MAX))
			.build();
	}

	private static Option<Integer> buildModifierMaxOption(String id) {
		return Option.<Integer>createBuilder()
			.name(indented(Text.translatable("gui.gexpress.config.option.tuning.modifier_max")))
			.description(OptionDescription.of(Text.translatable("gui.gexpress.config.option.tuning.modifier_max.tooltip")))
			.binding(RoleModifierTuningConfig.DEFAULT_MAX,
				() -> GexpressOptionsScreen.getModifierMax(id),
				v -> GexpressOptionsScreen.setModifierMax(id, v))
			.controller(opt -> IntegerFieldControllerBuilder.create(opt)
				.range(RoleModifierTuningConfig.MAX_MIN, RoleModifierTuningConfig.MAX_MAX))
			.build();
	}

	private static void attachDropdown(OptionGroup.Builder g, Option<Boolean> toggle, List<Option<?>> subOptions) {
		if (subOptions.isEmpty()) {
			g.option(toggle);
			g.option(spacer());
			return;
		}

		for (Option<?> sub : subOptions) {
			OptionVisibility.setHiddenWhen(sub, () -> !Boolean.TRUE.equals(toggle.pendingValue()));
			OptionVisibility.ensureDropdownName(sub);
			OptionVisibility.addSearchAlias(sub, toggle.name().getString());
		}

		g.option(toggle);
		for (Option<?> sub : subOptions) g.option(sub);
		g.option(spacer());
	}

	private static LabelOption spacer() {
		return LabelOption.create(Text.empty());
	}

	private static Option<String> floatStringOption(String langKey, float defaultValue, Supplier<Float> getter,
			Consumer<Float> setter, float min, float max) {
		return Option.<String>createBuilder()
			.name(indented(Text.translatable(langKey)))
			.description(OptionDescription.of(Text.translatable(langKey + ".tooltip")))
			.binding(formatFloat(defaultValue), () -> formatFloat(getter.get()),
				value -> parseAndApplyFloat(value, setter, min, max))
			.controller(StringControllerBuilder::create)
			.build();
	}

	private static String formatFloat(float value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static void parseAndApplyFloat(String raw, Consumer<Float> setter, float min, float max) {
		Float parsed = parseFloat(raw);
		if (parsed == null) return;
		setter.accept(Math.max(min, Math.min(max, parsed)));
	}

	private static Float parseFloat(String raw) {
		if (raw == null) return null;
		String normalized = raw.trim().replace(',', '.');
		if (normalized.isEmpty() || normalized.equals("-") || normalized.equals(".") || normalized.equals("-.")) {
			return null;
		}
		try {
			float value = Float.parseFloat(normalized);
			return Float.isFinite(value) ? value : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static OptionGroup buildPassthroughGroup(String langKey, List<Option<?>> options) {
		OptionGroup.Builder g = OptionGroup.createBuilder()
			.name(Text.translatable(langKey))
			.collapsed(false);
		for (Option<?> o : options) g.option(o);
		return g.build();
	}

	private static MutableText indented(Text inner) {
		return OptionVisibility.dropdownName(inner);
	}

	private static MutableText colorizedRoleName(RolesDisplay.RoleDisplay rd, String fallbackId) {
		if (rd == null) {
			if (KILLER_ID.equals(fallbackId)) return Text.literal("Killer!").formatted(Formatting.RED);
			if (VIGILANTE_ID.equals(fallbackId)) return Text.literal("Vigilante").formatted(Formatting.AQUA);
			if (VULTURE_ID.equals(fallbackId)) return Text.literal("Pelican").formatted(Formatting.GRAY);
			return Text.literal(fallbackId);
		}
		MutableText name = rd.display().copy();
		try {
			name.setStyle(name.getStyle().withColor(TextColor.fromRgb(rd.color())));
		} catch (Throwable ignored) {}
		return name;
	}

	private static MutableText colorizedModifierName(ModifiersDisplay.ModifierDisplay md, String fallbackId) {
		if (md == null) return Text.literal(fallbackId);
		MutableText name = md.display().copy();
		try {
			name.setStyle(name.getStyle().withColor(TextColor.fromRgb(md.color())));
		} catch (Throwable ignored) {}
		return name;
	}

	private static OptionDescription buildRoleDescription(String roleId, RolesDisplay.RoleDisplay rd) {
		return DynamicOptionDescription.of(() -> buildRoleDescriptionLines(roleId, rd));
	}

	private static List<Text> buildRoleDescriptionLines(String roleId, RolesDisplay.RoleDisplay rd) {
		List<Text> lines = new ArrayList<>();
		String key = roleIdAsLangPath(roleId);

		lines.add(headerLine("Description"));
		appendLangLineOrFallback(lines, "gui.watheextended.guidebook.role.desc." + key,
			"(No description available.)");

		appendAbilitiesSection(lines, "gui.watheextended.guidebook.role.abilities." + key);

		boolean killerSided = rd != null && rd.side() == RolesDisplay.Side.KILLER;
		List<GexpressRoleMeta.ShopItem> shop = GexpressRoleMeta.resolveShop(roleId, killerSided);
		if (!shop.isEmpty()) {
			lines.add(Text.literal(""));
			lines.add(headerLine("Shop"));
			appendShopLines(lines, shop);
		}

		return lines;
	}

	private static OptionDescription buildModifierDescription(String modifierId) {
		List<Text> lines = new ArrayList<>();
		lines.add(headerLine("Description"));
		appendLangLineOrFallback(lines,
			"gui.watheextended.guidebook.modifier.desc." + roleIdAsLangPath(modifierId),
			"(No description available.)");
		return OptionDescription.of(lines.toArray(new Text[0]));
	}

	private static void appendLangLineOrFallback(List<Text> out, String key, String fallback) {
		String override = roleDescriptionOverride(key);
		if (override != null) {
			for (String line : override.split("\\\\n|\\n")) {
				out.add(Text.literal(WeIcons.replaceTokens(line)).formatted(Formatting.WHITE));
			}
			return;
		}
		String resolved = resolveLang(key);
		if (resolved == null) {
			out.add(Text.literal(fallback).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
			return;
		}
		for (String line : resolved.split("\n")) {
			out.add(Text.literal(WeIcons.replaceTokens(line)).formatted(Formatting.WHITE));
		}
	}

	private static String roleDescriptionOverride(String key) {
		String prefix = "gui.watheextended.guidebook.role.desc.gexpress.";
		if (key == null || !key.startsWith(prefix)) return null;
		String value = GexpressConfig.getRoleDescriptionOverride(key.substring(prefix.length()));
		return value == null || value.isBlank() ? null : value;
	}

	private static void appendAbilitiesSection(List<Text> out, String key) {
		String resolved = resolveLang(key);
		if (resolved == null || resolved.isBlank()) return;
		out.add(Text.literal(""));
		out.add(headerLine("Abilities"));
		for (String line : resolved.split("\n")) {
			if (line.isEmpty()) {
				out.add(Text.literal(""));
			} else {
				out.add(renderWeLine(line));
			}
		}
	}

	private static String resolveLang(String key) {
		String resolved = Text.translatable(key).getString();
		if (resolved == null || resolved.equals(key)) return null;
		return resolved;
	}

	private static Text headerLine(String label) {
		return Text.literal(label).formatted(Formatting.GOLD, Formatting.BOLD);
	}

	private static Text renderWeLine(String raw) {
		String processed = WeIcons.replaceTokens(raw);
		MutableText t = Text.literal(" \u25CF ").formatted(Formatting.GOLD);
		t.append(Text.literal(processed).formatted(Formatting.WHITE));
		return t;
	}

	private static void appendShopLines(List<Text> out, List<GexpressRoleMeta.ShopItem> shop) {
		List<String> names = new ArrayList<>(shop.size());
		int maxName = 0;
		for (GexpressRoleMeta.ShopItem item : shop) {
			String n = itemDisplayName(item);
			names.add(n);
			if (n.length() > maxName) maxName = n.length();
		}
		for (int i = 0; i < shop.size(); i++) {
			GexpressRoleMeta.ShopItem item = shop.get(i);
			String padded = padRight(names.get(i), maxName);
			MutableText line = Text.literal(" \u25CF ").formatted(Formatting.GOLD);
			String iconChar = item.iconName() != null ? WeIcons.charFor(item.iconName()) : null;
			if (iconChar != null) {
				line.append(Text.literal(iconChar + " ").formatted(Formatting.WHITE));
			} else {
				line.append(Text.literal("  ").formatted(Formatting.WHITE));
			}
			line.append(Text.literal(padded).formatted(Formatting.WHITE));
			line.append(Text.literal("   ").formatted(Formatting.DARK_GRAY));
			if (item.starting()) {
				line.append(Text.literal("(Starting)").formatted(Formatting.GRAY, Formatting.ITALIC));
			} else {
				line.append(Text.literal(item.price() + " " + WeIcons.COIN).formatted(Formatting.YELLOW));
			}
			out.add(line);
		}
	}

	private static String itemDisplayName(GexpressRoleMeta.ShopItem item) {
		String resolved = resolveLang(item.nameKey());
		if (resolved != null) return resolved;
		return Text.translatable(item.nameKey()).getString();
	}

	private static String padRight(String s, int width) {
		if (s.length() >= width) return s;
		StringBuilder sb = new StringBuilder(s);
		for (int i = s.length(); i < width; i++) sb.append(' ');
		return sb.toString();
	}

	private static String roleIdAsLangPath(String roleId) {
		int colon = roleId.indexOf(':');
		if (colon < 0) return roleId;
		return roleId.substring(0, colon) + "." + roleId.substring(colon + 1);
	}

	private static Text sideTitle(RolesDisplay.Side side) {
		return switch (side) {
			case KILLER -> Text.translatable("gui.gexpress.config.group.roles.killers").formatted(Formatting.RED);
			case INNOCENT -> Text.translatable("gui.gexpress.config.group.roles.civilians").formatted(Formatting.AQUA);
			case NEUTRAL -> Text.translatable("gui.gexpress.config.group.roles.neutrals").formatted(Formatting.GRAY);
		};
	}

	private static boolean isMafiaRoleId(String roleId) {
		return roleId != null && MAFIA_ROLE_IDS.contains(roleId);
	}

	private static OptionGroup errorGroup(String langKey) {
		return OptionGroup.createBuilder()
			.name(Text.translatable(langKey).formatted(Formatting.RED))
			.option(LabelOption.create(Text.translatable(langKey).formatted(Formatting.RED)))
			.build();
	}

	private static boolean isBuiltinNonConfigRole(String roleId) {
		return CIVILIAN_ID.equals(roleId)
			|| DISCOVERY_CIVILIAN_ID.equals(roleId)
			|| LOOSE_END_ID.equals(roleId);
	}

	private static boolean isRetiredExternalVulture(String roleId) {
		if (roleId == null) return false;
		int colon = roleId.indexOf(':');
		String path = colon < 0 ? roleId : roleId.substring(colon + 1);
		return "vulture".equals(path);
	}

	private static boolean hasConfigurableModifierMax(String modifierId) {
		// Lovers has an inherent fixed pair limit, so it does not need an editable amount slider.
		return false;
	}
}
