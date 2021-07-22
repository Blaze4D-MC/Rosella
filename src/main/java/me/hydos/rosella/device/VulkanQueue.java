package me.hydos.rosella.device;

import org.lwjgl.vulkan.*;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class VulkanQueue {

    private final VkQueue queue;
    private final int family;

    private final Lock lock = new ReentrantLock();

    public VulkanQueue(VkQueue queue, int family) {
        this.queue = queue;
        this.family = family;
    }

    public VkQueue getQueue() {
        return this.queue;
    }

    public VkDevice getDevice() {
        return this.queue.getDevice();
    }

    public int getQueueFamily() {
        return this.family;
    }

    public int vkQueueSubmit(VkSubmitInfo submit, long fence) {
        int result;
        try {
            lock();
            result = VK10.vkQueueSubmit(this.queue, submit, fence);
        } finally {
            unlock();
        }
        return result;
    }

    public int vkQueueSubmit(VkSubmitInfo.Buffer pSubmits, long fence) {
        int result;
        try {
            lock();
            result = VK10.vkQueueSubmit(this.queue, pSubmits, fence);
        } finally {
            unlock();
        }
        return result;
    }

    public int vkQueueBindSparse(VkBindSparseInfo bindInfo, long fence) {
        int result;
        try {
            lock();
            result = VK10.vkQueueBindSparse(this.queue, bindInfo, fence);
        } finally {
            unlock();
        }
        return result;
    }

    public int vkQueueBindSparse(VkBindSparseInfo.Buffer pBindInfo, long fence) {
        int result;
        try {
            lock();
            result = VK10.vkQueueBindSparse(this.queue, pBindInfo, fence);
        } finally {
            unlock();
        }
        return result;
    }

    /**
     * THIS IS BAD. VERY VERY BAD. DO NOT DO THIS. EVER... (but if for some reason you do need to here is a safe function)
     */
    @Deprecated
    public int vkQueueWaitIdle() {
        int result;
        try {
            lock();
            result = VK10.vkQueueWaitIdle(this.queue);
        } finally {
            unlock();
        }
        return result;
    }

    public int vkQueuePresentKHR(VkPresentInfoKHR presentInfo) {
        int result;
        try {
            lock();
            result = KHRSwapchain.vkQueuePresentKHR(this.queue, presentInfo);
        } finally {
            unlock();
        }
        return result;
    }

    private void lock() {
        this.lock.lock();
    }

    private void unlock() {
        this.lock.unlock();
    }
}
