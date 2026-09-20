package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Which cell of the sigil emblem atlas stands for a skill, a sin or a status on the HUD.
 *
 * <p>The rule for skills is that the HUD icon is the emblem of the skill's own cast circle, from
 * its {@link VisualProfiles visual profile}, so what is on the card is what appears under the
 * player's feet when they cast it. Only skills whose profile has no emblem fall through to the
 * table here, and then to their school's stamp. Sins and statuses are drawn at eight pixels, where
 * a stamp reads and an emblem does not.
 */
public final class HudGlyphs {
    /** Explicit cells for skills whose profile carries no emblem; checked in-game, edited by hand. */
    public static final Map<ResourceLocation, Integer> OVERRIDES = new HashMap<>();

    private static final Map<ResourceLocation, Integer> FALLBACK = new HashMap<>();

    static {
        fallback("flare_ring", EmblemId.FLAME.atlasCell());
        fallback("sovereign_aegis", StampId.KITE.atlasCell());
        fallback("judgement", StampId.CROSS.atlasCell());
        fallback("divine_divider", StampId.BAR.atlasCell());
        fallback("circle_arsenal", StampId.RING.atlasCell());
        // The four incantations: one plain counting mark each, I to IV.
        fallback("incantation_1", StampId.DOT.atlasCell());
        fallback("incantation_2", StampId.BAR.atlasCell());
        fallback("incantation_3", StampId.TRIANGLE.atlasCell());
        fallback("incantation_4", StampId.SQUARE.atlasCell());
        fallback("grimoire", StampId.DIAMOND.atlasCell());
        fallback("vault_of_avarice", StampId.SQUARE.atlasCell());
        fallback("create_subspace", StampId.RING.atlasCell());
        fallback("manipulate_space", StampId.LINK.atlasCell());
        fallback("pocket_dimension", StampId.DIAMOND.atlasCell());
        fallback("spatial_arsenal", StampId.CHEVRON.atlasCell());
        fallback("soul_vow", StampId.LINK.atlasCell());
    }

    private HudGlyphs() {}

    private static void fallback(String path, int cell) {
        FALLBACK.put(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path), cell);
    }

    /** The atlas cell for a skill, or -1 for an unknown id. */
    public static int skillCell(ResourceLocation id) {
        if (id == null) {
            return -1;
        }
        Integer override = OVERRIDES.get(id);
        if (override != null) {
            return override;
        }
        MagicSkillDefinition definition = MagicContent.get(id);
        if (definition == null) {
            return -1;
        }
        EmblemId emblem = VisualProfiles.of(id).castCircle().emblem();
        if (emblem != null && emblem != EmblemId.BLANK) {
            return emblem.atlasCell();
        }
        Integer fallback = FALLBACK.get(id);
        if (fallback != null) {
            return fallback;
        }
        return SchoolMaterial.of(definition.school()).defaultStamp().atlasCell();
    }

    /** The stamp for a sin passive (or its curse), by the sin's registration order. */
    public static int sinCell(ResourceLocation passiveId) {
        if (passiveId == null) {
            return StampId.STAR4.atlasCell();
        }
        String path = passiveId.getPath();
        if (path.startsWith("sin_pride")) {
            return StampId.STAR4.atlasCell();
        }
        if (path.startsWith("sin_greed")) {
            return StampId.DIAMOND.atlasCell();
        }
        if (path.startsWith("sin_lust")) {
            return StampId.TEARDROP.atlasCell();
        }
        if (path.startsWith("sin_envy")) {
            return StampId.EYE.atlasCell();
        }
        if (path.startsWith("sin_gluttony")) {
            return StampId.TOOTH.atlasCell();
        }
        if (path.startsWith("sin_wrath")) {
            return StampId.FLAME.atlasCell();
        }
        if (path.startsWith("sin_sloth")) {
            return StampId.HOURGLASS.atlasCell();
        }
        return MagicPassiveContent.get(passiveId) != null && MagicPassiveContent.get(passiveId).curse()
                ? StampId.THORN.atlasCell()
                : StampId.STAR4.atlasCell();
    }

    public static int statusCell(MagicStatus status) {
        return switch (status) {
            case FACING_PINNED -> StampId.NEEDLE.atlasCell();
            case EXILED -> StampId.RING.atlasCell();
            case PUPPETED -> StampId.CROSS.atlasCell();
            case SILENCED -> StampId.BAR.atlasCell();
            case REACH_CLAMPED -> StampId.CHEVRON.atlasCell();
            case ROOTED -> StampId.TRIANGLE.atlasCell();
            case ASLEEP -> StampId.CRESCENT.atlasCell();
            case DAZZLED -> StampId.STAR4.atlasCell();
            case POLYMORPHED -> StampId.FOOTPRINT.atlasCell();
            case REVEALED, GAZE -> StampId.EYE.atlasCell();
            case BRANDED -> StampId.FLAME.atlasCell();
            case INFECTED -> StampId.DROP.atlasCell();
            case TAUNTED -> StampId.ARROW.atlasCell();
            case IMMOVABLE -> StampId.SQUARE.atlasCell();
            case UNHALLOWED -> StampId.BONE.atlasCell();
            case HARRIED -> StampId.FEATHER.atlasCell();
            case COMPRESSED -> StampId.DOT.atlasCell();
        };
    }

    /** The mark of a curse taken, drawn in the ink register. */
    public static int curseCell() {
        return StampId.THORN.atlasCell();
    }

    public static int classCell() {
        return StampId.CHEVRON.atlasCell();
    }

    public static int authorityCell() {
        return EmblemId.CROWN.atlasCell();
    }

    public static int raceCell(ResourceLocation raceId) {
        String path = raceId == null ? "" : raceId.getPath();
        return switch (path) {
            case "elf" -> StampId.LEAF.atlasCell();
            case "dwarf" -> StampId.GEAR.atlasCell();
            case "beastkin" -> StampId.FOOTPRINT.atlasCell();
            case "demon" -> StampId.THORN.atlasCell();
            case "celestial" -> StampId.STAR4.atlasCell();
            default -> StampId.DOT.atlasCell();
        };
    }
}
