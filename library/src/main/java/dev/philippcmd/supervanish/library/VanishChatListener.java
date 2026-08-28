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

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Stops a vanished player from speaking into a room that thinks it is empty.
 *
 * <p>Chat is the loudest possible way to undo a vanish, and the easiest to do by
 * accident: the player is hidden, forgets, and answers a question. So the message is
 * held rather than sent, and the sender is told why - silently dropping it would leave
 * them believing they had spoken.
 *
 * <p>Separate from the other behaviours because the event belongs to Paper rather than
 * to Bukkit. A server without it simply never registers this listener.
 */
public final class VanishChatListener implements Listener {

    private final VanishService service;

    public VanishChatListener(VanishService service) {
        this.service = service;
    }

    /**
     * Runs first, and cancels: the point is that nothing downstream - no chat plugin, no
     * log, no bridge to somewhere else - ever sees the message.
     *
     * <p>Chat is delivered asynchronously, so vanish state is read from the published
     * snapshot rather than from the store, which belongs to the main thread.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!this.service.behaviour().silenceChat()
                || !this.service.isVanishedConcurrently(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.GRAY
                + "You are vanished, so that was not sent. Unvanish first, or use a command.");
    }
}
