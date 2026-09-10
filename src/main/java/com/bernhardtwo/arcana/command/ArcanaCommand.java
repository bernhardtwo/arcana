package com.bernhardtwo.arcana.command;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.PlayerStore;
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
            case "reset" -> reset(sender, args);
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

    /** /arcana reset [player] [ability]: clears cooldowns and refills charge pools. Only online players have a container. */
    private void reset(CommandSender sender, String[] args) {
        Player target;
        String abilityId = null;
        if (args.length >= 2 && !isAbility(args[1])) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(warn("No online player with that name."));
                return;
            }
            abilityId = args.length >= 3 ? args[2] : null;
        } else if (sender instanceof Player self) {
            target = self;
            abilityId = args.length >= 2 ? args[1] : null;
        } else {
            sender.sendMessage(warn("Usage: /arcana reset <player> [ability]"));
            return;
        }

        List<String> ids = new ArrayList<>();
        if (abilityId == null) {
            plugin.abilities().all().forEach(ability -> ids.add(ability.id()));
        } else if (isAbility(abilityId)) {
            ids.add(abilityId.toLowerCase(Locale.ROOT));
        } else {
            sender.sendMessage(warn("Unknown ability. Use /arcana reset <player> <ability> with one of: "
                    + String.join(", ", allAbilityIds()) + "."));
            return;
        }

        int cooldowns = 0;
        int pools = 0;
        for (String id : ids) {
            PlayerStore.Cleared cleared = plugin.store().clear(target, id);
            cooldowns += cleared.cooldown() ? 1 : 0;
            pools += cleared.charges() ? 1 : 0;
        }
        sender.sendMessage(info("Cleared " + cooldowns + (cooldowns == 1 ? " cooldown" : " cooldowns")
                + " and refilled " + pools + (pools == 1 ? " charge pool" : " charge pools")
                + " for " + target.getName() + "."));
        if (!sender.equals(target)) {
            target.sendMessage(info("Your Arcana cooldowns were reset."));
        }
    }

    private boolean isAbility(String id) {
        return plugin.abilities().find(id.toLowerCase(Locale.ROOT)).isPresent();
    }

    private List<String> allAbilityIds() {
        List<String> ids = new ArrayList<>();
        plugin.abilities().all().forEach(ability -> ids.add(ability.id()));
        return ids;
    }

    private void list(CommandSender sender) {
        sender.sendMessage(info("Registered staffs:"));
        for (Wand wand : plugin.wands().all()) {
            sender.sendMessage(Component.text("  " + wand.id() + " - " + wand.displayName(), NamedTextColor.GRAY));
        }
        sender.sendMessage(info("GriefPrevention claims: " + (plugin.claims().isActive() ? "active" : "not detected")));
        sender.sendMessage(info("CoreProtect logging: " + (plugin.coreProtect().isActive() ? "active" : "not detected")));
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(warn("/" + label + " give <player> <staff>"));
        sender.sendMessage(warn("/" + label + " list"));
        sender.sendMessage(warn("/" + label + " reset [player] [ability]"));
        sender.sendMessage(warn("/" + label + " reload"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("give", "list", "reload", "reset"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            plugin.wands().all().forEach(wand -> options.add(wand.id()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            options.addAll(allAbilityIds());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            options.addAll(allAbilityIds());
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
