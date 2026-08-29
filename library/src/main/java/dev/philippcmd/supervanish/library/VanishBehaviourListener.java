/*
 * SuperVanish - MIT License
 *
 * Copyright (c) 2025 Philipp Hechler
 * Copyright (c) 2026 SuperVanish contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dev.philippcmd.supervanish.library;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Everything vanishing means beyond not being rendered.
 *
 * <p>Each handler closes one way a hidden player still touches the world, and each is
 * gated by {@link VanishBehaviour} so a server can decide how far "hidden" goes. All of
 * it uses the ordinary server API - the packet library the original plugin needed for
 * some of this is not required on the versions this runs on.
 *
 * <p>Every handler runs at {@link EventPriority#LOWEST} and, where it declines
 * something, cancels rather than modifies: a vanished player's interaction should stop
 * before any other plugin builds on it, so that nothing downstream records an action
 * that is meant not to have happened.
 */
public final class VanishBehaviourListener implements Listener {

    private final VanishService service;

    /** Chest copies currently open, so their contents can be put back on close. */
    private final Map<UUID, Inventory> openContainers = new HashMap<>();

    public VanishBehaviourListener(VanishService service) {
        this.service = service;
    }

    private boolean vanished(Player player) {
        return player != null && this.service.isVanished(player);
    }

    private VanishBehaviour behaviour() {
        return this.service.behaviour();
    }

    // --------------------------------------------------------------- movement kicks

    /**
     * Keeps the server from kicking a vanished player for how they move.
     *
     * <p>A player nobody can see moves as an observer does - flying, floating, covering
     * ground quickly - and the server, which does not know they are hidden, reads that
     * as cheating: "flying is not enabled", "floating too long", "moved wrongly", "moved
     * too quickly". Flight is already permitted while vanished so the flight checks never
     * fire (see {@link VanishService}); this cancels any movement kick that still gets
     * raised, so a vanish cannot end in a disconnect.
     *
     * <p>The kick's cause is read as an enum where the server offers one and matched by
     * name, so no particular constant has to exist; the reason text is a fallback for a
     * server that does not carry a cause. Runs late and only cancels - it never kicks.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onKick(PlayerKickEvent event) {
        if (!behaviour().preventFlyingKick() || !vanished(event.getPlayer())) {
            return;
        }
        if (isMovementKick(event)) {
            event.setCancelled(true);
        }
    }

    /** Whether a kick is the server policing movement rather than anything deliberate. */
    private static boolean isMovementKick(PlayerKickEvent event) {
        String cause = null;
        try {
            Object value = event.getCause();
            cause = value == null ? null : value.toString().toUpperCase(Locale.ROOT);
        } catch (Throwable noCause) {
            // Older servers expose no cause; the reason text below is enough.
        }
        if (cause != null && (cause.contains("FLYING") || cause.contains("FLOAT")
                || cause.contains("MOVED") || cause.contains("INVALID"))) {
            return true;
        }
        String reason = event.getReason();
        if (reason == null) {
            return false;
        }
        String text = reason.toLowerCase(Locale.ROOT);
        return text.contains("flying is not enabled") || text.contains("floating")
                || text.contains("moved wrongly") || text.contains("moved too quickly")
                || text.contains("flying");
    }

    // ------------------------------------------------------------------- the world

    /** Items stay where they fell rather than disappearing into somebody invisible. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (behaviour().blockItemPickup() && event.getEntity() instanceof Player player
                && vanished(player)) {
            event.setCancelled(true);
        }
    }

    /** Mobs do not track somebody nobody can see. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (behaviour().blockMobTargeting() && event.getTarget() instanceof Player player
                && vanished(player)) {
            event.setTarget(null);
            event.setCancelled(true);
        }
    }

    /**
     * Pressure plates, tripwires and farmland are left alone.
     *
     * <p>{@link Action#PHYSICAL} is every way a player affects a block by standing on
     * it, which is the whole category worth suppressing: a plate clicking in an empty
     * corridor is exactly how somebody notices they are not alone.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysical(PlayerInteractEvent event) {
        if (behaviour().blockPhysicalContact() && event.getAction() == Action.PHYSICAL
                && vanished(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** A vanished player does not starve while standing still. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!behaviour().noHunger() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (vanished(player) && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- announcements

    /** A death nobody saw is not announced. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        if (behaviour().silenceDeathMessages() && vanished(event.getEntity())) {
            event.setDeathMessage(null);
        }
    }

    /**
     * Nor are advancements.
     *
     * <p>Reflective because silencing one is a Paper addition: where the server has no
     * such method the advancement is simply announced, as it was before.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!behaviour().silenceAdvancements() || !vanished(event.getPlayer())) {
            return;
        }
        try {
            event.getClass().getMethod("message", net.kyori.adventure.text.Component.class)
                    .invoke(event, (Object) null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            // Not this server; nothing to silence through.
        }
    }

    // -------------------------------------------------------------------- the list

    /** {@code /list} run by a player names only who that player could see anyway. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerList(PlayerCommandPreprocessEvent event) {
        if (!behaviour().hideFromPlayerList() || !isListCommand(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(listFor(event.getPlayer()));
    }

    /** And the same for the console, which sees everybody unless told otherwise. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onConsoleList(ServerCommandEvent event) {
        if (!behaviour().hideFromPlayerList() || !isListCommand("/" + event.getCommand())) {
            return;
        }
        event.setCancelled(true);
        event.getSender().sendMessage(listFor(event.getSender()));
    }

    private static boolean isListCommand(String message) {
        String command = message.split(" ")[0].toLowerCase(Locale.ROOT);
        return command.equals("/list") || command.equals("/minecraft:list");
    }

    /** The vanilla wording, over the players this sender is allowed to know about. */
    private String listFor(CommandSender sender) {
        Player viewer = sender instanceof Player player ? player : null;
        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean visible = viewer == null
                    ? !this.service.isVanished(online)
                    : this.service.canSee(viewer, online);
            if (visible || online.equals(viewer)) {
                names.add(online.getName());
            }
        }
        return "There are " + names.size() + " of a max of " + Bukkit.getMaxPlayers()
                + " players online: " + String.join(", ", names);
    }

    // --------------------------------------------------------------- silent chests

    /**
     * Opens a copy of a container, so the block neither animates nor sounds.
     *
     * <p>The lid opening is the server telling everyone nearby that somebody is at that
     * chest, and it is driven by the block being opened rather than by the inventory
     * being shown. Showing a copy and writing it back on close avoids the whole
     * mechanism without touching a packet.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpenContainer(PlayerInteractEvent event) {
        if (!behaviour().silentContainers() || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !vanished(event.getPlayer())
                || event.getPlayer().isSneaking()) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Container container)) {
            return;
        }
        Inventory source = container.getInventory();
        Inventory copy = copyOf(source);
        if (copy == null) {
            return;
        }
        event.setCancelled(true);
        copy.setContents(source.getContents());
        this.openContainers.put(event.getPlayer().getUniqueId(), source);
        event.getPlayer().openInventory(copy);
    }

    /** Puts whatever the player did back into the real container. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCloseContainer(InventoryCloseEvent event) {
        Inventory source = this.openContainers.remove(event.getPlayer().getUniqueId());
        if (source == null) {
            return;
        }
        try {
            source.setContents(event.getInventory().getContents());
        } catch (IllegalArgumentException mismatched) {
            // The container changed shape while it was open; the real one wins.
        }
    }

    /** A detached inventory of the same shape, or null when one cannot be made. */
    private static Inventory copyOf(Inventory source) {
        try {
            if (source.getSize() % 9 == 0) {
                return Bukkit.createInventory(null, source.getSize());
            }
            return Bukkit.createInventory(null, source.getType());
        } catch (IllegalArgumentException | UnsupportedOperationException unsupported) {
            return null;
        }
    }

    /**
     * Fake join/leave text, in the server's own wording.
     *
     * <p>Yellow, and built from the display name rather than the account name, because
     * that is what the real message uses: on a server with nicknames a leave that named
     * somebody differently from every other leave would be the tell it exists to avoid.
     * The colour is restated after the name so that a nickname carrying its own
     * formatting does not bleed into the rest of the line.
     */
    static String joinMessage(Player player) {
        return ChatColor.YELLOW + displayName(player) + ChatColor.YELLOW + " joined the game";
    }

    static String leaveMessage(Player player) {
        return ChatColor.YELLOW + displayName(player) + ChatColor.YELLOW + " left the game";
    }

    @SuppressWarnings("deprecation")
    private static String displayName(Player player) {
        try {
            String display = player.getDisplayName();
            if (display != null && !display.isBlank()) {
                return display;
            }
        } catch (Throwable unavailable) {
            // A server without the legacy accessor; the account name is right anyway.
        }
        return player.getName();
    }
}
