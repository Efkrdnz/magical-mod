package com.efkrdnz.magical.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.menu.ClassSelectMenu;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A hidden class is hidden in exactly one way - nothing enumerates it - and there is no single
 * place that does the enumerating. The class tree draws {@code all()}, the tower rolls
 * {@code roots()}, three commands complete {@code commandIds()}, the first-spawn chooser reads
 * {@code startingRoots()}, the codex's Passives tab reads {@code classGranting}, and onboarding
 * reads {@code hasAnyRootClass()}. Seven doors, each of which fails open, and six of them lead
 * somewhere a player would never guess a secret had been kept.
 *
 * <p>So this is one test over every door at once, and it is the only thing that will stop the
 * eighth door somebody adds next month. Two of the seven are not reachable from a unit test -
 * {@code ClassTreeScreen.drawNodes}/{@code drawEdgePass} are client rendering and
 * {@code DungeonTowerService.addClassRewards} is private - and both of those are written as a
 * {@code MagicalClasses.isVisible} guard and nothing else, so the first assertion below is
 * literally the condition they branch on.</p>
 *
 * <p>What is <b>not</b> claimed: secrecy against a text editor. {@code MagicalClasses} is common
 * code the client imports and every {@code class.magical.*} string ships in plaintext. The value
 * is the rite, not the ignorance.</p>
 */
class ClassVisibilityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A method rather than a static field: a static initialiser here would load MagicalClasses,
     * and with it MagicContent, before {@code @BeforeAll} has bootstrapped anything.
     */
    private static List<ResourceLocation> chain() {
        return List.of(
                MagicalClasses.SWORD_SUMMONER,
                MagicalClasses.SWORD_RIDER,
                MagicalClasses.SWORD_SAINT,
                MagicalClasses.SWORD_GOD);
    }

    private static PlayerMagicState found() {
        PlayerMagicState state = new PlayerMagicState();
        assertTrue(state.unlockClass(MagicalClasses.SWORD_SUMMONER), "the rite unlocks the root");
        return state;
    }

    @Test
    void everyOneOfTheFourIsRegisteredAndSecret() {
        for (ResourceLocation id : chain()) {
            MagicalClassDefinition definition = MagicalClasses.get(id);
            assertNotNull(definition, id + " is not registered at all");
            assertTrue(definition.secret(), id + " must carry the secret flag; every gate reads it");
        }
    }

    @Test
    void nothingNamesTheChainToAWielderWhoHasFoundNothing() {
        PlayerMagicState empty = new PlayerMagicState();
        List<String> stateless = MagicalClasses.commandIds();
        List<String> statelessRoots = MagicalClasses.rootCommandIds();
        List<String> completed = MagicalClasses.commandIds(empty);
        List<String> completedRoots = MagicalClasses.rootCommandIds(empty);
        List<String> chooser = ClassSelectMenu.choices().stream().map(d -> d.id().getPath()).toList();
        List<String> starting = MagicalClasses.startingRootCommandIds();

        for (ResourceLocation id : chain()) {
            String path = id.getPath();
            MagicalClassDefinition definition = MagicalClasses.get(id);
            // The class tree screen's two passes and the tower's wish roll both branch on exactly
            // this call, so it stands in for all three of those sites.
            assertFalse(MagicalClasses.isVisible(definition, empty), path + " is drawn in the class tree");
            assertFalse(stateless.contains(path), path + " is completed by /magical class evolve");
            assertFalse(statelessRoots.contains(path), path + " is completed by /magical class unlock");
            assertFalse(completed.contains(path), path + " is completed for a wielder who owns nothing");
            assertFalse(completedRoots.contains(path), path + " is completed as a root for a wielder who owns nothing");
            assertFalse(chooser.contains(path), path + " is offered by the first-spawn chooser");
            assertFalse(starting.contains(path), path + " is a starting root");
            assertFalse(MagicalClasses.isStartingRoot(id), path + " answers true to isStartingRoot");
        }
    }

    /**
     * The reveal is the whole chain, not the next rung. Drip-feeding it would turn a discovery into
     * a progress bar, and the player who has just performed the rite is precisely the player who
     * should be shown how far it goes.
     */
    @Test
    void takingTheRootRevealsTheWholeChainAtOnce() {
        PlayerMagicState state = found();
        List<String> completed = MagicalClasses.commandIds(state);
        for (ResourceLocation id : chain()) {
            assertTrue(MagicalClasses.isVisible(MagicalClasses.get(id), state),
                    id + " should be visible the moment the root is owned");
            assertTrue(completed.contains(id.getPath()), id + " should complete for its own wielder");
        }
        assertTrue(MagicalClasses.rootCommandIds(state).contains(MagicalClasses.SWORD_SUMMONER.getPath()));
    }

    /**
     * A secret root is a ROOT that is not a STARTING root, which is the Spell Creator's shape. That
     * distinction is doing real work: a sixth starting base turns the tree screen's {@code 360 / 5}
     * sector into {@code 360 / 6} and moves four of the five existing trees for every player in the
     * game, with a green build. Owning the class must not change that either.
     */
    @Test
    void aFoundChainIsStillNeverAStartingClass() {
        PlayerMagicState state = found();
        assertEquals(5, MagicalClasses.startingRoots().size(), "the five trees, and only ever the five");
        assertFalse(MagicalClasses.isStartingRoot(MagicalClasses.SWORD_SUMMONER));
        assertFalse(ClassSelectMenu.choices().contains(MagicalClasses.get(MagicalClasses.SWORD_SUMMONER)));
        assertTrue(MagicalClasses.isRoot(MagicalClasses.SWORD_SUMMONER),
                "it must stay a base, because evolveClass returns early on isBase() and that is what "
                        + "stops a forged evolve packet taking it");
        assertTrue(state.hasClass(MagicalClasses.SWORD_SUMMONER));
    }

    /**
     * The softlock, and the reason it is worth its own method: {@code hasAnyRootClass} iterates
     * {@code roots()}, so owning a secret root satisfies onboarding - the starting-class chooser
     * stops reopening and {@code chooseStartingClass} refuses forever, silently, with no way back.
     * A player who found the rite before picking a class would simply never get one.
     *
     * <p>The gate itself lives in {@code PlayerMagicState.hasAnyRootClass()}, which is the only one
     * of the seven that is not in this package; this is what holds it there.</p>
     */
    @Test
    void owningTheHiddenRootDoesNotSatisfyTheStartingClassChooser() {
        PlayerMagicState state = found();
        assertFalse(state.hasAnyRootClass(),
                "a secret root must not count as having chosen a starting class");
        assertTrue(state.chooseStartingClass(null, MagicalClasses.MYSTIC),
                "a wielder who found the rite first must still be able to pick a real starting class");
    }

    /**
     * The codex Passives tab prints "From %s" from {@code classGranting}, so a class passive hung
     * off this chain would name the class in a tab every player can open. All four of its passives
     * register as forbidden rather than class passives, which makes this moot today - which is
     * exactly why the gate needs a test rather than a comment.
     */
    @Test
    void noSecretClassIsEverAPassiveSource() {
        for (ResourceLocation id : chain()) {
            MagicalClassDefinition definition = MagicalClasses.get(id);
            for (ResourceLocation passiveId : definition.rewardPassives()) {
                assertNull(MagicalClasses.classGranting(passiveId),
                        passiveId + " names " + id + " in the codex's From line");
            }
        }
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            for (ResourceLocation passiveId : definition.rewardPassives()) {
                ResourceLocation source = MagicalClasses.classGranting(passiveId);
                if (source == null) {
                    continue;
                }
                assertFalse(MagicalClasses.get(source).secret(),
                        passiveId + " is sourced to the secret class " + source);
            }
        }
    }
}
