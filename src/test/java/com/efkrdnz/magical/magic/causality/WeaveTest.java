package com.efkrdnz.magical.magic.causality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The grammar of the board, on exact boards.
 *
 * <p>Three things are worth more here than anywhere else in the Authority, because all three are
 * what stop a wielder from drawing a machine that cannot work: string runs one way, a cause can
 * never be its own consequence, and a board that will not fit the budget is refused while it is
 * being drawn rather than when it is saved.
 */
class WeaveTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Weave board() {
        return new Weave();
    }

    @Test
    void stringRunsCauseToConditionToConsequenceAndNeverBack() {
        Weave weave = board();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode question = weave.add(CausalNode.of(0, Condition.IS_MELEE, 60, 10));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.RETURN, 110, 10));

        assertTrue(weave.connect(cause.id(), question.id()).ok(), "a cause may feed a condition");
        assertTrue(weave.connect(question.id(), effect.id()).ok(), "a condition may feed a consequence");
        assertEquals(Weave.WireRefusal.WRONG_WAY, weave.connect(effect.id(), question.id()),
                "nothing runs out of a consequence");
        assertEquals(Weave.WireRefusal.WRONG_WAY, weave.connect(question.id(), cause.id()),
                "nothing runs into a cause");
    }

    @Test
    void aCauseMayReachAConsequenceDirectly() {
        Weave weave = board();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.DECREE, 10, 10));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.SPEND, 90, 10));
        assertTrue(weave.connect(cause.id(), effect.id()).ok(), "a rule with no question is still a rule");
    }

    @Test
    void aRunOfQuestionsMayNotBiteItsOwnTail() {
        // The one shape the forward-only rule does not already forbid, and the reason connect asks
        // whether the far end can already reach the near one.
        Weave weave = board();
        CausalNode a = weave.add(CausalNode.of(0, Condition.IS_MELEE, 10, 10));
        CausalNode b = weave.add(CausalNode.of(0, Condition.CROUCHED, 60, 10));
        CausalNode c = weave.add(CausalNode.of(0, Condition.IS_FIRE, 110, 10));
        assertTrue(weave.connect(a.id(), b.id()).ok());
        assertTrue(weave.connect(b.id(), c.id()).ok());
        assertEquals(Weave.WireRefusal.LOOP, weave.connect(c.id(), a.id()),
                "a signal that could arrive back where it started is a loop, however long the way round");
        assertEquals(Weave.WireRefusal.NOTHING_THERE, weave.connect(a.id(), a.id()),
                "and the shortest loop of all is a pin tied to itself");
    }

    @Test
    void theSameStringIsNotDrawnTwice() {
        Weave weave = board();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.STORE, 90, 10));
        assertTrue(weave.connect(cause.id(), effect.id()).ok());
        assertEquals(Weave.WireRefusal.ALREADY, weave.connect(cause.id(), effect.id()));
        assertEquals(1, weave.wires().size());
    }

    @Test
    void aPinIsRefusedWhenTheBudgetCannotPayForIt() {
        Weave weave = board();
        int placed = 0;
        while (weave.add(CausalNode.of(0, Cause.HURT, 10, 10)) != null) {
            placed++;
        }
        assertTrue(placed > 0, "the board should hold something");
        assertTrue(weave.weight() <= Weave.CAPACITY, "and never more than it can pay for");
        assertNull(weave.add(CausalNode.of(0, Cause.HURT, 10, 10)),
                "a board that can be drawn and not kept is a board that lies to whoever drew it");
    }

    @Test
    void retuningAPinKeepsItsIdAndThereforeItsString() {
        Weave weave = board();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.STORE, 90, 10));
        weave.connect(cause.id(), effect.id());
        assertTrue(weave.replace(effect.withParam(80)));
        assertEquals(80, weave.node(effect.id()).param());
        assertEquals(1, weave.wires().size(), "retuning is not redrawing");
    }

    @Test
    void aChangeThatWouldNotFitTheBudgetMovesNothingAtAll() {
        Weave weave = board();
        CausalNode effect = weave.add(CausalNode.of(0, Effect.STORE, 10, 10));
        while (weave.spare() > 1 && weave.add(CausalNode.of(0, Cause.HURT, 10, 10)) != null) {
            continue;
        }
        int before = weave.weight();
        CausalNode heavy = effect.withScope(Scope.FIELD);
        assertTrue(heavy.weight() > effect.weight() + 1, "the change has to actually cost more");
        assertFalse(weave.replace(heavy), "refused");
        assertEquals(before, weave.weight(), "and nothing moved");
        assertSame(Scope.OTHER, weave.node(effect.id()).scope());
    }

    @Test
    void takingAPinDownTakesItsStringWithIt() {
        Weave weave = board();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode question = weave.add(CausalNode.of(0, Condition.IS_MELEE, 60, 10));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.RETURN, 110, 10));
        weave.connect(cause.id(), question.id());
        weave.connect(question.id(), effect.id());
        assertTrue(weave.remove(question.id()));
        assertTrue(weave.wires().isEmpty(), "no string may hang from a pin that is not there");
    }

    @Test
    void aBoardSurvivesBeingSavedAndOpenedAgain() {
        Weave weave = board();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.BRIM, 20, 30).withParam(35));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.SPEND, 140, 30)
                .withParam(18).withScope(Scope.MARKED).toggled(Modifier.AFTER));
        weave.connect(cause.id(), effect.id());
        weave.setSuspended(true);

        Weave opened = board();
        opened.load(weave.save());

        assertEquals(weave.weight(), opened.weight());
        assertEquals(1, opened.wires().size());
        assertTrue(opened.suspended());
        CausalNode reopened = opened.node(effect.id());
        assertNotNull(reopened);
        assertEquals(18, reopened.param());
        assertSame(Scope.MARKED, reopened.scope());
        assertTrue(reopened.wears(Modifier.AFTER));
        assertSame(Cause.BRIM, opened.node(cause.id()).cause());
        assertEquals(35, opened.node(cause.id()).param());
    }

    @Test
    void aWordThisBuildHasNeverHeardOfDropsItsPinRatherThanBecomingAnotherWord() {
        // The reason the save writes names and not ordinals: reordering a vocabulary must never turn
        // every Erase on every board in the world into a Store.
        Weave weave = board();
        CausalNode cause = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CausalNode effect = weave.add(CausalNode.of(0, Effect.ERASE, 90, 10));
        weave.connect(cause.id(), effect.id());
        CompoundTag tag = weave.save();
        tag.getList("nodes", Tag.TAG_COMPOUND).getCompound(1).putString("word", "UNMAKE_EVERYTHING");

        Weave opened = board();
        opened.load(tag);
        assertEquals(1, opened.size(), "the pin is gone");
        assertTrue(opened.wires().isEmpty(), "and so is the string that hung off it");
    }

    @Test
    void aForgedSaveCannotHandTheEngineALoop() {
        Weave weave = board();
        CausalNode a = weave.add(CausalNode.of(0, Condition.IS_MELEE, 10, 10));
        CausalNode b = weave.add(CausalNode.of(0, Condition.CROUCHED, 60, 10));
        weave.connect(a.id(), b.id());
        CompoundTag tag = weave.save();
        CompoundTag forged = new CompoundTag();
        forged.putInt("from", b.id());
        forged.putInt("to", a.id());
        tag.getList("wires", Tag.TAG_COMPOUND).add(forged);

        Weave opened = board();
        opened.load(tag);
        assertEquals(1, opened.wires().size(), "loading goes through connect, so the loop is refused");
    }

    @Test
    void aPinIsNeverLoadedOutsideTheBoard() {
        Weave weave = board();
        CausalNode pin = weave.add(CausalNode.of(0, Cause.HURT, 10, 10));
        CompoundTag tag = weave.save();
        CompoundTag entry = tag.getList("nodes", Tag.TAG_COMPOUND).getCompound(0);
        entry.putInt("x", 99999);
        entry.putInt("y", -4000);

        Weave opened = board();
        opened.load(tag);
        CausalNode reopened = opened.node(pin.id());
        assertEquals(Weave.BOARD_W, reopened.x());
        assertEquals(0, reopened.y());
    }

    @Test
    void aNumberOutsideWhatThisBuildAllowsIsPulledIntoRange() {
        Weave weave = board();
        CausalNode pin = weave.add(CausalNode.of(0, Effect.STORE, 10, 10));
        CompoundTag tag = weave.save();
        tag.getList("nodes", Tag.TAG_COMPOUND).getCompound(0).putInt("param", 10000);

        Weave opened = board();
        opened.load(tag);
        assertEquals(Effect.STORE.maxParam(), opened.node(pin.id()).param());
    }

    @Test
    void aConsequenceWearsAtMostTwoTools() {
        CausalNode pin = CausalNode.of(1, Effect.SPEND, 0, 0)
                .toggled(Modifier.AFTER).toggled(Modifier.GREATER).toggled(Modifier.TWICE);
        assertEquals(Modifier.MAX_PER_NODE, pin.modifiers().size());
        assertFalse(pin.wears(Modifier.TWICE), "the third is refused rather than pushing one off");
        assertFalse(pin.toggled(Modifier.AFTER).wears(Modifier.AFTER), "and toggling takes one back off");
    }
}
