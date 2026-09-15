package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.entity.ForgeStrikeEntity;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.StrikeSpec;
import com.efkrdnz.magical.forge.strike.TemperStats;
import com.efkrdnz.magical.magic.ForgeComboService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Debug: spawns forged strikes without a forged weapon, a combo window or a left click.
 *
 * <p>There is no other way to look at one. A strike is born from an attack press holding an item
 * that carries a {@code magical:forged_weapon} component, which an unattended capture cannot
 * perform — {@code -PautoClick} only reaches an open screen and {@code -PautoHold} only a registered
 * key mapping, and neither is the vanilla attack button. So the strike visuals could not be captured
 * at all, and a visual that cannot be captured cannot be iterated on.
 *
 * <p>Everything here goes through the real {@link ForgeStrikeMath#resolve} and
 * {@link ForgeStrikeEntity#spawn}, and asks {@link ForgeComboService#originFor} for the same origin
 * a real press would use. A bench that built its own spec or picked its own origin would be showing
 * a strike the game never spawns, which is worse than showing nothing.
 */
public final class ForgeStrikeBench {

    /** How far apart the eight families stand in a row, in blocks. */
    private static final double BENCH_SPACING = 5.0;

    /** A bench weapon carries no temper, no modifiers and no program: the form and element only. */
    private static final int BENCH_QUALITY = 100;

    private ForgeStrikeBench() {}

    /** One strike of {@code formId} in {@code elementId}, aimed where the player is looking. */
    public static boolean one(ServerPlayer player, ResourceLocation formId, ResourceLocation elementId,
            WeaponClass archetype, ForgeGrade grade, boolean heavy) {
        Optional<FormDefinition> form = ForgeForms.get(formId);
        Optional<ElementDefinition> element = ForgeElements.get(elementId);
        if (form.isEmpty() || element.isEmpty()) {
            return false;
        }
        Vec3 look = look(player);
        spawn(player, form.get(), element.get(), archetype, grade, heavy, look, Vec3.ZERO);
        return true;
    }

    /**
     * Every form of one element at once, stood in a row across the player's view.
     *
     * <p>The row is the point: eight strikes side by side is the only way to see whether the forms
     * actually read as eight different moves rather than one arc in three planes.
     *
     * @return how many were spawned
     */
    public static int bench(ServerPlayer player, ResourceLocation elementId, WeaponClass archetype,
            ForgeGrade grade, boolean heavy) {
        Optional<ElementDefinition> element = ForgeElements.get(elementId);
        if (element.isEmpty()) {
            return 0;
        }
        List<FormDefinition> forms = new ArrayList<>();
        for (FormDefinition form : ForgeForms.all()) {
            // One per family: the archetype forms reuse a family that is already in the row, and a
            // duplicate would take a slot without showing anything new.
            if (forms.stream().noneMatch(seen -> seen.family() == form.family())) {
                forms.add(form);
            }
        }
        Vec3 look = look(player);
        Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
        double start = -(forms.size() - 1) * BENCH_SPACING * 0.5;
        for (int i = 0; i < forms.size(); i++) {
            Vec3 offset = right.scale(start + i * BENCH_SPACING);
            spawn(player, forms.get(i), element.get(), archetype, grade, heavy, look, offset);
        }
        return forms.size();
    }

    private static void spawn(ServerPlayer player, FormDefinition form, ElementDefinition element,
            WeaponClass archetype, ForgeGrade grade, boolean heavy, Vec3 look, Vec3 offset) {
        ServerLevel level = player.serverLevel();
        ForgedWeapon weapon = new ForgedWeapon(element.id(), grade, Optional.empty(), List.of(form.id()),
                List.of(), List.of(), BENCH_QUALITY, level.getGameTime());
        StrikeSpec spec = ForgeStrikeMath.resolve(form.stats(), TemperStats.NONE, archetype, ModifierStack.EMPTY,
                grade, BENCH_QUALITY, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), heavy, 0f,
                false, 0, element.kind());
        Vec3 origin = ForgeComboService.originFor(player, spec.family(), look, spec.reach()).add(offset);
        ForgeStrikeEntity.spawn(level, player, spec, weapon, element, form, origin, look, false,
                StrikeLoadout.NO_PRIMARY_TARGET, archetype);
    }

    private static Vec3 look(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        return look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
    }
}
