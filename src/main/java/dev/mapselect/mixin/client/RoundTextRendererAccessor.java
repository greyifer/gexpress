package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.RoundTextRenderer;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RoundTextRenderer.class, remap = false)
public interface RoundTextRendererAccessor {
	@Accessor("welcomeTime")
	static int gexpress$getWelcomeTime() {
		throw new AssertionError();
	}

	@Accessor("endTime")
	static int gexpress$getEndTime() {
		throw new AssertionError();
	}

	@Accessor("role")
	static RoleAnnouncementTexts.RoleAnnouncementText gexpress$getRole() {
		throw new AssertionError();
	}
}
