package dev.mccastle91.createcontrolling.gametest;

import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Not a behavior test: generates the structure template consumed by the
 * NeoForge module's game tests — a 7x4x7 box with a stone floor at y=0.
 * Idempotent: only writes when the .nbt is absent (delete it to regenerate).
 */
class GameTestStructureGenerator {

    private static final int SIZE_X = 7;
    private static final int SIZE_Y = 4;
    private static final int SIZE_Z = 7;
    private static final int DATA_VERSION_1_21_1 = 3955;

    @Test
    void generateEmptyTemplate() throws IOException {
        Path target = repoRoot().resolve(Path.of("src", "versioned", "1.21.1", "resources",
                "data", "create_controlling", "structure", "empty.nbt"));
        if (Files.exists(target))
            return;
        Files.createDirectories(target.getParent());

        CompoundTag root = new CompoundTag();
        root.put("size", intList(SIZE_X, SIZE_Y, SIZE_Z));
        root.put("entities", new ListTag());

        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                CompoundTag block = new CompoundTag();
                block.put("pos", intList(x, 0, z));
                block.putInt("state", 0);
                blocks.add(block);
            }
        }
        root.put("blocks", blocks);
        root.putInt("DataVersion", DATA_VERSION_1_21_1);

        NbtIo.writeCompressed(root, target);
    }

    /** Tests run from a per-version project folder; generated files belong in the shared tree. */
    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle.kts")))
            dir = dir.getParent();
        if (dir == null)
            throw new IllegalStateException("Could not locate the repository root from " + Path.of("").toAbsolutePath());
        return dir;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values)
            list.add(IntTag.valueOf(value));
        return list;
    }

    /**
     * Ponder scene schematic: a brass-casing pedestal with a sloped brass
     * console on top, its layout pre-populated with a typical boiler-room
     * arrangement (block-entity NBT baked into the structure). Written to
     * assets/&lt;ns&gt;/ponder/; delete the file to regenerate.
     */
    @Test
    void generatePonderSchematic() throws IOException {
        Path target = repoRoot().resolve(Path.of("src", "main", "resources", "assets", "create_controlling",
                "ponder", "control_panel.nbt"));
        if (Files.exists(target))
            return;
        Files.createDirectories(target.getParent());

        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        dev.mccastle91.createcontrolling.panel.element.PanelElements.init();
        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();

        var layout = new dev.mccastle91.createcontrolling.panel.PanelLayout();
        var label = PanelElements.LABEL.create();
        label.setText("BOILER");
        label.setPos(1, 1);
        layout.addElement(label);
        var needle = PanelElements.NEEDLE_METER.create();
        needle.setPos(1, 2.5f);
        layout.addElement(needle);
        var nixie = PanelElements.NIXIE.create();
        nixie.setPos(8.5f, 2.5f);
        layout.addElement(nixie);
        var knob = PanelElements.KNOB.create();
        knob.setPos(1, 8.5f);
        knob.setValue(8);
        layout.addElement(knob);
        var toggle = PanelElements.TOGGLE_SWITCH.create();
        toggle.setPos(6, 9);
        toggle.setOn(true);
        layout.addElement(toggle);
        var covered = PanelElements.COVERED_SWITCH.create();
        covered.setPos(9, 8.5f);
        covered.setColor(net.minecraft.world.item.DyeColor.RED);
        layout.addElement(covered);
        var lamp = PanelElements.LAMP.create();
        lamp.setPos(13, 9);
        layout.addElement(lamp);
        var lamp2 = PanelElements.LAMP.create();
        lamp2.setPos(13, 12.5f);
        lamp2.setColor(net.minecraft.world.item.DyeColor.LIME);
        layout.addElement(lamp2);
        var slider = PanelElements.SLIDER_H.create();
        slider.setPos(1, 13);
        slider.setValue(5);
        layout.addElement(slider);

        CompoundTag panelNbt = new CompoundTag();
        panelNbt.putString("id", "create_controlling:control_panel");
        panelNbt.put("Layout", layout.save(registries));
        panelNbt.put("Shape", dev.mccastle91.createcontrolling.panel.ConsoleShape.DEFAULT.save());
        panelNbt.putString("Casing", dev.mccastle91.createcontrolling.panel.ConsoleCasing.BRASS.id());

        CompoundTag root = new CompoundTag();
        root.put("size", intList(5, 2, 5));
        root.put("entities", new ListTag());

        ListTag palette = new ListTag();
        CompoundTag casing = new CompoundTag();
        casing.putString("Name", "create:brass_casing");
        palette.add(casing);
        CompoundTag panelState = new CompoundTag();
        panelState.putString("Name", "create_controlling:control_panel");
        CompoundTag properties = new CompoundTag();
        properties.putString("face", "floor");
        properties.putString("facing", "north");
        panelState.put("Properties", properties);
        palette.add(panelState);
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        CompoundTag casingBlock = new CompoundTag();
        casingBlock.put("pos", intList(2, 0, 2));
        casingBlock.putInt("state", 0);
        blocks.add(casingBlock);
        CompoundTag panelBlock = new CompoundTag();
        panelBlock.put("pos", intList(2, 1, 2));
        panelBlock.putInt("state", 1);
        panelBlock.put("nbt", panelNbt);
        blocks.add(panelBlock);
        root.put("blocks", blocks);
        root.putInt("DataVersion", DATA_VERSION_1_21_1);

        NbtIo.writeCompressed(root, target);
    }
}
