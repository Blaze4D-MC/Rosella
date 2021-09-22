package graphics.kiln.rosella.render.graph.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record SerializedGraph(
        List<Serialization> serializations,
        Map<Long, BufferResource> bufferResources,
        int semaphoreCount) {

    public SerializedGraph(@NotNull List<Serialization> serializations, @NotNull Map<Long, BufferResource> bufferResources, int semaphoreCount) {
        this.serializations = Collections.unmodifiableList(serializations);
        this.semaphoreCount = semaphoreCount;

        this.bufferResources = Collections.unmodifiableMap(bufferResources);
    }

    public JsonObject convertToJson() {
        JsonObject result = new JsonObject();

        result.add("buffers", generateJsonBuffers());
        result.add("serializations", generateJsonSerializations());

        return result;
    }

    private JsonArray generateJsonSerializations() {
        JsonArray result = new JsonArray();

        for(Serialization serialization : this.serializations) {
            result.add(serialization.convertToJson());
        }

        return result;
    }

    private JsonArray generateJsonBuffers() {
        JsonArray result = new JsonArray();

        for(BufferResource buffer : this.bufferResources.values()) {
            result.add(buffer.convertToJson());
        }

        return result;
    }

    public static record BufferResource(long id, Serialization firstUsed, Serialization lastUsed) {

        JsonObject convertToJson() {
            JsonObject result = new JsonObject();

            result.addProperty("id", this.id);
            result.addProperty("firstUsed", this.firstUsed.uuid());
            result.addProperty("lastUsed", this.lastUsed.uuid());

            return result;
        }
    }
}