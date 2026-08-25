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

/**
 * The host's answer to "may this player see vanished players at all?".
 *
 * <p>Kept out of the library because the answer depends on the host's authorisation
 * model. The original code hard-coded a {@code vanish.*} permission tree; an embedder
 * with its own permission engine plugs that in here instead.
 */
@FunctionalInterface
public interface VanishAudience {

    /** A viewer who is always allowed to see normally-vanished players. */
    boolean isPrivilegedViewer(Player viewer);

    /** An audience where nobody is privileged; only explicit viewer grants apply. */
    static VanishAudience explicitGrantsOnly() {
        return viewer -> false;
    }

    /** An audience gated on a Bukkit permission node. */
    static VanishAudience permission(String node) {
        return viewer -> viewer.hasPermission(node);
    }
}
