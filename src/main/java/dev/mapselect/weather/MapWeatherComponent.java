package dev.mapselect.weather;

import dev.mapselect.MapSelect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

public class MapWeatherComponent implements AutoSyncedComponent {
	public static final ComponentKey<MapWeatherComponent> KEY = ComponentRegistry.getOrCreate(
		Identifier.of(MapSelect.MOD_ID, "map_weather"),
		MapWeatherComponent.class
	);

	public static final int NO_FOG_OVERRIDE = -1;

	private final World world;
	private WeatherType weather = WeatherType.NONE;
	private int fogColor = NO_FOG_OVERRIDE;
	private String currentMapName;

	public MapWeatherComponent(World world) {
		this.world = world;
	}

	public WeatherType getWeather() {
		return weather;
	}

	public int getFogColor() {
		return fogColor;
	}

	public boolean hasFogOverride() {
		return fogColor != NO_FOG_OVERRIDE;
	}

	public void set(WeatherType weather, int fogColor) {
		this.weather = weather == null ? WeatherType.NONE : weather;
		this.fogColor = fogColor;
		KEY.sync(this.world);
	}

	public String getCurrentMapName() {
		return currentMapName;
	}

	public void setCurrentMapName(String name) {
		this.currentMapName = name;
	}

	@Override
	public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		this.weather = WeatherType.fromString(tag.getString("weather"));
		this.fogColor = tag.contains("fogColor") ? tag.getInt("fogColor") : NO_FOG_OVERRIDE;
		this.currentMapName = tag.contains("currentMap") ? tag.getString("currentMap") : null;
	}

	@Override
	public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
		tag.putString("weather", weather.name());
		tag.putInt("fogColor", fogColor);
		if (currentMapName != null) tag.putString("currentMap", currentMapName);
	}
}
