package dev.mapselect.client.replay;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class ReplayLoadingScreen extends Screen {
    private String phase = "Reading replay";
    private int progress;
    public ReplayLoadingScreen() { super(Text.literal("Loading replay")); }
    public void update(String phase, int progress) {
        this.phase = phase; this.progress = Math.max(this.progress, Math.clamp(progress, 0, 100));
    }
    @Override public void blur() {}
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0101010);
    }
    @Override public boolean shouldPause() { return false; }
    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close()).dimensions(width / 2 - 40, height / 2 + 48, 80, 20).build());
    }
    @Override public void close() {
        ClientReplay.loading = false; ClientReplay.control("stop", ""); client.setScreen(new ReplayLibraryScreen());
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 40, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, phase, width / 2, height / 2 - 18, 0xFFCCCCCC);
        int barWidth = Math.min(260, width - 40), x = (width - barWidth) / 2, y = height / 2 + 4;
        context.fill(x, y, x + barWidth, y + 12, 0xFFFFFFFF);
        context.fill(x + 1, y + 1, x + barWidth - 1, y + 11, 0xFF171717);
        context.fill(x + 3, y + 3, x + 3 + (barWidth - 6) * progress / 100, y + 9, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, progress + "%", width / 2, y + 20, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
