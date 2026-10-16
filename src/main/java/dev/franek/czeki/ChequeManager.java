package dev.franek.czeki;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChequeManager {

    private final FranekCzekiPlugin plugin;
    private final NamespacedKey chequeIdKey;
    private final Map<String, ChequeEntry> cheques = new LinkedHashMap<>();
    private final Random random = new Random();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    private final DecimalFormat moneyFormat;
    private File dataFile;
    private FileConfiguration dataConfig;

    public ChequeManager(FranekCzekiPlugin plugin) {
        this.plugin = plugin;
        this.chequeIdKey = new NamespacedKey(plugin, "cheque_id");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        this.moneyFormat = new DecimalFormat("#,##0.##", symbols);
        loadData();
    }

    public void loadData() {
        cheques.clear();
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie udalo sie utworzyc pliku data.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = dataConfig.getConfigurationSection("cheques");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String path = "cheques." + id + ".";
                String creator = dataConfig.getString(path + "creator", "Nieznany");
                double initialAmount = dataConfig.getDouble(path + "initial-amount", 0.0);
                long createdAt = dataConfig.getLong(path + "created-at", System.currentTimeMillis());
                String statusStr = dataConfig.getString(path + "status", "ACTIVE");
                ChequeEntry.Status status;
                try {
                    status = ChequeEntry.Status.valueOf(statusStr);
                } catch (IllegalArgumentException ex) {
                    status = ChequeEntry.Status.ACTIVE;
                }
                String redeemedBy = dataConfig.getString(path + "redeemed-by", "");
                long redeemedAt = dataConfig.getLong(path + "redeemed-at", 0L);
                double redeemedAmount = dataConfig.getDouble(path + "redeemed-amount", 0.0);

                cheques.put(id, new ChequeEntry(id, creator, initialAmount, createdAt, status, redeemedBy, redeemedAt, redeemedAmount));
            }
        }
    }

    public void saveData() {
        if (dataConfig == null || dataFile == null) return;
        dataConfig.set("cheques", null);
        for (ChequeEntry entry : cheques.values()) {
            String path = "cheques." + entry.getId() + ".";
            dataConfig.set(path + "creator", entry.getCreator());
            dataConfig.set(path + "initial-amount", entry.getInitialAmount());
            dataConfig.set(path + "created-at", entry.getCreatedAt());
            dataConfig.set(path + "status", entry.getStatus().name());
            dataConfig.set(path + "redeemed-by", entry.getRedeemedBy());
            dataConfig.set(path + "redeemed-at", entry.getRedeemedAt());
            dataConfig.set(path + "redeemed-amount", entry.getRedeemedAmount());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Blad podczas zapisywania pliku data.yml!");
        }
    }

    private String generateUniqueId() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String id;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            id = sb.toString();
        } while (cheques.containsKey(id));
        return id;
    }

    public ChequeEntry createCheque(String creator, double amount) {
        String id = generateUniqueId();
        long now = System.currentTimeMillis();
        ChequeEntry entry = new ChequeEntry(id, creator, amount, now, ChequeEntry.Status.ACTIVE, "", 0L, 0.0);
        cheques.put(id, entry);
        saveData();
        return entry;
    }

    public ItemStack buildChequeItem(ChequeEntry entry) {
        String matName = plugin.getConfig().getString("cheque-item.material", "PAPER");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.PAPER;

        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("cheque-item.name", "&a&lCZEK BANKOWY &8(&f#{id}&8)");
            meta.setDisplayName(color(replacePlaceholders(name, entry)));

            List<String> rawLore = plugin.getConfig().getStringList("cheque-item.lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(color(replacePlaceholders(line, entry)));
            }
            meta.setLore(lore);

            if (plugin.getConfig().getBoolean("cheque-item.glow", true)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.getPersistentDataContainer().set(chequeIdKey, PersistentDataType.STRING, entry.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String replacePlaceholders(String text, ChequeEntry entry) {
        return text
                .replace("{id}", entry.getId())
                .replace("{amount}", formatMoney(entry.getInitialAmount()))
                .replace("{creator}", entry.getCreator())
                .replace("{date}", formatDate(entry.getCreatedAt()));
    }

    public String getChequeIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(chequeIdKey, PersistentDataType.STRING);
    }

    public ChequeEntry getCheque(String id) {
        return cheques.get(id);
    }

    public List<ChequeEntry> getAllChequesNewestFirst() {
        List<ChequeEntry> list = new ArrayList<>(cheques.values());
        Collections.reverse(list);
        return list;
    }

    public boolean voidCheque(String id) {
        ChequeEntry entry = cheques.get(id);
        if (entry == null) return false;
        entry.setStatus(ChequeEntry.Status.VOIDED);
        entry.setRedeemedAmount(0.0);
        saveData();
        return true;
    }

    public boolean deleteChequeFromHistory(String id) {
        if (cheques.remove(id) != null) {
            saveData();
            return true;
        }
        return false;
    }

    public void clearHistory() {
        cheques.clear();
        saveData();
    }

    public double getTaxPerHour() {
        return plugin.getConfig().getDouble("settings.tax-percent-per-hour", 3.0);
    }

    public double getMaxTaxPercent() {
        return plugin.getConfig().getDouble("settings.max-tax-percent", 90.0);
    }

    public String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }

    public String formatDate(long timestamp) {
        if (timestamp <= 0L) return "-";
        return dateFormat.format(new Date(timestamp));
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
