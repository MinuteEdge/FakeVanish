package fakeplugins.fakeplugins.fakevanish;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FakeVanish extends JavaPlugin implements CommandExecutor, Listener {

    private static final List<String> STAFF_PERMISSIONS = Arrays.asList(
            "fakevanish.helper",
            "fakevanish.sthelper",
            "fakevanish.moder",
            "fakevanish.stmoder",
            "fakevanish.glmoder",
            "fakevanish.zam",
            "fakevanish.curator",
            "fakevanish.stcurator",
            "fakevanish.glcurator",
            "fakevanish.mladm"
    );

    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();

    private String prefix;
    private String enabledMessage;
    private String disabledMessage;
    private String noPermissionMessage;
    private String onlyPlayerMessage;

    private boolean bossBarEnabled;
    private boolean bossBarVisible;
    private String bossBarTitle;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;
    private double bossBarProgress;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPluginConfig();
        registerCommand();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("FakeVanish enabled");
    }

    @Override
    public void onDisable() {
        for (UUID uniqueId : new HashSet<>(vanishedPlayers)) {
            Player vanishedPlayer = Bukkit.getPlayer(uniqueId);
            if (vanishedPlayer == null) {
                continue;
            }

            revealToEveryone(vanishedPlayer);
            removeBossBar(vanishedPlayer);
        }

        vanishedPlayers.clear();
        activeBossBars.values().forEach(BossBar::removeAll);
        activeBossBars.clear();

        getLogger().info("FakeVanish disabled");
    }

    private void registerCommand() {
        PluginCommand vanishCommand = getCommand("va");
        if (vanishCommand == null) {
            throw new IllegalStateException("Command 'va' is not defined in plugin.yml");
        }

        vanishCommand.setExecutor(this);
    }

    private void loadPluginConfig() {
        FileConfiguration config = getConfig();

        prefix = colorize(config.getString("prefix", "&b[FakeVanish] &f"));
        enabledMessage = prefix + colorize(config.getString("messages.enabled", "Вы &aвключили&f fake vanish"));
        disabledMessage = prefix + colorize(config.getString("messages.disabled", "Вы &cвыключили&f fake vanish"));
        noPermissionMessage = prefix + colorize(config.getString("messages.no-permission", "&cНедостаточно прав"));
        onlyPlayerMessage = prefix + colorize(config.getString("messages.only-player", "&cКоманда доступна только игроку"));

        bossBarEnabled = config.getBoolean("bossbar.enabled", true);
        bossBarVisible = config.getBoolean("bossbar.visible", true);
        bossBarTitle = colorize(config.getString("bossbar.title", "&fРежим скрытия активен"));
        bossBarColor = parseEnum(config.getString("bossbar.color"), BarColor.class, BarColor.WHITE, "bossbar.color");
        bossBarStyle = parseEnum(config.getString("bossbar.style"), BarStyle.class, BarStyle.SOLID, "bossbar.style");
        bossBarProgress = clamp(config.getDouble("bossbar.progress", 1.0D), 0.0D, 1.0D);
    }

    private String colorize(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private boolean isStaff(Player player) {
        if (player == null) {
            return false;
        }

        for (String permission : STAFF_PERMISSIONS) {
            if (player.hasPermission(permission)) {
                return true;
            }
        }

        return false;
    }

    private void applyVanishState(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }

            if (isStaff(viewer)) {
                viewer.showPlayer(this, target);
            } else {
                viewer.hidePlayer(this, target);
            }
        }
    }

    private void revealToEveryone(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(this, target);
        }
    }

    private void syncViewerVisibility(Player viewer) {
        for (UUID uniqueId : vanishedPlayers) {
            Player vanishedPlayer = Bukkit.getPlayer(uniqueId);
            if (vanishedPlayer == null || vanishedPlayer.equals(viewer)) {
                continue;
            }

            if (isStaff(viewer)) {
                viewer.showPlayer(this, vanishedPlayer);
            } else {
                viewer.hidePlayer(this, vanishedPlayer);
            }
        }
    }

    private void showBossBar(Player player) {
        if (!bossBarEnabled || !bossBarVisible) {
            return;
        }

        BossBar bossBar = activeBossBars.computeIfAbsent(player.getUniqueId(), key -> {
            BossBar createdBar = Bukkit.createBossBar(bossBarTitle, bossBarColor, bossBarStyle);
            createdBar.setProgress(bossBarProgress);
            return createdBar;
        });

        bossBar.setVisible(true);
        bossBar.addPlayer(player);
    }

    private void removeBossBar(Player player) {
        BossBar bossBar = activeBossBars.remove(player.getUniqueId());
        if (bossBar == null) {
            return;
        }

        bossBar.removePlayer(player);
        bossBar.removeAll();
    }

    private void enableFakeVanish(Player player) {
        vanishedPlayers.add(player.getUniqueId());
        applyVanishState(player);
        showBossBar(player);
        player.sendMessage(enabledMessage);
    }

    private void disableFakeVanish(Player player) {
        vanishedPlayers.remove(player.getUniqueId());
        revealToEveryone(player);
        removeBossBar(player);
        player.sendMessage(disabledMessage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(onlyPlayerMessage);
            return true;
        }

        Player player = (Player) sender;
        if (!isStaff(player)) {
            player.sendMessage(noPermissionMessage);
            return true;
        }

        if (vanishedPlayers.contains(player.getUniqueId())) {
            disableFakeVanish(player);
        } else {
            enableFakeVanish(player);
        }

        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        syncViewerVisibility(player);

        if (vanishedPlayers.contains(player.getUniqueId())) {
            applyVanishState(player);
            showBossBar(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeBossBar(event.getPlayer());
    }

    private <E extends Enum<E>> E parseEnum(String rawValue, Class<E> enumType, E fallback, String fieldName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumType, rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Invalid " + fieldName + ": " + rawValue + ". Using " + fallback.name());
            return fallback;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
