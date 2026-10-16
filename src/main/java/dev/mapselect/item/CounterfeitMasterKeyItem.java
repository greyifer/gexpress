package dev.mapselect.item;

/** Damage is counted explicitly by the door hook; enchantments cannot extend its three uses. */
public final class CounterfeitMasterKeyItem extends CounterfeitItem {
    public CounterfeitMasterKeyItem() { super(new Settings().maxDamage(3)); }
}
