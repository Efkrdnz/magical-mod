package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ShotPlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VerseIds;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import com.efkrdnz.magical.magic.incantation.Wake;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * A body in an unloaded chunk keeps its payload: every number of a shot state, every body of a plan
 * and every payload beneath it come back from NBT as they went in, and a prototype the table no
 * longer holds is dropped rather than poisoning the plan.
 */
class ShotPlanCodecTest {

    private static final double EPS = 1.0E-12D;

    private static ShotState everything() {
        ShotState s = new ShotState();
        s.setBeat(7);
        s.multiplySpeed(2.5D);
        s.addGravity(0.04D);
        s.addBounces(3);
        s.addLifetime(25);
        s.addSpread(12.0D);
        s.setPattern(45.0D);
        s.addDamage(2.5D);
        s.addHealing(1.0D);
        s.addExplosionRadius(1.5D);
        s.addExplosionDamage(1.5D);
        s.addCrit(15.0D);
        s.addKnockback(1.0D);
        s.setSchool(MagicSchool.FIRE);
        s.behaviour(Behaviour.SEEKER);
        s.behaviour(Behaviour.PUNCTURE);
        s.hitEffect(HitEffect.BURN);
        s.hitEffect(HitEffect.FREEZE);
        s.wake(Wake.FIRE, 5);
        s.wake(Wake.FROST, 5);
        s.allowFriendlyFire();
        s.addRecoil(10.0D);
        s.addScreenshake(1.0D);
        s.light(12);
        s.setDrawManyCount(2);
        return s;
    }

    @Test
    void everyNumberOfTheStateSurvivesTheRoundTrip() {
        ShotState back = ShotPlanCodec.loadState(ShotPlanCodec.saveState(everything()));
        assertEquals(7, back.beatTicks());
        assertEquals(2.5D, back.speedMultiplier(), EPS);
        assertEquals(0.04D, back.gravity(), EPS);
        assertEquals(3, back.bounces());
        assertEquals(25, back.lifetimeAddTicks());
        assertEquals(12.0D, back.spreadDegrees(), EPS);
        assertEquals(45.0D, back.patternDegrees(), EPS);
        assertEquals(2.5D, back.damageAdd(), EPS);
        assertEquals(1.0D, back.healingAdd(), EPS);
        assertEquals(1.5D, back.explosionRadius(), EPS);
        assertEquals(1.5D, back.explosionDamageAdd(), EPS);
        assertEquals(15.0D, back.critChance(), EPS);
        assertEquals(1.0D, back.knockback(), EPS);
        assertFalse(back.nullsDamage());
        assertEquals(MagicSchool.FIRE, back.school());
        assertEquals(List.of(Behaviour.SEEKER, Behaviour.PUNCTURE), back.behaviours());
        assertEquals(List.of(HitEffect.BURN, HitEffect.FREEZE), back.hitEffects());
        assertEquals(List.of(Wake.FIRE, Wake.FROST), back.wakes());
        assertEquals(10, back.wakeAmount());
        assertTrue(back.friendlyFire());
        assertEquals(10.0D, back.recoil(), EPS);
        assertEquals(1.0D, back.screenshake(), EPS);
        assertEquals(12, back.lightLevel());
        assertEquals(2, back.drawManyCount());
    }

    @Test
    void aBluntStateStaysBluntAndAnUnschooledOneStaysUnschooled() {
        ShotState s = new ShotState();
        s.nullDamage();
        ShotState back = ShotPlanCodec.loadState(ShotPlanCodec.saveState(s));
        assertTrue(back.nullsDamage());
        assertEquals(null, back.school());
        assertEquals(1.0D, back.speedMultiplier(), EPS);
    }

    @Test
    void aBodyKeepsItsPrototypeVerseKindFuseAndPayload() {
        ShotState inner = new ShotState();
        inner.addDamage(1.0D);
        ProjectilePlan child = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle"), inner, PayloadKind.NONE, 0, null);
        ShotPlan payload = new ShotPlan(List.of(child), new ShotState());
        ProjectilePlan body = new ProjectilePlan(VersePrototypes.ORB, VerseIds.of("orb_fuse"), everything(), PayloadKind.FUSE, 8, payload);
        ShotState group = new ShotState();
        group.setPattern(45.0D);
        group.addSpread(-8.0D);
        ShotPlan plan = new ShotPlan(List.of(body), group);

        ShotPlan back = ShotPlanCodec.load(ShotPlanCodec.save(plan));

        assertEquals(1, back.bodies().size());
        ProjectilePlan b = back.bodies().get(0);
        assertSame(VersePrototypes.ORB, b.prototype());
        assertEquals(VerseIds.of("orb_fuse"), b.verse());
        assertEquals(PayloadKind.FUSE, b.payloadKind());
        assertEquals(8, b.fuseTicks());
        assertEquals(2.5D, b.stamped().damageAdd(), EPS);
        assertTrue(b.hasPayload());
        assertSame(VersePrototypes.NEEDLE, b.payload().bodies().get(0).prototype());
        assertEquals(1.0D, b.payload().bodies().get(0).stamped().damageAdd(), EPS);
        assertFalse(b.payload().bodies().get(0).hasPayload());
        assertEquals(2, back.countAll());
        assertEquals(45.0D, back.state().patternDegrees(), EPS);
        assertEquals(-8.0D, back.state().spreadDegrees(), EPS);
    }

    @Test
    void anUnknownPrototypeIsDroppedRatherThanPoisoningThePlan() {
        ProjectilePlan body = new ProjectilePlan(VersePrototypes.NEEDLE, VerseIds.of("needle"), new ShotState(), PayloadKind.NONE, 0, null);
        CompoundTag tag = ShotPlanCodec.save(new ShotPlan(List.of(body), new ShotState()));
        tag.getList("bodies", Tag.TAG_COMPOUND).getCompound(0).putString("prototype", "magical:body/nothing");
        assertTrue(ShotPlanCodec.load(tag).bodies().isEmpty());
        assertEquals(null, ShotPlanCodec.loadBody(tag.getList("bodies", Tag.TAG_COMPOUND).getCompound(0)));
    }

    @Test
    void anEmptyTagIsAnEmptyPlan() {
        ShotPlan back = ShotPlanCodec.load(new CompoundTag());
        assertTrue(back.bodies().isEmpty());
        assertEquals(1.0D, back.state().speedMultiplier(), EPS);
        assertEquals(0, back.state().beatTicks());
    }
}
