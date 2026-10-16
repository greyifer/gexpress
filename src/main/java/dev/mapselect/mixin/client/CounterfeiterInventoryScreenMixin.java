package dev.mapselect.mixin.client;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.mapselect.client.role.counterfeiter.*;
import dev.mapselect.role.counterfeiter.CounterfeiterItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mixin(value = LimitedInventoryScreen.class, remap = false)
public abstract class CounterfeiterInventoryScreenMixin extends LimitedHandledScreen<PlayerScreenHandler>
    implements CounterfeiterInventoryInput {
    @Unique private static final Identifier GEXPRESS$FORGE_SLOT = Identifier.of("gexpress", "textures/gui/counterfeiter_slot.png");
    @Unique private final List<int[]> gexpress$forgeTargets = new ArrayList<>();
    protected CounterfeiterInventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void gexpress$drawForgeChoices(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        gexpress$forgeTargets.clear();
        if (!ClientCounterfeiterState.visible(MinecraftClient.getInstance())) return;
        List<Integer> choices = new ArrayList<>();
        for (int i=0;i<CounterfeiterItems.CHOICES.size();i++) if (ClientCounterfeiterState.available(i)) choices.add(i);
        int step = Math.min(56, (this.width - 24) / Math.max(1, choices.size()));
        int startX = this.width / 2 - (choices.size() - 1) * step / 2 - 16;
        int slotY = Math.max(70, Math.min(this.height - 82, this.height - 214));
        boolean spent = ClientCounterfeiterState.spent();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable(spent
            ? "gexpress.counterfeiter.spent" : "gexpress.counterfeiter.choose"), this.width / 2, slotY - 24, 0xFF6DE0DF);
        for (int row=0;row<choices.size();row++) {
            int index = choices.get(row), slotX = startX + row * step;
            var choice = CounterfeiterItems.CHOICES.get(index);
            boolean hovered = mouseX >= slotX && mouseX < slotX + 32 && mouseY >= slotY && mouseY < slotY + 32;
            context.drawTexture(GEXPRESS$FORGE_SLOT, slotX, slotY, 32, 32, 0, 0, 30, 30, 30, 30);
            context.drawItem(choice.forgery(), slotX + 8, slotY + 8);
            gexpress$forgeTargets.add(new int[]{slotX, slotY, index});
            if (spent && index!=ClientCounterfeiterState.selected()) context.fill(slotX + 2, slotY + 2, slotX + 30, slotY + 30, 0x88000000);
            else {
                if (hovered) context.drawBorder(slotX - 2, slotY - 2, 36, 36, 0xFF6DE0DF);
            }
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(choice.name().getString(), step - 3)),
                slotX + 16, slotY + 39, spent && index!=ClientCounterfeiterState.selected() ? 0xFF888888 : 0xFFFFFFFF);
            if (hovered) context.drawTooltip(this.textRenderer, List.of(choice.name(), Text.literal(choice.civilian()),
                choice.description(), Text.translatable(spent
                    ? "gexpress.counterfeiter.spent" : "gexpress.counterfeiter.click")), mouseX, mouseY);
        }
    }
    @Override public boolean gexpress$handleCounterfeiterMouse(double mouseX, double mouseY, int button) {
        if (!ClientCounterfeiterState.visible(MinecraftClient.getInstance())) return false;
        for (int[] target : gexpress$forgeTargets) {
            if (mouseX < target[0] || mouseX >= target[0] + 32 || mouseY < target[1] || mouseY >= target[1] + 32) continue;
            if (button == 0) ClientCounterfeiterState.forge(target[2]);
            return true;
        }
        return false;
    }
}
