package com.efkrdnz.magical.forge;

import java.util.Optional;

import com.efkrdnz.magical.forge.chain.ForgeStep;
import com.efkrdnz.magical.forge.chain.Payload;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.StrikeSpec;
import com.efkrdnz.magical.entity.ForgeStrikeEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Fires the step nested inside a strike when its trigger comes due.
 *
 * <p>A payload is not a press. It costs no combo position, claims no vanilla hit, and lands wherever
 * the carrier was rather than wherever the smith is standing - which is the whole point of the late
 * trigger: a crescent thrown downrange can put a slam where it stopped.
 */
public final class ForgePayloadService {

    /** What a payload is worth next to the press that carried it. */
    public static final float PAYLOAD_SCALE = 0.65f;

    private ForgePayloadService() {}

    /**
     * Spawns the payload of {@code carrier} at {@code position}, travelling along {@code direction}.
     *
     * <p>Does nothing when the strike has no payload, when it is itself an echo or a payload, or
     * when the nested form is one this build does not know. Nesting stops one deep on purpose: a
     * payload that could carry its own payload turns one press into an unbounded cascade.
     */
    public static void fire(ServerLevel level, ServerPlayer owner, StrikeLoadout carrier, Vec3 position,
            Vec3 direction) {
        Optional<Payload> payload = carrier.payload();
        if (payload.isEmpty() || carrier.echo()) {
            return;
        }
        ForgeStep step = payload.get().step();
        Optional<FormDefinition> form = ForgeForms.get(ForgeIds.id(step.leadForm()));
        if (form.isEmpty()) {
            return;
        }
        // The carrier's own archetype rather than a hardcoded sword: a payload thrown off a
        // greatsword should keep the greatsword's reach and recovery, not a sword's.
        StrikeSpec spec = ForgeStrikeMath.resolve(form.get().stats(), temperOf(carrier.weapon()),
                carrier.archetype(), step.mods().without(ForgeModifierKind.ECHO), carrier.weapon().grade(),
                carrier.weapon().quality(), carrier.weaponAttack(), false, 0f, false, carrier.comboIndex(),
                carrier.element().kind()).withDamageScale(PAYLOAD_SCALE);
        ForgeStrikeEntity.spawn(level, owner, spec, carrier.weapon(), carrier.element(), form.get(),
                position, direction, false, StrikeLoadout.NO_PRIMARY_TARGET, carrier.archetype());
    }

    private static com.efkrdnz.magical.forge.strike.TemperStats temperOf(ForgedWeapon weapon) {
        return weapon.temper()
                .flatMap(ForgeTempers::get)
                .map(TemperDefinition::stats)
                .orElse(com.efkrdnz.magical.forge.strike.TemperStats.NONE);
    }
}
