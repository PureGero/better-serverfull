package com.github.puregero.betterserverfull;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BetterServerfullPlugin extends JavaPlugin implements Listener {

    private final Set<PrioritySlot> prioritySlots = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onStatusPing(PaperServerListPingEvent event) {
        if (!this.prioritySlots.isEmpty()) {
            event.setNumPlayers(event.getNumPlayers() + this.prioritySlots.size());
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (this.getServer().getOnlinePlayers().size() + this.prioritySlots.size() < this.getServer().getMaxPlayers()) return;

        LuckPerms luckPerms = LuckPermsProvider.get();
        User user = luckPerms.getUserManager().getUser(event.getUniqueId());
        if (user == null) {
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
            event.kickMessage(Component.text("Could not load your permissions. Please try again later."));
            return;
        }

        if (this.getServer().getOperators().stream().anyMatch(player -> player.getUniqueId().equals(event.getUniqueId())) ||
                user.getCachedData().getPermissionData().checkPermission("betterserverfull.bypass").asBoolean()) {
            return;
        }

        if (user.getCachedData().getPermissionData().checkPermission("betterserverfull.priority").asBoolean() && !this.prioritySlots.isEmpty()) {
            try {
                if (this.prioritySlots.remove(this.prioritySlots.iterator().next())) {
                    return;
                }
            } catch (NoSuchElementException ignored) {}
        }

        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_FULL);
        event.kickMessage(Component.translatable("multiplayer.disconnect.server_full"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() == PlayerLoginEvent.Result.KICK_FULL) {
            if (event.getPlayer().hasPermission("betterserverfull.bypass")) {
                event.setResult(PlayerLoginEvent.Result.ALLOWED);
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        PrioritySlot slot = new PrioritySlot();
        this.prioritySlots.add(slot);
        this.getServer().getAsyncScheduler().runDelayed(this, t -> {
            this.prioritySlots.remove(slot);
        }, 15, TimeUnit.SECONDS);
    }

    private static class PrioritySlot {}

}
