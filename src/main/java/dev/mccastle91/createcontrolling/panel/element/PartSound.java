package dev.mccastle91.createcontrolling.panel.element;

import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * The sound a part makes when it is worked. A part notes one as its state
 * changes (see {@link PanelElement#takeSound}); the console plays it from its
 * own position, so everyone nearby hears a button go down — whoever, or
 * whatever (a computer), pressed it.
 *
 * Each {@link Kind} is a sound event of the mod's own
 * ({@code create_controlling:part.button_on} and so on), which
 * {@code sounds.json} points at vanilla's button, lever and trapdoor sounds,
 * at the volume and pitches vanilla's buttons and levers use. So a console
 * sounds like the redstone parts players already know, and a resource pack
 * can give any of them a sound of its own.
 */
public record PartSound(Kind kind, float volume, float pitch) {

    /** What happened to the part; each is one sound event. */
    public enum Kind {
        BUTTON_ON(SoundEvents.STONE_BUTTON_CLICK_ON),
        BUTTON_OFF(SoundEvents.STONE_BUTTON_CLICK_OFF),
        LEVER(SoundEvents.LEVER_CLICK),
        LID_OPEN(SoundEvents.IRON_TRAPDOOR_OPEN),
        LID_CLOSE(SoundEvents.IRON_TRAPDOOR_CLOSE),
        /** One step of a knob, slider or analog lever. */
        DETENT(SoundEvents.LEVER_CLICK);

        private final SoundEvent vanilla;
        private SoundEvent event;

        Kind(SoundEvent vanilla) {
            this.vanilla = vanilla;
        }

        /** The event's path under the mod's namespace: {@code part.button_on}. */
        public String path() {
            return "part." + name().toLowerCase(Locale.ROOT);
        }

        public ResourceLocation id() {
            return McCompat.id("create_controlling", path());
        }

        /** The mod's event, or the vanilla sound it stands for if it was never registered. */
        public SoundEvent event() {
            if (event == null)
                event = BuiltInRegistries.SOUND_EVENT.getOptional(id()).orElse(vanilla);
            return event;
        }
    }

    private static final float VOLUME = 0.3f;

    public SoundEvent event() {
        return kind.event();
    }

    /** A button going down, or coming back up; the large button sits a little lower. */
    public static PartSound button(boolean down, boolean large) {
        float pitch = (down ? 0.6f : 0.5f) - (large ? 0.1f : 0f);
        return new PartSound(down ? Kind.BUTTON_ON : Kind.BUTTON_OFF, large ? VOLUME + 0.1f : VOLUME, pitch);
    }

    /** A lever thrown on or off, as vanilla's lever. */
    public static PartSound lever(boolean on) {
        return new PartSound(Kind.LEVER, VOLUME, on ? 0.6f : 0.5f);
    }

    /** A guarded switch's lid: a small, light hatch. */
    public static PartSound lid(boolean open) {
        return new PartSound(open ? Kind.LID_OPEN : Kind.LID_CLOSE, 0.25f, 1.8f);
    }

    /** One step of a knob or slider: a light click, rising a little with the value. */
    public static PartSound detent(int value) {
        return new PartSound(Kind.DETENT, VOLUME, detentPitch(value));
    }

    /** Shared with the client, which clicks for itself while dragging (see AnalogDragScreen). */
    public static float detentPitch(int value) {
        return 1.1f + value * 0.03f;
    }

    public static float detentVolume() {
        return VOLUME;
    }
}
