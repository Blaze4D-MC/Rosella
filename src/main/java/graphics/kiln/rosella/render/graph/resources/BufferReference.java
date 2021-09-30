package graphics.kiln.rosella.render.graph.resources;

public class BufferReference extends ObjectReference {
    public BufferReference() {
        super(VulkanObjectType.BUFFER);
    }

    public BufferReference(long id) {
        super(VulkanObjectType.BUFFER, id);
    }
}
