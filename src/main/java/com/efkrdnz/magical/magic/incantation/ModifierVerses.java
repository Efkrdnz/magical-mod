package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Every modifier writes the state and draws one: {@code draw_actions(1, true)} is the whole of its grammar. */
public final class ModifierVerses {

    public static final ResourceLocation WEIGHT = VerseIds.of("weight");
    public static final ResourceLocation HASTE = VerseIds.of("haste");
    public static final ResourceLocation UNDYING = VerseIds.of("undying");
    public static final ResourceLocation SECOND_WIND = VerseIds.of("second_wind");

    private ModifierVerses() {
    }

    static Verse modifier(String path, int mana, int uses, int beat, int rest, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.MODIFIER, mana, uses, null, 1, Declared.of(1, beat, rest), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            r.addRest(rest);
            effect.accept(s);
            r.drawActions(1);
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(modifier("weight", 3, Verse.UNLIMITED, 2, 0, s -> {
            s.addDamage(2.5D);
            s.addRecoil(10.0D);
        }));
        c.register(modifier("haste", 2, Verse.UNLIMITED, 0, 0, s -> s.multiplySpeed(2.5D)));
        c.register(modifier("undying", 20, 3, 4, 0, s -> s.behaviour(Behaviour.UNDYING)));
        c.register(modifier("second_wind", 5, Verse.UNLIMITED, -3, -7, s -> { }));
        c.register(modifier("ballast", 5, Verse.UNLIMITED, 3, 0, s -> {
            s.addDamage(6.0D);
            s.multiplySpeed(0.35D);
            s.addRecoil(50.0D);
        }));
        c.register(modifier("endurance", 6, Verse.UNLIMITED, 4, 0, s -> s.addLifetime(25)));
        c.register(modifier("true_aim", 1, Verse.UNLIMITED, 0, 0, s -> s.addSpread(-60.0D)));
        c.register(modifier("keen_edge", 3, Verse.UNLIMITED, 0, 0, s -> s.addCrit(15.0D)));
        c.register(modifier("ricochet", 2, Verse.UNLIMITED, 0, 0, s -> s.addBounces(10)));
        c.register(modifier("sink", 1, Verse.UNLIMITED, 0, 0, s -> s.addGravity(0.04D)));
        c.register(modifier("loft", 1, Verse.UNLIMITED, 0, 0, s -> s.addGravity(-0.02D)));
        c.register(modifier("volatile", 8, Verse.UNLIMITED, 13, 0, s -> {
            s.addExplosionRadius(1.5D);
            s.addExplosionDamage(1.5D);
            s.multiplySpeed(0.75D);
            s.addRecoil(30.0D);
        }));
        c.register(modifier("blunt", 2, Verse.UNLIMITED, -2, 0, s -> {
            s.nullDamage();
            s.addLifetime(90);
        }));
        c.register(modifier("seeker", 12, Verse.UNLIMITED, 0, 0, s -> s.behaviour(Behaviour.SEEKER)));
        c.register(modifier("sightline", 6, Verse.UNLIMITED, 0, 0, s -> s.behaviour(Behaviour.SIGHTLINE)));
        c.register(modifier("puncture", 16, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.PUNCTURE);
            s.addDamage(-1.5D);
            s.allowFriendlyFire();
        }));
        c.register(modifier("serpentine", 2, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.SERPENTINE);
            s.multiplySpeed(2.0D);
        }));
        c.register(modifier("gyre", 2, Verse.UNLIMITED, -2, 0, s -> {
            s.behaviour(Behaviour.GYRE);
            s.addDamage(0.5D);
            s.addLifetime(8);
        }));
        c.register(modifier("errant", 2, Verse.UNLIMITED, 0, 0, s -> s.behaviour(Behaviour.ERRANT)));
        c.register(modifier("relay", 12, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.RELAY);
            s.addLifetime(-10);
            s.addDamage(-1.0D);
            s.addExplosionRadius(-1.0D);
            s.addSpread(10.0D);
        }));
        c.register(modifier("twin_path", 4, Verse.UNLIMITED, 2, 0, s -> s.behaviour(Behaviour.TWIN_PATH)));
        c.register(modifier("naught", 1, Verse.UNLIMITED, -5, 0, s -> s.behaviour(Behaviour.NAUGHT)));
        c.register(modifier("wellspring", -12, Verse.UNLIMITED, 3, 0, s -> { }));
        c.register(modifier("flame_wreath", 5, Verse.UNLIMITED, 0, 0, s -> {
            s.setSchool(MagicSchool.FIRE);
            s.hitEffect(HitEffect.BURN);
        }));
        c.register(modifier("rime_wreath", 5, Verse.UNLIMITED, 0, 0, s -> {
            s.setSchool(MagicSchool.WATER);
            s.hitEffect(HitEffect.FREEZE);
        }));
        c.register(modifier("storm_wreath", 5, Verse.UNLIMITED, 0, 0, s -> {
            s.hitEffect(HitEffect.SHOCK);
            s.addDamage(0.5D);
        }));
        c.register(modifier("umbral_wreath", 6, Verse.UNLIMITED, 0, 0, s -> {
            s.setSchool(MagicSchool.DARK);
            s.hitEffect(HitEffect.WITHER);
        }));
        c.register(modifier("fire_wake", 4, Verse.UNLIMITED, 0, 0, s -> s.wake(Wake.FIRE, 5)));
        c.register(modifier("water_wake", 3, Verse.UNLIMITED, 0, 0, s -> s.wake(Wake.WATER, 5)));
        c.register(modifier("frost_wake", 4, Verse.UNLIMITED, 0, 0, s -> s.wake(Wake.FROST, 5)));
        c.register(modifier("lantern", 1, Verse.UNLIMITED, 0, 0, s -> {
            s.behaviour(Behaviour.LANTERN);
            s.light(12);
        }));
        c.register(modifier("uplift", 4, Verse.UNLIMITED, 0, 0, s -> s.hitEffect(HitEffect.UPLIFT)));
        c.register(modifier("displace", 6, Verse.UNLIMITED, 0, 0, s -> s.hitEffect(HitEffect.DISPLACE)));
        c.register(modifier("bursting_ricochet", 8, Verse.UNLIMITED, 8, 0, s -> {
            s.addBounces(1);
            s.behaviour(Behaviour.BOUNCE_BURST);
            s.addRecoil(20.0D);
        }));
        // RANDOM_MODIFIER: a known modifier verse, which does the drawing.
        c.register(Verse.of("wild_mark", VerseType.MODIFIER, 6, Verse.UNLIMITED, null, 1, Declared.of(1, 0, 0), (r, rec, it) -> {
            ControlVerses.wild(r, rec, VerseType.MODIFIER);
            return VerseAction.NONE;
        }).asRecursive());
    }
}
