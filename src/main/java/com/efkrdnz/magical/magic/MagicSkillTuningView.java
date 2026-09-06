package com.efkrdnz.magical.magic;

import java.util.List;

public final class MagicSkillTuningView {
    private static final List<MagicTuningStat> STANDARD = List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SPEED, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
    private static final List<MagicTuningStat> RESTORATION = List.of(MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
    private static final List<MagicTuningStat> BARRIER = List.of(MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
    private static final List<MagicTuningStat> UTILITY_MOVEMENT = List.of(MagicTuningStat.SIZE, MagicTuningStat.SPEED, MagicTuningStat.EFFICIENCY);
    private static final List<MagicTuningStat> MARK = List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
    private static final List<MagicTuningStat> NONE = List.of();

    private MagicSkillTuningView() {}

    public static List<MagicTuningStat> statsFor(MagicSkillDefinition skill) {
        if (skill == null) {
            return STANDARD;
        }
        if (MagicContent.VAULT_OF_AVARICE.id().equals(skill.id()) || MagicContent.CREATE_SUBSPACE.id().equals(skill.id()) || MagicContent.MANIPULATE_SPACE.id().equals(skill.id()) || MagicContent.POCKET_DIMENSION.id().equals(skill.id()) || MagicContent.SPATIAL_ARSENAL.id().equals(skill.id()) || MagicContent.SOUL_VOW.id().equals(skill.id())) {
            return NONE;
        }
        if (MagicContent.GABRIEL.id().equals(skill.id())) {
            return NONE;
        }
        if (MagicContent.BLACK_FLAMES.id().equals(skill.id())) {
            return NONE;
        }
        if (MagicContent.BLACK_FLAMES_CAST.id().equals(skill.id())) {
            return List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SPEED, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.BLACK_FLAMES_IMBUE.id().equals(skill.id())) {
            return List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.BLACK_FLAMES_BRAND.id().equals(skill.id())) {
            return List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.SINGULARITY.id().equals(skill.id()) || MagicContent.DIMENSIONAL_GUILLOTINE.id().equals(skill.id())) {
            return List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SPEED, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.SOUL_VALLEY.id().equals(skill.id())) {
            return List.of(MagicTuningStat.SIZE, MagicTuningStat.SPEED, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.GABRIEL_ULTIMATE_PROTECTION.id().equals(skill.id())) {
            return List.of(MagicTuningStat.SIZE, MagicTuningStat.SPEED, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.GABRIEL_JUDGEMENT.id().equals(skill.id())) {
            return List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.GABRIEL_HOLY_FIELD.id().equals(skill.id())) {
            return List.of(MagicTuningStat.DAMAGE, MagicTuningStat.SPEED, MagicTuningStat.DURATION, MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.GABRIEL_PERFECT_SEAL.id().equals(skill.id())) {
            return List.of(MagicTuningStat.SIZE, MagicTuningStat.EFFICIENCY);
        }
        if (MagicContent.SPACE_WALKER.id().equals(skill.id())) {
            return NONE;
        }
        if (com.efkrdnz.magical.magic.cast.SkillCastRegistry.has(skill.id())) {
            return fromView(com.efkrdnz.magical.magic.cast.SkillCastRegistry.get(skill.id()).tuning());
        }
        if (skill.type() == MagicSkillType.BARRIER) {
            return BARRIER;
        }
        return STANDARD;
    }

    /** The registry-driven roster declares which stats matter through its handler. */
    private static List<MagicTuningStat> fromView(com.efkrdnz.magical.magic.cast.TuningView view) {
        List<MagicTuningStat> stats = new java.util.ArrayList<>(5);
        if (view.damage()) {
            stats.add(MagicTuningStat.DAMAGE);
        }
        if (view.speed()) {
            stats.add(MagicTuningStat.SPEED);
        }
        if (view.duration()) {
            stats.add(MagicTuningStat.DURATION);
        }
        if (view.size()) {
            stats.add(MagicTuningStat.SIZE);
        }
        if (view.efficiency()) {
            stats.add(MagicTuningStat.EFFICIENCY);
        }
        return List.copyOf(stats);
    }

    public static String labelKey(MagicSkillDefinition skill, MagicTuningStat stat) {
        if (skill != null) {
            if (MagicContent.BLACK_FLAMES_CAST.id().equals(skill.id())) {
                return switch (stat) {
                    case DAMAGE -> "screen.magical.tuning.abyssal_heat";
                    case SPEED -> "screen.magical.tuning.ember_drive";
                    case SIZE -> "screen.magical.tuning.cursed_ground";
                    case EFFICIENCY -> "screen.magical.tuning.mana_mercy";
                    default -> stat.translationKey();
                };
            }
            if (MagicContent.BLACK_FLAMES_IMBUE.id().equals(skill.id())) {
                return switch (stat) {
                    case DAMAGE -> "screen.magical.tuning.cursed_edge";
                    case SIZE -> "screen.magical.tuning.imbue_duration";
                    case EFFICIENCY -> "screen.magical.tuning.mana_mercy";
                    default -> stat.translationKey();
                };
            }
            if (MagicContent.BLACK_FLAMES_BRAND.id().equals(skill.id())) {
                return switch (stat) {
                    case DAMAGE -> "screen.magical.tuning.brand_punishment";
                    case SIZE -> "screen.magical.tuning.brand_duration";
                    case EFFICIENCY -> "screen.magical.tuning.mana_mercy";
                    default -> stat.translationKey();
                };
            }
            if (MagicContent.GABRIEL_ULTIMATE_PROTECTION.id().equals(skill.id())) {
                return switch (stat) {
                    case SIZE -> "screen.magical.tuning.nullification_cost";
                    case SPEED -> "screen.magical.tuning.mana_gate";
                    case EFFICIENCY -> "screen.magical.tuning.mana_mercy";
                    default -> stat.translationKey();
                };
            }
            if (MagicContent.GABRIEL_JUDGEMENT.id().equals(skill.id())) {
                return switch (stat) {
                    case DAMAGE -> "screen.magical.tuning.judgement_force";
                    case SIZE -> "screen.magical.tuning.beam_radius";
                    case EFFICIENCY -> "screen.magical.tuning.mana_mercy";
                    default -> stat.translationKey();
                };
            }
            if (MagicContent.GABRIEL_PERFECT_SEAL.id().equals(skill.id())) {
                return switch (stat) {
                    case SIZE -> "screen.magical.tuning.seal_reach";
                    case EFFICIENCY -> "screen.magical.tuning.mana_mercy";
                    default -> stat.translationKey();
                };
            }
            if (MagicContent.GABRIEL_HOLY_FIELD.id().equals(skill.id())) {
                return switch (stat) {
                    case DAMAGE -> "screen.magical.tuning.damage_per_hit";
                    case SPEED -> "screen.magical.tuning.hit_speed";
                    case DURATION -> "screen.magical.tuning.ability_duration";
                    case SIZE -> "screen.magical.tuning.ability_radius";
                    case EFFICIENCY -> "screen.magical.tuning.ability_efficiency";
                };
            }
            if (com.efkrdnz.magical.magic.cast.SkillCastRegistry.has(skill.id())) {
                com.efkrdnz.magical.magic.cast.TuningView view = com.efkrdnz.magical.magic.cast.SkillCastRegistry.get(skill.id()).tuning();
                String custom = switch (stat) {
                    case DAMAGE -> view.damageLabelKey();
                    case SPEED -> view.speedLabelKey();
                    case SIZE -> view.sizeLabelKey();
                    case DURATION -> view.durationLabelKey();
                    case EFFICIENCY -> null;
                };
                return custom != null ? custom : stat.translationKey();
            }
            if (skill.type() == MagicSkillType.BARRIER) {
                return switch (stat) {
                    case SIZE -> "screen.magical.tuning.barrier_output";
                    case EFFICIENCY -> "screen.magical.tuning.mana_mercy";
                    default -> stat.translationKey();
                };
            }
        }
        return stat.translationKey();
    }
}
