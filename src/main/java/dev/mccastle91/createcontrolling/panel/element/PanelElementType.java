package dev.mccastle91.createcontrolling.panel.element;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Identity, factory and physical size for one kind of panel component.
 * Sizes are in pixels of the 16×16 px console face; {@code height} is how far
 * the part stands off the face, used for click rays and selection shapes.
 * A string-keyed registry rather than a Minecraft one: types are plain data
 * shared verbatim by client and server.
 */
public final class PanelElementType<T extends PanelElement> {

    private static final Map<String, PanelElementType<?>> TYPES = new LinkedHashMap<>();

    private final String id;
    private final Function<PanelElementType<T>, T> factory;
    private final float width;
    private final float depth;
    private final float height;

    private PanelElementType(String id, Function<PanelElementType<T>, T> factory, float width, float depth, float height) {
        this.id = id;
        this.factory = factory;
        this.width = width;
        this.depth = depth;
        this.height = height;
    }

    public static <T extends PanelElement> PanelElementType<T> register(String id, Function<PanelElementType<T>, T> factory,
            float width, float depth, float height) {
        PanelElementType<T> type = new PanelElementType<>(id, factory, width, depth, height);
        if (TYPES.putIfAbsent(id, type) != null)
            throw new IllegalStateException("Duplicate panel element type: " + id);
        return type;
    }

    @Nullable
    public static PanelElementType<?> byId(String id) {
        return TYPES.get(id);
    }

    public static Collection<PanelElementType<?>> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public T create() {
        return factory.apply(this);
    }

    public String id() {
        return id;
    }

    /** Footprint along the face's horizontal axis, in px. */
    public float width() {
        return width;
    }

    /** Footprint along the face's slope axis, in px. */
    public float depth() {
        return depth;
    }

    /** How far the part stands off the face, in px. */
    public float height() {
        return height;
    }

    @Override
    public String toString() {
        return id;
    }
}
