package me.hydos.rosella.init.features;

import me.hydos.rosella.device.QueueFamilyIndices;
import me.hydos.rosella.device.VulkanQueue;
import me.hydos.rosella.init.DeviceBuilder;
import me.hydos.rosella.render.swapchain.Swapchain;
import me.hydos.rosella.render.swapchain.SwapchainSupportDetails;
import me.hydos.rosella.util.VkUtils;
import me.hydos.rosella.vkobjects.VkCommon;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;

import java.util.concurrent.Future;

/**
 * Configures the device to run the legacy rosella engine.
 */
public class RosellaLegacy extends ApplicationFeature {

    public static final String NAME = "rosella:legacy";

    private final VkCommon common;

    public RosellaLegacy(VkCommon common) {
        super(NAME);
        this.common = common;
    }

    @Override
    public RosellaLegacyInstance createInstance() {
        return new RosellaLegacyInstance();
    }

    public class RosellaLegacyInstance extends ApplicationFeature.Instance {

        private QueueFamilyIndices indices = null;

        @Override
        public void testFeatureSupport(DeviceBuilder.DeviceMeta meta) {
            canEnable = false;

            indices = VkUtils.findQueueFamilies(meta.physicalDevice, common.surface);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                boolean swapChainAdequate;
                boolean featureSupported;

                SwapchainSupportDetails swapchainSupport = Swapchain.Companion.querySwapchainSupport(meta.physicalDevice, stack, common.surface);
                swapChainAdequate = swapchainSupport.formats.hasRemaining() && swapchainSupport.presentModes.hasRemaining();
                featureSupported =
                        meta.availableFeatures.samplerAnisotropy() &&
                        meta.availableFeatures.depthClamp() &&
                        meta.availableFeatures.depthBounds();

                canEnable = indices.isComplete() && swapChainAdequate && featureSupported && meta.isExtensionSupported(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            }
        }

        @Override
        public Object enableFeature(DeviceBuilder.DeviceMeta meta) {
            meta.configureDeviceFeatures()
                    .samplerAnisotropy(true)
                    .depthClamp(true)
                    .depthBounds(true);

            meta.enableExtension(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);

            Future<VulkanQueue> graphicsRequest = meta.addQueueRequest(this.indices.graphicsFamily);
            Future<VulkanQueue> presentRequest = meta.addQueueRequest(this.indices.presentFamily);
            return new RosellaLegacyFeatures(graphicsRequest, presentRequest, this.indices);
        }
    }

    public static RosellaLegacyFeatures getMetaObject(Object o) {
        if(!(o instanceof RosellaLegacyFeatures)) {
            throw new RuntimeException("Meta object could not be cast to RosellaLegacyFeatures");
        }
        return (RosellaLegacyFeatures) o;
    }

    public record RosellaLegacyFeatures(Future<VulkanQueue> graphicsQueue, Future<VulkanQueue> presentQueue, QueueFamilyIndices indices) {
    }
}