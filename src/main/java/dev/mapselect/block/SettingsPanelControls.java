package dev.mapselect.block;

import cat.rezelyn.watheextended.api.config.ServerConfig;
import cat.rezelyn.watheextended.api.hml.ConfigHelper;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.mapselect.MapSelect;
import dev.mapselect.config.RoleModifierTuningConfig;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.network.config.GexpressConfigSyncHandler;
import dev.mapselect.network.preset.GexpressPresetsSyncHandler;
import dev.mapselect.preset.map.MapPreset;
import dev.mapselect.preset.map.PresetStorage;
import dev.mapselect.preset.train.TrainPresetStorage;
import dev.mapselect.role.RoleModifierTuningBridge;
import dev.mapselect.weather.WeatherType;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import java.util.*;
import java.util.function.IntConsumer;

/** All row values and mutations are resolved on the authoritative server. */
public final class SettingsPanelControls {
    private record Row(String name, String value, IntConsumer change) {}
    private SettingsPanelControls() {}

    public static void use(ServerPlayerEntity player, SettingsPanel panel, int action) {
        if (!player.hasPermissionLevel(2)) {
            player.sendMessage(Text.literal("Only operators can use the settings panel."), true); return;
        }
        var controller = panel.controller(player.getWorld());
        if (controller == null) return;
        int section = controller.section(), offset = controller.offset();
        int visible = SettingsPanelLayout.rows(panel.width(),panel.height(),section);
        try {
            if(action<=-100&&action>-100-SettingsPanelCategories.count(section)){controller.selectCategory(-100-action);offset=0;}
            else if(action<=-10&&action>=-13){section=-10-action;controller.selectCategory(0);offset=0;}
            else if (action == -1 || action == -2) { section = Math.floorMod(section + (action == -1 ? -1 : 1), 4);controller.selectCategory(0); offset = 0; }
            else if (action == -3 || action == -4) offset += action == -3 ? -visible : visible;
            else if (action >= 0) {
                List<Row> rows = rows(player.getServerWorld(), controller, section, player);
                int index = offset + action / 2;
                if (index < rows.size()) rows.get(index).change.accept(action % 2 == 0 ? -1 : 1);
            }
            refresh(player.getServerWorld(), panel, section, offset);
        } catch (Exception e) {
            MapSelect.LOGGER.warn("Settings panel interaction failed at {}", panel.origin(), e);
            player.sendMessage(Text.literal("Could not change that setting. Check the server log."), true);
        }
    }

    public static void refresh(ServerWorld world, SettingsPanel panel, int section, int offset) {
        var controller = panel.controller(world); if (controller == null) return;
        try {
            List<Row> all = rows(world, controller, section, null);
            int visible = SettingsPanelLayout.rows(panel.width(),panel.height(),section);
            offset = Math.clamp(offset, 0, Math.max(0, all.size() - visible));
            if(section>=2)offset-=offset%3;
            NbtList display = new NbtList();
            for (int i = offset; i < Math.min(all.size(), offset + visible); i++) {
                NbtCompound row = new NbtCompound(); row.putString("name", all.get(i).name); row.putString("value", all.get(i).value); display.add(row);
            }
            int pageSection = section, pageOffset = offset;
            panel.forEach(pos -> {
                if (world.getBlockEntity(pos) instanceof SettingsPanelBlockEntity entity) {
                    entity.selectMap(controller.selectedMap()); entity.display(pageSection, pageOffset, all.size(), display);entity.selectCategory(controller.category());
                }
            });
        } catch (Exception e) { MapSelect.LOGGER.warn("Could not refresh settings panel at {}", panel.origin(), e); }
    }

    private static List<Row> rows(ServerWorld world, SettingsPanelBlockEntity controller, int section, ServerPlayerEntity player) throws Exception {
        List<Row> rows = new ArrayList<>();
        int category=Math.floorMod(controller.category(),SettingsPanelCategories.count(section));
        if (section == 0) {
            if(category==0){
            rows.add(new Row("Custom role counts", Boolean.toString(GexpressConfig.useCustomRoleCounts), d -> {
                GexpressConfig.useCustomRoleCounts = !GexpressConfig.useCustomRoleCounts; saveGame(world);
            }));
            addGameInt(rows, world, "maxKillerAmount", "Max killers", 1, 64);
            addGameInt(rows, world, "maxVigilanteAmount", "Max vigilantes", 0, 64);
            addGameInt(rows, world, "maxNeutralAmount", "Max neutrals", 0, 64);
            addGameInt(rows, world, "playersPerKiller", "Players per killer", 1, 64);
            addGameInt(rows, world, "playersPerVigilante", "Players per vigilante", 1, 64);
            addGameInt(rows, world, "playersPerNeutral", "Players per neutral", 1, 64);
            }
            for (var entry : ServerConfig.entries().values().stream().sorted(Comparator.comparing(e -> readable(e.key),String.CASE_INSENSITIVE_ORDER)).toList()) if(SettingsPanelCategories.gameCategory(entry.key)==category)addConfig(rows, world, entry);
        } else if (section == 1) {
            List<String> maps = PresetStorage.list(world.getServer()).stream().sorted().toList();
            String selected = maps.contains(controller.selectedMap()) ? controller.selectedMap() : maps.isEmpty() ? "" : maps.getFirst();
            controller.selectMap(selected);
            rows.add(new Row("Map preset", selected.isEmpty() ? "None" : selected, direction -> {
                if (!maps.isEmpty()) controller.selectMap(maps.get(Math.floorMod(maps.indexOf(selected) + direction, maps.size())));
            }));
            if (selected.isEmpty()) return rows;
            MapPreset preset = PresetStorage.load(world.getServer(), selected);
            if (preset == null) return rows;
            rows.add(new Row("Apply map & train", "Apply", direction -> {
                if (player != null && direction > 0) world.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(), "g setup map set " + selected);
            }));
            rows.add(new Row("Rooms", Integer.toString(preset.roomCount), direction -> {
                preset.roomCount = Math.clamp(preset.roomCount + direction, MapPreset.MIN_ROOM_COUNT, MapPreset.MAX_ROOM_COUNT); save(world, selected, preset);
            }));
            rows.add(new Row("Weather", String.valueOf(preset.weather), direction -> {
                WeatherType[] values = WeatherType.values(); preset.weather = values[Math.floorMod((preset.weather == null ? 0 : preset.weather.ordinal()) + direction, values.length)]; save(world, selected, preset);
            }));
            rows.add(new Row("Couch sleeping", Boolean.toString(!Boolean.FALSE.equals(preset.couchSleepingEnabled)), direction -> {
                preset.couchSleepingEnabled = Boolean.FALSE.equals(preset.couchSleepingEnabled); save(world, selected, preset);
            }));
            rows.add(new Row("Terrain", String.valueOf(preset.terrainMode == null ? Boolean.TRUE.equals(preset.staticMapEnabled) ? MapPreset.TerrainMode.STATIC : MapPreset.TerrainMode.MOVING : preset.terrainMode), direction -> {
                boolean wasStatic = preset.terrainMode == MapPreset.TerrainMode.STATIC || preset.terrainMode == null && Boolean.TRUE.equals(preset.staticMapEnabled);
                preset.terrainMode = wasStatic ? MapPreset.TerrainMode.MOVING : MapPreset.TerrainMode.STATIC; preset.staticMapEnabled = !wasStatic; save(world, selected, preset);
            }));
            rows.add(new Row("Play area", String.valueOf(preset.playAreaMode == null ? MapPreset.PlayAreaMode.FULL_MAP : preset.playAreaMode), direction -> {
                preset.playAreaMode = preset.playAreaMode == MapPreset.PlayAreaMode.TRAIN ? MapPreset.PlayAreaMode.FULL_MAP : MapPreset.PlayAreaMode.TRAIN; save(world, selected, preset);
            }));
            List<String> trains = new ArrayList<>(); trains.add(""); trains.addAll(TrainPresetStorage.list(world.getServer()).stream().sorted().toList());
            rows.add(new Row("Train preset", preset.defaultTrainPreset == null || preset.defaultTrainPreset.isEmpty() ? "Default" : preset.defaultTrainPreset, direction -> {
                int next = Math.floorMod(Math.max(0, trains.indexOf(preset.defaultTrainPreset)) + direction, trains.size());
                preset.defaultTrainPreset = trains.get(next).isEmpty() ? null : trains.get(next); save(world, selected, preset);
            }));
            if(category==1)rows.removeIf(row->row.name.equals("Train preset"));
            if(category==2)rows.removeIf(row->!Set.of("Map preset","Apply map & train","Train preset").contains(row.name));
        } else {
            List<String> ids = section == 2 ? WatheRoles.ROLES.stream().map(r -> r.identifier().toString()).sorted(Comparator.comparing(SettingsPanelControls::readable,String.CASE_INSENSITIVE_ORDER)).toList()
                : HMLModifiers.MODIFIERS.stream().map(m -> m.identifier().toString()).sorted(Comparator.comparing(SettingsPanelControls::readable,String.CASE_INSENSITIVE_ORDER)).toList();
            for (String id : ids) {
                if(section==2 && category!=0){
                    var candidate=WatheRoles.ROLES.stream().filter(r->r.identifier().toString().equals(id)).findFirst().orElse(null);
                    boolean mafia=Set.of("gexpress:godfather","gexpress:mafioso","gexpress:janitor","gexpress:pickpocket","gexpress:burglar").contains(id);
                    int group=mafia?4:candidate!=null&&candidate.isInnocent()?1:candidate!=null&&candidate.canUseKiller()?2:3;
                    if(group!=category)continue;
                }
                if(section==3 && category!=0 && (id.startsWith("gexpress:")?1:2)!=category)continue;
                boolean role = section == 2;
                String label = readable(id);
                boolean enabled = !(role ? ConfigHelper.getDisabledRoles() : ConfigHelper.getDisabledModifiers()).contains(id);
                boolean bugged = role && dev.mapselect.role.BuggedRoles.isBugged(id);
                rows.add(new Row(label, bugged ? "Unavailable" : enabled ? "On" : "Off", d -> {
                    if (bugged) return;
                    List<String> disabled = new ArrayList<>(role ? ConfigHelper.getDisabledRoles() : ConfigHelper.getDisabledModifiers());
                    if (enabled) { if (!disabled.contains(id)) disabled.add(id); } else disabled.removeIf(id::equals);
                    ServerConfig.applyChanges(Map.of(role ? "hml.disabled" : "hml.disabledModifiers", String.join(",", disabled)), world);
                    ServerConfig.broadcastToAll(world.getServer());
                }));
                int chance = role ? RoleModifierTuningConfig.getRoleChance(id) : RoleModifierTuningConfig.getModifierChance(id);
                int max = role ? RoleModifierTuningConfig.getRoleMax(id) : RoleModifierTuningConfig.getModifierMax(id);
                rows.add(new Row(label + " chance", chance + "%", d -> tune(id, role, true, Math.clamp(chance + d * 5, 0, 100))));
                rows.add(new Row(label + " max", Integer.toString(max), d -> tune(id, role, false, Math.clamp(max + d, 0, 64))));
            }
        }
        return rows;
    }
    public static String readable(String key){
        String text=key.substring(Math.max(key.lastIndexOf('.'),key.lastIndexOf(':'))+1).replace('_',' ')
            .replaceAll("([a-z0-9])([A-Z])","$1 $2").replaceAll("([A-Z])([A-Z][a-z])","$1 $2");
        return text.isBlank()?key:Character.toUpperCase(text.charAt(0))+text.substring(1);
    }
    private static <T> void addConfig(List<Row> rows, ServerWorld world, ServerConfig.Entry<T> entry) {
        T value = entry.readServer(world);
        if (!(value instanceof Boolean || value instanceof Number || value instanceof Enum<?>)) return;
        rows.add(new Row(readable(entry.key), entry.serialize(value), direction -> {
            String next;
            if (value instanceof Boolean bool) next = Boolean.toString(!bool);
            else if (value instanceof Enum<?> choice) {
                Object[] choices = choice.getDeclaringClass().getEnumConstants();
                @SuppressWarnings("unchecked") T chosen = (T)choices[Math.floorMod(choice.ordinal() + direction, choices.length)]; next = entry.serialize(chosen);
            } else if (value instanceof Float || value instanceof Double) next = Double.toString(Math.max(0, ((Number)value).doubleValue() + direction * 0.5));
            else next = Long.toString(Math.max(0, ((Number)value).longValue() + direction));
            ServerConfig.applyChanges(Map.of(entry.key, next), world); ServerConfig.broadcastToAll(world.getServer());
        }));
    }
    private static void tune(String id, boolean role, boolean chance, int value) {
        if (role && chance) RoleModifierTuningConfig.setRoleChance(id, value);
        else if (role) RoleModifierTuningConfig.setRoleMax(id, value);
        else if (chance) RoleModifierTuningConfig.setModifierChance(id, value);
        else RoleModifierTuningConfig.setModifierMax(id, value);
        RoleModifierTuningConfig.save(); RoleModifierTuningBridge.applyConfiguredMaxima();
    }
    private static void addGameInt(List<Row> rows, ServerWorld world, String fieldName, String label, int min, int max) throws ReflectiveOperationException {
        var field = GexpressConfig.class.getField(fieldName);
        int value = field.getInt(null);
        rows.add(new Row(label, Integer.toString(value), d -> {
            try { field.setInt(null, Math.clamp(value + d, min, max)); saveGame(world); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }));
    }
    private static void saveGame(ServerWorld world) { GexpressConfig.save(); GexpressConfigSyncHandler.broadcastConfig(world.getServer()); }
    private static void save(ServerWorld world, String name, MapPreset preset) {
        try { preset.normalize(); PresetStorage.save(world.getServer(), name, preset); GexpressPresetsSyncHandler.broadcastPresets(world.getServer()); }
        catch (Exception e) { throw new IllegalStateException("Could not save map preset", e); }
    }
}
