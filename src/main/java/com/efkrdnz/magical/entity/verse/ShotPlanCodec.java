package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.ShotPlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import com.efkrdnz.magical.magic.incantation.Wake;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A shot plan to NBT and back, so a body in an unloaded chunk keeps its payload. The state is
 * rebuilt through the same mutators the verses use, so a number the state clamps on the way in is
 * clamped the same way on the way back; a body whose prototype the table no longer holds is dropped,
 * as {@code ReciteSession.of} drops a verse the catalogue no longer holds; a payload deeper than
 * {@link ReciteCaps#MAX_DEPTH} is refused, because nothing the Reciter made can be that deep.
 */
public final class ShotPlanCodec {

    private ShotPlanCodec() {
    }

    public static CompoundTag save(ShotPlan plan) {
        CompoundTag tag = new CompoundTag();
        tag.put("state", saveState(plan.state()));
        ListTag bodies = new ListTag();
        for (ProjectilePlan body : plan.bodies()) {
            bodies.add(saveBody(body));
        }
        tag.put("bodies", bodies);
        return tag;
    }

    /** Never null: an empty or unreadable tag is an empty plan. */
    public static ShotPlan load(CompoundTag tag) {
        return load(tag, 0);
    }

    private static ShotPlan load(CompoundTag tag, int depth) {
        if (tag == null || depth > ReciteCaps.MAX_DEPTH) {
            return new ShotPlan(List.of(), new ShotState());
        }
        List<ProjectilePlan> bodies = new ArrayList<>();
        ListTag list = tag.getList("bodies", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && bodies.size() < ReciteCaps.MAX_BODIES; i++) {
            ProjectilePlan body = loadBody(list.getCompound(i), depth);
            if (body != null) {
                bodies.add(body);
            }
        }
        return new ShotPlan(bodies, loadState(tag.getCompound("state")));
    }

    public static CompoundTag saveBody(ProjectilePlan body) {
        CompoundTag tag = new CompoundTag();
        tag.putString("prototype", body.prototype().id().toString());
        tag.putString("verse", body.verse().toString());
        tag.put("stamped", saveState(body.stamped()));
        tag.putInt("payloadKind", body.payloadKind().ordinal());
        tag.putInt("fuse", body.fuseTicks());
        if (body.hasPayload()) {
            tag.put("payload", save(body.payload()));
        }
        return tag;
    }

    /** Null when the prototype is unknown. */
    public static ProjectilePlan loadBody(CompoundTag tag) {
        return loadBody(tag, 0);
    }

    private static ProjectilePlan loadBody(CompoundTag tag, int depth) {
        VersePrototype prototype = VersePrototypes.byId(ResourceLocation.tryParse(tag.getString("prototype")));
        if (prototype == null) {
            return null;
        }
        ResourceLocation verse = tag.contains("verse", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("verse")) : null;
        PayloadKind kind = enumAt(PayloadKind.values(), tag.getInt("payloadKind"), PayloadKind.NONE);
        ShotPlan payload = tag.contains("payload", Tag.TAG_COMPOUND) ? load(tag.getCompound("payload"), depth + 1) : null;
        return new ProjectilePlan(prototype, verse == null ? prototype.id() : verse, loadState(tag.getCompound("stamped")),
                kind, tag.getInt("fuse"), payload);
    }

    static CompoundTag saveState(ShotState s) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("beat", s.beatTicks());
        tag.putDouble("speed", s.speedMultiplier());
        tag.putDouble("gravity", s.gravity());
        tag.putInt("bounces", s.bounces());
        tag.putInt("lifetime", s.lifetimeAddTicks());
        tag.putDouble("spread", s.spreadDegrees());
        tag.putDouble("pattern", s.patternDegrees());
        tag.putDouble("damage", s.damageAdd());
        tag.putDouble("healing", s.healingAdd());
        tag.putDouble("explosionRadius", s.explosionRadius());
        tag.putDouble("explosionDamage", s.explosionDamageAdd());
        tag.putDouble("crit", s.critChance());
        tag.putDouble("knockback", s.knockback());
        tag.putBoolean("blunt", s.nullsDamage());
        tag.putInt("school", s.school() == null ? -1 : s.school().ordinal());
        tag.putIntArray("behaviours", ordinals(s.behaviours()));
        tag.putIntArray("hitEffects", ordinals(s.hitEffects()));
        tag.putIntArray("wakes", ordinals(s.wakes()));
        tag.putInt("wakeAmount", s.wakeAmount());
        tag.putBoolean("friendlyFire", s.friendlyFire());
        tag.putDouble("recoil", s.recoil());
        tag.putDouble("screenshake", s.screenshake());
        tag.putInt("light", s.lightLevel());
        tag.putInt("drawMany", s.drawManyCount());
        return tag;
    }

    /** Through the mutators, so the state's own clamps hold on the way back. The wake amount rides on the first wake. */
    static ShotState loadState(CompoundTag tag) {
        ShotState s = new ShotState();
        if (tag == null) {
            return s;
        }
        s.setBeat(tag.getInt("beat"));
        s.multiplySpeed(tag.contains("speed", Tag.TAG_DOUBLE) ? tag.getDouble("speed") : 1.0D);
        s.addGravity(tag.getDouble("gravity"));
        s.addBounces(tag.getInt("bounces"));
        s.addLifetime(tag.getInt("lifetime"));
        s.addSpread(tag.getDouble("spread"));
        s.setPattern(tag.getDouble("pattern"));
        s.addDamage(tag.getDouble("damage"));
        s.addHealing(tag.getDouble("healing"));
        s.addExplosionRadius(tag.getDouble("explosionRadius"));
        s.addExplosionDamage(tag.getDouble("explosionDamage"));
        s.addCrit(tag.getDouble("crit"));
        s.addKnockback(tag.getDouble("knockback"));
        if (tag.getBoolean("blunt")) {
            s.nullDamage();
        }
        MagicSchool school = enumAt(MagicSchool.values(), tag.contains("school", Tag.TAG_INT) ? tag.getInt("school") : -1, null);
        if (school != null) {
            s.setSchool(school);
        }
        for (int ordinal : tag.getIntArray("behaviours")) {
            Behaviour behaviour = enumAt(Behaviour.values(), ordinal, null);
            if (behaviour != null) {
                s.behaviour(behaviour);
            }
        }
        for (int ordinal : tag.getIntArray("hitEffects")) {
            HitEffect effect = enumAt(HitEffect.values(), ordinal, null);
            if (effect != null) {
                s.hitEffect(effect);
            }
        }
        int[] wakes = tag.getIntArray("wakes");
        for (int i = 0; i < wakes.length; i++) {
            Wake wake = enumAt(Wake.values(), wakes[i], null);
            if (wake != null) {
                s.wake(wake, i == 0 ? tag.getInt("wakeAmount") : 0);
            }
        }
        if (tag.getBoolean("friendlyFire")) {
            s.allowFriendlyFire();
        }
        s.addRecoil(tag.getDouble("recoil"));
        s.addScreenshake(tag.getDouble("screenshake"));
        s.light(tag.getInt("light"));
        s.setDrawManyCount(tag.getInt("drawMany"));
        return s;
    }

    private static int[] ordinals(List<? extends Enum<?>> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i).ordinal();
        }
        return out;
    }

    private static <E extends Enum<E>> E enumAt(E[] values, int ordinal, E fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
