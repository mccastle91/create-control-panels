package dev.mccastle91.createcontrolling.panel;

import dev.mccastle91.createcontrolling.CreateControlling;
import dev.mccastle91.createcontrolling.config.CCConfig;
import dev.mccastle91.createcontrolling.panel.element.LabelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.PanelElementType;
import dev.mccastle91.createcontrolling.panel.element.ReadoutElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * The components on one console face, positioned in pixels of the 16×16 px
 * design area. Serializes to a versioned NBT root; components of unknown type
 * are skipped on load, so older versions of the mod tolerate newer saves.
 */
public class PanelLayout {

    /** v2: pixel coordinates, fixed component sizes. */
    public static final int FORMAT_VERSION = 2;
    public static final String TAG_VERSION = "Version";
    public static final String TAG_ELEMENTS = "Elements";

    private final List<PanelElement> elements = new ArrayList<>();

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_VERSION, FORMAT_VERSION);
        ListTag list = new ListTag();
        for (PanelElement element : elements)
            list.add(element.save(registries));
        tag.put(TAG_ELEMENTS, list);
        return tag;
    }

    public static PanelLayout load(CompoundTag tag, HolderLookup.Provider registries) {
        PanelLayout layout = new PanelLayout();
        // v1 layouts used a cell grid that doesn't map onto the pixel face;
        // they are dropped rather than misplaced
        if (tag.getInt(TAG_VERSION) < FORMAT_VERSION)
            return layout;
        ListTag list = tag.getList(TAG_ELEMENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag elementTag = list.getCompound(i);
            String typeId = elementTag.getString(PanelElement.TAG_TYPE);
            PanelElementType<?> type = PanelElementType.byId(typeId);
            if (type == null) {
                CreateControlling.LOGGER.warn("Skipping panel component of unknown type '{}'", typeId);
                continue;
            }
            PanelElement element = type.create();
            element.load(elementTag, registries);
            layout.elements.add(element);
        }
        return layout;
    }

    public static int maxElements() {
        return CCConfig.maxElementsPerPanel;
    }

    public boolean addElement(PanelElement element) {
        if (elements.size() >= maxElements())
            return false;
        elements.add(element);
        return true;
    }

    public Optional<PanelElement> removeElement(UUID id) {
        for (int i = 0; i < elements.size(); i++)
            if (elements.get(i).id().equals(id))
                return Optional.of(elements.remove(i));
        return Optional.empty();
    }

    public Optional<PanelElement> elementById(UUID id) {
        return elements.stream().filter(element -> element.id().equals(id)).findFirst();
    }

    /** Topmost component covering the point (labels included) — for selecting in the designer. */
    public Optional<PanelElement> elementAt(float px, float py) {
        for (int i = elements.size() - 1; i >= 0; i--)
            if (elements.get(i).contains(px, py))
                return Optional.of(elements.get(i));
        return Optional.empty();
    }

    /** Topmost clickable component at the point; clicks pass through labels to what's beneath. */
    public Optional<PanelElement> interactiveElementAt(float px, float py) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            PanelElement element = elements.get(i);
            if (element.takesClicks() && element.contains(px, py))
                return Optional.of(element);
        }
        return Optional.empty();
    }

    /**
     * True if a component of the given footprint may sit there. Controls may
     * not overlap each other; labels are captions, so a label collides with
     * nothing and controls ignore labels.
     */
    public boolean isAreaFree(float x, float y, float width, float depth, UUID ignoreId, boolean movingIsLabel) {
        if (movingIsLabel)
            return true;
        for (PanelElement element : elements) {
            if (element.id().equals(ignoreId) || element instanceof LabelElement)
                continue;
            if (element.overlaps(x, y, width, depth))
                return false;
        }
        return true;
    }

    /** Every line number some readout shows. */
    public Set<Integer> readoutLines() {
        Set<Integer> lines = new TreeSet<>();
        for (PanelElement element : elements)
            if (element instanceof ReadoutElement readout)
                for (int row = 0; row < readout.rows(); row++)
                    if (readout.line(row) != ReadoutElement.NO_LINE)
                        lines.add(readout.line(row));
        return lines;
    }

    /** How many lines the console offers a Display Link: up to the highest one shown. */
    public int lineCount() {
        return readoutLines().stream().max(Integer::compare).orElse(0);
    }

    public List<PanelElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    public int elementCount() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }
}
