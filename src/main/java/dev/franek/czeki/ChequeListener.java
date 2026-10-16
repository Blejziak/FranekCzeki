package dev.franek.czeki;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import static dev.franek.czeki.ChequeManager.color;

public class ChequeListener implements Listener {

    private final FranekCzekiPlugin plugin;
    private final ChequeManager manager;
    private final ChequeGuiManager guiManager;

    public ChequeListener(FranekCzekiPlugin plugin, ChequeManager manager, ChequeGuiManager guiManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onRedeemCheque(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String chequeId = manager.getChequeIdFromItem(item);
        if (chequeId == null) return;

        event.setCancelled(true);

        ChequeEntry entry = manager.getCheque(chequeId);
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&aCzeki&8] &7");

        if (entry == null) {
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.cheque-invalid")));
            return;
        }

        // Anti-Dupe: Check if already redeemed
        if (entry.getStatus() == ChequeEntry.Status.REDEEMED) {
            item.setAmount(item.getAmount() - 1);
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.cheque-already-used", "")
                    .replace("{id}", chequeId)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if voided by admin
        if (entry.getStatus() == ChequeEntry.Status.VOIDED) {
            item.setAmount(item.getAmount() - 1);
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.cheque-voided", "")
                    .replace("{id}", chequeId)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double taxPerHour = manager.getTaxPerHour();
        double maxTax = manager.getMaxTaxPercent();
        double currentVal = entry.getCurrentValue(taxPerHour, maxTax);
        double taxPct = entry.getCurrentTaxPercent(taxPerHour, maxTax);

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);

        // Mark as redeemed & save
        entry.setStatus(ChequeEntry.Status.REDEEMED);
        entry.setRedeemedBy(player.getName());
        entry.setRedeemedAt(System.currentTimeMillis());
        entry.setRedeemedAmount(currentVal);
        manager.saveData();

        // Deposit to Vault
        if (currentVal > 0 && plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, currentVal);
        }

        String msg = plugin.getConfig().getString("messages.cheque-redeemed", "")
                .replace("{id}", chequeId)
                .replace("{current}", manager.formatMoney(currentVal))
                .replace("{initial}", manager.formatMoney(entry.getInitialAmount()))
                .replace("{tax}", String.format("%.0f", taxPct));
        player.sendMessage(color(prefix + msg));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChequeGuiHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        int page = holder.getPage();
        boolean adminMode = holder.isAdminMode();

        if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == org.bukkit.Material.ARROW) {
            if (adminMode) guiManager.openAdminGui(player, page - 1);
            else guiManager.openHistoryGui(player, page - 1);
            return;
        }

        if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == org.bukkit.Material.ARROW) {
            if (adminMode) guiManager.openAdminGui(player, page + 1);
            else guiManager.openHistoryGui(player, page + 1);
            return;
        }

        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&aCzeki&8] &7");

        // Admin Clear All button at slot 50
        if (adminMode && slot == 50) {
            if (!hasClearPerm(player)) {
                player.sendMessage(color(prefix + plugin.getConfig().getString("messages.no-permission")));
                return;
            }
            manager.clearHistory();
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.admin-cleared-history")));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.2f);
            guiManager.openAdminGui(player, 1);
            return;
        }

        // Clicking a cheque entry in Admin GUI
        if (adminMode && slot < 45) {
            ItemStack clicked = event.getCurrentItem();
            String chequeId = guiManager.getGuiChequeId(clicked);
            if (chequeId == null) return;

            ChequeEntry entry = manager.getCheque(chequeId);
            if (entry == null) return;

            if (event.isLeftClick()) {
                manager.voidCheque(chequeId);
                String msg = plugin.getConfig().getString("messages.admin-voided-cheque", "")
                        .replace("{id}", chequeId)
                        .replace("{creator}", entry.getCreator());
                player.sendMessage(color(prefix + msg));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                guiManager.openAdminGui(player, page);
            } else if (event.isRightClick()) {
                manager.deleteChequeFromHistory(chequeId);
                player.sendMessage(color(prefix + "&eUsunieto czek &f#" + chequeId + " &ez historii."));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                guiManager.openAdminGui(player, page);
            }
        }
    }

    private boolean hasClearPerm(Player player) {
        return player.isOp()
                || player.hasPermission("franek.czek.adminclear")
                || player.hasPermission("tojak1cper.czek.adminclear")
                || player.hasPermission("franek.czek.admin")
                || player.hasPermission("tojak1cper.czek.admin");
    }
}
