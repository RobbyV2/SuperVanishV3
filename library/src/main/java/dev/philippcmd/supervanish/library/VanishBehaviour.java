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

/**
 * What being vanished does, beyond not being seen.
 *
 * <p>Hiding a player from view is only half of vanishing: a player nobody can see who
 * still picks up items, angers mobs, treads on pressure plates and announces their
 * advancements is not hidden, merely invisible. Each of these closes one of those
 * gaps, and each is separately switchable because a server's idea of what an
 * administrator should be able to do while hidden is its own.
 *
 * <p>Everything here is reachable through the ordinary server API. The original
 * SuperVanish needed a packet library for some of it; the versions this runs on no
 * longer do.
 *
 * @param fakeJoinQuit         announce a leave when vanishing and a join when returning,
 *                             in the server's own wording, so the room sees what it
 *                             would have seen had the player really left
 * @param blockItemPickup      a vanished player picks nothing up, so items do not
 *                             vanish from under the people who dropped them
 * @param blockMobTargeting    mobs lose interest, rather than tracking somebody nobody
 *                             else can see
 * @param blockPhysicalContact pressure plates, tripwires and farmland stay as they were
 * @param silenceDeathMessages a vanished player's death is not announced
 * @param silenceAdvancements  nor are their advancements
 * @param silentContainers     opening a chest does not animate or sound it for anybody
 *                             watching the block
 * @param actionBarReminder    a standing reminder that you are hidden, because
 *                             forgetting is how a vanish is given away
 * @param hideFromPlayerList   {@code /list} does not name them
 * @param noHunger             a hidden player does not starve while doing nothing
 * @param silenceChat          a vanished player's chat is held rather than sent, since
 *                             speaking into a room that believes it is empty is the
 *                             loudest way there is to undo a vanish
 * @param preventFlyingKick    a vanished player is exempt from the server's flight and
 *                             movement kicks - "flying is not enabled", "floating too
 *                             long", "moved wrongly", "moved too quickly" - which a
 *                             player nobody can see, moving as an observer does, trips
 *                             constantly; flight is permitted while hidden so the check
 *                             never fires, and any such kick is cancelled as a backstop
 */
public record VanishBehaviour(boolean fakeJoinQuit,
                              boolean blockItemPickup,
                              boolean blockMobTargeting,
                              boolean blockPhysicalContact,
                              boolean silenceDeathMessages,
                              boolean silenceAdvancements,
                              boolean silentContainers,
                              boolean actionBarReminder,
                              boolean hideFromPlayerList,
                              boolean noHunger,
                              boolean silenceChat,
                              boolean preventFlyingKick) {

    /** Everything on, which is what a server that asked for vanishing usually wants. */
    public static VanishBehaviour all() {
        return new VanishBehaviour(true, true, true, true, true, true, true, true, true, true, true, true);
    }

    /** Nothing but invisibility, which is what this library did before. */
    public static VanishBehaviour none() {
        return new VanishBehaviour(false, false, false, false, false, false, false, false, false, false,
                false, false);
    }

    public boolean needsListener() {
        return this.fakeJoinQuit || this.blockItemPickup || this.blockMobTargeting
                || this.blockPhysicalContact || this.silenceDeathMessages || this.silenceAdvancements
                || this.silentContainers || this.hideFromPlayerList || this.noHunger
                || this.preventFlyingKick;
    }
}
