package dev.mapselect.client.hud;

import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.item.KnifeItem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;

public final class ClientKnifeChargeHud {
	private static final int READY_TICKS = 10;

	private ClientKnifeChargeHud() {}

	public static void register() {
		HudRenderCallback.EVENT.register(ClientKnifeChargeHud::render);
	}

	private static void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || client.world == null || client.currentScreen != null) return;
		if (!client.player.isUsingItem()) return;
		ItemStack stack = client.player.getActiveItem();
		if (stack == null || stack.isEmpty() || !stack.isOf(WatheItems.KNIFE)) return;
		if (!(KnifeItem.getKnifeTarget(client.player) instanceof EntityHitResult hit)
				|| !isValidTarget(hit.getEntity(), client.player)) {
			return;
		}

		float progress = Math.min(1.0F, Math.max(0.0F, client.player.getItemUseTime() / (float) READY_TICKS));
		int centerX = context.getScaledWindowWidth() / 2;
		int centerY = context.getScaledWindowHeight() / 2;
		int width = 38;
		int height = 3;
		int x = centerX - width / 2;
		int y = centerY - 14;
		int fill = Math.round((width - 2) * progress);
		int color = progress >= 1.0F ? 0xFFE6D56E : 0xFFCED8E0;

		context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xAA000000);
		context.fill(x, y, x + width, y + height, 0xAA15191D);
		context.fill(x + 1, y + 1, x + 1 + fill, y + height - 1, color);
	}

	private static boolean isValidTarget(Entity entity, PlayerEntity player) {
		return entity instanceof PlayerEntity target
			&& target != player
			&& target.isAlive()
			&& !target.isSpectator()
			&& player.squaredDistanceTo(target) <= 9.0D;
	}
}
