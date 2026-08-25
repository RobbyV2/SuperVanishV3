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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiPredicate;

/**
 * Applies visibility decisions to the server.
 *
 * <p>Two changes from the original {@code VanishManager}:
 *
 * <ul>
 *   <li>{@code hidePlayer(Player)} / {@code showPlayer(Player)} were deprecated and
 *       unowned - once any plugin called the unowned form, no other plugin could tell
 *       whose hide it was undoing. The plugin-scoped overloads are reference-counted
 *       per owner, so SuperVanish now only ever undoes its own hides.</li>
 *   <li>Visibility is recomputed from a predicate rather than mutated ad hoc, so a
 *       player joining, a viewer being granted access and a reload all converge on the
 *       same state instead of drifting apart.</li>
 * </ul>
 */
public final class VisibilityDriver {

    private final Plugin owner;
    private final boolean unlistFromTab;

    /**
     * {@code Player#setListed(boolean)} exists on Paper but not on Spigot, and is the
     * only API that removes a player from the tab list globally (which also drops them
     * out of most vanilla name-completion paths). Resolved once, reflectively, so the
     * library keeps compiling and running against either.
     */
    private static final MethodHandle SET_LISTED = resolveSetListed();

    public VisibilityDriver(Plugin owner, boolean unlistFromTab) {
        this.owner = owner;
        this.unlistFromTab = unlistFromTab;
    }

    private static MethodHandle resolveSetListed() {
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(Player.class, "setListed", MethodType.methodType(void.class, boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /** Whether this server can remove a player from the tab list globally. */
    public boolean supportsTabUnlisting() {
        return SET_LISTED != null;
    }

    public void hideFrom(Player vanished, Player viewer) {
        if (!viewer.equals(vanished)) {
            viewer.hidePlayer(this.owner, vanished);
        }
    }

    public void showTo(Player vanished, Player viewer) {
        if (!viewer.equals(vanished)) {
            viewer.showPlayer(this.owner, vanished);
        }
    }

    /** Removes a player from, or restores them to, the tab list for everyone. */
    public void setTabListed(Player player, boolean listed) {
        if (!this.unlistFromTab || SET_LISTED == null) {
            return;
        }
        try {
            SET_LISTED.invoke(player, listed);
        } catch (Throwable ignored) {
            // A server that advertises the method but rejects the call is not worth
            // failing a vanish over; the per-viewer hide above already did the work.
        }
    }

    /**
     * Recomputes who can see {@code vanished}. {@code canSee} receives
     * {@code (viewer, vanished)} and decides.
     */
    public void refreshSubject(Player vanished, BiPredicate<Player, Player> canSee) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(vanished)) {
                continue;
            }
            if (canSee.test(viewer, vanished)) {
                showTo(vanished, viewer);
            } else {
                hideFrom(vanished, viewer);
            }
        }
    }

    /** Recomputes what a single viewer can see, for use when that viewer joins. */
    public void refreshViewer(Player viewer, Iterable<Player> subjects, BiPredicate<Player, Player> canSee) {
        for (Player subject : subjects) {
            if (subject.equals(viewer)) {
                continue;
            }
            if (canSee.test(viewer, subject)) {
                showTo(subject, viewer);
            } else {
                hideFrom(subject, viewer);
            }
        }
    }
}
