package dev.mapselect.client.replay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import java.util.Locale;

/** A compact transport, drawn identically with and without the mouse controls open. */
public final class ReplayControlsScreen extends Screen {
    private boolean dragging;
    private int preview = -1;
    private long lastSeek;
    private static final String[] LABELS = {"|<", "-5s", "Pause", "+5s", "-", "1x", "+", "POV", "Free", "Exit"};
    public ReplayControlsScreen() { super(Text.literal("Replay controls")); }
    @Override public void blur() {}
    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        timeline(context, mouseX, mouseY);
    }
    public static String time(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
    private static int barWidth(int width) { return Math.max(1, Math.min(460, width - 28)); }
    public static void timeline(DrawContext context, int mouseX, int mouseY) {
        if (!ClientReplay.active) return;
        var client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        int bw = barWidth(width), x = (width - bw) / 2, y = height - 42;
        boolean controls = client.currentScreen instanceof ReplayControlsScreen;
        ReplayControlsScreen screen = controls ? (ReplayControlsScreen)client.currentScreen : null;
        int tick = screen != null && screen.preview >= 0 ? screen.preview : ClientReplay.tick;
        context.fill(x - 7, y - 23, x + bw + 7, height - 5, 0xD51A1D23);
        context.drawTextWithShadow(client.textRenderer, time(tick) + " / " + time(ClientReplay.duration), x, y - 17, 0xFFF2F4F7);
        String camera = ClientReplay.buffering ? "Buffering..." : ClientReplay.cameraName();
        if (client.textRenderer.getWidth(camera) > bw / 2) camera = client.textRenderer.trimToWidth(camera, bw / 2 - 12) + "...";
        context.drawTextWithShadow(client.textRenderer, camera, x + bw - client.textRenderer.getWidth(camera), y - 17, 0xFFB7BEC9);
        context.fill(x, y, x + bw, y + 3, 0xFF454B56);
        int progress = x + (int)(bw * (double)tick / Math.max(1, ClientReplay.duration));
        context.fill(x, y, progress, y + 3, 0xFF75C8DB);
        NbtCompound hovered = null;
        for (NbtElement element : ClientReplay.markers) {
            NbtCompound marker = (NbtCompound)element;
            int mx = x + (int)(bw * (double)marker.getInt("tick") / Math.max(1, ClientReplay.duration));
            context.fill(mx - 1, y - 3, mx + 1, y + 6, 0xFFFF7380);
            if (controls && Math.abs(mouseX - mx) <= 4 && mouseY >= y - 6 && mouseY <= y + 8) hovered = marker;
        }
        context.fill(progress - 2, y - 3, progress + 2, y + 6, 0xFFF5FAFC);
        int cell = bw / LABELS.length, by = y + 12;
        for (int i = 0; i < LABELS.length; i++) {
            int bx = x + i * cell, end = i == LABELS.length - 1 ? x + bw : bx + cell - 2;
            boolean hover = controls && mouseX >= bx && mouseX < end && mouseY >= by && mouseY < by + 18;
            context.fill(bx, by, end, by + 18, hover ? 0xFF454D5B : 0xFF2C323C);
            String label = i == 2 ? (ClientReplay.paused ? "Play" : "Pause") : i == 5 ? String.format(Locale.ROOT, "%sx", ClientReplay.speed) : LABELS[i];
            context.drawCenteredTextWithShadow(client.textRenderer, label, (bx + end) / 2, by + 5, i == 9 ? 0xFFFFA0A8 : 0xFFE6ECF4);
        }
        if (!controls) context.drawCenteredTextWithShadow(client.textRenderer, "R: unlock mouse / controls", width / 2, y - 36, 0xFFADB8C6);
        if (hovered != null) {
            String killer = hovered.getString("killer");
            context.drawTooltip(client.textRenderer, Text.literal(time(hovered.getInt("tick")) + " \u00b7 " + (killer.isEmpty() ? hovered.getString("victim") + " died" : killer + " killed " + hovered.getString("victim"))), mouseX, mouseY);
        }
    }
    private boolean inBar(double mx, double my) {
        int bw = barWidth(width), x = (width - bw) / 2, y = height - 42;
        return mx >= x && mx <= x + bw && my >= y - 7 && my <= y + 8;
    }
    private void seek(double mouseX, boolean force) {
        int bw = barWidth(width), x = (width - bw) / 2;
        preview = (int)(Math.clamp((mouseX - x) / bw, 0, 1) * ClientReplay.duration);
        long now = System.nanoTime();
        if (force || now - lastSeek >= 80_000_000L) {
            ClientReplay.control("seek", Integer.toString(preview)); lastSeek = now;
        }
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (inBar(mx, my)) { dragging = true; seek(mx, true); return true; }
        int bw = barWidth(width), x = (width - bw) / 2, cell = bw / LABELS.length;
        if (mx >= x && mx < x + bw && my >= height - 30 && my < height - 12) {
            int action = Math.min(9, (int)(mx - x) / Math.max(1, cell));
            switch (action) {
                case 0 -> ClientReplay.control("seek", "0");
                case 1 -> ClientReplay.control("seek", Integer.toString(Math.max(0, ClientReplay.tick - 100)));
                case 2 -> ClientReplay.control("pause", "");
                case 3 -> ClientReplay.control("seek", Integer.toString(Math.min(ClientReplay.duration, ClientReplay.tick + 100)));
                case 4 -> ClientReplay.changeSpeed(-1);
                case 5 -> ClientReplay.control("speed", "1.0");
                case 6 -> ClientReplay.changeSpeed(1);
                case 7 -> ClientReplay.cyclePov();
                case 8 -> { ClientReplay.freeCamera(); close(); }
                case 9 -> ClientReplay.control("stop", "");
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_P) {
            ClientReplay.control("pause", ""); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            int direction = key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT ? -100 : 100;
            ClientReplay.control("seek", Integer.toString(Math.clamp(ClientReplay.tick + direction, 0, ClientReplay.duration))); return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_R) { close(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) { seek(x, false); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && dragging) { dragging = false; seek(x, true); preview = -1; return true; }
        return super.mouseReleased(x, y, button);
    }
}
