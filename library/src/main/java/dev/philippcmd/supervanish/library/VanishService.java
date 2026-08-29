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
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The visibility engine, free of any plugin lifecycle.
 *
 * <p>This is the refactored {@code VanishManager}. The differences that matter:
 *
 * <ul>
 *   <li>it takes a {@link Plugin} to own its Bukkit calls rather than being a
 *       {@code JavaPlugin} itself, so an embedding host passes its own plugin and no
 *       second plugin is ever registered;</li>
 *   <li>runtime state is keyed by {@link UUID} rather than by {@code Player}
 *       instances, which previously leaked across reconnects and made
 *       {@code vanishViewers} a map of stale references;</li>
 *   <li>persistence is delegated to {@link VanishStateStore};</li>
 *   <li>the two tiers are one enum instead of two sets that could both contain the
 *       same player.</li>
 * </ul>
 */
public final class VanishService {

    private final Plugin owner;
    private final VanishStateStore store;

    /**
     * Who is vanished, as an immutable snapshot safe to read from any thread.
     *
     * <p>The server answers pings on a thread of its own, so deciding whether to hide
     * somebody from one means reading this state off the main thread while a vanish
     * toggle may be writing it. Rather than lock the store on every read, every write
     * republishes a copy: readers get a consistent answer that is at worst one toggle
     * stale, which for a server list is indistinguishable from being on time.
     */
    private volatile java.util.Set<UUID> snapshot = java.util.Set.of();

    /** What vanishing does beyond hiding. Defaults to nothing, as this library always did. */
    private volatile VanishBehaviour behaviour = VanishBehaviour.none();

    /**
     * Players this service turned flight on for while they are vanished, so it can turn
     * it off again for exactly those and leave everyone else's flight alone.
     */
    private final Set<UUID> flightGranted = ConcurrentHashMap.newKeySet();

    private org.bukkit.scheduler.BukkitTask reminder;
    private final VanishAudience audience;
    private final VisibilityDriver visibility;

    public VanishService(Plugin owner, VanishStateStore store, VanishAudience audience, boolean unlistFromTab) {
        this.owner = owner;
        this.store = store;
        this.audience = audience;
        this.visibility = new VisibilityDriver(owner, unlistFromTab);
    }

    /**
     * Tells the room, without using the broadcast channel.
     *
     * <p>Sent to each player directly rather than through
     * {@code Bukkit.broadcastMessage}, which raises an event any plugin may edit or
     * cancel - including the host's own command-feedback scoping, which narrows the
     * recipients of every broadcast made while an administrator's command is running.
     * Vanishing is always such a command, so the announcement was being made to nobody.
     * It also sidesteps the broadcast permission, which a server may have taken away.
     */
    private void announce(String message) {
        if (!this.behaviour.fakeJoinQuit()) {
            return;
        }
        for (Player recipient : org.bukkit.Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(message);
        }
    }

    /**
     * Writes the lines a real connection or disconnection would leave in the log.
     *
     * <p>A leave message in chat with no matching lines in {@code latest.log} is its own
     * tell: anybody comparing the two sees somebody who left the game without ever
     * losing a connection. So the surrounding lines are written too, in the server's own
     * wording and through the server's own logger, which is what makes them
     * indistinguishable from the real ones - a plugin logger would stamp them with the
     * host plugin's name.
     *
     * @param leaving whether to write the disconnection pair or the connection pair
     */
    private void logConnection(Player player, boolean leaving) {
        if (!this.behaviour.fakeJoinQuit()) {
            return;
        }
        java.util.logging.Logger log = org.bukkit.Bukkit.getLogger();
        try {
            if (leaving) {
                log.info(player.getName() + " lost connection: Disconnected");
                log.info(org.bukkit.ChatColor.stripColor(
                        VanishBehaviourListener.leaveMessage(player)));
                return;
            }
            org.bukkit.Location at = player.getLocation();
            log.info(String.format("%s[%s] logged in with entity id %d at ([%s]%.1f, %.1f, %.1f)",
                    player.getName(), address(player), player.getEntityId(),
                    at.getWorld() == null ? "world" : at.getWorld().getName(),
                    at.getX(), at.getY(), at.getZ()));
            log.info(org.bukkit.ChatColor.stripColor(VanishBehaviourListener.joinMessage(player)));
        } catch (Throwable ignored) {
            // A line that cannot be written is a missing line, not a failed vanish.
        }
    }

    /** The address in the shape the server prints it, or a plausible stand-in. */
    private static String address(Player player) {
        java.net.InetSocketAddress socket = player.getAddress();
        return socket == null ? "/127.0.0.1:0" : "/" + socket.getAddress().getHostAddress()
                + ":" + socket.getPort();
    }

    public VanishBehaviour behaviour() {
        return this.behaviour;
    }

    /**
     * Sets what vanishing does beyond hiding, and starts or stops the standing reminder
     * to match.
     */
    public void behaviour(VanishBehaviour behaviour) {
        this.behaviour = behaviour == null ? VanishBehaviour.none() : behaviour;
        stopReminder();
        if (this.behaviour.actionBarReminder()) {
            startReminder();
        }
    }

    /**
     * Reminds vanished players that they are hidden.
     *
     * <p>Once a second, on the action bar, because the expensive mistake is forgetting:
     * a player who thinks they are visible behaves as though they are, and gives the
     * whole thing away in one sentence.
     */
    private void startReminder() {
        try {
            this.reminder = org.bukkit.Bukkit.getScheduler().runTaskTimer(this.owner, () -> {
                for (Player player : onlineVanished()) {
                    sendActionBar(player);
                }
            }, 20L, 20L);
        } catch (Throwable unavailable) {
            // A server without a scheduler here is one that is shutting down.
        }
    }

    private void stopReminder() {
        if (this.reminder != null) {
            try {
                this.reminder.cancel();
            } catch (Throwable ignored) {
                // Already gone.
            }
            this.reminder = null;
        }
    }

    /** Action bar text, through whichever API this server offers. */
    private static void sendActionBar(Player player) {
        try {
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    new net.md_5.bungee.api.chat.TextComponent(
                            org.bukkit.ChatColor.GRAY + "You are vanished"));
        } catch (Throwable unavailable) {
            // No action bar on this server; the reminder is a courtesy, not a feature.
        }
    }

    public Plugin owner() {
        return this.owner;
    }

    public VanishStateStore store() {
        return this.store;
    }

    public VisibilityDriver visibility() {
        return this.visibility;
    }

    // ------------------------------------------------------------------- queries

    /**
     * Whether a player is vanished, answerable from any thread.
     *
     * <p>{@link #isVanished(UUID)} reads the store, which belongs to the main thread.
     * This reads the published snapshot instead.
     */
    public boolean isVanishedConcurrently(UUID uuid) {
        return this.snapshot.contains(uuid);
    }

    /** Republishes the snapshot. Called on the main thread, after every change. */
    private void republish() {
        java.util.Set<UUID> vanished = new java.util.LinkedHashSet<>();
        for (UUID uuid : this.store.vanished()) {
            vanished.add(uuid);
        }
        this.snapshot = java.util.Set.copyOf(vanished);
    }

    public boolean isVanished(UUID uuid) {
        return this.store.isVanished(uuid);
    }

    public boolean isVanished(Player player) {
        return isVanished(player.getUniqueId());
    }

    public VanishTier tier(UUID uuid) {
        return this.store.tier(uuid);
    }

    /**
     * Whether {@code viewer} may see {@code subject}.
     *
     * <p>A player always sees themselves. A silently vanished player is visible to
     * nobody else. A normally vanished player is visible to privileged viewers and to
     * anyone holding an explicit grant.
     */
    public boolean canSee(Player viewer, Player subject) {
        UUID subjectId = subject.getUniqueId();
        VanishTier tier = this.store.tier(subjectId);
        if (tier == null) {
            return true;
        }
        if (viewer.getUniqueId().equals(subjectId)) {
            return true;
        }
        if (tier == VanishTier.SILENT) {
            return false;
        }
        return this.audience.isPrivilegedViewer(viewer)
                || this.store.viewers(subjectId).contains(viewer.getUniqueId());
    }

    public List<Player> onlineVanished() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : this.store.vanished()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public List<String> vanishedNames() {
        List<String> names = new ArrayList<>();
        for (Player player : onlineVanished()) {
            names.add(player.getName());
        }
        return names;
    }

    // ----------------------------------------------------------------- mutations

    /**
     * Hides a player, and - when asked - tells the server they left.
     *
     * <p>The announcement is the difference between a player who is invisible and a
     * player who appears to have gone. Without it, somebody vanishing in front of others
     * simply stops existing, which is far more conspicuous than leaving.
     */
    public void vanish(Player player, VanishTier tier) {
        vanish(player, tier, true);
    }

    /**
     * Hides a player, optionally without saying so.
     *
     * @param announced false when the player was never visible in the first place - a
     *                  session that starts vanished must not be announced as having
     *                  ended, because as far as the room is concerned it never began.
     *                  Announcing it produces the worst of both: a player who joins and
     *                  immediately leaves, which is more conspicuous than either.
     */
    public void vanish(Player player, VanishTier tier, boolean announced) {
        this.store.put(player.getUniqueId(), tier, System.currentTimeMillis());
        this.store.flush();
        republish();
        apply(player);
        if (!announced) {
            return;
        }
        announce(VanishBehaviourListener.leaveMessage(player));
        logConnection(player, true);
    }

    public void unvanish(Player player) {
        this.store.remove(player.getUniqueId());
        this.store.flush();
        republish();
        apply(player);
        announce(VanishBehaviourListener.joinMessage(player));
        logConnection(player, false);
    }

    /** Toggles the given tier, returning true when the player ended up vanished. */
    public boolean toggle(Player player, VanishTier tier) {
        VanishTier current = this.store.tier(player.getUniqueId());
        if (current == tier) {
            unvanish(player);
            return false;
        }
        vanish(player, tier);
        return true;
    }

    public boolean addViewer(Player subject, Player viewer) {
        UUID subjectId = subject.getUniqueId();
        VanishTier tier = this.store.tier(subjectId);
        if (tier == null || tier == VanishTier.SILENT) {
            return false;
        }
        this.store.addViewer(subjectId, viewer.getUniqueId());
        this.store.flush();
        republish();
        apply(subject);
        return true;
    }

    public boolean removeViewer(Player subject, Player viewer) {
        if (!this.store.isVanished(subject.getUniqueId())) {
            return false;
        }
        this.store.removeViewer(subject.getUniqueId(), viewer.getUniqueId());
        this.store.flush();
        republish();
        apply(subject);
        return true;
    }

    // ------------------------------------------------------------- reconciliation

    /** Pushes the stored state for one player out to every online viewer. */
    public void apply(Player subject) {
        this.visibility.refreshSubject(subject, this::canSee, isVanished(subject));
        adjustFlight(subject);
    }

    /**
     * Lets a vanished player fly, and puts flight back as it was when they return.
     *
     * <p>The server kicks a grounded player who spends too long in the air unless they
     * are allowed to fly, and an observer drifting through walls trips exactly that. So
     * flight is switched on for the duration of a vanish - but only when the player did
     * not already have it, and switched off again only for those same players, so a
     * creative-mode flier or another plugin's grant is never disturbed. What game mode
     * implies is left untouched; this only lends flight that vanishing needs.
     */
    private void adjustFlight(Player subject) {
        if (!this.behaviour.preventFlyingKick()) {
            return;
        }
        UUID id = subject.getUniqueId();
        if (isVanished(subject)) {
            if (!subject.getAllowFlight()) {
                subject.setAllowFlight(true);
                this.flightGranted.add(id);
            }
        } else if (this.flightGranted.remove(id)) {
            if (subject.isFlying()) {
                subject.setFlying(false);
            }
            subject.setAllowFlight(false);
        }
    }

    /**
     * Restores stored state when a player connects. Covers both directions: the
     * joining player may themselves be vanished, and they must not be shown anyone
     * else who is.
     */
    public void handleJoin(Player player) {
        if (isVanished(player)) {
            apply(player);
        }
        this.visibility.refreshViewer(player, onlineVanished(), this::canSee);
    }

    /**
     * Drops per-session bookkeeping. Persistent state is deliberately left alone so a
     * vanished player is still vanished when they come back.
     */
    public void handleQuit(Player player) {
        // Nothing session-scoped survives in this implementation; kept as an explicit
        // hook so callers do not have to know that.
    }

    /** Recomputes visibility for every online player, e.g. after a reload. */
    public void refreshAll() {
        Collection<Player> vanished = onlineVanished();
        Set<Player> touched = new LinkedHashSet<>(vanished);
        for (Player subject : touched) {
            apply(subject);
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.visibility.refreshViewer(viewer, vanished, this::canSee);
        }
    }

    /** Makes every vanished player visible again, for use during shutdown. */
    public void releaseAll() {
        for (Player subject : onlineVanished()) {
            // Released, so no longer subject to the tab-list rule: they must be listed
            // again, not merely shown.
            this.visibility.refreshSubject(subject, (viewer, target) -> true, false);
        }
        // Hand back the flight vanishing lent, so a survival admin does not keep it.
        for (UUID id : Set.copyOf(this.flightGranted)) {
            Player subject = Bukkit.getPlayer(id);
            if (subject != null && subject.isOnline() && subject.getGameMode() != GameMode.CREATIVE
                    && subject.getGameMode() != GameMode.SPECTATOR) {
                if (subject.isFlying()) {
                    subject.setFlying(false);
                }
                subject.setAllowFlight(false);
            }
            this.flightGranted.remove(id);
        }
    }
}
