package dev.mapselect.role.medic;

import dev.mapselect.MapSelect;
import dev.mapselect.config.GexpressConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MedicShieldComponent implements AutoSyncedComponent {
	public static final ComponentKey<MedicShieldComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "medic_shield"),
		MedicShieldComponent.class
	);

	private final World world;
	private final Map<UUID, UUID> targetToMedic = new LinkedHashMap<>();
	private final Map<UUID, UUID> medicToTarget = new LinkedHashMap<>();
	private final Map<UUID, Long> medicCooldownUntil = new LinkedHashMap<>();

	public MedicShieldComponent(World world) {
		this.world = world;
	}

	public boolean hasShield(UUID target) {
		return target != null && targetToMedic.containsKey(target);
	}

	public UUID getMedicForTarget(UUID target) {
		return target == null ? null : targetToMedic.get(target);
	}

	public UUID getTargetForMedic(UUID medic) {
		return medic == null ? null : medicToTarget.get(medic);
	}

	public long cooldownRemainingTicks(UUID medic) {
		if (medic == null) return 0L;
		Long until = medicCooldownUntil.get(medic);
		if (until == null) return 0L;
		return Math.max(0L, until - world.getTime());
	}

	public long reduceCooldown(UUID medic, long ticks) {
		if (medic == null || ticks <= 0L) return cooldownRemainingTicks(medic);
		Long until = medicCooldownUntil.get(medic);
		if (until == null) return 0L;
		long remaining = until - world.getTime();
		if (remaining <= 0L) {
			medicCooldownUntil.remove(medic);
			KEY.sync(world);
			return 0L;
		}
		long nextRemaining = Math.max(0L, remaining - ticks);
		if (nextRemaining <= 0L) medicCooldownUntil.remove(medic);
		else medicCooldownUntil.put(medic, world.getTime() + nextRemaining);
		KEY.sync(world);
		return nextRemaining;
	}

	public Map<UUID, UUID> getShields() {
		return Collections.unmodifiableMap(targetToMedic);
	}

	public List<UUID> getShieldedTargets() {
		return new ArrayList<>(targetToMedic.keySet());
	}

	public boolean assignShield(UUID medic, UUID target) {
		if (medic == null || target == null || medic.equals(target)) return false;

		UUID existingMedic = targetToMedic.get(target);
		if (existingMedic != null && !existingMedic.equals(medic)) return false;

		UUID previousTarget = medicToTarget.remove(medic);
		if (previousTarget != null) {
			targetToMedic.remove(previousTarget);
		}

		targetToMedic.put(target, medic);
		medicToTarget.put(medic, target);
		medicCooldownUntil.put(medic, world.getTime() + (long) GexpressConfig.getMedicShieldCooldownSeconds() * 20L);
		KEY.sync(this.world);
		return true;
	}

	public UUID removeShield(UUID target) {
		if (target == null) return null;
		UUID medic = targetToMedic.remove(target);
		if (medic == null) return null;
		medicToTarget.remove(medic);
		KEY.sync(this.world);
		return medic;
	}

	public boolean removeMedic(UUID medic) {
		if (medic == null) return false;
		UUID target = medicToTarget.remove(medic);
		if (target == null) return false;
		targetToMedic.remove(target);
		KEY.sync(this.world);
		return true;
	}

	public boolean clearAll() {
		if (targetToMedic.isEmpty() && medicToTarget.isEmpty() && medicCooldownUntil.isEmpty()) return false;
		targetToMedic.clear();
		medicToTarget.clear();
		medicCooldownUntil.clear();
		KEY.sync(this.world);
		return true;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		targetToMedic.clear();
		medicToTarget.clear();
		medicCooldownUntil.clear();

		NbtList shields = tag.getList("shields", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < shields.size(); i++) {
			NbtCompound entry = shields.getCompound(i);
			UUID target = parseUuid(entry.getString("target"));
			UUID medic = parseUuid(entry.getString("medic"));
			if (target == null || medic == null || target.equals(medic)) continue;
			if (targetToMedic.containsKey(target) || medicToTarget.containsKey(medic)) continue;
			targetToMedic.put(target, medic);
			medicToTarget.put(medic, target);
		}

		NbtList cooldowns = tag.getList("cooldowns", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < cooldowns.size(); i++) {
			NbtCompound entry = cooldowns.getCompound(i);
			UUID medic = parseUuid(entry.getString("medic"));
			if (medic == null) continue;
			medicCooldownUntil.put(medic, entry.getLong("until"));
		}
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		NbtList shields = new NbtList();
		for (Map.Entry<UUID, UUID> entry : targetToMedic.entrySet()) {
			NbtCompound shield = new NbtCompound();
			shield.putString("target", entry.getKey().toString());
			shield.putString("medic", entry.getValue().toString());
			shields.add(shield);
		}
		tag.put("shields", shields);

		NbtList cooldowns = new NbtList();
		for (Map.Entry<UUID, Long> entry : medicCooldownUntil.entrySet()) {
			NbtCompound cooldown = new NbtCompound();
			cooldown.putString("medic", entry.getKey().toString());
			cooldown.putLong("until", entry.getValue());
			cooldowns.add(cooldown);
		}
		tag.put("cooldowns", cooldowns);
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isEmpty()) return null;
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
