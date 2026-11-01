package dev.autozoomtweaks;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.textures.MultiTextureFBO2;

public final class Patches {
    private Patches() {}

    // Per-player auto zoom levels (see AutoZoom). Called from Core.StartFrame once per local
    // player per frame; vanilla runs when auto zoom is off for that player.
    @Patch(className = "zombie.core.textures.MultiTextureFBO2", methodName = "update")
    public static class Update {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.This MultiTextureFBO2 fbo) {
            return AutoZoom.update(fbo);
        }
    }
}
