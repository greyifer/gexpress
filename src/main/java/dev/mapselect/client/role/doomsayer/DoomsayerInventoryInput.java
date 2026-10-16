package dev.mapselect.client.role.doomsayer;

/** Input bridge implemented by the Doomsayer overlay mixin. */
public interface DoomsayerInventoryInput {
	boolean gexpress$handleDoomsayerMouse(double mouseX, double mouseY, int button);

	boolean gexpress$handleDoomsayerKey(int keyCode, int scanCode, int modifiers);
}
