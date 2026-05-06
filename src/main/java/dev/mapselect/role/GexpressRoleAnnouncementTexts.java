package dev.mapselect.role;

import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;

public final class GexpressRoleAnnouncementTexts {
	public static final RoleAnnouncementTexts.RoleAnnouncementText MAFIA =
		RoleAnnouncementTexts.registerRoleAnnouncementText(
			new RoleAnnouncementTexts.RoleAnnouncementText("gexpress.mafia", 0x5A5A5A));

	private GexpressRoleAnnouncementTexts() {}

	public static void register() {
		// Static initializer registers the custom announcement text.
	}
}
