package dev.mapselect.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.mapselect.MapSelect;
import dev.mapselect.mixin.client.GameRendererAccessor;
import dev.mapselect.network.MafiaActionPayload;
import dev.mapselect.network.MafiaAmmoPayload;
import dev.mapselect.network.MafiaIntroPayload;
import dev.mapselect.network.MafiaStatePayload;
import dev.mapselect.registry.MapSelectItems;
import dev.mapselect.registry.MapSelectRoles;
import dev.mapselect.registry.MapSelectSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Set;
import java.util.UUID;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientMafiaState {
	private static final int FAMILY_GLOW_COLOR = 0x8C8C8C;
	private static final Identifier BLACK_WHITE_SHADER = Identifier.of(MapSelect.MOD_ID, "shaders/post/mafia_black_white.json");
	private static final ItemStack BULLET_STACK = MapSelectItems.BULLET.getDefaultStack();
	private static final Random WEATHER_RANDOM = new Random();
	private static final Set<UUID> familyIds = ConcurrentHashMap.newKeySet();
	private static boolean wasPrimaryDown;
	private static boolean wasSecondaryDown;
	private static int introTicks;
	private static boolean shaderActive;
	private static int loadedBullets;
	private static int maxBullets = 3;
	private static int familyGlowColor = FAMILY_GLOW_COLOR;
	private static float ammoAlpha;
	private static float blackWhiteStrength;
	private static long lastMafiaIntroMs;
	private static boolean pendingMafiaIntro;
	private static int pendingIntroDurationTicks;
	private static boolean playedMafiaIntroThisRound;
	private static int mafiaThunderFlashTicks;
	private static long nextMafiaThunderTick;

	private ClientMafiaState() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MafiaStatePayload.ID, (payload, context) ->
			context.client().execute(() -> {
				familyIds.clear();
				familyIds.addAll(payload.memberIds());
				familyGlowColor = payload.color();
			}));
		ClientPlayNetworking.registerGlobalReceiver(MafiaIntroPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				pendingMafiaIntro = true;
				pendingIntroDurationTicks = Math.max(pendingIntroDurationTicks, payload.durationTicks());
			}));
		ClientPlayNetworking.registerGlobalReceiver(MafiaAmmoPayload.ID, (payload, context) ->
			context.client().execute(() -> {
				loadedBullets = Math.max(0, payload.loadedBullets());
				maxBullets = Math.max(1, payload.maxBullets());
			}));
		ClientTickEvents.END_CLIENT_TICK.register(ClientMafiaState::tick);
		HudRenderCallback.EVENT.register(ClientMafiaState::renderHud);
	}

	public static boolean shouldGlow(UUID playerId) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || playerId == null || playerId.equals(client.player.getUuid())) {
			return false;
		}
		return isFamilyInstinctEnabled(client) && isLocalMafia(client) && familyIds.contains(playerId);
	}

	public static int glowColor() {
		return familyGlowColor;
	}

	private static boolean isFamilyInstinctEnabled(MinecraftClient client) {
		return client != null
			&& client.player != null
			&& WatheClient.instinctKeybind != null
			&& WatheClient.instinctKeybind.isPressed()
			&& isRoundRunning(client)
			&& !ClientVultureState.isLocalStashed(client)
			&& GameFunctions.isPlayerAliveAndSurvival(client.player);
	}

	private static void tick(MinecraftClient client) {
		if (introTicks > 0) introTicks--;
		updateMafiaIntro(client);
		updateMafiaWeather(client);
		if (ClientVultureState.isLocalStashed(client)) {
			blackWhiteStrength = 1.0F;
		} else {
			blackWhiteStrength = MathHelper.lerp(0.045F, blackWhiteStrength, shouldUseBlackWhite(client) ? 1.0F : 0.0F);
		}
		if (blackWhiteStrength < 0.01F) blackWhiteStrength = 0.0F;
		if (blackWhiteStrength > 0.99F) blackWhiteStrength = 1.0F;
		updateBlackWhiteShader(client);
		ammoAlpha = MathHelper.lerp(0.22F, ammoAlpha, shouldShowAmmo(client) ? 1.0F : 0.0F);
		if (client == null || client.player == null || client.world == null
				|| client.currentScreen != null || ClientVultureState.isLocalStashed(client)
				|| !ClientRoleRevealState.canUseRoleAbility(client)) {
			wasPrimaryDown = false;
			wasSecondaryDown = false;
			return;
		}
		Identifier roleId = localRoleId(client);
		if (MapSelectRoles.GODFATHER_ID.equals(roleId)) {
			boolean primary = isDown(client, ClientAbilityKeys.primaryBinding());
			boolean secondary = isDown(client, ClientAbilityKeys.secondaryBinding());
			if (primary && !wasPrimaryDown) {
				client.setScreen(new GodfatherRecruitScreen());
			}
			wasPrimaryDown = primary;
			wasSecondaryDown = secondary;
			return;
		}
		if (MapSelectRoles.JANITOR_ID.equals(roleId)) {
			boolean primary = isDown(client, ClientAbilityKeys.primaryBinding());
			if (primary && !wasPrimaryDown && ClientJanitorState.hasTarget()
					&& ClientPlayNetworking.canSend(MafiaActionPayload.ID)) {
				ClientPlayNetworking.send(new MafiaActionPayload(MafiaActionPayload.CLEAN_BODY));
			}
			wasPrimaryDown = primary;
			wasSecondaryDown = false;
			return;
		}
		wasPrimaryDown = false;
		wasSecondaryDown = false;
	}

	private static boolean isDown(MinecraftClient client, KeyBinding binding) {
		return binding != null && ClientAbilityKeys.isDown(client, binding);
	}

	private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.options.hudHidden) return;
		renderDarkOverlay(context);
		renderAmmo(context, client);
	}

	private static void renderDarkOverlay(DrawContext context) {
		if (blackWhiteStrength <= 0.02F) return;
		int alpha = Math.max(0, Math.min(76, Math.round(76.0F * blackWhiteStrength)));
		context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), alpha << 24);
	}

	private static void renderAmmo(DrawContext context, MinecraftClient client) {
		if (ammoAlpha <= 0.02F || client.textRenderer == null) return;
		int alpha = Math.max(0, Math.min(255, (int) (ammoAlpha * 255.0F)));
		Text text = Text.literal(Math.max(0, loadedBullets) + "/" + Math.max(1, maxBullets));
		int iconSize = 16;
		int gap = 2;
		int width = iconSize + gap + client.textRenderer.getWidth(text);
		int x = context.getScaledWindowWidth() / 2 - width / 2;
		int y = context.getScaledWindowHeight() - 92;
		context.drawItem(BULLET_STACK, x, y - 4);
		context.drawTextWithShadow(client.textRenderer, text, x + iconSize + gap, y, 0x00E6C35A | (alpha << 24));
	}

	private static void updateBlackWhiteShader(MinecraftClient client) {
		boolean shouldUseShader = blackWhiteStrength > 0.02F;
		if (client == null || client.gameRenderer == null) {
			shaderActive = false;
			return;
		}
		if (shouldUseShader && shaderActive && client.gameRenderer.getPostProcessor() == null) {
			shaderActive = false;
		}
		if (shouldUseShader == shaderActive) {
			updateShaderStrength(client);
			return;
		}
		try {
			if (shouldUseShader) {
				((GameRendererAccessor) client.gameRenderer).gexpress$loadPostProcessor(BLACK_WHITE_SHADER);
				shaderActive = true;
			} else {
				client.gameRenderer.disablePostProcessor();
				shaderActive = false;
			}
		} catch (Throwable ignored) {
			shaderActive = false;
		}
		updateShaderStrength(client);
	}

	private static void updateShaderStrength(MinecraftClient client) {
		if (!shaderActive || client == null || client.gameRenderer == null
				|| client.gameRenderer.getPostProcessor() == null) {
			return;
		}
		client.gameRenderer.getPostProcessor().setUniforms("Saturation",
			MathHelper.clamp(1.0F - blackWhiteStrength, 0.0F, 1.0F));
	}

	public static boolean shouldSuppressWatheRiser() {
		return pendingMafiaIntro || isLocalMafia(MinecraftClient.getInstance());
	}

	public static void playLocalMafiaIntro() {
		playMafiaIntro(MinecraftClient.getInstance());
	}

	public static boolean shouldBlockLocalGodfatherShot() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || !MapSelectRoles.GODFATHER_ID.equals(localRoleId(client))) return false;
		if (loadedBullets > 0) return false;
		if (client.player != null) {
			client.player.sendMessage(Text.literal("Out of bullets.").formatted(Formatting.GRAY), true);
		}
		return true;
	}

	private static boolean shouldUseBlackWhite(MinecraftClient client) {
		return ClientVultureState.isLocalStashed(client)
			|| (isLocalMafia(client) && isRoundRunning(client) && client != null && client.player != null
				&& !ClientVultureState.isLocalStashed(client)
				&& GameFunctions.isPlayerAliveAndSurvival(client.player));
	}

	public static boolean shouldUseMafiaWeather() {
		return shouldUseMafiaWeather(MinecraftClient.getInstance());
	}

	public static float mafiaRainGradient() {
		return shouldUseMafiaWeather() ? 1.0F : 0.0F;
	}

	public static float mafiaThunderGradient() {
		if (!shouldUseMafiaWeather()) return 0.0F;
		return 0.35F + mafiaLightningStrength() * 0.65F;
	}

	public static float mafiaLightningStrength() {
		return mafiaThunderFlashTicks <= 0 ? 0.0F : Math.min(1.0F, mafiaThunderFlashTicks / 10.0F);
	}

	private static boolean shouldUseMafiaWeather(MinecraftClient client) {
		return isLocalMafia(client) && isRoundRunning(client) && client != null && client.player != null
			&& !ClientVultureState.isLocalStashed(client)
			&& GameFunctions.isPlayerAliveAndSurvival(client.player);
	}

	public static boolean shouldShowOutsideRain(MinecraftClient client) {
		return shouldUseMafiaWeather(client) && isUnderOpenSky(client);
	}

	private static boolean isUnderOpenSky(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		BlockPos pos = client.player.getBlockPos();
		return client.world.isSkyVisible(pos) || client.world.isSkyVisible(pos.up());
	}

	private static boolean isRoundRunning(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return false;
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			return game != null && game.isRunning();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean shouldShowAmmo(MinecraftClient client) {
		return client != null && client.player != null && client.world != null
			&& isRoundRunning(client)
			&& MapSelectRoles.GODFATHER_ID.equals(localRoleId(client))
			&& !ClientVultureState.isLocalStashed(client)
			&& GameFunctions.isPlayerAliveAndSurvival(client.player);
	}

	private static void updateMafiaIntro(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || !isRoundRunning(client)) {
			playedMafiaIntroThisRound = false;
			pendingMafiaIntro = false;
			pendingIntroDurationTicks = 0;
			return;
		}
		if (!isLocalMafia(client)) {
			playedMafiaIntroThisRound = false;
			return;
		}
		if (playedMafiaIntroThisRound || !ClientRoleRevealState.isRoleRevealSettled()) return;
		playedMafiaIntroThisRound = true;
		pendingMafiaIntro = false;
		introTicks = Math.max(introTicks, pendingIntroDurationTicks > 0 ? pendingIntroDurationTicks : 90);
		pendingIntroDurationTicks = 0;
		playMafiaIntro(client);
	}

	private static void updateMafiaWeather(MinecraftClient client) {
		boolean active = client != null && client.player != null && client.world != null && shouldUseMafiaWeather(client);
		if (!active) {
			mafiaThunderFlashTicks = 0;
			nextMafiaThunderTick = 0L;
			return;
		}
		long now = client.world.getTime();
		if (nextMafiaThunderTick <= 0L) {
			nextMafiaThunderTick = now + 120L + WEATHER_RANDOM.nextInt(220);
		}
		if (now >= nextMafiaThunderTick) {
			mafiaThunderFlashTicks = 10;
			nextMafiaThunderTick = now + 180L + WEATHER_RANDOM.nextInt(360);
			if (client.getSoundManager() != null) {
				float pitch = 0.85F + WEATHER_RANDOM.nextFloat() * 0.25F;
				client.getSoundManager().play(PositionedSoundInstance.master(
					SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.85F, pitch));
			}
		} else if (mafiaThunderFlashTicks > 0) {
			mafiaThunderFlashTicks--;
		}
	}

	private static boolean isLocalMafia(MinecraftClient client) {
		Identifier id = localRoleId(client);
		if (MapSelectRoles.GODFATHER_ID.equals(id)
			|| MapSelectRoles.MAFIOSO_ID.equals(id)
			|| MapSelectRoles.JANITOR_ID.equals(id)) {
			return true;
		}
		return client != null && client.player != null && familyIds.contains(client.player.getUuid());
	}

	private static Identifier localRoleId(MinecraftClient client) {
		try {
			GameWorldComponent game = GameWorldComponent.KEY.getNullable(client.world);
			Role role = game == null ? null : game.getRole(client.player);
			return role == null ? null : role.identifier();
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static void playMafiaIntro(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null || client.getSoundManager() == null) return;
		long now = System.currentTimeMillis();
		if (now - lastMafiaIntroMs < 5000L) return;
		lastMafiaIntroMs = now;
		client.getSoundManager().play(PositionedSoundInstance.master(MapSelectSounds.MAFIA, 1.0F, 1.0F));
	}
}
