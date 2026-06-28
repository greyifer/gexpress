package dev.mapselect.mixin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.mapselect.host.PlayerTag;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.permissions.GexpressPermissions;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Locale;

@Mixin(EntitySelectorOptions.class)
public abstract class GexpressEntitySelectorOptionsMixin {
	private static final SimpleCommandExceptionType GEXPRESS_EMPTY_GTAG =
		new SimpleCommandExceptionType(Text.literal("Expected a G'Express tag selector."));

	private static final SimpleCommandExceptionType GEXPRESS_INVALID_GTAG =
		new SimpleCommandExceptionType(Text.literal("Invalid G'Express tag selector."));

	@Invoker("putOption")
	private static void gexpress$putOption(String id, EntitySelectorOptions.SelectorHandler handler,
			java.util.function.Predicate<EntitySelectorReader> condition, Text description) {
		throw new AssertionError();
	}

	@Inject(method = "register", at = @At("RETURN"))
	private static void gexpress$registerGTagSelector(CallbackInfo ci) {
		gexpress$putOption("gTag", GexpressEntitySelectorOptionsMixin::gexpress$readGTag,
			reader -> true, Text.literal("Filter players by G'Express tag"));
	}

	private static void gexpress$readGTag(EntitySelectorReader reader) throws CommandSyntaxException {
		StringReader stringReader = reader.getReader();
		GTagSelector selector = GTagSelector.parse(gexpress$readRawValue(stringReader), stringReader);
		reader.setIncludesNonPlayers(false);
		reader.addPredicate(entity -> entity instanceof ServerPlayerEntity player && selector.matches(player));
	}

	private static String gexpress$readRawValue(StringReader reader) throws CommandSyntaxException {
		if (!reader.canRead()) throw GEXPRESS_EMPTY_GTAG.createWithContext(reader);
		if (reader.peek() == '"' || reader.peek() == '\'') return reader.readString();
		int start = reader.getCursor();
		while (reader.canRead() && reader.peek() != ',' && reader.peek() != ']') {
			reader.skip();
		}
		String value = reader.getString().substring(start, reader.getCursor()).trim();
		if (value.isBlank()) throw GEXPRESS_EMPTY_GTAG.createWithContext(reader);
		return value;
	}

	private record GTagSelector(String id, Mode mode, boolean negated) {
		static GTagSelector parse(String raw, StringReader reader) throws CommandSyntaxException {
			String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
			boolean negated = false;
			while (value.startsWith("!")) {
				negated = !negated;
				value = value.substring(1).trim();
			}
			Mode mode = Mode.EXACT;
			if (value.startsWith(">=") || value.startsWith("=>")) {
				mode = Mode.AT_OR_ABOVE;
				value = value.substring(2).trim();
			} else if (value.startsWith("<=") || value.startsWith("=<")) {
				mode = Mode.AT_OR_BELOW;
				value = value.substring(2).trim();
			} else if (value.startsWith(">")) {
				mode = Mode.ABOVE;
				value = value.substring(1).trim();
			} else if (value.startsWith("<")) {
				mode = Mode.BELOW;
				value = value.substring(1).trim();
			} else if (value.startsWith("at_or_above:") || value.startsWith("above_or_equal:")) {
				mode = Mode.AT_OR_ABOVE;
				value = value.substring(value.indexOf(':') + 1).trim();
			} else if (value.startsWith("above:")) {
				mode = Mode.ABOVE;
				value = value.substring("above:".length()).trim();
			} else if (value.startsWith("at_or_below:") || value.startsWith("below_or_equal:")) {
				mode = Mode.AT_OR_BELOW;
				value = value.substring(value.indexOf(':') + 1).trim();
			} else if (value.startsWith("below:")) {
				mode = Mode.BELOW;
				value = value.substring("below:".length()).trim();
			}
			String id = PlayerTagComponent.normalizeCustomId(value);
			if (id == null) throw GEXPRESS_INVALID_GTAG.createWithContext(reader);
			return new GTagSelector(id, mode, negated);
		}

		boolean matches(ServerPlayerEntity player) {
			boolean result = switch (mode) {
				case EXACT -> hasExactTag(player);
				case ABOVE -> comparePriority(player, false, true);
				case AT_OR_ABOVE -> comparePriority(player, true, true);
				case BELOW -> comparePriority(player, false, false);
				case AT_OR_BELOW -> comparePriority(player, true, false);
			};
			return negated ? !result : result;
		}

		private boolean hasExactTag(ServerPlayerEntity player) {
			List<GexpressPermissions.TagInfo> tags = GexpressPermissions.effectiveTagInfos(player);
			return tags.stream().anyMatch(tag -> id.equals(tag.id()));
		}

		private boolean comparePriority(ServerPlayerEntity player, boolean inclusive, boolean above) {
			Integer targetPriority = targetPriority(player.getWorld(), id);
			if (targetPriority == null) return false;
			int playerPriority = GexpressPermissions.effectiveTagInfos(player).stream()
				.mapToInt(GexpressPermissions.TagInfo::priority)
				.max()
				.orElse(PlayerTag.PASSENGER.priority());
			if (above) return inclusive ? playerPriority >= targetPriority : playerPriority > targetPriority;
			return inclusive ? playerPriority <= targetPriority : playerPriority < targetPriority;
		}

		private static Integer targetPriority(World world, String id) {
			PlayerTag builtin = PlayerTag.byId(id);
			PlayerTagComponent component = world == null ? null : PlayerTagComponent.KEY.getNullable(world);
			if (builtin != null) return component == null ? builtin.priority() : component.priority(builtin);
			PlayerTagComponent.CustomTag custom = component == null ? null : component.getCustomTag(id);
			return custom == null ? null : custom.priority();
		}
	}

	private enum Mode {
		EXACT,
		ABOVE,
		AT_OR_ABOVE,
		BELOW,
		AT_OR_BELOW
	}
}
