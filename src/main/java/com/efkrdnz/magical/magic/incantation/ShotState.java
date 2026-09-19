package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The bag every verse of a shot writes into: {@code GunActionState} under our names.
 *
 * <p>A body added while the shot runs is stamped with a {@link #copy()} of this at that instant;
 * the shot's final state carries the shot-wide numbers (spread, pattern, beat) the spawner reads for
 * the group. That split is the whole binding rule: a modifier reaches every body added after it and
 * none added before.
 */
public final class ShotState {

    public static final double MAX_SPEED_MULTIPLIER = 20.0D;

    private int beatTicks;
    private double speedMultiplier = 1.0D;
    private double childSpeedMultiplier = 1.0D;
    private double dampening = 1.0D;
    private double gravity;
    private int bounces;
    private int lifetimeAddTicks;
    private double spreadDegrees;
    private double patternDegrees;
    private double damageAdd;
    private double healingAdd;
    private double explosionRadius;
    private double explosionDamageAdd;
    private double critChance;
    private double knockback;
    private boolean nullAllDamage;
    private MagicSchool school;
    private final List<Behaviour> behaviours = new ArrayList<>();
    private final List<HitEffect> hitEffects = new ArrayList<>();
    private final List<Wake> wakes = new ArrayList<>();
    private int wakeAmount;
    private boolean friendlyFire;
    private double recoil;
    private double screenshake;
    private int lightLevel;
    private int drawManyCount;

    public ShotState copy() {
        ShotState c = new ShotState();
        c.beatTicks = beatTicks;
        c.speedMultiplier = speedMultiplier;
        c.childSpeedMultiplier = childSpeedMultiplier;
        c.dampening = dampening;
        c.gravity = gravity;
        c.bounces = bounces;
        c.lifetimeAddTicks = lifetimeAddTicks;
        c.spreadDegrees = spreadDegrees;
        c.patternDegrees = patternDegrees;
        c.damageAdd = damageAdd;
        c.healingAdd = healingAdd;
        c.explosionRadius = explosionRadius;
        c.explosionDamageAdd = explosionDamageAdd;
        c.critChance = critChance;
        c.knockback = knockback;
        c.nullAllDamage = nullAllDamage;
        c.school = school;
        c.behaviours.addAll(behaviours);
        c.hitEffects.addAll(hitEffects);
        c.wakes.addAll(wakes);
        c.wakeAmount = wakeAmount;
        c.friendlyFire = friendlyFire;
        c.recoil = recoil;
        c.screenshake = screenshake;
        c.lightLevel = lightLevel;
        c.drawManyCount = drawManyCount;
        return c;
    }

    // ---- timing ----
    public int beatTicks() { return beatTicks; }
    public void addBeat(int ticks) { beatTicks += ticks; }
    public void setBeat(int ticks) { beatTicks = ticks; }

    // ---- motion ----
    public double speedMultiplier() { return speedMultiplier; }
    public void multiplySpeed(double factor) {
        speedMultiplier = Math.max(0.0D, Math.min(MAX_SPEED_MULTIPLIER, speedMultiplier * factor));
    }
    public double childSpeedMultiplier() { return childSpeedMultiplier; }
    public double dampening() { return dampening; }
    public double gravity() { return gravity; }
    public void addGravity(double amount) { gravity += amount; }
    public int bounces() { return bounces; }
    public void addBounces(int count) { bounces += count; }
    public int lifetimeAddTicks() { return lifetimeAddTicks; }
    public void addLifetime(int ticks) { lifetimeAddTicks += ticks; }
    public double spreadDegrees() { return spreadDegrees; }
    public void addSpread(double degrees) { spreadDegrees += degrees; }
    public double patternDegrees() { return patternDegrees; }
    public void setPattern(double degrees) { patternDegrees = degrees; }

    // ---- damage ----
    public double damageAdd() { return damageAdd; }
    public void addDamage(double halfHearts) { damageAdd += halfHearts; }
    public double healingAdd() { return healingAdd; }
    public void addHealing(double halfHearts) { healingAdd += halfHearts; }
    public double explosionRadius() { return explosionRadius; }
    public void addExplosionRadius(double blocks) { explosionRadius = Math.max(0.0D, explosionRadius + blocks); }
    public double explosionDamageAdd() { return explosionDamageAdd; }
    public void addExplosionDamage(double halfHearts) { explosionDamageAdd += halfHearts; }
    public double critChance() { return critChance; }
    public void addCrit(double percent) { critChance += percent; }
    public double knockback() { return knockback; }
    public void addKnockback(double amount) { knockback += amount; }
    public boolean nullsDamage() { return nullAllDamage; }
    public void nullDamage() { nullAllDamage = true; }

    // ---- element and bundles ----
    public MagicSchool school() { return school; }
    public void setSchool(MagicSchool school) { this.school = school; }
    public List<Behaviour> behaviours() { return Collections.unmodifiableList(behaviours); }
    public void behaviour(Behaviour behaviour) { behaviours.add(behaviour); }
    public boolean has(Behaviour behaviour) { return behaviours.contains(behaviour); }
    public List<HitEffect> hitEffects() { return Collections.unmodifiableList(hitEffects); }
    public void hitEffect(HitEffect effect) { hitEffects.add(effect); }
    public List<Wake> wakes() { return Collections.unmodifiableList(wakes); }
    public int wakeAmount() { return wakeAmount; }
    public void wake(Wake wake, int amount) { wakes.add(wake); wakeAmount += amount; }
    public boolean friendlyFire() { return friendlyFire; }
    public void allowFriendlyFire() { friendlyFire = true; }

    // ---- feel ----
    public double recoil() { return recoil; }
    public void addRecoil(double amount) { recoil += amount; }
    public double screenshake() { return screenshake; }
    public void addScreenshake(double amount) { screenshake += amount; }
    public int lightLevel() { return lightLevel; }
    public void light(int level) { lightLevel = Math.max(lightLevel, level); }

    // ---- bookkeeping ----
    public int drawManyCount() { return drawManyCount; }
    public void setDrawManyCount(int count) { drawManyCount = count; }
}
