package dev.mccastle91.createcontrolling.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.util.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * What a resource pack can restyle, read again on every resource reload.
 *
 * <b>Colours</b>: {@code assets/create_controlling/console_style.json}.
 * Every pack's copy is read, lowest first, so a pack lists only what it
 * changes:
 * <pre>
 * {
 *   "colors": { "nixie_glow": "#33CCFF", "grid_major": "#30FFFFFF" },
 *   "themes": { "brass": { "title_text": "#592424", "accent": "#E4B763" } }
 * }
 * </pre>
 * A colour is {@code #RRGGBB} or {@code #AARRGGBB}. The keys are those of
 * {@link StyleColor}; the themes those of {@link CasingTheme}. Anything
 * unreadable is logged and left at the mod's own value.
 *
 * <b>Textures</b>: {@code textures/gui/console/<name>.png}, and a theme's own
 * copy of any of them in {@code textures/gui/console/<theme>/<name>.png},
 * which wins where it exists.
 */
public final class ConsoleStyle implements ResourceManagerReloadListener {

    public static final ConsoleStyle INSTANCE = new ConsoleStyle();

    private static final ResourceLocation FILE = McCompat.id(CreateControlling.MOD_ID, "console_style.json");
    private static final String TEXTURES = "textures/gui/console/";

    /** Which file each (theme, name) resolved to, until the next reload. */
    private static final Map<String, ResourceLocation> textures = new HashMap<>();

    private ConsoleStyle() {
    }

    /** The texture for a window part: the theme's own if a pack (or the mod) has one, else the shared one. */
    public static ResourceLocation texture(CasingTheme theme, String name) {
        String key = theme == null ? name : theme.id() + "/" + name;
        ResourceLocation found = textures.get(key);
        if (found != null)
            return found;
        found = McCompat.id(CreateControlling.MOD_ID, TEXTURES + name + ".png");
        if (theme != null) {
            ResourceLocation own = McCompat.id(CreateControlling.MOD_ID, TEXTURES + key + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(own).isPresent())
                found = own;
        }
        textures.put(key, found);
        return found;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        textures.clear();
        for (StyleColor color : StyleColor.values())
            color.reset();
        for (CasingTheme theme : CasingTheme.values())
            theme.reset();
        for (Resource resource : manager.getResourceStack(FILE)) {
            try (Reader reader = resource.openAsReader()) {
                apply(JsonParser.parseReader(reader).getAsJsonObject(), resource.sourcePackId());
            } catch (Exception e) {
                CreateControlling.LOGGER.warn("Couldn't read {} from pack {}: {}", FILE, resource.sourcePackId(), e.toString());
            }
        }
    }

    static void apply(JsonObject root, String pack) {
        if (root.has("colors")) {
            JsonObject colors = root.getAsJsonObject("colors");
            for (StyleColor color : StyleColor.values())
                if (colors.has(color.key())) {
                    Integer argb = parse(colors.get(color.key()), pack, color.key());
                    if (argb != null)
                        color.set(argb);
                }
        }
        if (root.has("themes")) {
            JsonObject themes = root.getAsJsonObject("themes");
            for (CasingTheme theme : CasingTheme.values()) {
                if (!themes.has(theme.id()))
                    continue;
                JsonObject entry = themes.getAsJsonObject(theme.id());
                Integer title = entry.has("title_text") ? parse(entry.get("title_text"), pack, "title_text") : null;
                Integer accent = entry.has("accent") ? parse(entry.get("accent"), pack, "accent") : null;
                if (title != null)
                    theme.titleText = title & 0xFFFFFF;
                if (accent != null)
                    theme.accent = accent & 0xFFFFFF;
            }
        }
    }

    /** {@code #RRGGBB} (opaque) or {@code #AARRGGBB}; null, with a warning, for anything else. */
    static Integer parse(JsonElement element, String pack, String key) {
        try {
            String text = element.getAsString().trim();
            if (text.startsWith("#"))
                text = text.substring(1);
            if (text.length() == 6)
                return 0xFF000000 | Integer.parseInt(text, 16);
            if (text.length() == 8)
                return (int) Long.parseLong(text, 16);
        } catch (RuntimeException ignored) {
            // falls through to the warning
        }
        CreateControlling.LOGGER.warn("Pack {}: console_style.json colour \"{}\" should be \"#RRGGBB\" or \"#AARRGGBB\", got {}",
                pack, key, element);
        return null;
    }
}
