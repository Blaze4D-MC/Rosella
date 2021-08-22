package me.hydos.rosella.file.model;

import me.hydos.rosella.Rosella;
import me.hydos.rosella.render.Topology;
import me.hydos.rosella.render.material.Material;
import me.hydos.rosella.render.model.AssimpHelperKt;
import me.hydos.rosella.render.pipeline.Pipeline;
import me.hydos.rosella.render.pipeline.state.StateInfo;
import me.hydos.rosella.render.resource.Resource;
import me.hydos.rosella.render.shader.ShaderProgram;
import me.hydos.rosella.render.texture.*;
import me.hydos.rosella.render.vertex.VertexFormat;
import me.hydos.rosella.scene.object.RenderObject;
import me.hydos.rosella.ubo.BasicUboDataProvider;
import me.hydos.rosella.ubo.UboDataProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.*;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.lwjgl.assimp.Assimp.aiGetErrorString;
import static org.lwjgl.assimp.Assimp.aiTextureType_DIFFUSE;

public class GlbModelLoader {

    private static final Logger LOGGER = LogManager.getLogger();

    public static List<GlbRenderObject> createGlbRenderObject(Rosella rosella, Resource glbFile, ShaderProgram program, VertexFormat format, NodeSelector selector, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        return createGlbRenderObject(rosella, glbFile, program, format, selector, viewMatrix, projectionMatrix, StateInfo.DEFAULT_3D, new BasicUboDataProvider());
    }

    public static List<GlbRenderObject> createGlbRenderObject(Rosella rosella, Resource glbFile, ShaderProgram program, VertexFormat format, NodeSelector selector, Matrix4f viewMatrix, Matrix4f projectionMatrix, StateInfo stateInfo, UboDataProvider<RenderObject> dataProvider) {
        AIScene scene = AssimpHelperKt.loadScene(glbFile, Assimp.aiProcess_FlipUVs);
        List<AssimpMaterial> rawMaterials = new ArrayList<>();
        List<AITexture> rawTextures = new ArrayList<>();

        // Retrieve Materials
        assert scene != null;
        PointerBuffer pMaterials = scene.mMaterials();
        if (pMaterials != null) {
            for (int i = 0; i < pMaterials.capacity(); i++) {
                rawMaterials.add(new AssimpMaterial(AIMaterial.create(pMaterials.get(i))));
            }
        } else {
            throw new RuntimeException("Can't handle models with no materials. We can't guess how you want us to render the object?");
        }

        // Retrieve Textures
        PointerBuffer pTextures = scene.mTextures();
        if (pTextures != null) {
            for (int i = 0; i < scene.mNumTextures(); i++) {
                rawTextures.add(AITexture.create(pTextures.get(i)));
            }
        } else {
            throw new RuntimeException("How do you expect us to render without textures? Use colours? we don't support that yet!");
        }

        // Try to load the textures into rosella
        List<UploadableImage> textures = new ArrayList<>();
        for (AITexture rawTexture : rawTextures) {
            if (rawTexture.mHeight() > 0) {
                throw new RuntimeException("We can't pass that data :(");
            } else {
                try {
                    textures.add(new StbiImage(rawTexture.pcDataCompressed(), ImageFormat.RGBA)); //FIXME: hacks
                } catch (RuntimeException e) {
                    textures.add(new StbiImage(rawTexture.pcDataCompressed(), ImageFormat.RGB));
                }
            }
        }
        // Now lets create some materials from those textures
        List<Material> materials = new ArrayList<>();
        for (AssimpMaterial rawMaterial : rawMaterials) {
            int textureCount = Assimp.aiGetMaterialTextureCount(rawMaterial.material, aiTextureType_DIFFUSE);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                UploadableImage[] images;
                if (textureCount == 0) {
                    LOGGER.warn("Skipped material with no textures");
                    images = new UploadableImage[1];
                } else {
                    images = new UploadableImage[textureCount];
                    for (int i = 0; i < textureCount; i++) {
                        AIString path = AIString.callocStack(stack);
                        Assimp.aiGetMaterialTexture(rawMaterial.material, Assimp.aiTextureType_DIFFUSE, 0, path, (IntBuffer) null, null, null, null, null, null);
                        String texturePath = path.dataString();
                        images[i] = textures.get(Integer.parseInt(texturePath.substring(1)));
                    }
                }

                TextureMap textureMap = new ImmutableTextureMap(images, new SamplerCreateInfo(TextureFilter.NEAREST, WrapMode.CLAMP_TO_EDGE), rosella, rosella.common.textureManager);

                // FIXME: generate shaders for models based on their properties
                Pipeline pipeline = new Pipeline(
                        rosella.renderer.mainRenderPass,
                        program,
                        Topology.TRIANGLES,
                        format,
                        stateInfo
                );
                materials.add(new Material(rosella.common.pipelineManager.registerPipeline(pipeline), textureMap));
            }
        }

        // Create a list of GlbRenderObjects from the data we have
        List<MeshData> meshes = loadMeshes(scene, selector);
        List<GlbRenderObject> renderObjects = new ArrayList<>(meshes.size());
        for (MeshData mesh : meshes) {
            Material material = materials.get(mesh.materialIndex);
            renderObjects.add(new GlbRenderObject(material, mesh, viewMatrix, projectionMatrix, dataProvider));
        }

        // Return our hard work
        return renderObjects;
    }

    public static List<MeshData> loadMeshes(AIScene scene, NodeSelector selector) {
        if (scene.mRootNode() == null) {
            throw new RuntimeException("Could not load model " + aiGetErrorString());
        }

        List<MeshData> models = new ArrayList<>();
        processNode(scene.mRootNode(), scene, models, selector);
        return models;
    }

    private static void processNode(AINode node, AIScene scene, List<MeshData> model, NodeSelector selector) {
        if (node.mMeshes() != null && selector.loadNodeMesh(node.mName().dataString())) {
            processNodeMeshes(scene, node, model);
        }
        if (node.mChildren() != null) {
            PointerBuffer children = node.mChildren();
            for (int i = 0; i < node.mNumChildren(); i++) {
                processNode(AINode.create(children.get(i)), scene, model, selector);
            }
        }
    }

    private static void processNodeMeshes(AIScene scene, AINode node, List<MeshData> models) {
        PointerBuffer pMeshes = scene.mMeshes();
        IntBuffer meshIndices = node.mMeshes();
        for (int i = 0; i < meshIndices.capacity(); i++) {
            processMesh(scene, AIMesh.create(pMeshes.get(meshIndices.get(i))), node, models);
        }
    }

    private static void processMesh(AIScene scene, AIMesh mesh, AINode node, List<MeshData> models) {
        MeshData meshData = new MeshData();
        meshData.modelMatrix = convertMatrix(node.mTransformation());
        meshData.materialIndex = mesh.mMaterialIndex();
        processPositions(mesh, meshData.positions);
        processTexCoords(mesh, meshData.texCoords);
        processIndices(mesh, meshData.indices);
        processNormals(mesh, meshData.normals);
        models.add(meshData);
    }

    private static Matrix4f convertMatrix(AIMatrix4x4 assimpMat4) {
        Matrix4f dest = new Matrix4f();
        dest.m00(assimpMat4.a1());
        dest.m10(assimpMat4.a2());
        dest.m20(assimpMat4.a3());
        dest.m30(assimpMat4.a4());
        dest.m01(assimpMat4.b1());
        dest.m11(assimpMat4.b2());
        dest.m21(assimpMat4.b3());
        dest.m31(assimpMat4.b4());
        dest.m02(assimpMat4.c1());
        dest.m12(assimpMat4.c2());
        dest.m22(assimpMat4.c3());
        dest.m32(assimpMat4.c4());
        dest.m03(assimpMat4.d1());
        dest.m13(assimpMat4.d2());
        dest.m23(assimpMat4.d3());
        dest.m33(assimpMat4.d4());
        return dest;
    }

    private static void processPositions(AIMesh mesh, List<Vector3fc> positions) {
        AIVector3D.Buffer vertices = requireNonNull(mesh.mVertices());
        for (int i = 0; i < vertices.capacity(); i++) {
            AIVector3D position = vertices.get(i);
            positions.add(new Vector3f(position.x(), position.y(), position.z()));
        }
    }

    private static void processNormals(AIMesh mesh, List<Vector3fc> normals) {
        AIVector3D.Buffer normalBuffer = requireNonNull(mesh.mNormals());
        for (int i = 0; i < normalBuffer.capacity(); i++) {
            AIVector3D normal = normalBuffer.get(i);
            normals.add(new Vector3f(normal.x(), normal.y(), normal.z()));
        }
    }

    private static void processTexCoords(AIMesh mesh, List<Vector2fc> texCoords) {
        AIVector3D.Buffer aiTexCoords = requireNonNull(mesh.mTextureCoords(0));
        for (int i = 0; i < aiTexCoords.capacity(); i++) {
            AIVector3D coords = aiTexCoords.get(i);
            texCoords.add(new Vector2f(coords.x(), coords.y()));
        }
    }

    private static void processIndices(AIMesh mesh, List<Integer> indices) {
        AIFace.Buffer aiFaces = mesh.mFaces();
        for (int i = 0; i < mesh.mNumFaces(); i++) {
            AIFace face = aiFaces.get(i);
            IntBuffer pIndices = face.mIndices();
            for (int i1 = 0; i1 < face.mNumIndices(); i1++) {
                indices.add(pIndices.get(i1));
            }
        }
    }

    public static class MeshData {
        public int materialIndex;
        public List<Vector3fc> positions = new ArrayList<>();
        public List<Vector2fc> texCoords = new ArrayList<>();
        public List<Vector3fc> normals = new ArrayList<>();
        public List<Integer> indices = new ArrayList<>();
        public Matrix4f modelMatrix;
    }

    public static class AssimpMaterial {

        private final AIMaterial material;
        public HashMap<String, AssimpMaterialProperty<?>> properties = new HashMap<>();

        public AssimpMaterial(AIMaterial material) {
            this.material = material;

            for (int i = 0; i < material.mNumProperties(); i++) {
                AIMaterialProperty property = AIMaterialProperty.create(material.mProperties().get(i));

                String name = property.mKey().dataString();
                int rawType = property.mType();
                ByteBuffer data = property.mData();
                int dataLength = property.mDataLength();
                switch (rawType) {

                    /* Array of single-precision (32 Bit) floats

                       It is possible to use aiGetMaterialInteger[Array]() (or the C++-API
                       aiMaterial::Get()) to query properties stored in floating-point format.
                       The material system performs the type conversion automatically.
                     */
                    case 0x1 -> properties.put(name, AssimpMaterialProperty.of(data.getFloat(), name));

                    /* Array of double-precision (64 Bit) floats

                       It is possible to use aiGetMaterialInteger[Array]() (or the C++-API
                       aiMaterial::Get()) to query properties stored in floating-point format.
                       The material system performs the type conversion automatically.
                     */
                    case 0x2 -> properties.put(name, AssimpMaterialProperty.of(data.getDouble(), name));

                    /* The material property is an aiString.

                       Arrays of strings aren't possible, aiGetMaterialString() (or the
                       C++-API aiMaterial::Get()) *must* be used to query a string property.
                     */
                    case 0x3 -> properties.put(name, AssimpMaterialProperty.of(MemoryUtil.memUTF8(data), name));

                    /* Array of (32 Bit) integers

                       It is possible to use aiGetMaterialFloat[Array]() (or the C++-API
                       aiMaterial::Get()) to query properties stored in integer format.
                       The material system performs the type conversion automatically.
                     */
                    case 0x4 -> {
                        int[] intArray = new int[dataLength];
                        for (int i1 = 0; i1 < dataLength; i1++) {
                            intArray[i1] = data.getInt();
                        }
                        properties.put(name, AssimpMaterialProperty.of(intArray, name));
                    }

                    /* Simple binary buffer, content undefined. Not convertible to anything.
                     */
                    case 0x5 -> properties.put(name, AssimpMaterialProperty.of(data, name));

                    /*
                      Backup in case all the above fails
                     */
                    default -> throw new RuntimeException("Property '" + name + "' has unknown data type: " + rawType);
                }
            }
        }

        public String getStringProperty(String s) {
            AssimpMaterialProperty<String> property = (AssimpMaterialProperty<String>) properties.get(s);
            if (property != null) {
                return property.value;
            } else {
                return null;
            }
        }
    }

    public static class AssimpMaterialProperty<T> {
        public String name;
        public T value;

        public static <T> AssimpMaterialProperty<T> of(T value, String key) {
            AssimpMaterialProperty<T> property = new AssimpMaterialProperty<>();
            property.name = key;
            property.value = value;
            return property;
        }
    }

    public interface NodeSelector {
        boolean loadNodeMesh(String name);
    }
}
