package dev.mapselect.network;

import dev.mapselect.MapSelect;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AbilityCooldownPayload(String key, int remainingTicks, int totalTicks, boolean draining,
                                     int usesRemaining, int maxUses)
		implements CustomPayload {
	public static final String PUPPETMASTER_CONTROL = "puppetmaster_control";
	public static final String PELICAN_SWALLOW = "pelican_swallow";
	public static final String HARLEQUIN_MASQUERADE = "harlequin_masquerade";
	public static final String HARLEQUIN_DANCING_CARTS = "harlequin_dancing_carts";
	public static final String JUGGERNAUT_WEAPONS = "juggernaut_weapons";
	public static final String JUGGERNAUT_SHIELD = "juggernaut_shield";
	public static final String SCATTER_BRAIN_SCATTER = "scatter_brain_scatter";
	public static final String TRACKER_TRACK = "tracker_track";
	public static final String SEER_COMPARE = "seer_compare";
	public static final String CUPID_LINK = "cupid_link";
	public static final String COVENANT_BITE = "covenant_bite";
	public static final String GODFATHER_RECRUIT_MAFIOSO = "godfather_recruit_mafioso";
	public static final String GODFATHER_RECRUIT_JANITOR = "godfather_recruit_janitor";
	public static final String GODFATHER_RECRUIT_PICKPOCKET = "godfather_recruit_pickpocket";
	public static final String GODFATHER_RECRUIT_BURGLAR = "godfather_recruit_burglar";
	public static final String JANITOR_CLEAN = "janitor_clean";
	public static final String PICKPOCKET_STEAL = "pickpocket_steal";
	public static final String SKINCRAWLER_STEAL = "skincrawler_steal";
	public static final String COPYCAT_COPY = "copycat_copy";
	public static final String SPY_BUG = "spy_bug";

	public static final CustomPayload.Id<AbilityCooldownPayload> ID =
		new CustomPayload.Id<>(Identifier.of(MapSelect.MOD_ID, "ability_cooldown"));

	public AbilityCooldownPayload(String key, int remainingTicks, int totalTicks, boolean draining) {
		this(key, remainingTicks, totalTicks, draining, 0, 0);
	}

	public AbilityCooldownPayload {
		key = key == null ? "" : key;
		remainingTicks = Math.max(0, remainingTicks);
		totalTicks = Math.max(0, totalTicks);
		maxUses = Math.max(0, maxUses);
		usesRemaining = maxUses <= 0 ? 0 : Math.max(0, Math.min(maxUses, usesRemaining));
	}

	public static final PacketCodec<PacketByteBuf, AbilityCooldownPayload> CODEC = PacketCodec.of(
		(payload, buf) -> {
			buf.writeString(payload.key(), 64);
			buf.writeInt(payload.remainingTicks());
			buf.writeInt(payload.totalTicks());
			buf.writeBoolean(payload.draining());
			buf.writeInt(payload.usesRemaining());
			buf.writeInt(payload.maxUses());
		},
		buf -> new AbilityCooldownPayload(buf.readString(64), buf.readInt(), buf.readInt(), buf.readBoolean(),
			buf.readInt(), buf.readInt())
	);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
