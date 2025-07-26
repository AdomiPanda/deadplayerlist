package me.adomi.DeadPlayerList;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeadPlayerList extends JavaPlugin implements Listener, TabExecutor {

    private final List<String> deadPlayers = new ArrayList<>();
    private boolean isActive = false;
    private boolean isEnabled = false;
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");
    private static final Pattern PLAYER_PATTERN = Pattern.compile("%player%");
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("dl").setExecutor(this);

        getLogger().info(getMessage("plugin-loaded"));
    }

    public String getMessage(String path) {
        return getMessage(path, null);
    }

    public String getMessage(String path, Player player) {
        String message = config.getString("messages." + path);
        if (message == null) return path;

        message = colorize(message);
        if (player != null) {
            message = PLAYER_PATTERN.matcher(message).replaceAll(player.getName());
        }
        return message;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!isActive || !isEnabled) return;
        addDeadPlayer(e.getEntity().getName());
        checkWinner();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!isActive || !isEnabled) return;
        Player p = e.getPlayer();
        if (!deadPlayers.contains(p.getName())) {
            addDeadPlayer(p.getName());
            p.sendMessage(getMessage("late-join", p));
        }
        checkWinner();
    }

    private void addDeadPlayer(String name) {
        if (!deadPlayers.contains(name)) {
            deadPlayers.add(name);
        }
    }

    private void checkWinner() {
        if (!isEnabled) return;

        List<Player> alive = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!deadPlayers.contains(p.getName())) alive.add(p);
        }
        if (alive.size() == 1) {
            Bukkit.broadcastMessage(getMessage("winner1"));
            Bukkit.broadcastMessage("#FF84DD");
            Bukkit.broadcastMessage(getMessage("winner2", alive.get(0)));
            Bukkit.broadcastMessage("#FF84DD");
            isActive = false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("dl")) return false;

        if (!sender.hasPermission("deadplayer.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "toggle":
                return toggleCommand(sender);
            case "start":
                return startCommand(sender);
            case "stop":
                return stopCommand(sender);
            case "kill":
                return killCommand(sender, args);
            case "revive":
                return reviveCommand(sender, args);
            case "tp":
                return tpCommand(sender, args);
            case "reload":
                return reloadCommand(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage(getMessage("unknown-command"));
                return true;
        }
    }

    private boolean toggleCommand(CommandSender sender) {
        isEnabled = !isEnabled;
        if (isEnabled) {
            sender.sendMessage(getMessage("plugin-enabled"));
        } else {
            isActive = false;
            sender.sendMessage(getMessage("plugin-disabled"));
        }
        return true;
    }

    private boolean startCommand(CommandSender sender) {
        if (!isEnabled) {
            sender.sendMessage(getMessage("not-enabled"));
            return true;
        }
        if (isActive) {
            sender.sendMessage(getMessage("already-active"));
            return true;
        }
        isActive = true;
        deadPlayers.clear();
        sender.sendMessage(getMessage("plugin-activated"));
        return true;
    }

    private boolean stopCommand(CommandSender sender) {
        if (!isActive) {
            sender.sendMessage(getMessage("already-inactive"));
            return true;
        }
        isActive = false;
        sender.sendMessage(getMessage("plugin-deactivated"));
        return true;
    }

    private boolean killCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getMessage("kill-usage"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(getMessage("player-offline"));
            return true;
        }
        addDeadPlayer(target.getName());

        sender.sendMessage(getMessage("player-killed", target));
        target.sendMessage(getMessage("you-were-killed", target));

        checkWinner();
        return true;
    }

    private boolean reviveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getMessage("revive-usage"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(getMessage("player-offline"));
            return true;
        }

        if (deadPlayers.remove(target.getName())) {
            sender.sendMessage(getMessage("player-revived", target));
            target.sendMessage(getMessage("you-were-revived", target));
            Bukkit.broadcastMessage(getMessage("broadcast-revived", target));
        } else {
            sender.sendMessage(getMessage("player-not-dead", target));
        }
        return true;
    }

    private boolean tpCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(getMessage("tp-usage"));
            return true;
        }
        Player p = (Player) sender;
        if (args[1].equalsIgnoreCase("alive")) {
            Bukkit.getOnlinePlayers().stream()
                    .filter(player -> !deadPlayers.contains(player.getName()))
                    .forEach(player -> player.teleport(p.getLocation()));
            sender.sendMessage(getMessage("teleported-alive"));
        } else if (args[1].equalsIgnoreCase("dead")) {
            Bukkit.getOnlinePlayers().stream()
                    .filter(player -> deadPlayers.contains(player.getName()))
                    .forEach(player -> player.teleport(p.getLocation()));
            sender.sendMessage(getMessage("teleported-dead"));
        } else {
            sender.sendMessage(getMessage("tp-usage"));
        }
        return true;
    }

    private boolean reloadCommand(CommandSender sender) {
        reloadConfig();
        config = getConfig();
        sender.sendMessage(getMessage("config-reloaded"));
        return true;
    }

    private void sendHelp(CommandSender sender) {
        List<String> helpLines = config.getStringList("messages.help");
        for (String line : helpLines) {
            sender.sendMessage(colorize(line));
        }

        // Uneditable copyright notice
        sender.sendMessage(colorize(""));
        sender.sendMessage(colorize("#FF84DC + Created by _Adomi + "));
        sender.sendMessage(colorize(""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("toggle", "start", "stop", "kill", "revive", "tp", "help", "reload"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "kill":
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !deadPlayers.contains(p.getName()))
                            .map(Player::getName)
                            .forEach(completions::add);
                    break;
                case "revive":
                    completions.addAll(deadPlayers);
                    break;
                case "tp":
                    completions.addAll(Arrays.asList("alive", "dead"));
                    break;
            }
        }

        return completions;
    }

    public static String colorize(String message) {
        if (message == null) return null;

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group();
            try {
                matcher.appendReplacement(buffer, ChatColor.of(hex).toString());
            } catch (IllegalArgumentException e) {
                matcher.appendReplacement(buffer, hex);
            }
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}