package com.efkrdnz.magical.magic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A figment is no body to anything outside a mind, and the only thing that makes that true of a
 * sweep is that the sweep goes through {@link Bodies}. A sweep that goes round it compiles, runs and
 * gathers the figment along with everything else - and nothing fails, because the spell still lands
 * on the husk beside it. So this reads the source: outside the Mind's own packages, no untyped
 * {@code getEntities} and no {@code getEntitiesOfClass} of a class a figment belongs to. A sweep of
 * one of the mod's own entity types, or of projectiles or players, can never meet a figment and is
 * left alone.
 */
class BodiesSweepTest {

    private static final String SOURCE_PACKAGE = "src/main/java/com/efkrdnz/magical";

    private static final int MAX_DEPTH_TO_PROJECT_ROOT = 6;

    /** The Mind sees figments on purpose; everything else must not. */
    private static final List<String> MIND = List.of("magic/mind/", "entity/mind/");

    /** Every class a figment is an instance of that a sweep could name. */
    private static final Set<String> FIGMENT_SUPERTYPES = Set.of(
            "Entity", "LivingEntity", "Mob", "PathfinderMob");

    private static final Pattern SWEEP = Pattern.compile("\\.(getEntitiesOfClass|getEntities)\\(\\s*([^,)]*)");

    private static final Pattern TYPED = Pattern.compile("(MagicalEntities\\.|EntityType\\.).*");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void everySweepForBodiesGoesThroughBodies() throws IOException {
        Path root = sourceRoot();
        List<String> raw = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (MIND.stream().anyMatch(relative::startsWith) || relative.endsWith("GameTests.java")
                        || relative.equals("magic/service/Bodies.java")) {
                    continue;
                }
                scanned++;
                for (String sweep : rawSweeps(code(file))) {
                    raw.add(relative + ": " + sweep);
                }
            }
        }
        assertTrue(scanned > 100, "the scan found only " + scanned + " files; it is not reading the source");
        assertTrue(raw.isEmpty(), "these sweeps can gather a figment; route them through Bodies:\n  "
                + String.join("\n  ", raw));
    }

    /** The scan cannot pass by reading nothing: a raw sweep of each shape is caught, a typed one is not. */
    @Test
    void theScanCatchesARawSweepAndPassesATypedOne() {
        assertEquals(1, rawSweeps("for (Entity e : level().getEntities(this, area, e -> true)) {}").size());
        assertEquals(1, rawSweeps("level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)").size());
        assertEquals(1, rawSweeps("level.getEntitiesOfClass(Mob.class,\n box)").size());
        assertEquals(1, rawSweeps("caster.level().getEntities(caster, box)").size());
        assertEquals(1, rawSweeps("level.getEntities(EntityTypeTest.forClass(LivingEntity.class), box, e -> true)").size());
        assertEquals(0, rawSweeps("level.getEntitiesOfClass(Projectile.class, box)").size());
        assertEquals(0, rawSweeps("level.getEntitiesOfClass(ServerPlayer.class, box)").size());
        assertEquals(0, rawSweeps("level.getEntities(MagicalEntities.SPELL_EFFECT.get(), box, e -> true)").size());
        assertEquals(0, rawSweeps("Bodies.around(level(), this, area, e -> true)").size());
    }

    static List<String> rawSweeps(String code) {
        List<String> raw = new ArrayList<>();
        Matcher sweep = SWEEP.matcher(code);
        while (sweep.find()) {
            String first = sweep.group(2).strip();
            boolean broad = sweep.group(1).equals("getEntitiesOfClass")
                    ? FIGMENT_SUPERTYPES.contains(first.replaceAll("\\.class$", "").replaceAll(".*\\.", ""))
                    : !TYPED.matcher(first).matches();
            if (broad) {
                raw.add(sweep.group().replaceAll("\\s+", " "));
            }
        }
        return raw;
    }

    private static String code(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll("")).replaceAll("");
    }

    private static Path sourceRoot() {
        Path at = Path.of("").toAbsolutePath();
        for (int up = 0; at != null && up < MAX_DEPTH_TO_PROJECT_ROOT; up++, at = at.getParent()) {
            Path source = at.resolve(SOURCE_PACKAGE);
            if (Files.isDirectory(source)) {
                return source;
            }
        }
        throw new AssertionError("could not find " + SOURCE_PACKAGE + " above " + Path.of("").toAbsolutePath());
    }
}
