package me.hydos.rosella.device;

import org.lwjgl.vulkan.VkDevice;

import java.util.Collections;
import java.util.Map;

public class VulkanDevice {

    private final VkDevice device;
    private final Map<String, Object> enableFeatures;

    public VulkanDevice(VkDevice device, Map<String, Object> enabledFeatures) {
        this.device = device;
        this.enableFeatures = Collections.unmodifiableMap(enabledFeatures);
    }

    public VkDevice getDevice() {
        return this.device;
    }

    /**
     * Tests if a ApplicationFeature is enabled for this device.
     *
     * @param name The name of the feature.
     * @return True if the feature is enabled. False otherwise.
     */
    public boolean isFeatureEnabled(String name) {
        return enableFeatures.containsKey(name);
    }

    /**
     * Retrieves the metadata for an enabled ApplicationFeature.
     *
     * @param name The name of the feature.
     * @return The metadata for the feature or null if the feature didnt generate any metadata.
     */
    public Object getFeatureMeta(String name) {
        return enableFeatures.get(name);
    }
}
