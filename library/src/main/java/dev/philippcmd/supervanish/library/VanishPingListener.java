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

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.lang.reflect.Method;
import java.util.Iterator;

/**
 * Keeps vanished players out of the server list.
 *
 * <p>Hiding a player from everyone in the world still leaves them in the answer the
 * server gives to anybody who pings it: the online count includes them, and the sample
 * that clients show on hover names them. That is the simplest way there is to notice a
 * vanished administrator - it needs no permissions, no plugin and no access to the
 * server at all, only the multiplayer list.
 *
 * <p>This runs on whichever thread answers the ping, not the main one, so it reads a
 * published snapshot of who is vanished and never touches the server's own player list.
 *
 * <p>The two halves are reached differently. The sample is standard Bukkit: the event
 * is iterable over the players it will advertise, and removing through that iterator
 * removes them from the response. The count is not - plain Bukkit exposes it read-only
 * - so it is set through the Paper subclass when the server has one, reflectively, so
 * that this library still runs where it does not. Where neither is available the ping
 * is simply left alone, which is what happened before this existed.
 */
public final class VanishPingListener implements Listener {

    private final VanishService service;

    public VanishPingListener(VanishService service) {
        this.service = service;
    }

    /**
     * Runs late so that a plugin which rewrites the sample for its own reasons has
     * already done so, and cannot put a vanished player back.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ServerListPingEvent event) {
        int removed = removeFromSample(event);
        if (removed > 0) {
            reduceCount(event, removed);
        }
    }

    /**
     * Removes vanished players from the advertised sample.
     *
     * <p>{@code iterator()} is optional in the API: an implementation that does not
     * advertise a sample raises rather than returning an empty one, and that is not a
     * failure worth propagating into a ping.
     */
    private int removeFromSample(ServerListPingEvent event) {
        int removed = 0;
        try {
            Iterator<Player> players = event.iterator();
            while (players.hasNext()) {
                Player player = players.next();
                if (player != null && this.service.isVanishedConcurrently(player.getUniqueId())) {
                    players.remove();
                    removed++;
                }
            }
        } catch (UnsupportedOperationException | IllegalStateException unavailable) {
            // This server does not offer a sample to edit. The count cannot be corrected
            // either without walking the server's player list, and this runs on the
            // ping thread rather than the main one - so it is left alone.
            return 0;
        }
        return removed;
    }

    /**
     * Lowers the advertised online count, where the server allows it.
     *
     * <p>Reflective because the setter belongs to Paper's subclass of this event: a
     * server without it keeps its count, and nothing here fails.
     */
    private void reduceCount(ServerListPingEvent event, int removed) {
        try {
            Method setter = event.getClass().getMethod("setNumPlayers", int.class);
            setter.setAccessible(true);
            setter.invoke(event, Math.max(0, event.getNumPlayers() - removed));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // Plain Bukkit: the count is read-only, and the sample edit above is all
            // this server will accept.
        }
    }
}
