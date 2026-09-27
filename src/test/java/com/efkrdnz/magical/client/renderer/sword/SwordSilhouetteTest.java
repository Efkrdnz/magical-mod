package com.efkrdnz.magical.client.renderer.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.sword.SwordBladeRenderer.Geometry;
import com.efkrdnz.magical.magic.sword.SwordRules;
import com.efkrdnz.magical.magic.sword.stance.Formation;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What a summoned blade is, now that it is a sword and not a shape invented in a shader.
 *
 * <p>It used to be {@code prism(4)} - a 0.15 by 1.6 diamond needle - and on screen that is a flat
 * dark domino with one blown highlight on it: no point, no taper, no crossguard, nothing that says
 * sword. So the steel is now the Duskfall model, the one true 3D weapon this mod already ships,
 * drawn through {@code ItemRenderer.renderStatic} the way {@code WrenchedItemRenderer} draws its
 * item. That swap moves where every claim lives, so this file moves with it.
 *
 * <p>Three kinds of claim, and each is a <b>floor</b> rather than a subtlety ceiling, because a
 * green suite in this repo once certified a wall nobody could see.
 *
 * <p><b>It is the model, and the model resolves.</b> A Blockbench export copied out of a standalone
 * pack keeps that pack's namespace on its textures, and a reference into a namespace this mod does
 * not ship loads perfectly and draws the missing texture - the exact bug that bit Duskfall on
 * import and the reason {@code WeaponModelAssetsTest} exists. The blade renderer now depends on
 * that one model by name, so it asks the question again for that one model.
 *
 * <p><b>The drawing is measured from the model, not guessed.</b> Every number the pose is built
 * from is a bound read off {@code duskfall.json}, so the test reads the same file and re-derives
 * them. A re-export that lengthens the blade by a third would otherwise silently push the drawn
 * steel outside the corridor the raycast catches in.
 *
 * <p><b>The picture never outgrows the hit.</b> The drawn steel stays inside what the sweep reaches
 * sideways, stays shorter than one step of its own raycast, and never reaches back past the frame
 * origin from the nearest station a wielder can set. There is no glow and no glint round it any
 * more - the steel is the whole of the drawing, so the steel is the whole of the measurement.
 *
 * <p>Pure arithmetic and one resource read: {@code SwordBladeRenderer$Geometry} is a nested class
 * precisely so that measuring a silhouette never drags {@code EntityRenderer} into a unit test.
 */
class SwordSilhouetteTest {

    /** The render origin of a vanilla item model: the middle of the sixteen-unit cube. */
    private static final double MODEL_ORIGIN = 8.0D;

    /** Re-measuring a rotated export never lands on the authored decimal exactly. */
    private static final double MEASURED = 1.0E-6D;

    @Test
    void theBladeIsTheSwordThisModAlreadyShips() {
        // The user's ruling, as an assertion: the steel is Duskfall, not geometry made up here.
        // Both halves of a 1.21.4 item have to be present - the item model definition that maps
        // the item id onto a model, and the model itself.
        assertEquals("magical:item/duskfall", Geometry.MODEL,
                "the blade is drawn from some other model than the one this mod ships");
        JsonObject definition = readJson("/assets/magical/items/duskfall.json");
        assertEquals(Geometry.MODEL,
                definition.getAsJsonObject("model").get("model").getAsString(),
                "the duskfall item no longer resolves to the model the blade renderer draws");

        JsonObject model = readJson("/assets/magical/models/item/duskfall.json");
        JsonObject textures = model.getAsJsonObject("textures");
        assertNotNull(textures, "the duskfall model names no textures at all");
        for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
            String reference = entry.getValue().getAsString();
            assertTrue(reference.startsWith("magical:"),
                    entry.getKey() + " = " + reference + " is a namespace this mod does not ship, so"
                            + " the model loads perfectly and every blade in the Array is magenta");
            String png = "/assets/magical/textures/" + reference.substring("magical:".length()) + ".png";
            assertNotNull(SwordSilhouetteTest.class.getResource(png), "no file at " + png);
        }
    }

    @Test
    void theDrawingIsMeasuredFromTheModelAndNotGuessed() {
        // Every bound the pose is built from, re-derived from the file. Element rotations count:
        // 130 of 130 cuboids carry one, and a rotated cuboid reaches past its own from/to box.
        double[] bounds = measureModel();
        assertEquals(bounds[0], Geometry.MODEL_HALF_X, MEASURED,
                "the blade is not as broad as the model says");
        assertEquals(bounds[1], Geometry.MODEL_MIN_Y, MEASURED,
                "the pommel is not where the model says");
        assertEquals(bounds[2], Geometry.MODEL_MAX_Y, MEASURED,
                "the point is not where the model says");
        assertEquals(bounds[3], Geometry.MODEL_HALF_Z, MEASURED,
                "the blade is not as thick as the model says");
        assertEquals((int) bounds[4] * 6, Geometry.MODEL_QUADS,
                "the quad count the renderer reports to the frame budget is not the model's");
    }

    @Test
    void theWholeArrayCostsWhatTheRendererClaimsItCosts() {
        // A Sword God fields twelve swords and nothing draws a thirteenth, so the worst frame is
        // twelve of these. It is a real number and the point of stating it is that it goes
        // through FxBudget: unaccounted quads report to everything else as headroom.
        assertEquals(SwordRules.GOD.swords(), Geometry.MAX_BLADES,
                "the formation can stand a number of blades the cost claim was not written for");
        assertEquals(Geometry.MODEL_QUADS * Geometry.MAX_BLADES, Geometry.WORST_CASE_QUADS,
                "the stated worst case is no longer the worst case");
    }

    @Test
    void aBladeIsDrawnNoWiderThanTheCorridorItCatchesIn() {
        // An arc measured from the centre of its own circle put every one of the wave's pixels a
        // radius off the strike; a sword canted or scaled too hard does the same thing. The steel
        // has to sit inside what the sweep reaches sideways.
        double reach = Geometry.lateralReach();
        assertTrue(reach <= Geometry.CATCH_HALF_EXTENT,
                "the blade is drawn " + reach + " blocks off its flight line but only catches within "
                        + Geometry.CATCH_HALF_EXTENT);
        assertTrue(Geometry.SCALE <= Geometry.scaleCeiling(),
                "a scale of " + Geometry.SCALE + " reaches past the " + Geometry.scaleCeiling()
                        + " the hit corridor allows");
    }

    @Test
    void aBladeIsDrawnNoLongerThanOneStepOfItsOwnRaycastAndNeverBackPastTheFrame() {
        // A blade flies by explicit raycast, one step a tick. Drawn longer than a step, the steel
        // arrives somewhere the hit test has not been yet, which reads as a miss on contact.
        double along = Geometry.axialReach();
        assertTrue(along <= Geometry.FLIGHT_PER_TICK,
                "the blade is drawn " + along + " blocks along its flight and steps only "
                        + Geometry.FLIGHT_PER_TICK + " a tick");
        // And it is drawn about its own middle, so half of it hangs back toward the wielder. The
        // nearest a stance ever puts a sword is Formation.BODY_CLEARANCE, and that half must not
        // reach past it - otherwise the closest sword of a formation is drawn inside the person
        // carrying it. Formation's own test holds every stance to that clearance; this holds the
        // drawing to it, and the two together are what say the steel is outside the body.
        assertTrue(along <= Formation.BODY_CLEARANCE,
                "a blade at the " + Formation.BODY_CLEARANCE + " clearance reaches " + along
                        + " blocks back, which is into the wielder");
        assertTrue(Geometry.LENGTH <= Formation.MAX_EXTENT,
                "one blade is longer than the whole formation is wide");
    }

    @Test
    void aBladeIsStillBigEnoughToReadAsASword() {
        // The floor, and the reason this file was rewritten: the thing that was here before was
        // 0.15 blocks across and read as a domino. A sword has to be long enough to see across a
        // room and broad enough to have a shape, and the only way to lose both is to quietly wind
        // SCALE down until every test above passes with room to spare.
        assertTrue(Geometry.LENGTH >= 1.0F,
                "a blade drawn " + Geometry.LENGTH + " blocks long is a dagger");
        assertTrue(Geometry.HALF_BREADTH * 2.0F >= Geometry.LENGTH / 6.0F,
                "the blade is " + (Geometry.LENGTH / (Geometry.HALF_BREADTH * 2.0F))
                        + " times longer than it is broad, which is a needle again");
        assertTrue(Geometry.MODEL_QUADS > 0, "the blade is drawn from nothing");
    }

    @Test
    void aBladeIsNeverFlownNoseOn() {
        // The thrower's eye IS the flight line, and a long thin thing seen down its own axis
        // projects to its cross-section. The cant survives the model swap unchanged: it is the
        // genre as much as the fix - flying swords travel canted and broadside, never nose-on like
        // arrows - and it is a rotation in the flight frame, so it is the same angle at every
        // bearing and every pitch, including straight up and down where orientAlong's yaw is
        // undefined and the composition has to survive it anyway.
        for (float yaw = 0.0f; yaw < 360.0f; yaw += 17.0f) {
            for (float pitch = -90.0f; pitch <= 90.0f; pitch += 15.0f) {
                double[] flight = look(yaw, pitch);
                double cant = Geometry.cantDegrees(flight[0], flight[1], flight[2]);
                assertTrue(cant >= Geometry.MIN_CANT_DEGREES,
                        "a blade flown at yaw " + yaw + " pitch " + pitch + " leans only " + cant
                                + " degrees off its own flight line, which is a line to the thrower");
                assertEquals(Geometry.CANT_YAW, cant, 1.0E-6D,
                        "the cant is not the same at yaw " + yaw + " pitch " + pitch);
            }
        }
    }

    @Test
    void theCantIsBoundedFromBothSides() {
        // Both bounds are real and they pull opposite ways: under the floor the thrower is looking
        // down a line, over the ceiling the drawn steel hangs outside the volume that catches. The
        // ceiling is a function of the model's own box now, so state it as the angle.
        assertTrue(Geometry.CANT_YAW > Geometry.MIN_CANT_DEGREES,
                "the cant is at or under its own floor");
        assertTrue(Geometry.CANT_YAW < Geometry.cantCeilingDegrees(),
                "a cant of " + Geometry.CANT_YAW + " degrees reaches past the "
                        + Geometry.cantCeilingDegrees() + " degrees the hit corridor allows");
    }

    @Test
    void theBladeIsOrientedOntoTheFlightVectorAndNotBesideIt() {
        // orientAlong's job, restated: local +Z lands exactly on the flight vector. The cant is
        // measured against that, so a sign error here would move every blade in the kit at once
        // and leave the cant test perfectly green.
        for (float yaw = 0.0f; yaw < 360.0f; yaw += 23.0f) {
            for (float pitch = -90.0f; pitch <= 90.0f; pitch += 18.0f) {
                double[] flight = look(yaw, pitch);
                double[] forward = Geometry.orient(0.0D, 0.0D, 1.0D, flight[0], flight[1], flight[2]);
                assertEquals(flight[0], forward[0], 1.0E-9D, "x at yaw " + yaw + " pitch " + pitch);
                assertEquals(flight[1], forward[1], 1.0E-9D, "y at yaw " + yaw + " pitch " + pitch);
                assertEquals(flight[2], forward[2], 1.0E-9D, "z at yaw " + yaw + " pitch " + pitch);
                double[] axis = Geometry.bladeAxis(flight[0], flight[1], flight[2]);
                assertEquals(1.0D, Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]),
                        1.0E-9D, "the blade's axis is not a unit vector at yaw " + yaw);
            }
        }
    }

    @Test
    void theSteelIsNeverDrawnBiggerThanTheScaleEveryBoundWasMeasuredAt() {
        // The dissolve closes the sword on nothing rather than popping it out at full size, which
        // is what the crack shader used to do for us. The floor that matters is the other end: the
        // shrink may only ever make the drawing smaller than the bound the corridor test measured.
        assertEquals(Geometry.SCALE, Geometry.drawnScale(0.0F), 1.0E-6F,
                "a whole blade is not drawn at the scale every bound in this file assumes");
        assertEquals(0.0F, Geometry.drawnScale(1.0F), 1.0E-6F, "a blade out of time never goes");
        float previous = Geometry.SCALE + 1.0F;
        for (int step = 0; step <= 20; step++) {
            float drawn = Geometry.drawnScale(step / 20.0F);
            assertTrue(drawn <= Geometry.SCALE,
                    "the dissolve draws the steel bigger than " + Geometry.SCALE + " at integrity "
                            + (step / 20.0F) + ", which is outside every bound measured here");
            assertTrue(drawn <= previous, "the steel grows back at integrity " + (step / 20.0F));
            previous = drawn;
        }
    }

    /**
     * The model's true bounds relative to the render origin, plus its element count:
     * {@code {halfX, minY, maxY, halfZ, elements}}, in model units.
     *
     * <p>Every cuboid's eight corners, with its own rotation applied, because a rotated cuboid
     * reaches outside the {@code from}/{@code to} box it is written as and all 130 of these carry
     * a rotation. Vanilla's own element rotation, which is right-handed about one axis through an
     * origin, and never rescaled here.
     */
    private static double[] measureModel() {
        JsonArray elements = readJson("/assets/magical/models/item/duskfall.json")
                .getAsJsonArray("elements");
        double halfX = 0.0D;
        double halfZ = 0.0D;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (JsonElement raw : elements) {
            JsonObject element = raw.getAsJsonObject();
            double[] from = triple(element.getAsJsonArray("from"));
            double[] to = triple(element.getAsJsonArray("to"));
            JsonObject rotation = element.getAsJsonObject("rotation");
            for (int corner = 0; corner < 8; corner++) {
                double[] point = {
                        (corner & 1) == 0 ? from[0] : to[0],
                        (corner & 2) == 0 ? from[1] : to[1],
                        (corner & 4) == 0 ? from[2] : to[2]};
                if (rotation != null) {
                    point = turn(point, rotation);
                }
                halfX = Math.max(halfX, Math.abs(point[0] - MODEL_ORIGIN));
                halfZ = Math.max(halfZ, Math.abs(point[2] - MODEL_ORIGIN));
                minY = Math.min(minY, point[1] - MODEL_ORIGIN);
                maxY = Math.max(maxY, point[1] - MODEL_ORIGIN);
            }
        }
        return new double[] {halfX, minY, maxY, halfZ, elements.size()};
    }

    private static double[] turn(double[] point, JsonObject rotation) {
        double angle = Math.toRadians(rotation.get("angle").getAsDouble());
        double[] origin = triple(rotation.getAsJsonArray("origin"));
        double x = point[0] - origin[0];
        double y = point[1] - origin[1];
        double z = point[2] - origin[2];
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        switch (rotation.get("axis").getAsString()) {
            case "x" -> {
                double ny = y * c - z * s;
                z = y * s + z * c;
                y = ny;
            }
            case "y" -> {
                double nx = x * c + z * s;
                z = -x * s + z * c;
                x = nx;
            }
            default -> {
                double nx = x * c - y * s;
                y = x * s + y * c;
                x = nx;
            }
        }
        return new double[] {x + origin[0], y + origin[1], z + origin[2]};
    }

    private static double[] triple(JsonArray array) {
        return new double[] {
                array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static JsonObject readJson(String resource) {
        try (InputStream in = SwordSilhouetteTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not shipped");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + resource, e);
        }
    }

    /** Minecraft's own look vector, so the test asks the question in the game's handedness. */
    private static double[] look(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }
}
