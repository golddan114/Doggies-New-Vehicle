package com.liucodex.carriercube;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.SimpleUnbakedGeometry;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Indexed aircraft and carrier triangles, baked through the regular block atlas. */
final class CubeMesh extends SimpleUnbakedGeometry<CubeMesh> {
    private static final int MAGIC = 0x434d4831; // CMH1

    private final float[] positions;
    private final float[] uv;
    private final float[] normals;
    private final Surface[] surfaces;
    private final short[] faces;

    private record Surface(String texture, float red, float green, float blue) {}

    private CubeMesh(DataInputStream input) throws IOException {
        if (input.readInt() != MAGIC) throw new IOException("Invalid CMH1 header");
        positions = readFloats(input, input.readUnsignedShort() * 3);
        uv = readFloats(input, input.readUnsignedShort() * 2);
        normals = readFloats(input, input.readUnsignedShort() * 3);
        surfaces = new Surface[input.readUnsignedShort()];
        for (int i = 0; i < surfaces.length; i++) {
            surfaces[i] = new Surface(input.readUTF(), readFloat(input), readFloat(input), readFloat(input));
        }
        faces = new short[input.readUnsignedShort() * 10];
        for (int face = 0; face < faces.length; face += 10) {
            faces[face] = readIndex(input, surfaces.length);
            for (int corner = face + 1; corner < face + 10; corner += 3) {
                faces[corner] = readIndex(input, positions.length / 3);
                faces[corner + 1] = readIndex(input, uv.length / 2);
                faces[corner + 2] = readIndex(input, normals.length / 3);
            }
        }
        if (faces.length == 0 || input.read() != -1) throw new IOException("Invalid CMH1 triangle data");
    }

    static CubeMesh load(JsonObject json) {
        var mesh = ResourceLocation.tryParse(json.get("model").getAsString());
        if (mesh == null) throw new JsonParseException("Invalid CMH1 resource location");
        try (var input = new DataInputStream(new BufferedInputStream(Minecraft.getInstance().getResourceManager().open(mesh)))) {
            return new CubeMesh(input);
        } catch (IOException e) {
            throw new JsonParseException("Cannot load CMH1 mesh " + mesh, e);
        }
    }

    private static float[] readFloats(DataInputStream input, int length) throws IOException {
        float[] values = new float[length];
        for (int i = 0; i < length; i++) values[i] = readFloat(input);
        return values;
    }

    private static float readFloat(DataInputStream input) throws IOException {
        float value = input.readFloat();
        if (!Float.isFinite(value)) throw new IOException("Non-finite CMH1 value");
        return value;
    }

    private static short readIndex(DataInputStream input, int size) throws IOException {
        int index = input.readUnsignedShort();
        if (index >= size) throw new IOException("CMH1 index outside its table");
        return (short) index;
    }

    @Override
    protected void addQuads(IGeometryBakingContext owner, IModelBuilder<?> builder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> sprites, ModelState state, ResourceLocation modelLocation) {
        var textures = new TextureAtlasSprite[surfaces.length];
        for (int i = 0; i < surfaces.length; i++) {
            textures[i] = sprites.apply(owner.getMaterial(surfaces[i].texture()));
        }
        var rotation = state.getRotation().compose(owner.getRootTransform());
        boolean transformVertices = !rotation.isIdentity();
        var transform = transformVertices ? rotation.blockCenterToCorner() : rotation;
        var position = new Vector4f();
        var normal = new Vector3f();
        for (int face = 0; face < faces.length; face += 10) {
            int surfaceIndex = Short.toUnsignedInt(faces[face]);
            Surface surface = surfaces[surfaceIndex];
            TextureAtlasSprite texture = textures[surfaceIndex];
            var quad = new QuadBakingVertexConsumer.Buffered();
            quad.setSprite(texture);
            quad.setTintIndex(0);
            quad.setShade(false);
            for (int i = 0; i < 4; i++) {
                // Minecraft bakes triangles as quads by repeating the last corner.
                int corner = face + 1 + Math.min(i, 2) * 3;
                int p = Short.toUnsignedInt(faces[corner]) * 3;
                int t = Short.toUnsignedInt(faces[corner + 1]) * 2;
                int n = Short.toUnsignedInt(faces[corner + 2]) * 3;
                position.set(positions[p], positions[p + 1], positions[p + 2], 1);
                normal.set(normals[n], normals[n + 1], normals[n + 2]);
                if (transformVertices) {
                    transform.transformPosition(position);
                    transform.transformNormal(normal);
                }
                quad.vertex(position.x(), position.y(), position.z());
                quad.color(surface.red(), surface.green(), surface.blue(), 1F);
                quad.uv(texture.getU(uv[t] * 16F), texture.getV((1F - uv[t + 1]) * 16F));
                quad.uv2(0);
                quad.normal(normal.x(), normal.y(), normal.z());
                if (i == 0) quad.setDirection(Direction.getNearest(normal.x(), normal.y(), normal.z()));
                quad.endVertex();
            }
            builder.addUnculledFace(quad.getQuad());
        }
    }
}
