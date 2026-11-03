package dev.mccastle91.createcontrolling.panel;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * The sloped body of a console, all in pixels of a 16 px block. The low edge
 * (the hinge) stands {@code frontHeight} px tall; the face rises at
 * {@code angle} degrees towards the opposite edge, which therefore stands
 * {@code frontHeight + 16·tan(angle)} px — at most a full block, so steeper
 * consoles need lower front edges (45° leaves a knife edge at the hinge).
 *
 * In model space (see {@link ConsoleOrientation}) the face rises towards the
 * far edge (z = 0, the top of the design area) unless {@code reversed}, when
 * it rises towards the near edge (z = 1) instead. Which way a console may
 * slope depends on where it hangs (see {@link #forFace}): floors rise away
 * from the player, ceilings hang their far edge down — reversed in model
 * space, because a ceiling's design area is turned to read from below — and
 * walls may raise either their top edge or their bottom edge. A flat shape is
 * never reversed, so equal-looking flat consoles compare equal.
 */
public record ConsoleShape(float angle, float frontHeight, boolean reversed) {

    public static final float MAX_ANGLE = 45f;
    /** One-click presets for the wrench and the designer's snap points. */
    public static final float[] PRESETS = { 0f, 15f, 30f, 45f };
    /** A flat face needs some thickness to exist at all. */
    public static final float MIN_FLAT_THICKNESS = 1f;
    public static final float BLOCK_PX = 16f;

    public static final ConsoleShape FLAT = new ConsoleShape(0f, 2f, false);
    public static final ConsoleShape DEFAULT = of(15f, 2f);

    private static final String TAG_ANGLE = "Angle";
    private static final String TAG_FRONT = "Front";
    private static final String TAG_REVERSED = "Reversed";

    /** Clamps into the valid range: the back edge never leaves the block. */
    public static ConsoleShape of(float angle, float frontHeight) {
        return of(angle, frontHeight, false);
    }

    /** As {@link #of(float, float)}, rising towards the near edge when {@code reversed}. */
    public static ConsoleShape of(float angle, float frontHeight, boolean reversed) {
        float a = Mth.clamp(angle, 0f, MAX_ANGLE);
        float rise = rise(a);
        boolean flat = a < 0.01f;
        float minFront = flat ? MIN_FLAT_THICKNESS : 0f;
        float maxFront = Math.max(minFront, BLOCK_PX - rise);
        return new ConsoleShape(a, Mth.clamp(frontHeight, minFront, maxFront), reversed && !flat);
    }

    /** The shape a fresh console starts with on {@code face}: walls start flat. */
    public static ConsoleShape defaultFor(AttachFace face) {
        return face == AttachFace.WALL ? FLAT : DEFAULT.forFace(face);
    }

    /**
     * This shape as a console on {@code face} may hold it: floors always rise
     * away from the player, ceilings always the same way seen from below (in
     * model space that's reversed), walls keep whichever edge is raised.
     */
    public ConsoleShape forFace(AttachFace face) {
        if (face == AttachFace.WALL)
            return this;
        return of(angle, frontHeight, face == AttachFace.CEILING);
    }

    /** Same slope with the other edge raised. */
    public ConsoleShape withReversed(boolean reversed) {
        return of(angle, frontHeight, reversed);
    }

    private static float rise(float angle) {
        return (float) (BLOCK_PX * Math.tan(Math.toRadians(angle)));
    }

    public float backHeight() {
        return frontHeight + rise(angle);
    }

    /** Height of the model-space far edge (z = 0), px. */
    public float farHeight() {
        return reversed ? frontHeight : backHeight();
    }

    /** Height of the model-space near edge (z = 1), px. */
    public float nearHeight() {
        return reversed ? backHeight() : frontHeight;
    }

    /** Length of the sloped face from near to far edge, px (≥ 16). */
    public float slopeLength() {
        return (float) (BLOCK_PX / Math.cos(Math.toRadians(angle)));
    }

    /**
     * Slope beyond the 16 px design area at each end, px: the design area sits
     * centred on the face, with this much bare plate above and below it.
     */
    public float designMargin() {
        return (slopeLength() - BLOCK_PX) / 2f;
    }

    public boolean isFlat() {
        return angle < 0.01f;
    }

    /** Next angle preset after this one (wrapping), keeping the front height where it still fits. */
    public ConsoleShape nextPreset() {
        return nextPreset(false);
    }

    /**
     * Next angle preset; with {@code bothWays} (wall consoles) the cycle runs
     * up the presets with the top edge raised, then again with the bottom edge
     * raised, then back to flat.
     */
    public ConsoleShape nextPreset(boolean bothWays) {
        for (float preset : PRESETS)
            if (preset > angle + 0.01f)
                return of(preset, frontHeight, reversed);
        if (bothWays && !reversed)
            return of(PRESETS[1], frontHeight, true);
        return of(PRESETS[0], Math.max(frontHeight, FLAT.frontHeight));
    }

    /** Snaps to a preset angle when within {@code tolerance} degrees of one. */
    public static float snapAngle(float angle, float tolerance) {
        for (float preset : PRESETS)
            if (Math.abs(angle - preset) <= tolerance)
                return preset;
        return angle;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(TAG_ANGLE, angle);
        tag.putFloat(TAG_FRONT, frontHeight);
        if (reversed)
            tag.putBoolean(TAG_REVERSED, true);
        return tag;
    }

    public static ConsoleShape load(CompoundTag tag) {
        if (!tag.contains(TAG_ANGLE))
            return DEFAULT;
        return of(tag.getFloat(TAG_ANGLE), tag.getFloat(TAG_FRONT), tag.getBoolean(TAG_REVERSED));
    }
}
