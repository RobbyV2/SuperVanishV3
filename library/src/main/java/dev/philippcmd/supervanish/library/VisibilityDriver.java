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
     * {@code Player#unlistPlayer} / {@code Player#listPlayer} are Paper-only and take
     * the player to unlist from the caller's tab list. Resolved once, reflectively, so
     * the library keeps compiling and running against Spigot, where the pair does not
     * exist and tab handling falls back to what hidePlayer already does.
     */
    private static final MethodHandle UNLIST_PLAYER = resolveListing("unlistPlayer");
    private static final MethodHandle LIST_PLAYER = resolveListing("listPlayer");

    public VisibilityDriver(Plugin owner, boolean unlistFromTab) {
        this.owner = owner;
        this.unlistFromTab = unlistFromTab;
    }

    private static MethodHandle resolveListing(String name) {
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(Player.class, name, MethodType.methodType(boolean.class, Player.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    /** Whether this server can remove a player from another player's tab list. */
    public boolean supportsTabUnlisting() {
        return UNLIST_PLAYER != null && LIST_PLAYER != null;
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

    /**
     * Controls whether a viewer who <em>can</em> see a vanished player also sees them in
     * the tab list.
     *
     * <p>Only relevant for that case: hidePlayer already removes the player from the tab
     * list of everyone who cannot see them. This is what makes a vanished player absent
     * from the tab list - and from the name completion the tab list feeds - even for an
     * authorised viewer, when the embedder asks for it.
     */
    public void setTabVisible(Player viewer, Player subject, boolean listed) {
        if (viewer.equals(subject) || !supportsTabUnlisting()) {
            return;
        }
        try {
            if (listed) {
                LIST_PLAYER.invoke(viewer, subject);
            } else {
                UNLIST_PLAYER.invoke(viewer, subject);
            }
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
                setTabVisible(viewer, vanished, !this.unlistFromTab);
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
                setTabVisible(viewer, subject, !this.unlistFromTab);
            } else {
                hideFrom(subject, viewer);
            }
        }
    }
}
