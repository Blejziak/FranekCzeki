package dev.franek.czeki;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.franek.czeki.ChequeManager.color;

public class ChequeGuiManager {

    private final FranekCzekiPlugin plugin;
    private final ChequeManager manager;
    private final NamespacedKey guiChequeIdKey;

    public ChequeGuiManager(FranekCzekiPlugin plugin, ChequeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.guiChequeIdKey = new NamespacedKey(plugin, "gui_cheque_id");
    }

    public void openHistoryGui(Player player, int page) {
        openGui(player, page, false);
    }

    public void openAdminGui(Player player, int page) {
        openGui(player, page, true);
    }

    private void openGui(Player player, int page, boolean adminMode) {
        List<ChequeEntry> all = manager.getAllChequesNewestFirst();
        int perPage = 45;
        int maxPage = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        int safePage = Math.max(1, Math.min(page, maxPage));

        String rawTitle = plugin.getConfig().getString(
                adminMode ? "gui.admin-title" : "gui.history-title",
                adminMode ? "&8Panel Admina - Czeki &7(Strona {page})" : "&8Historia Czekow &7(Strona {page})"
        );
        String title = color(rawTitle.replace("{page}", safePage + "/" + maxPage));

        Inventory inv = Bukkit.createInventory(new ChequeGuiHolder(adminMode, safePage), 54, title);

        int startIndex = (safePage - 1) * perPage;
        int endIndex = Math.min(startIndex + perPage, all.size());

        double taxPerHour = manager.getTaxPerHour();
        double maxTax = manager.getMaxTaxPercent();

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            ChequeEntry entry = all.get(i);
            inv.setItem(slot, createGuiChequeIcon(entry, adminMode, taxPerHour, maxTax));
        }

        // Bottom navigation bar (slots 45..53)
        ItemStack glass = createSimpleItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ");
        for (int s = 45; s < 54; s++) {
            inv.setItem(s, glass);
        }

        if (safePage > 1) {
            inv.setItem(45, createSimpleItem(Material.ARROW, "&e« Poprzednia strona", "&7Kliknij, aby przejsc na strone &f" + (safePage - 1)));
        }

        inv.setItem(48, createSimpleItem(
                Material.BOOK,
                "&a&lPodsumowanie Czekow",
                "&8» &7Wszystkich czekow w historii: &f" + all.size(),
                "&8» &7Podatek czasowy: &c-" + taxPerHour + "% / 1h",
                "&8» &7Strona: &e" + safePage + "&7/&e" + maxPage
        ));

        if (adminMode) {
            inv.setItem(50, createSimpleItem(
                    Material.BARRIER,
                    "&c&lWyczysc Historie Czekow",
                    "&7Usuwa cala historie czekow z serwera.",
                    "",
                    "&eKliknij, aby wyczyscic cala historie!"
            ));
        }

        if (safePage < maxPage) {
            inv.setItem(53, createSimpleItem(Material.ARROW, "&eNastepna strona »", "&7Kliknij, aby przejsc na strone &f" + (safePage + 1)));
        }

        player.openInventory(inv);
    }

    private ItemStack createGuiChequeIcon(ChequeEntry entry, boolean adminMode, double taxPerHour, double maxTax) {
        Material mat;
        String statusText;
        if (entry.getStatus() == ChequeEntry.Status.ACTIVE) {
            mat = Material.PAPER;
            statusText = "&aAKTYWNY (Niezrealizowany)";
        } else if (entry.getStatus() == ChequeEntry.Status.REDEEMED) {
            mat = Material.MAP;
            statusText = "&eZREALIZOWANY (&f" + entry.getRedeemedBy() + "&e)";
        } else {
            mat = Material.RED_STAINED_GLASS_PANE;
            statusText = "&cUNIEWAZNIONY (Wartosc 0$)";
        }

        double currentVal = entry.getCurrentValue(taxPerHour, maxTax);
        double taxPct = entry.getCurrentTaxPercent(taxPerHour, maxTax);

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color("&a&lCzek &f#" + entry.getId() + " &8(&e$" + manager.formatMoney(entry.getInitialAmount()) + "&8)"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&8» &7Unikalne ID: &b#" + entry.getId()));
            lore.add(color("&8» &7Stworzyl: &f" + entry.getCreator()));
            lore.add(color("&8» &7Kwota poczatkowa: &a$" + manager.formatMoney(entry.getInitialAmount())));
            lore.add(color("&8» &7Aktualna wartosc: &2$" + manager.formatMoney(currentVal) + " &8(&c-" + String.format("%.0f", taxPct) + "%&8)"));
            lore.add(color("&8» &7Czas od stworzenia: &e" + entry.getElapsedHours() + "h"));
            lore.add(color("&8» &7Data wystawienia: &f" + manager.formatDate(entry.getCreatedAt())));
            lore.add(color("&8» &7Status: " + statusText));

            if (entry.getStatus() == ChequeEntry.Status.REDEEMED) {
                lore.add(color("&8» &7Data realizacji: &f" + manager.formatDate(entry.getRedeemedAt())));
            }

            if (adminMode) {
                lore.add("");
                lore.add(color("&eLPM &8- &cUsun wartosc tego czeku (0$)"));
                lore.add(color("&ePPM &8- &4Usun ten czek z historii"));
            }

            meta.setLore(lore);
            if (entry.getStatus() == ChequeEntry.Status.ACTIVE) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.getPersistentDataContainer().set(guiChequeIdKey, PersistentDataType.STRING, entry.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getGuiChequeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(guiChequeIdKey, PersistentDataType.STRING);
    }

    private ItemStack createSimpleItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (loreLines != null && loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String l : loreLines) {
                    lore.add(color(l));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
