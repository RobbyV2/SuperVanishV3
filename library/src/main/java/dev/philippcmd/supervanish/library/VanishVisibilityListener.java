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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Restores vanish state across reconnects and suppresses the join/quit announcement
 * that would otherwise give a vanished player away.
 *
 * <p>Only the messages for players this service is actually hiding are suppressed;
 * everybody else's join and quit announcements are untouched. Registered by the
 * embedder against its own plugin, so no separate plugin registration is involved.
 */
public final class VanishVisibilityListener implements Listener {

    private final VanishService service;
    private final boolean suppressJoinQuitMessages;

    public VanishVisibilityListener(VanishService service, boolean suppressJoinQuitMessages) {
        this.service = service;
        this.suppressJoinQuitMessages = suppressJoinQuitMessages;
    }

    // setJoinMessage/setQuitMessage rather than Paper's Component overloads: the
    // library has to keep compiling against plain Spigot, and both servers implement
    // the Bukkit form.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (this.suppressJoinQuitMessages && this.service.isVanished(player)) {
            event.setJoinMessage(null);
        }
        this.service.handleJoin(player);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (this.suppressJoinQuitMessages && this.service.isVanished(player)) {
            event.setQuitMessage(null);
        }
        this.service.handleQuit(player);
    }
}
