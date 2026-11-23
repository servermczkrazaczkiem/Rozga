package pl.skypvp.rozga;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class Rozga extends JavaPlugin implements Listener {
    private NamespacedKey key;
    private long tick;
    private final Map<UUID, Long> locks = new HashMap<>();
    private final Map<UUID, Long> uses = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        key = new NamespacedKey(this, "rozga");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            tick++;
            locks.entrySet().removeIf(entry -> entry.getValue() <= tick);
            uses.entrySet().removeIf(entry -> entry.getValue() <= tick);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (locked(player) && player.isGliding()) player.setGliding(false);
            }
        }, 1L, 1L);
    }

    private boolean locked(Player player) {
        return locks.getOrDefault(player.getUniqueId(), 0L) > tick;
    }

    private boolean isRod(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private ItemStack createRod() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Rózga", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text("Uderz gracza, aby zablokowac elytre.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Blokada elytry: " + seconds() + " s", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private int seconds() { return Math.max(1, Math.min(3600, getConfig().getInt("lock-seconds", 7))); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)
            || !(event.getEntity() instanceof Player victim)) return;
        if (!isRod(attacker.getInventory().getItemInMainHand()) || event.getFinalDamage() <= 0) return;
        long remaining = uses.getOrDefault(attacker.getUniqueId(), 0L) - tick;
        if (remaining > 0) {
            attacker.sendActionBar(Component.text("Rozga za " + ((remaining + 19) / 20) + " s", NamedTextColor.RED));
            return;
        }
        int duration = seconds() * 20;
        long until = Math.max(locks.getOrDefault(victim.getUniqueId(), 0L), tick + duration);
        locks.put(victim.getUniqueId(), until);
        int visualTicks = (int) (until - tick);
        victim.setCooldown(Material.ELYTRA, Math.max(victim.getCooldown(Material.ELYTRA), visualTicks));
        ItemStack chest = victim.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA)
            victim.setCooldown(chest, Math.max(victim.getCooldown(chest), visualTicks));
        victim.setGliding(false);
        int cooldown = Math.max(0, Math.min(3600, getConfig().getInt("cooldown-seconds", 0)));
        if (cooldown > 0) uses.put(attacker.getUniqueId(), tick + cooldown * 20L);
        attacker.sendActionBar(Component.text("Zablokowano elytre: " + victim.getName(), NamedTextColor.GOLD));
        victim.sendMessage(Component.text("Rozga zablokowala Twoja elytre na " + seconds() + " sekund!", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player && locked(player)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Elytra zablokowana: "
                + ((locks.get(player.getUniqueId()) - tick + 19) / 20) + " s", NamedTextColor.RED));
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (locked(player)) {
            int remaining = (int) (locks.get(player.getUniqueId()) - tick);
            player.setCooldown(Material.ELYTRA, Math.max(player.getCooldown(Material.ELYTRA), remaining));
            player.setGliding(false);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("rozga.admin")) {
            sender.sendMessage("Brak uprawnien: rozga.admin"); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig(); sender.sendMessage("Przeladowano konfiguracje Rozgi."); return true;
        }
        if (args.length > 1) return false;
        Player target = args.length == 1 ? Bukkit.getPlayerExact(args[0])
            : (sender instanceof Player player ? player : null);
        if (target == null) { sender.sendMessage("Uzycie: /rozga <nick gracza online>"); return true; }
        if (target.getInventory().firstEmpty() == -1) {
            sender.sendMessage("Gracz musi miec wolny slot."); return true;
        }
        target.getInventory().addItem(createRod());
        sender.sendMessage("Przekazano Rozge graczowi " + target.getName());
        return true;
    }
}
