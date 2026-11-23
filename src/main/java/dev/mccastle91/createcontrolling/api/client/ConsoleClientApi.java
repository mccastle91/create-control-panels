package dev.mccastle91.createcontrolling.api.client;

import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client half of the add-on API (see {@code api.ConsoleApi}): never touch
 * this class on a dedicated server.
 */
public final class ConsoleClientApi {

    private static final Map<String, ComponentRenderer<?>> RENDERERS = new ConcurrentHashMap<>();

    private ConsoleClientApi() {
    }

    /**
     * Sets how a component type is drawn. Call during client setup. A type
     * registered through the API with no renderer shows as a flat grey plate.
     * Registering for one of the mod's own types replaces its look.
     */
    public static <T extends PanelElement> void registerRenderer(PanelElementType<T> type, ComponentRenderer<? super T> renderer) {
        RENDERERS.put(type.id(), renderer);
    }

    /** The renderer registered for a type, if any. */
    @SuppressWarnings("unchecked")
    @Nullable
    public static ComponentRenderer<PanelElement> rendererFor(PanelElementType<?> type) {
        return (ComponentRenderer<PanelElement>) RENDERERS.get(type.id());
    }
}
