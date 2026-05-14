package dev.mapselect.registry;

import dev.mapselect.host.HostComponent;
import dev.mapselect.host.PlayerTagComponent;
import dev.mapselect.host.TrustedComponent;
import dev.mapselect.level.LevelComponent;
import dev.mapselect.role.bombspecialist.C4BackComponent;
import dev.mapselect.role.medic.MedicShieldComponent;
import dev.mapselect.role.silent.SilentShadowComponent;
import dev.mapselect.role.spy.SpyBugComponent;
import dev.mapselect.role.timemaster.TimeMasterComponent;
import dev.mapselect.role.warlock.WarlockComponent;
import dev.mapselect.skin.PlayerSkinComponent;
import dev.mapselect.voice.VoiceMuteState;
import dev.mapselect.weather.MapWeatherComponent;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

public class MapSelectComponents implements WorldComponentInitializer {
	@Override
	public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
		registry.register(MapWeatherComponent.KEY, MapWeatherComponent::new);
		registry.register(HostComponent.KEY, HostComponent::new);
		registry.register(TrustedComponent.KEY, TrustedComponent::new);
		registry.register(PlayerTagComponent.KEY, PlayerTagComponent::new);
		registry.register(PlayerSkinComponent.KEY, PlayerSkinComponent::new);
		registry.register(LevelComponent.KEY, LevelComponent::new);
		registry.register(C4BackComponent.KEY, C4BackComponent::new);
		registry.register(SpyBugComponent.KEY, SpyBugComponent::new);
		registry.register(MedicShieldComponent.KEY, MedicShieldComponent::new);
		registry.register(SilentShadowComponent.KEY, SilentShadowComponent::new);
		registry.register(WarlockComponent.KEY, WarlockComponent::new);
		registry.register(TimeMasterComponent.KEY, TimeMasterComponent::new);
		registry.register(VoiceMuteState.KEY, w -> new VoiceMuteState());
	}
}
