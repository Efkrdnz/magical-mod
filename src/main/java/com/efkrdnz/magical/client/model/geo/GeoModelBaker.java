package com.efkrdnz.magical.client.model.geo;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Turns a {@link GeoModel} into vanilla model parts, once, at resource load.
 *
 * <p>The only conversion in the whole pipeline is here: Bedrock is y up and vanilla model space is
 * y down (the renderer's {@code scale(-1, -1, 1)} turns it back), so every y is negated and a
 * cube's box starts at the top of it. Everything else is copied: x, z, the degrees of rotation
 * and their signs, the Box UV corner, inflate, mirror. A cube rotated on its own becomes a part
 * of its own at its pivot, since a vanilla cube cannot be rotated alone.
 */
public final class GeoModelBaker {

    public record Baked(ModelPart root, Map<String, ModelPart> parts) {
        public ModelPart part(String name) {
            return parts.get(name);
        }
    }

    private GeoModelBaker() {
    }

    public static Baked bake(GeoModel model) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> definitions = new HashMap<>();
        Map<String, String> parents = new HashMap<>();
        for (GeoModel.Bone bone : model.bones()) {
            define(model, bone, mesh, definitions, parents);
        }
        ModelPart root = LayerDefinition.create(mesh, model.textureWidth(), model.textureHeight()).bakeRoot();
        Map<String, ModelPart> parts = new HashMap<>();
        for (GeoModel.Bone bone : model.bones()) {
            parts.put(bone.name(), resolve(root, bone.name(), parents));
        }
        return new Baked(root, Map.copyOf(parts));
    }

    private static PartDefinition define(GeoModel model, GeoModel.Bone bone, MeshDefinition mesh,
            Map<String, PartDefinition> definitions, Map<String, String> parents) {
        PartDefinition existing = definitions.get(bone.name());
        if (existing != null) {
            return existing;
        }
        PartDefinition parent = mesh.getRoot();
        float[] parentPivot = {0.0F, 0.0F, 0.0F};
        if (bone.parent() != null) {
            GeoModel.Bone parentBone = model.bone(bone.parent());
            if (parentBone == null) {
                throw new GeoFormatException("bone " + bone.name() + " names a parent that is not there: " + bone.parent());
            }
            parent = define(model, parentBone, mesh, definitions, parents);
            parentPivot = parentBone.pivot();
            parents.put(bone.name(), bone.parent());
        }
        float[] p = bone.pivot();
        CubeListBuilder cubes = CubeListBuilder.create();
        for (GeoModel.Cube cube : bone.cubes()) {
            if (cube.rotation() == null) {
                box(cubes, cube, p);
            }
        }
        PartDefinition definition = parent.addOrReplaceChild(bone.name(), cubes, PartPose.offsetAndRotation(
                p[0] - parentPivot[0], -(p[1] - parentPivot[1]), p[2] - parentPivot[2],
                rad(bone.rotation()[0]), rad(bone.rotation()[1]), rad(bone.rotation()[2])));
        int index = 0;
        for (GeoModel.Cube cube : bone.cubes()) {
            if (cube.rotation() == null) {
                continue;
            }
            float[] c = cube.pivot();
            CubeListBuilder one = CubeListBuilder.create();
            box(one, cube, c);
            definition.addOrReplaceChild(bone.name() + "$cube" + index++, one, PartPose.offsetAndRotation(
                    c[0] - p[0], -(c[1] - p[1]), c[2] - p[2],
                    rad(cube.rotation()[0]), rad(cube.rotation()[1]), rad(cube.rotation()[2])));
        }
        definitions.put(bone.name(), definition);
        return definition;
    }

    /** One box relative to a pivot: the top of the Bedrock box is the bottom of the vanilla one. */
    private static void box(CubeListBuilder cubes, GeoModel.Cube cube, float[] pivot) {
        float[] o = cube.origin();
        float[] s = cube.size();
        cubes.texOffs(Math.round(cube.uv()[0]), Math.round(cube.uv()[1]))
                .mirror(cube.mirror())
                .addBox(o[0] - pivot[0], -(o[1] + s[1]) + pivot[1], o[2] - pivot[2], s[0], s[1], s[2],
                        new CubeDeformation(cube.inflate()));
    }

    private static ModelPart resolve(ModelPart root, String name, Map<String, String> parents) {
        String parent = parents.get(name);
        ModelPart holder = parent == null ? root : resolve(root, parent, parents);
        return holder.getChild(name);
    }

    private static float rad(float degrees) {
        return (float) Math.toRadians(degrees);
    }
}
