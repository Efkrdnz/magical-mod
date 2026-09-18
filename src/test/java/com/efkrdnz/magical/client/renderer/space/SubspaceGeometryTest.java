package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shell meshes, held to the one contract the fragment shader depends on.
 *
 * <p>The shader has no normals: the vertex format is position, colour, UV0 and two packed
 * integers, and there is nowhere to put one. So it rebuilds the shell normal from UV0 - bearing
 * across, height up - and every Fresnel term in the boundary follows from that reconstruction
 * being exact. {@code FxMesh.sphere} cannot serve, because it writes a <em>mirrored</em> u that
 * runs 0 to 1 and back again so a texture will not seam; that is the right choice for a texture
 * and the wrong one for a bearing, since two opposite points on the dome would claim the same
 * one.
 */
class SubspaceGeometryTest {

    private static final int STRIDE = FxMesh.FACE_STRIDE / 4;
    private static final double ON_SPHERE = 1.0E-5D;
    private static final double REBUILD = 1.0E-4D;

    @Test
    @DisplayName("a globe is exactly one quad per segment per ring")
    void theGlobeCostsWhatItSays() {
        for (int rung = 0; rung < SubspaceLod.RUNGS; rung++) {
            int segments = SubspaceLod.segments(rung);
            int rings = SubspaceLod.rings(rung);
            assertEquals(segments * rings, quads(FxMesh.globe(segments, rings)));
            assertEquals(segments * rings, quads(FxMesh.globeInverted(segments, rings)));
        }
    }

    @Test
    @DisplayName("every vertex of the shell is on the unit sphere")
    void theShellIsASphere() {
        for (float[] mesh : new float[][] {
                FxMesh.globe(32, 16),
                FxMesh.globeInverted(32, 16),
                FxMesh.shellBand(24, 0.0F, 0.6F, 360.0F),
                FxMesh.shellBand(8, -6.0F, 1.4F, 6.0F),
                FxMesh.shellMeridian(12, 0.5F, 0.0F, 76.0F)}) {
            for (int i = 0; i < mesh.length; i += STRIDE) {
                double length = Math.sqrt(mesh[i] * mesh[i] + mesh[i + 1] * mesh[i + 1] + mesh[i + 2] * mesh[i + 2]);
                assertEquals(1.0D, length, ON_SPHERE, "a vertex left the sphere");
            }
        }
    }

    /**
     * The contract. If this drifts the whole boundary goes wrong at once and in a way that looks
     * like a shader bug: the Fresnel term reads a normal that does not belong to the pixel, so the
     * wall thickens in bands rather than toward its limb.
     */
    @Test
    @DisplayName("the shader can rebuild every vertex from its uv alone")
    void uvCarriesTheShellNormal() {
        float[] mesh = FxMesh.globe(48, 24);
        for (int i = 0; i < mesh.length; i += STRIDE) {
            double phi = mesh[i + 3] * 2.0D * Math.PI;
            double ny = mesh[i + 4] * 2.0D - 1.0D;
            double s = Math.sqrt(Math.max(0.0D, 1.0D - ny * ny));
            assertEquals(Math.sin(phi) * s, mesh[i], REBUILD, "x drifted from its bearing");
            assertEquals(ny, mesh[i + 1], REBUILD, "y drifted from its height");
            assertEquals(-Math.cos(phi) * s, mesh[i + 2], REBUILD, "z drifted from its bearing");
        }
    }

    @Test
    @DisplayName("bearing zero is due north and bearing a quarter is due east")
    void theShellIsOrientedToTheCompass() {
        float[] mesh = FxMesh.globe(32, 16);
        double[] north = vertexNearest(mesh, 0.0D, 0.5D);
        assertTrue(north[2] < -0.99D, "bearing 0 was not north: z=" + north[2]);
        double[] east = vertexNearest(mesh, 0.25D, 0.5D);
        assertTrue(east[0] > 0.99D, "bearing a quarter was not east: x=" + east[0]);
    }

    @Test
    @DisplayName("the two globes are one shell wound opposite ways")
    void oneShellTwoWindings() {
        float[] out = FxMesh.globe(32, 16);
        float[] in = FxMesh.globeInverted(32, 16);
        assertEquals(out.length, in.length);
        for (int q = 0; q < quads(out); q++) {
            assertTrue(facing(out, q) > 0.0D, "quad " + q + " of the globe faced inward");
            assertTrue(facing(in, q) < 0.0D, "quad " + q + " of the inverted globe faced outward");
            double[] a = centroid(out, q);
            double[] b = centroid(in, q);
            for (int c = 0; c < 3; c++) {
                assertEquals(a[c], b[c], ON_SPHERE, "the two globes covered different ground");
            }
        }
    }

    /**
     * The {@code boulder()} scar: every builder memoises into one HashMap, and a mapping function
     * that reaches back into that same map throws a ConcurrentModificationException on Java 9 and
     * up - on the render thread, the first frame anything uses it.
     */
    @Test
    @DisplayName("an inverted globe built cold does not reach into its own cache")
    void noNestedCacheLookup() {
        assertDoesNotThrow(() -> FxMesh.globeInverted(20, 10));
        assertNotSame(FxMesh.globe(20, 10), FxMesh.globeInverted(20, 10));
    }

    @Test
    @DisplayName("a band lies on its own latitude and is as thick as it was asked to be")
    void aBandKeepsItsLatitude() {
        float latitude = -6.0F;
        float half = 1.4F;
        float[] mesh = FxMesh.shellBand(16, latitude, half, 48.0F);
        double lowest = 90.0D;
        double highest = -90.0D;
        for (int i = 0; i < mesh.length; i += STRIDE) {
            double degrees = Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, mesh[i + 1]))));
            lowest = Math.min(lowest, degrees);
            highest = Math.max(highest, degrees);
        }
        assertEquals(latitude - half, lowest, 1.0E-3D);
        assertEquals(latitude + half, highest, 1.0E-3D);
    }

    @Test
    @DisplayName("a band across a quarter turn covers a quarter turn, centred on north")
    void aBandKeepsItsSweep() {
        float[] mesh = FxMesh.shellBand(12, 0.0F, 0.5F, 90.0F);
        double widest = 0.0D;
        for (int i = 0; i < mesh.length; i += STRIDE) {
            widest = Math.max(widest, Math.abs(Math.toDegrees(Math.atan2(mesh[i], -mesh[i + 2]))));
        }
        assertEquals(45.0D, widest, 1.0E-3D);
    }

    @Test
    @DisplayName("a band sweeping the whole circle closes on itself")
    void aFullBandHasNoSeam() {
        float[] mesh = FxMesh.shellBand(24, 0.0F, 0.6F, 360.0F);
        assertEquals(24, quads(mesh));
        double[] first = centroid(mesh, 0);
        double[] last = centroid(mesh, 23);
        double gap = Math.hypot(first[0] - last[0], first[2] - last[2]);
        assertTrue(gap < 0.30D, "the ends of a closed band were " + gap + " apart");
    }

    @Test
    @DisplayName("the meridian stands due north and runs from the horizon to the crown")
    void theMeridianStandsNorth() {
        float[] mesh = FxMesh.shellMeridian(12, 0.5F, 0.0F, 76.0F);
        assertEquals(12, quads(mesh));
        double lowest = 90.0D;
        double highest = -90.0D;
        for (int i = 0; i < mesh.length; i += STRIDE) {
            double bearing = Math.toDegrees(Math.atan2(mesh[i], -mesh[i + 2]));
            assertTrue(Math.abs(bearing) <= 0.5D + 1.0E-3D, "the meridian leant off north: " + bearing);
            double degrees = Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, mesh[i + 1]))));
            lowest = Math.min(lowest, degrees);
            highest = Math.max(highest, degrees);
        }
        assertEquals(0.0D, lowest, 1.0E-3D);
        assertEquals(76.0D, highest, 1.0E-3D);
    }

    @Test
    @DisplayName("a stroke carries its own across-coordinate, centred on its line")
    void strokesAreParameterisedAcross() {
        for (float[] mesh : new float[][] {
                FxMesh.shellBand(16, -6.0F, 1.4F, 48.0F),
                FxMesh.shellMeridian(12, 0.5F, 0.0F, 76.0F)}) {
            double lowest = 1.0D;
            double highest = 0.0D;
            for (int i = 0; i < mesh.length; i += STRIDE) {
                lowest = Math.min(lowest, mesh[i + 4]);
                highest = Math.max(highest, mesh[i + 4]);
            }
            assertEquals(0.0D, lowest, 1.0E-6D, "a stroke did not start at its own edge");
            assertEquals(1.0D, highest, 1.0E-6D, "a stroke did not reach its own edge");
        }
    }

    @Test
    @DisplayName("a mesh is built once and handed back by identity")
    void meshesAreCached() {
        assertSame(FxMesh.globe(32, 16), FxMesh.globe(32, 16));
        assertSame(FxMesh.globeInverted(32, 16), FxMesh.globeInverted(32, 16));
        assertSame(FxMesh.shellBand(16, -6.0F, 1.4F, 48.0F), FxMesh.shellBand(16, -6.0F, 1.4F, 48.0F));
        assertSame(FxMesh.shellMeridian(12, 0.5F, 0.0F, 76.0F), FxMesh.shellMeridian(12, 0.5F, 0.0F, 76.0F));
    }

    /** The budget class and the meshes must agree, or the budget is a number about nothing. */
    @Test
    @DisplayName("what the budget promises is what the meshes actually cost")
    void theBudgetIsTheMesh() {
        for (int rung = 0; rung < SubspaceLod.RUNGS; rung++) {
            int segments = SubspaceLod.segments(rung);
            int rings = SubspaceLod.rings(rung);
            assertEquals(SubspaceLod.membraneQuads(rung, true), quads(FxMesh.globeInverted(segments, rings)));

            int far = Math.min(SubspaceLod.RUNGS - 1, rung + 2);
            int outside = quads(FxMesh.globe(segments, rings))
                    + quads(FxMesh.globeInverted(SubspaceLod.segments(far), SubspaceLod.rings(far)));
            assertEquals(SubspaceLod.membraneQuads(rung, false), outside);
        }
    }

    private static int quads(float[] mesh) {
        return mesh.length / (STRIDE * 4);
    }

    private static double[] centroid(float[] mesh, int quad) {
        double[] out = new double[3];
        for (int v = 0; v < 4; v++) {
            int o = (quad * 4 + v) * STRIDE;
            for (int c = 0; c < 3; c++) {
                out[c] += mesh[o + c] / 4.0D;
            }
        }
        return out;
    }

    /**
     * Positive when the quad's winding points away from the sphere's centre.
     *
     * <p>By Newell's formula rather than from one corner's two edges, because the ring of quads
     * against each pole has two corners in the same place: one of its edges is zero, and a normal
     * taken there is zero whichever way the quad is wound. Newell sums every edge in turn, so a
     * collapsed corner simply contributes nothing.
     */
    private static double facing(float[] mesh, int quad) {
        double[] n = new double[3];
        for (int v = 0; v < 4; v++) {
            int a = (quad * 4 + v) * STRIDE;
            int b = (quad * 4 + (v + 1) % 4) * STRIDE;
            n[0] += (mesh[a + 1] - mesh[b + 1]) * (mesh[a + 2] + mesh[b + 2]);
            n[1] += (mesh[a + 2] - mesh[b + 2]) * (mesh[a] + mesh[b]);
            n[2] += (mesh[a] - mesh[b]) * (mesh[a + 1] + mesh[b + 1]);
        }
        double[] c = centroid(mesh, quad);
        return n[0] * c[0] + n[1] * c[1] + n[2] * c[2];
    }

    private static double[] vertexNearest(float[] mesh, double u, double v) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < mesh.length; i += STRIDE) {
            double distance = Math.hypot(mesh[i + 3] - u, mesh[i + 4] - v);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return new double[] {mesh[best], mesh[best + 1], mesh[best + 2]};
    }
}
