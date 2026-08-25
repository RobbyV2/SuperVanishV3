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

import java.util.Locale;

/**
 * How thoroughly a player is hidden.
 *
 * <p>Replaces the original pair of parallel sets ({@code vanishedPlayers} and
 * {@code superVanishedPlayers}), which could disagree with one another. A player is
 * now either not vanished at all, or vanished at exactly one tier.
 */
public enum VanishTier {

    /** Hidden from ordinary players; explicitly authorised viewers may be shown them. */
    NORMAL,

    /** Hidden from everyone. No viewer grant reveals a player at this tier. */
    SILENT;

    public static VanishTier parse(String text, VanishTier fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public String serialised() {
        return name().toLowerCase(Locale.ROOT);
    }
}
