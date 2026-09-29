package com.efkrdnz.magical.gametest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Fake players outlive their tests and stand in the neighbouring structures, as hostile as any
 * player, so every helper that makes one first removes the leftovers whose names end in
 * {@code -test}. A fake player named otherwise is never removed by anybody else's helper: the
 * sword rack's {@code rack-axe} stood ten blocks from the Eldritch call test, which grasps a
 * random hostile in reach, and failed it two runs in three the day the batch order moved it there.
 */
class GameTestPlayerNamesTest {

    /** A name handed to a fake-player helper or straight to a game profile. */
    private static final Pattern NAME = Pattern.compile(
            "(?:(?:wielder|survival)\\(helper,[^;]*?|GameProfile\\(UUID\\.randomUUID\\(\\), )\"([a-z][a-z0-9-]*)\"");

    @Test
    void everyFakePlayerIsNamedSoTheNextTestCanRemoveIt() throws IOException {
        List<String> names = new ArrayList<>();
        List<String> strays = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot())) {
            for (Path file : files.filter(p -> p.toString().endsWith("GameTests.java")).toList()) {
                Matcher matcher = NAME.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                    if (!matcher.group(1).endsWith("-test")) {
                        strays.add(file.getFileName() + ": " + matcher.group(1));
                    }
                }
            }
        }
        assertTrue(names.size() >= 20, "the scan found only " + names + ", so it is not reading the helpers");
        assertTrue(strays.isEmpty(), "fake players no other test will ever remove: " + strays);
    }

    /** The source tree, found by walking up from wherever Gradle runs the tests. */
    private static Path sourceRoot() {
        Path at = Path.of("").toAbsolutePath();
        for (int up = 0; at != null && up < 6; up++, at = at.getParent()) {
            Path source = at.resolve("src/main/java");
            if (Files.isDirectory(source)) {
                return source;
            }
        }
        throw new AssertionError("could not find src/main/java above " + Path.of("").toAbsolutePath());
    }
}
