package dev.mapselect.role.counterfeiter;

import dev.mapselect.item.CounterfeitItem;
import net.minecraft.registry.Registry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.List;

/** Audited civilian-role equipment from Wathe, Noelle's Roles and KinsWathe. */
public final class CounterfeiterItems {
    public record Choice(String key, Identifier original, String civilian, CounterfeitItem fake) {
        public boolean available() { return Registries.ITEM.containsId(original); }
        public Text description() { return Text.translatable("gexpress.counterfeiter." + (key.equals("master_key") ? "key" : key.equals("role_mine") ? "trap" : key.equals("pan") ? "pan" : "prop")); }
        public Text name() { return Text.translatable("item." + original.getNamespace() + "." + original.getPath()); }
        public ItemStack forgery() {
            ItemStack stack = fake.getDefaultStack(); stack.set(DataComponentTypes.ITEM_NAME, name()); return stack;
        }
    }
    private static Choice choice(String key, String original, String civilian) {
        return new Choice(key, Identifier.of(original), civilian, switch(key) { case "master_key" -> new dev.mapselect.item.CounterfeitMasterKeyItem(); case "pan" -> new dev.mapselect.item.CounterfeitPanItem(); case "role_mine" -> new dev.mapselect.item.CounterfeitTrapItem(); default -> new CounterfeitItem(); });
    }
    public static final List<Choice> CHOICES = List.of(
        choice("master_key", "noellesroles:master_key", "Conductor"),
        choice("role_mine", "noellesroles:role_mine", "Trapper"),
        choice("medical_kit", "kinswathe:medical_kit", "Physician"),
        choice("pill", "kinswathe:pill", "Physician"),
        choice("pan", "kinswathe:pan", "Cook")
    );
    private CounterfeiterItems() {}
    public static void register() {
        // Keep older forged props readable in saved inventories, without offering them to forge.
        Registry.register(Registries.ITEM, Identifier.of("gexpress", "counterfeit_revolver"), new CounterfeitItem());
        Registry.register(Registries.ITEM, Identifier.of("gexpress", "counterfeit_lockpick"), new CounterfeitItem());
        // Stable registrations on both sides; missing optional mods simply remove their choices.
        for (Choice choice : CHOICES) Registry.register(Registries.ITEM, Identifier.of("gexpress", "counterfeit_" + choice.key), choice.fake);
    }
    public static int availableMask() {
        int mask = 0;
        for (int i=0;i<CHOICES.size();i++) if (CHOICES.get(i).available()) mask |= 1 << i;
        return mask;
    }
}
