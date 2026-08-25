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
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final VanishAudience audience;
    private final VisibilityDriver visibility;

    public VanishService(Plugin owner, VanishStateStore store, VanishAudience audience, boolean unlistFromTab) {
        this.owner = owner;
        this.store = store;
        this.audience = audience;
        this.visibility = new VisibilityDriver(owner, unlistFromTab);
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

    public void vanish(Player player, VanishTier tier) {
        this.store.put(player.getUniqueId(), tier, System.currentTimeMillis());
        this.store.flush();
        apply(player);
    }

    public void unvanish(Player player) {
        this.store.remove(player.getUniqueId());
        this.store.flush();
        apply(player);
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
        apply(subject);
        return true;
    }

    public boolean removeViewer(Player subject, Player viewer) {
        if (!this.store.isVanished(subject.getUniqueId())) {
            return false;
        }
        this.store.removeViewer(subject.getUniqueId(), viewer.getUniqueId());
        this.store.flush();
        apply(subject);
        return true;
    }

    // ------------------------------------------------------------- reconciliation

    /** Pushes the stored state for one player out to every online viewer. */
    public void apply(Player subject) {
        this.visibility.refreshSubject(subject, this::canSee);
        this.visibility.setTabListed(subject, !isVanished(subject));
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
            this.visibility.refreshSubject(subject, (viewer, target) -> true);
            this.visibility.setTabListed(subject, true);
        }
    }
}
