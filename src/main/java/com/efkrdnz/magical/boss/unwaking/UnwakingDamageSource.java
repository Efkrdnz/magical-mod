package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.world.damagesource.DamageSource;

/** Preserves ordinary magic defenses while exposing the attack's actual rank to ward checks. */
public final class UnwakingDamageSource extends DamageSource {
    private final UnwakingHazard.Kind attack;
    public UnwakingDamageSource(UnwakingGodEntity owner, UnwakingHazard.Kind attack) {
        super(owner.damageSources().indirectMagic(owner,owner).typeHolder(),owner,owner);
        this.attack = attack;
    }
    public UnwakingHazard.Kind attack() { return attack; }
    @Override public boolean is(net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> tag) {
        // The controller already checked the real incoming direction and applied shield reduction.
        // Do not let vanilla cancel it a second time using the distant boss body's direction.
        return tag.equals(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)||super.is(tag);
    }
}
