package dev.mccastle91.createcontrolling.compat.create;

import com.simibubi.create.api.behaviour.display.DisplayTarget;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.mccastle91.createcontrolling.panel.PanelBlockEntity;
import dev.mccastle91.createcontrolling.panel.element.PanelElement;
import dev.mccastle91.createcontrolling.panel.element.ReadoutElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Lets a Display Link write to a console. Link line 0 is console line 1, and
 * the console offers lines up to the highest one a readout shows. A source
 * with several lines fills them downwards, like on a sign, stopping at one
 * another link owns; lines it no longer fills are cleared rather than left
 * showing old text.
 */
public class ConsoleDisplayTarget extends DisplayTarget {

    private static final int DEFAULT_COLUMNS = 16;

    @Override
    public void acceptText(int line, List<MutableComponent> text, DisplayLinkContext context) {
        if (!(context.getTargetBlockEntity() instanceof PanelBlockEntity panel))
            return;
        boolean changed = false;
        int lines = panel.getLayout().lineCount();
        for (int i = 0; line + i < lines; i++) {
            if (i == 0)
                reserve(line, panel, context);
            else if (isReserved(line + i, panel, context))
                break;
            changed |= panel.setDisplayLine(line + i + 1, i < text.size() ? text.get(i) : Component.empty());
        }
        if (changed)
            panel.syncDisplayLines();
    }

    @Override
    public DisplayTargetStats provideStats(DisplayLinkContext context) {
        if (!(context.getTargetBlockEntity() instanceof PanelBlockEntity panel))
            return new DisplayTargetStats(1, DEFAULT_COLUMNS, this);
        int columns = 0;
        for (PanelElement element : panel.getLayout().elements())
            if (element instanceof ReadoutElement readout && readout.shows(context.blockEntity().targetLine + 1))
                columns = Math.max(columns, readout.columns());
        return new DisplayTargetStats(Math.max(1, panel.getLayout().lineCount()),
                columns == 0 ? DEFAULT_COLUMNS : columns, this);
    }
}
