package com.efkrdnz.magical.forge.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The catalogue is meant to grow a row at a time, and every row owes the game files nobody
 * generates. There is no datagen in this repo, so a weapon missing an asset or a lang key is not a
 * build failure - it is a purple-and-black item called {@code item.magical.whatever} that nobody
 * notices until they open the creative tab. These are the checks that make adding a row fail loudly
 * instead.
 */
class MagicalWeaponsTest {

    private static final String LANG_PATH = "/assets/magical/lang/en_us.json";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theCatalogueIsNotEmptyAndEveryIdIsItsOwn() {
        assertTrue(!MagicalWeapons.all().isEmpty(), "the catalogue holds no weapons");
        assertEquals(MagicalWeapons.all().size(), MagicalWeapons.orderedIds().size(),
                "two weapons share an id, so one silently replaced the other in the map");
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            assertEquals(weapon, MagicalWeapons.get(weapon.id()).orElse(null),
                    weapon.id() + " does not look itself up");
        }
    }

    /**
     * A signature naming a glyph that does not exist is exclusivity over nothing: the weapon claims
     * a shape no one can ever draw, and the forge never says so.
     */
    @Test
    void everySignatureNamesAGlyphThatExists() {
        Set<String> glyphs = new HashSet<>();
        for (GlyphTemplate template : ForgeGlyphLibrary.all()) {
            glyphs.add(template.id());
        }
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            for (String signature : weapon.signatureGlyphs()) {
                assertTrue(glyphs.contains(signature),
                        weapon.id() + " claims the glyph " + signature + ", which is not in the library");
            }
        }
    }

    /** Every glyph some weapon claims is reachable from that weapon, and from no other. */
    @Test
    void ownershipIsTheExactInverseOfTheSignatureColumn() {
        for (String glyph : MagicalWeapons.allSignatureGlyphs()) {
            Set<WeaponDefinition> claiming = new HashSet<>();
            for (WeaponDefinition weapon : MagicalWeapons.all()) {
                if (weapon.signatureGlyphs().contains(glyph)) {
                    claiming.add(weapon);
                }
            }
            assertEquals(claiming.size(), MagicalWeapons.owners(glyph).size(),
                    "owners(" + glyph + ") disagrees with the catalogue rows that claim it");
            for (WeaponDefinition weapon : claiming) {
                assertTrue(MagicalWeapons.owners(glyph).contains(weapon.id()),
                        "owners(" + glyph + ") leaves out " + weapon.id());
            }
        }
        assertTrue(MagicalWeapons.owners("slash").isEmpty(),
                "a universal glyph reads as owned, which would lock it to one weapon");
    }

    /**
     * The tooltip shows {@code attack}, so {@code attackModifier} has to be that number less what
     * vanilla adds back. Getting this backwards is invisible in code and obvious in game.
     */
    @Test
    void theDeclaredAttackIsWhatTheTooltipWillShow() {
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            float shown = 1.0f + weapon.toolMaterial().attackDamageBonus() + weapon.attackModifier();
            assertEquals(weapon.attack(), shown, 0.001f, weapon.id() + " would display " + shown);
            float speed = 4.0f + weapon.attackSpeedModifier();
            assertEquals(weapon.attackSpeed(), speed, 0.001f, weapon.id() + " would swing at " + speed);
            assertTrue(weapon.attackModifier() > 0f,
                    weapon.id() + " is weaker than a bare fist once vanilla's own bonus is taken off");
        }
    }

    /** Every archetype the catalogue uses carries real numbers rather than an unfilled default. */
    @Test
    void everyArchetypeInUseHasStats() {
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            WeaponClass archetype = weapon.archetype();
            assertTrue(archetype.knockbackScale() > 0f, archetype + " has no knockback at all");
            assertNotNull(archetype.nameKey());
        }
    }

    @Test
    void everyWeaponIsNamedAndDescribedInTheLanguageFile() throws IOException {
        String lang = readLang();
        List<String> missing = new ArrayList<>();
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            if (!lang.contains('"' + weapon.nameKey() + '"')) {
                missing.add(weapon.nameKey());
            }
            if (!lang.contains('"' + weapon.descKey() + '"')) {
                missing.add(weapon.descKey());
            }
        }
        for (WeaponClass archetype : WeaponClass.values()) {
            if (!lang.contains('"' + archetype.nameKey() + '"')) {
                missing.add(archetype.nameKey());
            }
        }
        assertTrue(missing.isEmpty(), "language file is missing: " + missing);
    }

    /**
     * The three hand-written files a weapon needs to render. Checked by presence on the classpath
     * rather than by parsing, because the failure this guards against is a forgotten file or a
     * mistyped name, not malformed JSON.
     */
    @Test
    void everyWeaponHasItsThreeAssetFiles() {
        List<String> missing = new ArrayList<>();
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            for (String path : List.of(
                    "/assets/magical/items/" + weapon.path() + ".json",
                    "/assets/magical/models/item/" + weapon.path() + ".json",
                    "/assets/magical/textures/item/" + weapon.path() + ".png")) {
                if (MagicalWeaponsTest.class.getResource(path) == null) {
                    missing.add(path);
                }
            }
        }
        assertTrue(missing.isEmpty(), "assets are missing: " + missing);
    }

    private static String readLang() throws IOException {
        try (InputStream stream = MagicalWeaponsTest.class.getResourceAsStream(LANG_PATH)) {
            assertNotNull(stream, "could not find " + LANG_PATH + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
