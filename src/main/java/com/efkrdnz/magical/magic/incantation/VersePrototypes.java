package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.VersePrototype.Look;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** The table of bodies. Every number here is the body's own; the verse's deltas sit on top. */
public final class VersePrototypes {

    private static final Map<ResourceLocation, VersePrototype> BY_ID = new LinkedHashMap<>();

    public static final VersePrototype NEEDLE = flying("needle", MagicSchool.ARCANE, 3.0D, 0.0D, 1.6D, 40, 0.15F, 0.0D, 0.0D, null, Look.NEEDLE, false);
    public static final VersePrototype ORB = flying("orb", MagicSchool.ARCANE, 5.0D, 0.0D, 1.0D, 60, 0.3F, 0.0D, 0.0D, null, Look.ORB, false);
    public static final VersePrototype SHARD = flying("shard", MagicSchool.ARCANE, 9.0D, 0.0D, 0.7D, 60, 0.35F, 0.0D, 0.0D, null, Look.SHARD, false);
    public static final VersePrototype EMBER = flying("ember", MagicSchool.FIRE, 6.0D, 0.0D, 0.9D, 50, 0.25F, 2.0D, 2.5D, HitEffect.BURN, Look.EMBER, false);
    public static final VersePrototype ARC = flying("arc", MagicSchool.LIGHT, 7.0D, 0.0D, 2.0D, 20, 0.2F, 0.0D, 0.0D, HitEffect.SHOCK, Look.ARC, false);
    public static final VersePrototype DART = flying("dart", MagicSchool.LIGHT, 0.0D, 4.0D, 1.0D, 50, 0.2F, 0.0D, 0.0D, null, Look.DART, false);
    public static final VersePrototype WHISPER = flying("whisper", MagicSchool.ARCANE, 2.5D, 0.0D, 0.6D, 4, 0.25F, 0.0D, 0.0D, null, Look.WHISPER, false);
    public static final VersePrototype BLINK = flying("blink", MagicSchool.SPATIAL, 1.0D, 0.0D, 1.5D, 30, 0.2F, 0.0D, 0.0D, null, Look.BLINK, true);
    public static final VersePrototype BURST = standing("burst", MagicSchool.FIRE, 0.0D, 0.0D, 0.5F, 1, 3.0D, 8.0D, null, null, 1, Look.BURST);
    public static final VersePrototype RING_RIME = standing("ring_rime", MagicSchool.WATER, 0.0D, 0.0D, 3.0F, 100, 0.0D, 0.0D, HitEffect.FREEZE, null, 10, Look.RING);
    public static final VersePrototype RING_STORM = standing("ring_storm", MagicSchool.LIGHT, 1.0D, 0.0D, 3.0F, 100, 0.0D, 0.0D, HitEffect.SHOCK, null, 10, Look.RING);
    public static final VersePrototype RING_BALM = standing("ring_balm", MagicSchool.LIGHT, 0.0D, 1.0D, 3.0F, 100, 0.0D, 0.0D, null, null, 20, Look.RING);
    public static final VersePrototype RING_UPLIFT = standing("ring_uplift", MagicSchool.ARCANE, 0.0D, 0.0D, 3.0F, 100, 0.0D, 0.0D, HitEffect.UPLIFT, null, 10, Look.RING);
    public static final VersePrototype PIT = standing("pit", MagicSchool.VOID, 1.0D, 0.0D, 2.5F, 80, 0.0D, 0.0D, null, null, 10, Look.PIT);
    public static final VersePrototype WORD_HELD = standing("word_held", MagicSchool.ARCANE, 0.0D, 0.0D, 0.2F, 20, 0.0D, 0.0D, null, null, 20, Look.WORD);
    public static final VersePrototype WORD_FAR = flying("word_far", MagicSchool.ARCANE, 0.0D, 0.0D, 2.0D, 10, 0.1F, 0.0D, 0.0D, null, Look.WORD, false);
    public static final VersePrototype WORD_STEP = flying("word_step", MagicSchool.SPATIAL, 0.0D, 0.0D, 1.5D, 30, 0.1F, 0.0D, 0.0D, null, Look.WORD, true);

    private VersePrototypes() {
    }

    private static VersePrototype flying(String path, MagicSchool school, double damage, double healing, double speed,
                                         int lifetimeTicks, float radius, double explosionRadius, double explosionDamage,
                                         HitEffect hit, Look look, boolean carriesCaster) {
        return put(new VersePrototype(VerseIds.body(path), school, damage, healing, speed, lifetimeTicks, radius, false, 0,
                explosionRadius, explosionDamage, null, hit, 0, look, carriesCaster));
    }

    private static VersePrototype standing(String path, MagicSchool school, double damage, double healing, float radius,
                                           int durationTicks, double explosionRadius, double explosionDamage,
                                           HitEffect pulse, HitEffect hit, int pulseIntervalTicks, Look look) {
        return put(new VersePrototype(VerseIds.body(path), school, damage, healing, 0.0D, 0, radius, true, durationTicks,
                explosionRadius, explosionDamage, pulse, hit, pulseIntervalTicks, look, false));
    }

    private static VersePrototype put(VersePrototype prototype) {
        BY_ID.put(prototype.id(), prototype);
        return prototype;
    }

    public static VersePrototype byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public static List<VersePrototype> all() {
        return List.copyOf(BY_ID.values());
    }
}
