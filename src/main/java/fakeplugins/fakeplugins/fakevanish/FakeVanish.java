package fakeplugins.fakeplugins.fakevanish;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FakeVanish extends JavaPlugin implements CommandExecutor, Listener {

    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    private static final String[] STAFF_PERMS = {
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
    };

    private String prefix;
    private String msgEnabled;
    private String msgDisabled;
    private String msgNoPerm;
    private String msgOnlyPlayer;

    // Боссбар настройки
    private boolean bossbarEnabled;
    private String bossbarTitle;
    private BarColor bossbarColor;
    private BarStyle bossbarStyle;
    private double bossbarProgress;
    private boolean bossbarVisible;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getCommand("va").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info(ChatColor.GREEN + "FakeVanish включён");
    }

    @Override
    public void onDisable() {
        // Убираем боссбары и показываем всех
        for (UUID uuid : new HashSet<>(vanished)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                showToAll(p);
                removeBossBar(p);
            }
        }
        vanished.clear();
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();

        getLogger().info(ChatColor.RED + "FakeVanish выключен");
    }

    private void loadConfig() {
        FileConfiguration cfg = getConfig();

        // Сообщения
        prefix        = color(cfg.getString("prefix", "&b[FakeVanish] &f"));
        msgEnabled    = prefix + color(cfg.getString("messages.enabled", "Вы &aвключили&f фейк-ваниш"));
        msgDisabled   = prefix + color(cfg.getString("messages.disabled", "Вы &cвыключили&f фейк-ваниш"));
        msgNoPerm     = prefix + color(cfg.getString("messages.no-permission", "&cНет прав"));
        msgOnlyPlayer = prefix + color(cfg.getString("messages.only-player", "&cТолько для игроков"));

        // Боссбар
        bossbarEnabled  = cfg.getBoolean("bossbar.enabled", true);
        bossbarTitle    = color(cfg.getString("bossbar.title", "&fВы в &bFakeVanish &fрежиме"));
        bossbarVisible  = cfg.getBoolean("bossbar.visible", true);

        String colorStr = cfg.getString("bossbar.color", "WHITE").toUpperCase();
        try {
            bossbarColor = BarColor.valueOf(colorStr);
        } catch (IllegalArgumentException e) {
            bossbarColor = BarColor.WHITE;
            getLogger().warning("Неверный цвет боссбара: " + colorStr + ". Установлен WHITE.");
        }

        String styleStr = cfg.getString("bossbar.style", "SOLID").toUpperCase();
        try {
            bossbarStyle = BarStyle.valueOf(styleStr);
        } catch (IllegalArgumentException e) {
            bossbarStyle = BarStyle.SOLID;
            getLogger().warning("Неверный стиль боссбара: " + styleStr + ". Установлен SOLID.");
        }

        bossbarProgress = cfg.getDouble("bossbar.progress", 1.0);
        if (bossbarProgress < 0 || bossbarProgress > 1) {
            bossbarProgress = 1.0;
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private boolean isStaff(Player p) {
        if (p == null) return false;
        for (String perm : STAFF_PERMS) {
            if (p.hasPermission(perm)) return true;
        }
        return false;
    }

    private void hideFromNonStaff(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (isStaff(viewer)) {
                viewer.showPlayer(this, target);
            } else {
                viewer.hidePlayer(this, target);
            }
        }
    }

    private void showToAll(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            viewer.showPlayer(this, target);
        }
    }

    private void updateVisibility(Player viewer) {
        for (UUID uuid : vanished) {
            Player vp = Bukkit.getPlayer(uuid);
            if (vp != null && vp.isOnline() && !vp.equals(viewer)) {
                if (isStaff(viewer)) {
                    viewer.showPlayer(this, vp);
                } else {
                    viewer.hidePlayer(this, vp);
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Боссбар методы
    // ──────────────────────────────────────────────

    private void showBossBar(Player player) {
        if (!bossbarEnabled || !bossbarVisible) return;

        BossBar bb = bossBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar newBar = Bukkit.createBossBar(bossbarTitle, bossbarColor, bossbarStyle);
            newBar.setProgress(bossbarProgress);
            return newBar;
        });

        bb.addPlayer(player);
    }

    private void removeBossBar(Player player) {
        BossBar bb = bossBars.remove(player.getUniqueId());
        if (bb != null) {
            bb.removePlayer(player);
            bb.removeAll(); // на всякий
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(msgOnlyPlayer);
            return true;
        }

        Player player = (Player) sender;

        if (!isStaff(player)) {
            player.sendMessage(msgNoPerm);
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (vanished.contains(uuid)) {
            // Выключаем
            vanished.remove(uuid);
            showToAll(player);
            removeBossBar(player);
            player.sendMessage(msgDisabled);
        } else {
            // Включаем
            vanished.add(uuid);
            hideFromNonStaff(player);
            showBossBar(player);
            player.sendMessage(msgEnabled);
        }

        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        updateVisibility(p);

        if (vanished.contains(p.getUniqueId())) {
            hideFromNonStaff(p);
            showBossBar(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Убираем боссбар при выходе (чтобы не висел в памяти)
        removeBossBar(e.getPlayer());
    }
}