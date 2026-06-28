package dev.mapselect.client.screen;

import com.mojang.authlib.GameProfile;
import dev.mapselect.client.render.ClientModelAttachmentPreview;
import dev.mapselect.config.GexpressConfig;
import dev.mapselect.role.bombspecialist.C4PlacementPreset;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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
	private static final int PANEL = 0xCC151A20;
	private static final int BORDER = 0x775E6D7E;
	private static final int GOLD = 0xFFE7C66A;
	private static final int RED = 0xFFFF5F63;
	private static final int BLUE = 0xFF7FB6FF;
	private static final int GREEN = 0xFF74D990;
	private static final int TEXT = 0xFFE8EDF2;
	private static final int MUTED = 0xFF9DA9B6;
	private static final C4PlacementPreset SPY_DEFAULT =
		new C4PlacementPreset(0.0F, 0.16F, 0.31F, 0.0F, 0.0F, 0.0F, 0.0F, 0.28F);

	private final Screen parent;
	private final List<TextFieldWidget> fields = new ArrayList<>();
	private ClientModelAttachmentPreview.Kind kind = ClientModelAttachmentPreview.Kind.C4;
	private ButtonWidget kindButton;
	private ButtonWidget toolButton;
	private ButtonWidget applyButton;
	private ButtonWidget copyButton;
	private ButtonWidget resetButton;
	private ButtonWidget cameraButton;
	private ButtonWidget doneButton;
	private boolean draggingPreview;
	private boolean panningPreview;
	private TransformAxis activeGizmo;
	private boolean rotateTool;
	private int lastGizmoX;
	private int lastGizmoY;
	private int lastGizmoRadius;
	private float cameraYaw = 180.0F;
	private float cameraPitch = 6.0F;
	private float cameraZoom = 1.0F;
	private float cameraPanX;
	private float cameraPanY;
	private AbstractClientPlayerEntity previewPlayer;

	public GexpressModelPlacementScreen(Screen parent) {
		super(Text.translatable("gui.gexpress.model_placement.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		fields.clear();
		for (String label : LABELS) {
			TextFieldWidget field = new TextFieldWidget(textRenderer, 0, 0, 90, 18, Text.literal(label));
			field.setMaxLength(16);
			fields.add(addDrawableChild(field));
		}
		kindButton = addDrawableChild(ButtonWidget.builder(kindText(), button -> toggleKind()).build());
		toolButton = addDrawableChild(ButtonWidget.builder(toolText(), button -> toggleTool()).build());
		applyButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.model_placement.apply"),
			button -> applyAndSave()).build());
		copyButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.model_placement.copy"),
			button -> copyCurrentDefaults()).build());
		resetButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.gexpress.model_placement.reset"),
			button -> resetCurrent()).build());
		cameraButton = addDrawableChild(ButtonWidget.builder(Text.literal("Reset View"), button -> resetCamera()).build());
		doneButton = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).build());
		layoutWidgets();
		loadFromConfig();
		updatePreview();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, 0xF00E1116);
		drawHeader(context);
		drawPreview(context, mouseX, mouseY);
		drawControls(context);
		super.render(context, mouseX, mouseY, delta);
		updatePreview();
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if ((button == 0 || button == 1 || button == 2) && inPreview(mouseX, mouseY)) {
			if (button == 0) {
				TransformAxis gizmo = hitGizmo(mouseX, mouseY);
				if (gizmo != null) {
					activeGizmo = gizmo;
					draggingPreview = true;
					panningPreview = false;
					return true;
				}
			}
			draggingPreview = true;
			panningPreview = button != 0 || Screen.hasShiftDown();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingPreview) {
			if (activeGizmo != null) {
				dragGizmo(activeGizmo, deltaX, deltaY);
				return true;
			}
			if (panningPreview || Screen.hasShiftDown()) {
				cameraPanX += (float) deltaX;
				cameraPanY += (float) deltaY;
			} else {
				cameraYaw = (cameraYaw + (float) deltaX * 0.95F) % 360.0F;
				cameraPitch = MathHelper.clamp(cameraPitch + (float) deltaY * 0.55F, -38.0F, 42.0F);
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingPreview = false;
		panningPreview = false;
		activeGizmo = null;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inPreview(mouseX, mouseY)) {
			cameraZoom = MathHelper.clamp(cameraZoom + (float) verticalAmount * 0.08F, 0.62F, 1.82F);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void close() {
		ClientModelAttachmentPreview.clear();
		MinecraftClient.getInstance().setScreen(parent);
	}

	private void drawHeader(DrawContext context) {
		context.fill(0, 0, width, 42, 0xEE111820);
		context.fill(0, 40, width, 42, GOLD);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 11, TEXT);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("gui.gexpress.model_placement.subtitle").formatted(Formatting.GRAY),
			width / 2, 24, MUTED);
	}

	private void drawPreview(DrawContext context, int mouseX, int mouseY) {
		int left = previewX();
		int top = previewY();
		int w = previewW();
		int h = previewH();
		context.fill(left, top, left + w, top + h, PANEL);
		context.drawBorder(left, top, w, h, inPreview(mouseX, mouseY) ? 0xFFC8D6EA : BORDER);
		context.fill(left, top, left + w, top + 2, GOLD);
		context.drawTextWithShadow(textRenderer, Text.literal("3D Placement Preview").formatted(Formatting.BOLD),
			left + 12, top + 10, TEXT);
		context.drawTextWithShadow(textRenderer, kindText(), left + 12, top + 24, MUTED);
		drawPreviewGrid(context, left + 14, top + 42, w - 28, h - 56);

		MinecraftClient client = MinecraftClient.getInstance();
		AbstractClientPlayerEntity entity = previewEntity(client);
		if (entity == null) return;

		int sceneX = left + 14;
		int sceneY = top + 42;
		int sceneW = w - 28;
		int sceneH = h - 56;
		int size = MathHelper.clamp((int) (Math.min(sceneW, sceneH) * 0.13F * cameraZoom), 42, 96);
		int centerX = sceneX + sceneW / 2 + Math.round(cameraPanX);
		int centerY = sceneY + sceneH / 2 + Math.round(cameraPanY) + Math.round(size * 0.85F);

		float oldYaw = entity.getYaw();
		float oldPitch = entity.getPitch();
		float oldBodyYaw = entity.bodyYaw;
		float oldPrevBodyYaw = entity.prevBodyYaw;
		float oldHeadYaw = entity.headYaw;
		float oldPrevHeadYaw = entity.prevHeadYaw;
		try {
			entity.setInvisible(false);
			entity.setYaw(cameraYaw);
			entity.setPitch(-cameraPitch);
			entity.bodyYaw = cameraYaw;
			entity.prevBodyYaw = cameraYaw;
			entity.headYaw = cameraYaw;
			entity.prevHeadYaw = cameraYaw;
			float pitchRadians = cameraPitch * ((float) Math.PI / 180.0F);
			Quaternionf bodyRotation = new Quaternionf().rotateX(pitchRadians);
			Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(bodyRotation);
			Vector3f translation = new Vector3f(0.0F, entity.getHeight() / 2.0F, 0.0F);
			context.enableScissor(sceneX, sceneY, sceneX + sceneW, sceneY + sceneH);
			InventoryScreen.drawEntity(context, centerX, centerY, size / entity.getScale(),
				translation, rotation, bodyRotation, entity);
			C4PlacementPreset preset = currentPreset();
			int gizmoX = centerX + Math.round(preset.offsetX() * size * 2.2F);
			int gizmoY = centerY - Math.round((0.92F + preset.offsetY()) * size);
			drawTransformGizmos(context, gizmoX, gizmoY, size, mouseX, mouseY);
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

	private void drawTransformGizmos(DrawContext context, int x, int y, int size, int mouseX, int mouseY) {
		int r = MathHelper.clamp(Math.round(size * 0.42F), 22, 42);
		lastGizmoX = x;
		lastGizmoY = y;
		lastGizmoRadius = r;
		TransformAxis hover = activeGizmo != null ? activeGizmo : hitGizmo(mouseX, mouseY);

		context.fill(x - 3, y - 3, x + 4, y + 4, 0xFFE8EDF2);
		drawLine(context, x, y, x + r, y, hover == TransformAxis.X ? 0xFFFFFFFF : RED, 3);
		drawLine(context, x, y, x, y - r, hover == TransformAxis.Y ? 0xFFFFFFFF : GREEN, 3);
		drawLine(context, x, y, x - r, y + r, hover == TransformAxis.Z ? 0xFFFFFFFF : BLUE, 3);
		drawArrowHead(context, x + r, y, 1, 0, RED);
		drawArrowHead(context, x, y - r, 0, -1, GREEN);
		drawArrowHead(context, x - r, y + r, -1, 1, BLUE);
		context.drawTextWithShadow(textRenderer, Text.literal("X"), x + r + 7, y - 4, RED);
		context.drawTextWithShadow(textRenderer, Text.literal("Y"), x - 4, y - r - 12, GREEN);
		context.drawTextWithShadow(textRenderer, Text.literal("Z"), x - r - 12, y + r - 4, BLUE);
		context.fill(x - 34, y + r + 16, x + 35, y + r + 30, 0xAA111820);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal(rotateTool ? "ROTATE" : "MOVE"),
			x, y + r + 19, rotateTool ? GOLD : TEXT);
	}

	private void drawArrowHead(DrawContext context, int x, int y, int dx, int dy, int color) {
		if (dx != 0 && dy == 0) {
			int sign = Integer.signum(dx);
			drawLine(context, x, y, x - sign * 7, y - 5, color, 2);
			drawLine(context, x, y, x - sign * 7, y + 5, color, 2);
		} else if (dy != 0 && dx == 0) {
			int sign = Integer.signum(dy);
			drawLine(context, x, y, x - 5, y - sign * 7, color, 2);
			drawLine(context, x, y, x + 5, y - sign * 7, color, 2);
		} else {
			drawLine(context, x, y, x + 1, y - 9, color, 2);
			drawLine(context, x, y, x + 9, y - 1, color, 2);
		}
	}

	private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color, int thickness) {
		int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
		if (steps <= 0) {
			context.fill(x1, y1, x1 + thickness, y1 + thickness, color);
			return;
		}
		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			int x = Math.round(MathHelper.lerp(t, x1, x2));
			int y = Math.round(MathHelper.lerp(t, y1, y2));
			context.fill(x - thickness / 2, y - thickness / 2, x + thickness / 2 + 1, y + thickness / 2 + 1, color);
		}
	}

	private TransformAxis hitGizmo(double mouseX, double mouseY) {
		if (lastGizmoRadius <= 0) return null;
		double dx = mouseX - lastGizmoX;
		double dy = mouseY - lastGizmoY;
		double r = lastGizmoRadius;
		if (dx >= 0.0D && dx <= r + 10.0D && Math.abs(dy) <= 7.0D) return TransformAxis.X;
		if (dy <= 0.0D && dy >= -r - 10.0D && Math.abs(dx) <= 7.0D) return TransformAxis.Y;
		if (dx <= 0.0D && dy >= 0.0D && dx >= -r - 12.0D && dy <= r + 12.0D
				&& Math.abs(Math.abs(dx) - Math.abs(dy)) <= 9.0D) {
			return TransformAxis.Z;
		}
		return null;
	}

	private void dragGizmo(TransformAxis gizmo, double deltaX, double deltaY) {
		int index = rotateTool ? 3 + gizmo.ordinal() : gizmo.ordinal();
		float delta = switch (gizmo) {
			case X -> (float) deltaX * (rotateTool ? 0.8F : 0.006F);
			case Y -> (float) -deltaY * (rotateTool ? 0.8F : 0.006F);
			case Z -> (float) (deltaX - deltaY) * (rotateTool ? 0.55F : 0.004F);
		};
		adjustField(index, delta, rotateTool ? -180.0F : -1.0F, rotateTool ? 180.0F : 1.0F);
		applyLocal();
	}

	private void adjustField(int index, float delta, float min, float max) {
		float[] values = toArray(currentPreset());
		float value = values[index];
		Float parsed = parseFloat(fields.get(index).getText());
		if (parsed != null) value = parsed;
		fields.get(index).setText(format(MathHelper.clamp(value + delta, min, max)));
	}

	private void drawPreviewGrid(DrawContext context, int x, int y, int w, int h) {
		context.fill(x, y, x + w, y + h, 0xEE090C11);
		int horizon = y + (int) (h * 0.68F);
		context.fill(x, horizon, x + w, horizon + 1, 0x446D7786);
		for (int i = -5; i <= 5; i++) {
			int gx = x + w / 2 + i * Math.max(18, w / 14);
			context.fill(gx, horizon, gx + 1, y + h - 8, 0x224A5360);
		}
		for (int i = 0; i < 6; i++) {
			int gy = horizon + i * Math.max(12, h / 18);
			context.fill(x + 18, gy, x + w - 18, gy + 1, 0x224A5360);
		}
	}

	private void drawControls(DrawContext context) {
		int x = controlsX();
		int y = previewY();
		int w = controlsW();
		int h = previewH();
		context.fill(x, y, x + w, y + h, PANEL);
		context.drawBorder(x, y, w, h, BORDER);
		context.fill(x, y, x + w, y + 2, GOLD);
		context.drawTextWithShadow(textRenderer, Text.literal("Placement").formatted(Formatting.BOLD), x + 12, y + 10, TEXT);
		context.drawTextWithShadow(textRenderer, Text.literal("Values update the preview live."), x + 12, y + 24, MUTED);
		for (int i = 0; i < LABELS.length; i++) {
			TextFieldWidget field = fields.get(i);
			context.drawTextWithShadow(textRenderer, Text.literal(LABELS[i]), field.getX(), field.getY() - 10, MUTED);
		}
		context.drawTextWithShadow(textRenderer,
			Text.literal("Camera: drag rotate, shift/right drag pan, scroll zoom."),
			x + 12, y + h - 74, 0xFF768391);
		context.drawTextWithShadow(textRenderer,
			Text.literal("Manipulator: choose Move or Rotate, then drag an X/Y/Z axis."),
			x + 12, y + h - 61, 0xFF768391);
	}

	private AbstractClientPlayerEntity previewEntity(MinecraftClient client) {
		if (client == null || client.player == null || client.world == null) return null;
		ClientWorld world = client.world;
		if (previewPlayer == null || previewPlayer.getWorld() != world) {
			GameProfile profile = client.player.getGameProfile();
			previewPlayer = new OtherClientPlayerEntity(world, profile);
		}
		previewPlayer.refreshPositionAndAngles(0.0D, 0.0D, 0.0D, cameraYaw, -cameraPitch);
		previewPlayer.setSneaking(false);
		previewPlayer.setSprinting(false);
		ClientModelAttachmentPreview.activate(kind, currentPreset(), previewPlayer);
		return previewPlayer;
	}

	private void layoutWidgets() {
		int x = controlsX() + 12;
		int y = previewY() + 54;
		kindButton.setDimensionsAndPosition(controlsW() - 24, 20, x, y);
		toolButton.setDimensionsAndPosition(controlsW() - 24, 20, x, y + 26);
		y += 60;
		int colW = (controlsW() - 34) / 2;
		for (int i = 0; i < fields.size(); i++) {
			int col = i % 2;
			int row = i / 2;
			fields.get(i).setDimensionsAndPosition(colW, 18, x + col * (colW + 10), y + row * 32);
		}
		int buttonY = y + 136;
		int buttonW = (controlsW() - 34) / 2;
		applyButton.setDimensionsAndPosition(buttonW, 20, x, buttonY);
		copyButton.setDimensionsAndPosition(buttonW, 20, x + buttonW + 10, buttonY);
		resetButton.setDimensionsAndPosition(buttonW, 20, x, buttonY + 28);
		cameraButton.setDimensionsAndPosition(buttonW, 20, x + buttonW + 10, buttonY + 28);
		doneButton.setDimensionsAndPosition(Math.min(112, controlsW() - 24), 20,
			x + controlsW() - 24 - Math.min(112, controlsW() - 24), height - 32);
	}

	private boolean inPreview(double mouseX, double mouseY) {
		return mouseX >= previewX() && mouseX < previewX() + previewW()
			&& mouseY >= previewY() && mouseY < previewY() + previewH();
	}

	private int previewX() {
		return 22;
	}

	private int previewY() {
		return 54;
	}

	private int controlsW() {
		return Math.min(328, Math.max(270, width / 4));
	}

	private int controlsX() {
		return width - controlsW() - 22;
	}

	private int previewW() {
		return Math.max(280, controlsX() - previewX() - 16);
	}

	private int previewH() {
		return Math.max(190, height - previewY() - 54);
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

	private void toggleTool() {
		rotateTool = !rotateTool;
		if (toolButton != null) toolButton.setMessage(toolText());
	}

	private Text toolText() {
		return Text.literal(rotateTool ? "Rotate Tool" : "Move Tool");
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
		for (int i = 0; i < fields.size(); i++) fields.get(i).setText(format(values[i]));
	}

	private void resetCurrent() {
		C4PlacementPreset preset = kind == ClientModelAttachmentPreview.Kind.C4 ? C4PlacementPreset.DEFAULT : SPY_DEFAULT;
		float[] values = toArray(preset);
		for (int i = 0; i < fields.size(); i++) fields.get(i).setText(format(values[i]));
		applyLocal();
	}

	private void resetCamera() {
		cameraYaw = 180.0F;
		cameraPitch = 6.0F;
		cameraZoom = 1.0F;
		cameraPanX = 0.0F;
		cameraPanY = 0.0F;
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

	private enum TransformAxis {
		X,
		Y,
		Z
	}
}
