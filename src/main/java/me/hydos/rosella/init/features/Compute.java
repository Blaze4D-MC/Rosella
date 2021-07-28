package me.hydos.rosella.init.features;

import me.hydos.rosella.init.DeviceBuildInformation;
import me.hydos.rosella.init.DeviceBuilder;
import org.lwjgl.vulkan.VK10;

/**
 * Tests if at least one queue exists with graphics capabilities.
 *
 * Does not allocate any queues.
 */
public class Compute extends SimpleApplicationFeature {

    public static final String NAME = "rosella:compute";

    public Compute() {
        super(NAME, Compute::canEnable, null);
    }

    private static boolean canEnable(DeviceBuildInformation meta) {
        return !meta.findQueueFamilies(VK10.VK_QUEUE_COMPUTE_BIT, true).isEmpty();
    }
}
