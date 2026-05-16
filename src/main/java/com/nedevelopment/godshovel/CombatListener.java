package com.nedevelopment.godshovel;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

public class CombatListener implements Listener {

    // Invincibility (Koi maar nahi sakta)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (ItemManager.isGodShovel(handItem)) {
                // Jab tak haath mein shovel hai, damage cancel ho jayega
                event.setCancelled(true);
            }
        }
    }

    // One-Shot Kill (Kitni bhi health/protection ho)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (ItemManager.isGodShovel(handItem)) {
                // Instant death damage
                event.setDamage(999999.0);
            }
        }
    }
        }

