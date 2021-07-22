package me.hydos.rosella.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SemaphorePool {

    private static final int ALLOCATION_SIZE = 16;

    private final VkDevice device;

    private final Set<Long> ownedSemaphores = new LongOpenHashSet();
    private final Deque<Long> availableSemaphores = new ArrayDeque<>();

    private final Lock lock = new ReentrantLock();

    public SemaphorePool(VkDevice device) {
        this.device = device;
    }

    public long getSemaphore() {
        long result;
        try {
            lock();
            if(this.availableSemaphores.isEmpty()) {
                allocateSemaphores(ALLOCATION_SIZE);
            }
            result = this.availableSemaphores.removeFirst();
        } finally {
           unlock();
        }
        return result;
    }

    public void returnSemaphore(long semaphore) {
        try {
            lock();
            if(!this.ownedSemaphores.contains(semaphore)) {
                throw new RuntimeException("Tried to return semaphore to semaphore pool that is not owned by the pool");
            }
            this.availableSemaphores.addFirst(semaphore);
        } finally {
            unlock();
        }
    }

    public void free() {
        try{
            lock();
            if(this.ownedSemaphores.size() != this.availableSemaphores.size()) {
                throw new RuntimeException("Tried to destroy semaphore pool where not all semaphores are returned");
            }

            for(long semaphore : this.ownedSemaphores) {
                VK10.vkDestroySemaphore(this.device, semaphore, null);
            }
            this.ownedSemaphores.clear();
            this.availableSemaphores.clear();
        } finally {
            unlock();
        }
    }

    private void allocateSemaphores(int count) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo info = VkSemaphoreCreateInfo.callocStack(stack);
            info.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            LongBuffer pSemaphore = stack.longs(0);
            for(int i = 0; i < count; i++) {
                int result = VK10.vkCreateSemaphore(this.device, info, null, pSemaphore);
                if(result != VK10.VK_SUCCESS) {
                    throw new RuntimeException("Failed to allocate semaphores");
                }

                this.ownedSemaphores.add(pSemaphore.get());
                this.availableSemaphores.addFirst(pSemaphore.get());
            }
        }
    }

    private void lock() {
        this.lock.lock();
    }

    private void unlock() {
        this.lock.unlock();
    }
}
