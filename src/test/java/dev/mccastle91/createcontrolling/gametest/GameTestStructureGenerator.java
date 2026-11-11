package dev.mccastle91.createcontrolling.gametest;

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
     * Ponder scene schematics: a 5x5 base plate and the blocks each scene in
     * CCPonderScenes animates. The console is empty (scenes mount their own
     * parts) with its low edge to the north, where Ponder's camera sits.
     * Written to assets/&lt;ns&gt;/ponder/; delete a file to regenerate it.
     */
    @Test
    void generatePonderSchematics() throws IOException {
        // Building: a console on a pedestal, dressed as freshly crafted
        PonderSchematic building = new PonderSchematic();
        building.block(2, 1, 2, "create:brass_casing");
        building.console(2, 2, 2, "andesite");
        building.write("control_panel");

        // Linking: a lamp under a receiving link, and a lever beside a transmitting one
        PonderSchematic linking = new PonderSchematic();
        linking.block(2, 1, 1, "create:brass_casing");
        linking.console(2, 2, 1, "brass");
        linking.block(0, 1, 3, "minecraft:redstone_lamp", "lit", "false");
        linking.block(0, 2, 3, "create:redstone_link", "facing", "up", "receiver", "true", "powered", "false");
        linking.block(4, 1, 3, "create:redstone_link", "facing", "up", "receiver", "false", "powered", "false");
        linking.block(4, 1, 2, "minecraft:lever", "face", "floor", "facing", "north", "powered", "false");
        linking.write("control_panel_linking");

        // Display Links: a link reading a Cuckoo Clock
        PonderSchematic display = new PonderSchematic();
        display.block(2, 1, 1, "create:brass_casing");
        display.console(2, 2, 1, "brass");
        display.block(1, 1, 3, "create:cuckoo_clock", "facing", "north");
        display.block(1, 2, 3, "create:display_link", "facing", "up", "powered", "false");
        display.write("control_panel_display");
    }

    /** A structure template under construction: a checkered 5x5 floor plus whatever is added. */
    private static final class PonderSchematic {

        private static final int SIZE = 5;

        private final ListTag palette = new ListTag();
        private final ListTag blocks = new ListTag();

        PonderSchematic() {
            for (int x = 0; x < SIZE; x++)
                for (int z = 0; z < SIZE; z++)
                    block(x, 0, z, (x + z) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:snow_block");
        }

        /** Adds a block; {@code properties} are name/value pairs. Returns its entry, for block-entity data. */
        CompoundTag block(int x, int y, int z, String name, String... properties) {
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            if (properties.length > 0) {
                CompoundTag values = new CompoundTag();
                for (int i = 0; i < properties.length; i += 2)
                    values.putString(properties[i], properties[i + 1]);
                state.put("Properties", values);
            }
            int index = palette.indexOf(state);
            if (index < 0) {
                index = palette.size();
                palette.add(state);
            }
            CompoundTag block = new CompoundTag();
            block.put("pos", intList(x, y, z));
            block.putInt("state", index);
            blocks.add(block);
            return block;
        }

        void console(int x, int y, int z, String casing) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", "create_controlling:control_panel");
            nbt.put("Shape", dev.mccastle91.createcontrolling.panel.ConsoleShape.DEFAULT.save());
            nbt.putString("Casing", casing);
            block(x, y, z, "create_controlling:control_panel", "face", "floor", "facing", "south").put("nbt", nbt);
        }

        void write(String name) throws IOException {
            Path target = repoRoot().resolve(Path.of("src", "main", "resources", "assets", "create_controlling",
                    "ponder", name + ".nbt"));
            if (Files.exists(target))
                return;
            Files.createDirectories(target.getParent());
            CompoundTag root = new CompoundTag();
            root.put("size", intList(SIZE, 3, SIZE));
            root.put("entities", new ListTag());
            root.put("palette", palette);
            root.put("blocks", blocks);
            root.putInt("DataVersion", DATA_VERSION_1_21_1);
            NbtIo.writeCompressed(root, target);
        }
    }
}
