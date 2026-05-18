package dev.mapselect.client.screen;

import com.mojang.authlib.GameProfile;
import dev.mapselect.client.ClientModelAttachmentPreview;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GexpressModelPlacementScreen extends Screen {
	private static final String[] LABELS = { "X", "Y", "Z", "Rot X", "Rot Y", "Rot Z", "Slant", "Scale" };
	private static final C4PlacementPreset SPY_DEFAULT =
		new C4PlacementPreset(0.0F, 0.16F, 0.31F, 0.0F, 0.0F, 0.0F, 0.0F, 0.28F);

	private final Screen parent;
	private final List<TextFieldWidget> fields = new ArrayList<>();
	private ClientModelAttachmentPreview.Kind kind = ClientModelAttachmentPreview.Kind.C4;
	private ButtonWidget kindButton;
	private boolean draggingPreview;
	private float modelYaw = 180.0F;
	private float modelPitch = 0.0F;
	private OtherClientPlayerEntity previewPlayer;

	public GexpressModelPlacementScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.model_placement.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		fields.clear();
		int x = 26;
		int y = 72;
		for (int i = 0; i < LABELS.length; i++) {
			TextFieldWidget field = new TextFieldWidget(textRenderer, x + (i % 2) * 118, y + (i / 2) * 34, 92, 18,
				Text.literal(LABELS[i]));
			field.setMaxLength(16);
			fields.add(addDrawableChild(field));
		}
		kindButton = addDrawableChild(ButtonWidget.builder(kindText(), button -> toggleKind())
			.dimensions(x, 36, 110, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.model_placement.apply"), button -> applyAndSave())
			.dimensions(x, y + 154, 74, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.model_placement.copy"), button -> copyCurrentDefaults())
			.dimensions(x + 82, y + 154, 74, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.model_placement.reset"), button -> resetCurrent())
			.dimensions(x + 164, y + 154, 74, 20)
			.build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close())
			.dimensions(width / 2 - 45, height - 28, 90, 20)
			.build());
		loadFromConfig();
		updatePreview();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xAA11151B);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.model_placement.subtitle").formatted(Formatting.GRAY),
			width / 2, 28, 0xFF9BA3AE);
		drawLabels(context);
		drawPreview(context, mouseX, mouseY);
		super.render(context, mouseX, mouseY, delta);
		updatePreview();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && inPreview(mouseX, mouseY)) {
			draggingPreview = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingPreview) {
			modelYaw = (modelYaw + (float) deltaX * 1.2F) % 360.0F;
			modelPitch = MathHelper.clamp(modelPitch + (float) deltaY * 0.7F, -35.0F, 35.0F);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingPreview = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		ClientModelAttachmentPreview.clear();
		MinecraftClient.getInstance().setScreen(parent);
	}

	private void drawLabels(DrawContext context) {
		int x = 26;
		int y = 60;
		for (int i = 0; i < LABELS.length; i++) {
			context.drawTextWithShadow(textRenderer, Text.literal(LABELS[i]), x + (i % 2) * 118,
				y + (i / 2) * 34, 0xFFB9C2CE);
		}
	}

	private void drawPreview(DrawContext context, int mouseX, int mouseY) {
		int left = Math.max(286, width / 2 - 42);
		int top = 50;
		int right = width - 26;
		int bottom = height - 44;
		context.fill(left, top, right, bottom, 0x55212833);
		context.drawBorder(left, top, right - left, bottom - top, inPreview(mouseX, mouseY) ? 0xFFC7D5E8 : 0xFF617083);
		context.drawTextWithShadow(textRenderer, kind == ClientModelAttachmentPreview.Kind.C4
				? Text.translatable("gui.gexpress.model_placement.c4")
				: Text.translatable("gui.gexpress.model_placement.spy_bug"),
			left + 10, top + 9, 0xFFFFFFFF);
		MinecraftClient client = MinecraftClient.getInstance();
		OtherClientPlayerEntity entity = previewEntity(client);
		if (entity == null) return;
		int size = Math.max(70, Math.min(128, (bottom - top) / 2));
		int centerX = (left + right) / 2;
		int centerY = (top + bottom) / 2;
		float entityScale = entity.getScale();
		float oldYaw = entity.getYaw();
		float oldPitch = entity.getPitch();
		float oldBodyYaw = entity.bodyYaw;
		float oldPrevBodyYaw = entity.prevBodyYaw;
		float oldHeadYaw = entity.headYaw;
		float oldPrevHeadYaw = entity.prevHeadYaw;
		try {
			entity.setInvisible(false);
			entity.setYaw(modelYaw);
			entity.setPitch(-modelPitch);
			entity.bodyYaw = modelYaw;
			entity.prevBodyYaw = modelYaw;
			entity.headYaw = modelYaw;
			entity.prevHeadYaw = modelYaw;
			float pitchRadians = modelPitch * ((float) Math.PI / 180.0F);
			Quaternionf bodyRotation = new Quaternionf().rotateX(pitchRadians);
			Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(bodyRotation);
			Vector3f translation = new Vector3f(0.0F,
				entity.getHeight() / 2.0F + size * entityScale, 0.0F);
			context.enableScissor(left + 20, top + 28, right - 20, bottom - 8);
			InventoryScreen.drawEntity(context, centerX, centerY, size / entityScale,
				translation, rotation, bodyRotation, entity);
		} finally {
			context.disableScissor();
			entity.setYaw(oldYaw);
			entity.setPitch(oldPitch);
			entity.bodyYaw = oldBodyYaw;
			entity.prevBodyYaw = oldPrevBodyYaw;
			entity.headYaw = oldHeadYaw;
			entity.prevHeadYaw = oldPrevHeadYaw;
		}
	}

	private OtherClientPlayerEntity previewEntity(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return null;
		ClientWorld world = client.world;
		if (previewPlayer == null || previewPlayer.getWorld() != world) {
			GameProfile profile = client.player.getGameProfile();
			previewPlayer = new OtherClientPlayerEntity(world, profile);
		}
		previewPlayer.refreshPositionAndAngles(0.0D, 0.0D, 0.0D, modelYaw, -modelPitch);
		previewPlayer.setSneaking(false);
		previewPlayer.setSprinting(false);
		ClientModelAttachmentPreview.activate(kind, currentPreset(), previewPlayer);
		return previewPlayer;
	}

	private boolean inPreview(double mouseX, double mouseY) {
		int left = Math.max(286, width / 2 - 42);
		int top = 50;
		int right = width - 26;
		int bottom = height - 44;
		return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
	}

	private void toggleKind() {
		applyLocal();
		kind = kind == ClientModelAttachmentPreview.Kind.C4
			? ClientModelAttachmentPreview.Kind.SPY_BUG
			: ClientModelAttachmentPreview.Kind.C4;
		if (kindButton != null) kindButton.setMessage(kindText());
		loadFromConfig();
		updatePreview();
	}

	private Text kindText() {
		return kind == ClientModelAttachmentPreview.Kind.C4
			? Text.translatable("gui.gexpress.model_placement.c4")
			: Text.translatable("gui.gexpress.model_placement.spy_bug");
	}

	private void loadFromConfig() {
		C4PlacementPreset preset = kind == ClientModelAttachmentPreview.Kind.C4
			? GexpressConfig.getC4PlacementPreset(0)
			: GexpressConfig.getSpyBugPlacementPreset();
		if (preset == null) preset = kind == ClientModelAttachmentPreview.Kind.C4 ? C4PlacementPreset.DEFAULT : SPY_DEFAULT;
		float[] values = toArray(preset);
		for (int i = 0; i < fields.size(); i++) {
			fields.get(i).setText(format(values[i]));
		}
	}

	private void resetCurrent() {
		C4PlacementPreset preset = kind == ClientModelAttachmentPreview.Kind.C4 ? C4PlacementPreset.DEFAULT : SPY_DEFAULT;
		float[] values = toArray(preset);
		for (int i = 0; i < fields.size(); i++) {
			fields.get(i).setText(format(values[i]));
		}
		applyLocal();
	}

	private void applyAndSave() {
		applyLocal();
		GexpressConfig.save();
		GexpressOptionsScreen.pushGexpressConfigToServer();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.translatable("gui.gexpress.model_placement.saved")
				.formatted(Formatting.GREEN), false);
		}
	}

	private void applyLocal() {
		C4PlacementPreset preset = currentPreset();
		if (kind == ClientModelAttachmentPreview.Kind.C4) {
			GexpressConfig.c4BackOffsetX = preset.offsetX();
			GexpressConfig.c4BackOffsetY = preset.offsetY();
			GexpressConfig.c4BackOffsetZ = preset.offsetZ();
			GexpressConfig.c4BackRotationX = preset.rotationX();
			GexpressConfig.c4BackRotationY = preset.rotationY();
			GexpressConfig.c4BackRotationZ = preset.rotationZ();
			GexpressConfig.c4BackSlant = preset.slant();
			GexpressConfig.c4BackScale = preset.scale();
		} else {
			GexpressConfig.spyBugOffsetX = preset.offsetX();
			GexpressConfig.spyBugOffsetY = preset.offsetY();
			GexpressConfig.spyBugOffsetZ = preset.offsetZ();
			GexpressConfig.spyBugRotationX = preset.rotationX();
			GexpressConfig.spyBugRotationY = preset.rotationY();
			GexpressConfig.spyBugRotationZ = preset.rotationZ();
			GexpressConfig.spyBugSlant = preset.slant();
			GexpressConfig.spyBugScale = preset.scale();
		}
		updatePreview();
	}

	private void copyCurrentDefaults() {
		applyLocal();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) return;
		String prefix = kind == ClientModelAttachmentPreview.Kind.C4 ? "c4Back" : "spyBug";
		C4PlacementPreset preset = currentPreset();
		String source = String.join("\n",
			defaultLine(prefix + "OffsetX", preset.offsetX()),
			defaultLine(prefix + "OffsetY", preset.offsetY()),
			defaultLine(prefix + "OffsetZ", preset.offsetZ()),
			defaultLine(prefix + "RotationX", preset.rotationX()),
			defaultLine(prefix + "RotationY", preset.rotationY()),
			defaultLine(prefix + "RotationZ", preset.rotationZ()),
			defaultLine(prefix + "Slant", preset.slant()),
			defaultLine(prefix + "Scale", preset.scale()));
		client.keyboard.setClipboard(source);
		if (client.player != null) {
			client.player.sendMessage(Text.translatable("gui.gexpress.config.option.dev.model_defaults_export.copied")
				.formatted(Formatting.GREEN), false);
		}
	}

	private void updatePreview() {
		ClientModelAttachmentPreview.activate(kind, currentPreset(), previewPlayer);
	}

	private C4PlacementPreset currentPreset() {
		C4PlacementPreset fallback = kind == ClientModelAttachmentPreview.Kind.C4
			? C4PlacementPreset.DEFAULT
			: SPY_DEFAULT;
		float[] values = toArray(fallback);
		for (int i = 0; i < fields.size(); i++) {
			Float parsed = parseFloat(fields.get(i).getText());
			if (parsed != null) values[i] = parsed;
		}
		return new C4PlacementPreset(values[0], values[1], values[2], values[3], values[4], values[5], values[6],
			values[7]).clamped();
	}

	private static float[] toArray(C4PlacementPreset preset) {
		return new float[] {
			preset.offsetX(), preset.offsetY(), preset.offsetZ(),
			preset.rotationX(), preset.rotationY(), preset.rotationZ(),
			preset.slant(), preset.scale()
		};
	}

	private static String defaultLine(String name, float value) {
		return "public static float " + name + " = " + format(value) + "F;";
	}

	private static String format(float value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static Float parseFloat(String raw) {
		if (raw == null) return null;
		String normalized = raw.trim().replace(',', '.');
		if (normalized.isEmpty() || normalized.equals("-") || normalized.equals(".") || normalized.equals("-.")) return null;
		try {
			float value = Float.parseFloat(normalized);
			return Float.isFinite(value) ? value : null;
		} catch (NumberFormatException ignored) {
			return null;
		}
	}
}
