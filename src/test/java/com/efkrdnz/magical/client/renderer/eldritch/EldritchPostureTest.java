package com.efkrdnz.magical.client.renderer.eldritch;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.model.geo.GeoModelBaker;
import com.efkrdnz.magical.client.model.geo.GeoModelParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The bones are posed in vanilla model space, where y runs down, so a sign slipped in the renderer
 * reads fine and only shows in a capture. These pin the directions against the shipped
 * placeholders: the upper jaw rises and the lower sinks while the maw opens, both come back to
 * rest on the snap, and an eye tilts its pupil up toward what stands above it.
 */
class EldritchPostureTest {
    private static final String MODELS = "/assets/magical/models/entity/eldritch/";
    /** How far a jaw tip moves at most while the jaws chatter shut, in sixteenths. */
    private static final float SHUT_TOLERANCE = 3.0F;

    @Test
    void theUpperJawRisesAndTheLowerSinksWhileTheMawOpens() throws IOException {
        GeoModelBaker.Baked maw = bake("maw");
        Vector3f upperRest = tip(maw.part("jaw_upper"));
        Vector3f lowerRest = tip(maw.part("jaw_lower"));
        EldritchConstructRenderer.maw(mawState(12.0F, 24, 0), maw);
        Vector3f upper = tip(maw.part("jaw_upper"));
        Vector3f lower = tip(maw.part("jaw_lower"));
        // Model y runs down: up in the world is a smaller y here.
        assertTrue(upper.y < upperRest.y - 4.0F, "the upper jaw tip must rise: rest " + upperRest.y + ", half open " + upper.y);
        assertTrue(lower.y > lowerRest.y + 4.0F, "the lower jaw tip must sink: rest " + lowerRest.y + ", half open " + lower.y);
    }

    @Test
    void theJawsComeBackToRestOnTheSnap() throws IOException {
        GeoModelBaker.Baked maw = bake("maw");
        Vector3f upperRest = tip(maw.part("jaw_upper"));
        EldritchConstructRenderer.maw(mawState(27.0F, 24, 24), maw);
        Vector3f upper = tip(maw.part("jaw_upper"));
        assertTrue(Math.abs(upper.y - upperRest.y) < SHUT_TOLERANCE, "shut jaws sit at rest: " + upper.y + " against " + upperRest.y);
    }

    @Test
    void anEyeTiltsItsPupilUpTowardWhatStandsAboveIt() throws IOException {
        GeoModelBaker.Baked eye = bake("eye");
        EldritchConstructRenderer.State state = new EldritchConstructRenderer.State();
        state.model = "eye";
        state.age = 30.0F;
        state.life = 200;
        state.gazePitch = 0.6F;
        EldritchConstructRenderer.eye(state, eye, 30.0F);
        Vector3f front = direction(eye.part("body"), new Vector3f(0.0F, 0.0F, -1.0F));
        assertTrue(front.y < -0.3F, "the pupil side must tilt up, which is a negative y in model space: " + front.y);
    }

    private static EldritchConstructRenderer.State mawState(float age, int windup, int snap) {
        EldritchConstructRenderer.State state = new EldritchConstructRenderer.State();
        state.model = "maw";
        state.age = age;
        state.life = 60;
        CompoundTag data = new CompoundTag();
        data.putInt("windup", windup);
        data.putInt("snap", snap);
        state.data = data;
        return state;
    }

    /** The far tip of a jaw, 24 units ahead of its hinge, in model space. */
    private static Vector3f tip(ModelPart jaw) {
        return point(jaw, new Vector3f(0.0F, 0.0F, -24.0F));
    }

    private static Vector3f point(ModelPart part, Vector3f local) {
        PoseStack pose = new PoseStack();
        part.translateAndRotate(pose);
        return pose.last().pose().transformPosition(new Vector3f(local));
    }

    private static Vector3f direction(ModelPart part, Vector3f local) {
        return point(part, local).sub(point(part, new Vector3f()));
    }

    private static GeoModelBaker.Baked bake(String name) throws IOException {
        try (InputStream in = EldritchPostureTest.class.getResourceAsStream(MODELS + name + ".geo.json")) {
            assertNotNull(in, name + " is not shipped");
            return GeoModelBaker.bake(GeoModelParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }
}
