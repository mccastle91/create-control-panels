package dev.mccastle91.createcontrolling;

import dev.mccastle91.createcontrolling.panel.element.PanelElements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic mod core. All panel logic (data model, element behaviors,
 * serialization, GUI logic) lives in this module; loader modules stay thin.
 */
public final class CreateControlling {

    public static final String MOD_ID = "create_controlling";
    public static final String MOD_NAME = "Create Controlling";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private CreateControlling() {
    }

    public static void init() {
        LOGGER.info("{} common init", MOD_NAME);
        PanelElements.init();
    }
}
