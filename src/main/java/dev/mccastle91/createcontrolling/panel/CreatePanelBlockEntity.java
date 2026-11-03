package dev.mccastle91.createcontrolling.panel;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.mccastle91.createcontrolling.panel.FrequencyPair;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
//?} else {
/*import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
*///?}

import java.util.List;

/**
 * Supplies the loader lifecycle hooks vanilla lacks ({@code onLoad} and
 * {@code onChunkUnloaded} are NeoForge patches) and Create's Engineer's
 * Goggles overlay, mirroring how Create surfaces frequencies and analog
 * values on its own link blocks.
 */
public class CreatePanelBlockEntity extends PanelBlockEntity implements IHaveGoggleInformation {

    /** Model data the console body is generated from (see ConsoleBakedModel). */
    public static final ModelProperty<ConsoleShape> SHAPE_PROPERTY = new ModelProperty<>();
    public static final ModelProperty<ConsoleCasing> CASING_PROPERTY = new ModelProperty<>();

    private static final int MAX_GOGGLE_LINES = 8;

    public CreatePanelBlockEntity(BlockEntityType<? extends PanelBlockEntity> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(SHAPE_PROPERTY, shape())
                .with(CASING_PROPERTY, casing())
                .build();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        rebuildLinkPorts();
    }

    @Override
    public void onChunkUnloaded() {
        unloadLinkPorts();
        super.onChunkUnloaded();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("gui.create_controlling.goggles.header"));
        List<PanelElement> elements = getLayout().elements();
        if (elements.isEmpty()) {
            tooltip.add(indented(Component.translatable("gui.create_controlling.goggles.empty")
                    .withStyle(ChatFormatting.GRAY)));
            return true;
        }

        int shown = 0;
        for (PanelElement element : elements) {
            boolean transmitter = element.isTransmitter();
            if (!transmitter && !element.isReceiver())
                continue;
            if (shown >= MAX_GOGGLE_LINES) {
                tooltip.add(indented(Component.literal("...").withStyle(ChatFormatting.DARK_GRAY)));
                break;
            }
            int strength = transmitter ? element.transmittedStrength() : element.displayStrength();
            MutableComponent line = Component.literal(transmitter ? "→ " : "← ")
                    .withStyle(transmitter ? ChatFormatting.GOLD : ChatFormatting.AQUA)
                    .append(Component.translatable("gui.create_controlling.element." + element.type().id())
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" " + strength).withStyle(ChatFormatting.WHITE));

            FrequencyPair frequency = element.frequency();
            if (frequency.isEmpty()) {
                line.append(Component.literal(" ").append(
                        Component.translatable("gui.create_controlling.goggles.unlinked")
                                .withStyle(ChatFormatting.DARK_GRAY)));
            } else {
                MutableComponent freq = Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY);
                freq.append(frequency.first().isEmpty()
                        ? Component.literal("-")
                        : frequency.first().getHoverName().copy().withStyle(ChatFormatting.GRAY));
                freq.append(Component.literal(" / ").withStyle(ChatFormatting.DARK_GRAY));
                freq.append(frequency.second().isEmpty()
                        ? Component.literal("-")
                        : frequency.second().getHoverName().copy().withStyle(ChatFormatting.GRAY));
                freq.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
                line.append(freq);
            }
            tooltip.add(indented(line));
            shown++;
        }
        if (shown == 0)
            tooltip.add(indented(Component.translatable("gui.create_controlling.goggles.empty")
                    .withStyle(ChatFormatting.GRAY)));
        return true;
    }

    private static Component indented(Component component) {
        return Component.literal("    ").append(component);
    }
}
