package me.hydos.rosella.render.shader.ubo

import me.hydos.rosella.device.VulkanDevice
import me.hydos.rosella.memory.BufferInfo
import me.hydos.rosella.memory.Memory
import me.hydos.rosella.render.descriptorsets.DescriptorSets
import me.hydos.rosella.render.shader.ShaderProgram
import me.hydos.rosella.render.swapchain.Swapchain
import me.hydos.rosella.scene.`object`.RenderObject
import me.hydos.rosella.memory.MemoryUtils
import org.joml.Matrix4f
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.vma.Vma
import org.lwjgl.vulkan.VK10

open class RenderObjectUbo(
    val device: VulkanDevice,
    val memory: Memory,
    private val renderObject: RenderObject,
    shaderProgram: ShaderProgram
) : Ubo() {

    private var uboFrames: MutableList<BufferInfo> = ArrayList()
    private var descSets: DescriptorSets = DescriptorSets(shaderProgram.raw.descriptorPool)

    override fun create(swapchain: Swapchain) {
        MemoryStack.stackPush().use { stack ->
            uboFrames = ArrayList(swapchain.swapChainImages.size)
            for (i in swapchain.swapChainImages.indices) {
                val pBuffer = stack.mallocLong(1)
                uboFrames.add(
                    memory.createBuffer(
                        getSize(),
                        VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                        Vma.VMA_MEMORY_USAGE_CPU_ONLY,
                        pBuffer
                    )
                )
            }
        }
    }

    override fun update(currentImg: Int, swapchain: Swapchain) {
        if (uboFrames.size == 0) {
            create(swapchain) //TODO: CONCERN. why did i write this
        }

        MemoryStack.stackPush().use {
            val data = it.mallocPointer(1)
            memory.map(uboFrames[currentImg].allocation(), false, data)
            val buffer = data.getByteBuffer(0, getSize())
            val mat4Size = 16 * java.lang.Float.BYTES
            renderObject.modelMatrix[0, buffer]
            renderObject.viewMatrix.get(
                MemoryUtils.align(
                    mat4Size,
                    MemoryUtils.alignment(renderObject.viewMatrix::class.java)
                ), buffer
            )
            renderObject.projectionMatrix.get(
                MemoryUtils.align(
                    mat4Size * 2,
                    MemoryUtils.alignment(renderObject.projectionMatrix::class.java)
                ), buffer
            )
            memory.unmap(uboFrames[currentImg].allocation())
        }
    }

    override fun free() {
        for (uboImg in uboFrames) {
            uboImg.free(device, memory)
        }
    }

    override fun getSize(): Int {
        return 3 * MemoryUtils.size(Matrix4f::class.java)
    }

    override fun getUniformBuffers(): List<BufferInfo> {
        return uboFrames
    }

    override fun getDescriptors(): DescriptorSets {
        return descSets
    }

    override fun setDescriptors(descriptorSets: DescriptorSets) {
        this.descSets = descriptorSets
    }
}
