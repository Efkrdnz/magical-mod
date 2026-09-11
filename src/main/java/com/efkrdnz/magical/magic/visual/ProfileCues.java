package com.efkrdnz.magical.magic.visual;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** The small data records that make up a {@link VisualProfile}. */
public final class ProfileCues {
    private ProfileCues() {}

    /** Camera/screen presets: the ONLY way skills touch the first-person channel. */
    public enum FirstPersonPreset {
        NONE(0, 0.0F, 0, 0.0F, 0, 0.0F),
        CASTER_LIGHT(4, 0.0F, 0, 0.0F, 0, -1.5F),
        CASTER_RECOIL(6, 0.14F, 3, 0.15F, 0, -4.0F),
        CASTER_BLOOM(12, 0.18F, 0, 0.0F, 0, 4.0F),
        CASTER_SURGE(14, 0.22F, 6, 0.3F, 2, 8.0F),
        HIT_CONFIRM(8, 0.12F, 0, 0.0F, 0, 0.0F),
        VICTIM_HIT(10, 0.25F, 4, 0.3F, 0, 0.0F),
        VICTIM_HEAVY(12, 0.4F, 8, 0.6F, 2, 0.0F),
        VICTIM_ZONE(6, 0.12F, 0, 0.0F, 0, 0.0F);

        private final int overlayTicks;
        private final float alpha;
        private final int shakeTicks;
        private final float shakeStrength;
        private final int freezeTicks;
        private final float fovKick;

        FirstPersonPreset(int overlayTicks, float alpha, int shakeTicks, float shakeStrength, int freezeTicks, float fovKick) {
            this.overlayTicks = overlayTicks;
            this.alpha = alpha;
            this.shakeTicks = shakeTicks;
            this.shakeStrength = shakeStrength;
            this.freezeTicks = freezeTicks;
            this.fovKick = fovKick;
        }

        public int overlayTicks() { return overlayTicks; }
        public float alpha() { return alpha; }
        public int shakeTicks() { return shakeTicks; }
        public float shakeStrength() { return shakeStrength; }
        public int freezeTicks() { return freezeTicks; }
        public float fovKick() { return fovKick; }
    }

    public record WindupCue(int windupTicks, int runeMoteCount, FxKinds.Orb handOrbKind, float handOrbRadius) {
        public static WindupCue forTier(TierProfile tier) {
            return new WindupCue(tier.windupTicks(), 3 + tier.tier(), FxKinds.Orb.HOLLOW_SHELL, 0.28F + tier.tier() * 0.05F);
        }
    }

    public record ReleaseCue(ReleaseMode mode, FxKinds.Orb muzzleFlashKind, int muzzleParticleBurst, FirstPersonPreset casterPreset, FxKinds.Overlay casterOverlay) {
        public static final ReleaseCue DEFAULT = new ReleaseCue(ReleaseMode.FUNNEL, FxKinds.Orb.SPARK_BURST, 10, FirstPersonPreset.CASTER_LIGHT, FxKinds.Overlay.VIGNETTE);
    }

    public record TrailSpec(FxKinds.Smoke kind, int emitPerTick, float size, int lifeTicks, float spread, int trailSegments, float stretch) {
        public static final TrailSpec NONE = new TrailSpec(FxKinds.Smoke.DUST, 0, 0.0F, 0, 0.0F, 0, 0.0F);

        public boolean active() {
            return emitPerTick > 0 || trailSegments > 0;
        }
    }

    public enum MarkOrientation {
        SURFACE_NORMAL, FLOOR, AIM
    }

    public record ImpactSpec(
            FxKinds.Orb flashKind,
            float flashSize,
            FxKinds.Mark markKind,
            int markTicks,
            MarkOrientation orientation,
            boolean stampDeliveryCircle,
            FxKinds.Smoke matterKind,
            int matterCount,
            float matterSpeed,
            int shakeTicks,
            int hitstopTicks,
            FirstPersonPreset casterPreset,
            FirstPersonPreset victimPreset,
            FxKinds.Overlay victimOverlay) {
        public static ImpactSpec forTier(TierProfile tier, FxKinds.Mark mark, FxKinds.Smoke matter, FxKinds.Overlay victimOverlay) {
            int t = tier.tier();
            return new ImpactSpec(FxKinds.Orb.BLOOM_FLASH, 0.8F + t * 0.3F, mark, 30 + t * 30, MarkOrientation.SURFACE_NORMAL, true, matter, 12 + t * 12, 0.25F + t * 0.08F, t >= 3 ? 4 + t : 0, tier.hitstopTicks(), FirstPersonPreset.HIT_CONFIRM, t >= 3 ? FirstPersonPreset.VICTIM_HEAVY : FirstPersonPreset.VICTIM_HIT, victimOverlay);
        }
    }

    public record LingerCue(FxKinds.Mark decalKind, int decalTicks, int residualMoteRate) {
        public static final LingerCue NONE = new LingerCue(FxKinds.Mark.SCORCH_DECAL, 0, 0);
    }

    public record FirstPersonSpec(FirstPersonPreset cast, FxKinds.Overlay castOverlay, FirstPersonPreset hitConfirm, FirstPersonPreset hitBy) {
        public static final FirstPersonSpec DEFAULT = new FirstPersonSpec(FirstPersonPreset.CASTER_LIGHT, FxKinds.Overlay.VIGNETTE, FirstPersonPreset.HIT_CONFIRM, FirstPersonPreset.VICTIM_HIT);
    }

    public record SoundCue(Holder<SoundEvent> sound, float volume, float pitch, float jitter) {
        public static SoundCue of(SoundEvent sound, float volume, float pitch) {
            return new SoundCue(Holder.direct(sound), volume, pitch, 0.06F);
        }

        public static SoundCue of(Holder<SoundEvent> sound, float volume, float pitch) {
            return new SoundCue(sound, volume, pitch, 0.06F);
        }

        public String key() {
            return sound.unwrapKey().map(k -> k.location().toString()).orElse("direct") + "@" + pitch + "x" + volume;
        }
    }

    /** School bed under every cast plus one unique accent per phase. */
    public record SoundSpec(SoundCue bed, SoundCue cast, SoundCue release, SoundCue impact, SoundCue linger) {
        public static SoundSpec defaults(SchoolMaterial material, int tier) {
            float pitch = 1.0F - tier * 0.055F;
            SoundCue bed = switch (material) {
                case FIRE -> SoundCue.of(SoundEvents.FIRECHARGE_USE, 0.5F, pitch);
                case WATER -> SoundCue.of(SoundEvents.PLAYER_SPLASH, 0.45F, pitch);
                case LIGHT -> SoundCue.of(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, pitch + 0.2F);
                case VOID -> SoundCue.of(SoundEvents.WARDEN_HEARTBEAT, 0.6F, pitch);
                case SPATIAL -> SoundCue.of(SoundEvents.ENDERMAN_TELEPORT, 0.4F, pitch + 0.3F);
                case SOUL -> SoundCue.of(SoundEvents.SOUL_ESCAPE.value(), 0.6F, pitch);
                // Forbidden schools. Without these five they would all share the beacon fallback,
                // which is the one sound in the set that reads as friendly.
                case BLOOD -> SoundCue.of(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.6F, pitch - 0.3F);
                case DARK -> SoundCue.of(SoundEvents.EVOKER_PREPARE_SUMMON, 0.55F, pitch - 0.2F);
                case CHAOS -> SoundCue.of(SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, pitch + 0.3F);
                case PRIMORDIAL -> SoundCue.of(SoundEvents.TRIDENT_THUNDER.value(), 0.5F, pitch - 0.5F);
                case ELDRITCH -> SoundCue.of(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.45F, pitch - 0.4F);
                default -> SoundCue.of(SoundEvents.BEACON_AMBIENT, 0.6F, pitch + 0.4F);
            };
            return new SoundSpec(bed,
                    SoundCue.of(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, pitch + 0.1F),
                    SoundCue.of(SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, pitch),
                    SoundCue.of(SoundEvents.GENERIC_EXPLODE.value(), 0.35F, pitch + 0.4F),
                    SoundCue.of(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, pitch - 0.2F));
        }
    }

    /** Enlarged render AABB extents (blocks) for shouldRender. */
    public record BoundsSpec(float horizontal, float up, float down) {
        public static final BoundsSpec DEFAULT = new BoundsSpec(3.0F, 3.0F, 1.0F);
    }
}
