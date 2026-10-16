package dev.franek.czeki;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static dev.franek.czeki.ChequeManager.color;

public class ChequeCommand implements CommandExecutor, TabCompleter {

    private final FranekCzekiPlugin plugin;
    private final ChequeManager manager;
    private final ChequeGuiManager guiManager;

    public ChequeCommand(FranekCzekiPlugin plugin, ChequeManager manager, ChequeGuiManager guiManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&aCzeki&8] &7");

        // /historiaczek-clear
        if (cmd.equals("historiaczek-clear")) {
            if (!hasClearPerm(sender)) {
                sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.no-permission")));
                return true;
            }
            manager.clearHistory();
            sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.admin-cleared-history")));
            return true;
        }

        // /historiaczeki
        if (cmd.equals("historiaczeki")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color(prefix + "&cTa komenda jest dostepna tylko dla graczy!"));
                return true;
            }
            guiManager.openHistoryGui(player, 1);
            return true;
        }

        // /aczek <gui|historia|clear|reload>
        if (cmd.equals("aczek")) {
            if (!hasAdminPerm(sender)) {
                sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.no-permission")));
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color(prefix + "&cGUI jest dostepne tylko w grze!"));
                    return true;
                }
                guiManager.openAdminGui(player, 1);
                return true;
            }
            String sub = args[0].toLowerCase();
            if (sub.equals("historia")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(color(prefix + "&cGUI jest dostepne tylko w grze!"));
                    return true;
                }
                guiManager.openHistoryGui(player, 1);
                return true;
            }
            if (sub.equals("clear")) {
                if (!hasClearPerm(sender)) {
                    sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.no-permission")));
                    return true;
                }
                manager.clearHistory();
                sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.admin-cleared-history")));
                return true;
            }
            if (sub.equals("reload")) {
                plugin.reloadConfig();
                sender.sendMessage(color(prefix + plugin.getConfig().getString("messages.reloaded")));
                return true;
            }
            sender.sendMessage(color(prefix + "&cPoprawne uzycie: &7/aczek <gui|historia|clear|reload>"));
            return true;
        }

        // /czek <kwota> lub /wyplac <kwota>
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(prefix + "&cTa komenda jest dostepna tylko dla graczy!"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.usage-czek")));
            return true;
        }

        double amount = parseAmount(args[0]);
        if (Double.isNaN(amount) || amount <= 0) {
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.invalid-amount")));
            return true;
        }

        double min = plugin.getConfig().getDouble("settings.min-amount", 100.0);
        double max = plugin.getConfig().getDouble("settings.max-amount", 100000000.0);

        if (amount < min) {
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.min-amount", "")
                    .replace("{min}", manager.formatMoney(min))));
            return true;
        }
        if (amount > max) {
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.max-amount", "")
                    .replace("{max}", manager.formatMoney(max))));
            return true;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.inventory-full")));
            return true;
        }

        double balance = plugin.getEconomy().getBalance(player);
        if (balance < amount) {
            double missing = amount - balance;
            player.sendMessage(color(prefix + plugin.getConfig().getString("messages.not-enough-money", "")
                    .replace("{missing}", manager.formatMoney(missing))));
            return true;
        }

        plugin.getEconomy().withdrawPlayer(player, amount);
        ChequeEntry entry = manager.createCheque(player.getName(), amount);
        ItemStack chequeItem = manager.buildChequeItem(entry);
        player.getInventory().addItem(chequeItem);

        String msg = plugin.getConfig().getString("messages.cheque-created", "")
                .replace("{id}", entry.getId())
                .replace("{amount}", manager.formatMoney(amount));
        player.sendMessage(color(prefix + msg));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        return true;
    }

    private double parseAmount(String raw) {
        String clean = raw.trim().toLowerCase().replace(",", "").replace("$", "");
        double multiplier = 1.0;
        if (clean.endsWith("k")) {
            multiplier = 1_000.0;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m") || clean.endsWith("mln")) {
            multiplier = 1_000_000.0;
            clean = clean.replace("mln", "").replace("m", "");
        }
        try {
            double val = Double.parseDouble(clean) * multiplier;
            return Math.round(val * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private boolean hasAdminPerm(CommandSender sender) {
        return sender.isOp()
                || sender.hasPermission("franek.czek.admin")
                || sender.hasPermission("tojak1cper.czek.admin");
    }

    private boolean hasClearPerm(CommandSender sender) {
        return sender.isOp()
                || sender.hasPermission("franek.czek.adminclear")
                || sender.hasPermission("tojak1cper.czek.adminclear")
                || hasAdminPerm(sender);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        if ((cmd.equals("czek") || cmd.equals("wyplac")) && args.length == 1) {
            return Arrays.asList("1000", "10000", "50000", "100000");
        }
        if (cmd.equals("aczek") && args.length == 1 && hasAdminPerm(sender)) {
            return Arrays.asList("gui", "historia", "clear", "reload");
        }
        return Collections.emptyList();
    }
}
