package com.nedevelopment.godshovel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    public static ItemStack getGodShovel() {
        ItemStack shovel = new ItemStack(Material.WOODEN_SHOVEL);
        ItemMeta meta = shovel.getItemMeta();

        if (meta != null) {
            // Name
            meta.displayName(Component.text("God Shovel").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));

            // Custom Lore
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(" "));
            lore.add(Component.text("The Ultimate Weapon of the Server").color(NamedTextColor.GOLD));
            lore.add(Component.text("Only one can exist.").color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            lore.add(Component.text(" "));
            lore.add(Component.text("Passive:").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text("▶ One-Shot Kills").color(NamedTextColor.GRAY));
            lore.add(Component.text("▶ Absolute Invincibility").color(NamedTextColor.GRAY));
            lore.add(Component.text(" "));
            lore.add(Component.text("Active:").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
            lore.add(Component.text("▶ Shift+Left: Mind Control (10s)").color(NamedTextColor.GRAY));
            lore.add(Component.text("▶ Shift+Right: MeteorRingPower (10s)").color(NamedTextColor.GRAY));
            
            meta.lore(lore);

            // Glowing Effect
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setUnbreakable(true);

            // Custom Model Data (Texture pack ke liye)
            meta.setCustomModelData(1001); // CMD value 1001

            shovel.setItemMeta(meta);
        }
        return shovel;
    }

    public static boolean isGodShovel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasCustomModelData() && meta.getCustomModelData() == 1001;
    }
}
