package com.bernhardtwo.arcana.command;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.item.AbilityItems;
import com.bernhardtwo.arcana.item.Wand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ArcanaCommand implements CommandExecutor, TabCompleter {

    private final ArcanaPlugin plugin;

    public ArcanaCommand(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "list" -> list(sender);
            case "reload" -> {
                plugin.reloadSettings();
                sender.sendMessage(info("Arcana config reloaded."));
            }
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(warn("Usage: /arcana give <player> <staff>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(warn("No online player with that name."));
            return;
        }
        Optional<Wand> wand = plugin.wands().find(args[2].toLowerCase(Locale.ROOT));
        if (wand.isEmpty()) {
            sender.sendMessage(warn("Unknown staff. Use /arcana list."));
            return;
        }

        ItemStack stack = AbilityItems.create(plugin, wand.get());
        target.getInventory().addItem(stack).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));

        sender.sendMessage(info("Gave " + wand.get().displayName() + " to " + target.getName() + "."));
        target.sendMessage(info("You received " + wand.get().displayName() + "."));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(info("Registered staffs:"));
        for (Wand wand : plugin.wands().all()) {
            sender.sendMessage(Component.text("  " + wand.id() + " - " + wand.displayName(), NamedTextColor.GRAY));
        }
        sender.sendMessage(info("GriefPrevention claims: " + (plugin.claims().isActive() ? "active" : "not detected")));
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(warn("/" + label + " give <player> <staff>"));
        sender.sendMessage(warn("/" + label + " list"));
        sender.sendMessage(warn("/" + label + " reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("give", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            plugin.wands().all().forEach(wand -> options.add(wand.id()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }

    private Component info(String text) {
        return Component.text(text, NamedTextColor.LIGHT_PURPLE);
    }

    private Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }
}
