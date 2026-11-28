package dev.mccastle91.createcontrolling.panel;

import net.minecraft.core.BlockPos;

/**
 * Whatever carries a console that is not a block in the world: a console
 * riding a Create contraption has no block entity there, so one is kept
 * running on the side and asks its host for everything a block entity would
 * ask the world at its own position (see compat/create/contraption).
 * A placed console has no host.
 */
public interface ConsoleHost {

    /** Where the console is in the world right now: link range and sounds are measured from here. */
    BlockPos position();

    /** The console's state changed in a way its viewers should see. Server side only. */
    void sync();

    /** The console's saved data changed. Server side only. */
    void changed();

    /** False once the host is gone, so the link network can drop the console's parts. */
    boolean alive();
}
