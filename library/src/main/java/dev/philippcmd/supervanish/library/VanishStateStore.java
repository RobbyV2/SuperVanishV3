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

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Where vanish state is kept.
 *
 * <p>The original {@code VanishManager} owned a {@code players.yml} inside the
 * plugin's own data folder, which made the visibility logic unusable by anything
 * that already had its own storage. Persistence is now an interface: the SuperVanish
 * plugin supplies a YAML-backed implementation, and an embedding host supplies one
 * backed by whatever it already writes.
 *
 * <p>All keys are UUIDs. State therefore survives a name change, a disconnect and a
 * server restart, and an entry may exist for a player who is not online.
 */
public interface VanishStateStore {

    boolean isVanished(UUID uuid);

    /** The tier for a UUID, or {@code null} when not vanished. */
    VanishTier tier(UUID uuid);

    Collection<UUID> vanished();

    /** Viewers explicitly authorised to see this player. Never applies at {@link VanishTier#SILENT}. */
    Set<UUID> viewers(UUID uuid);

    void put(UUID uuid, VanishTier tier, long since);

    void remove(UUID uuid);

    void addViewer(UUID uuid, UUID viewer);

    void removeViewer(UUID uuid, UUID viewer);

    /** Persists any pending changes. Called after every mutation. */
    void flush();
}
