package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.Projection;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordMath;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What the Array does to its wielder while they are doing something else.
 *
 * <p>Four passives and not one of them carries a number of its own: every one reads the shape the
 * wielder authored. Sword Heart makes the mana pool a function of how much steel is in the air, so
 * spending a blade lowers the ceiling and re-manning a bearing raises it. Ward of the Array has no
 * targeting at all, because <em>the shape already is the choice</em> - a blow is turned if and only
 * if it arrives down a bearing the wielder chose to man, so the wielder who spent four presses
 * covering their flanks is the one whose flanks are covered. Returning changes where spent Edge
 * goes and how fast it returns. Mirror of the Array has no hook in this file at all and says why
 * below.
 *
 * <p>The frame used here is the wielder's own body centre facing their own look at scale one, which
 * is {@code Bind.HELD} - the only bind describable without the live half. A wielder whose frame has
 * been set down, ridden, bound to a body or sunk under a point has a frame owned by the sword
 * service, and the ward reads that one instead the moment it exists.
 */
public final class SwordPassives implements ClassPassiveHandler {

    /** The offset from a player's feet to the frame origin: their body centre, not their eyes. */
    private static final double BODY_CENTRE = 0.9D;

    /** A blow arriving from nowhere has no bearing to be answered down. */
    private static final double NEAR_ZERO = 1.0e-6D;

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.SWORD_HEART.id(),
                MagicPassiveContent.WARD_OF_THE_ARRAY.id(),
                MagicPassiveContent.RETURNING.id(),
                // Mirror of the Array is a flag rather than a behaviour: Projection.mirror() reads
                // it when Below and the ward ask the Array for its shape, and no hook is called. It
                // is claimed here because ClassPassiveEffectsTest matches the registry against the
                // handlers in BOTH directions, so an unclaimed id is a red build rather than a
                // quiet one. That is the right trade and this comment is why it looks odd.
                MagicPassiveContent.MIRROR_OF_THE_ARRAY.id());
    }

    /**
     * Sword Heart: the pool is the blades.
     *
     * <p>{@link ClassPassiveEffects} re-sums this on the slow tick, so the ceiling falls the moment
     * a station is emptied by a volley, a ward or a shed. The fall is the point - every skill that
     * spends steel also spends the mage - and it is why {@code PlayerMagicState} has to clamp
     * current mana down with the maximum rather than leave the HUD ring overrunning its own track.
     */
    @Override
    public int bonusMaxMana(ServerPlayer player, PlayerMagicState state) {
        if (!state.isPassiveEnabled(MagicPassiveContent.SWORD_HEART.id())) {
            return 0;
        }
        return SwordMath.bonusMaxMana(state.swordArray().manned());
    }

    /**
     * Ward of the Array: a blow is turned by the bearing it arrives down, or it is not turned.
     *
     * <p>This runs after vanilla mitigation and before {@code state.absorbDamage}, so a warded hit
     * also saves barrier. That ordering costs nothing and it is the whole defensive identity of the
     * class.
     *
     * <p>Only melee and magic are answered here. A projectile is turned by the array entity's own
     * tick instead, because an arrow has to be deflected before it lands rather than discounted
     * after it has.
     */
    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        if (amount <= 0.0F || !state.isPassiveEnabled(MagicPassiveContent.WARD_OF_THE_ARRAY.id())) {
            return amount;
        }
        SwordArray array = state.swordArray();
        if (array.manned() == 0) {
            return amount;
        }
        double[] incoming = approachBearing(player, source);
        if (incoming == null) {
            return amount;
        }
        int slot = Projection.covers(array, heldFrame(player), incoming);
        if (slot < 0) {
            return amount;
        }
        Station station = array.station(slot);
        if (!station.manned()) {
            return amount;
        }
        return Math.max(0.0F, amount - (float) SwordMath.wardAbsorb(station.edge()));
    }

    /**
     * The frame a wielder carries: origin at their body centre, facing their look, scale one.
     *
     * <p>Body centre and not eye height. A shape pinned to the eyes sits most of a block above the
     * shape the player authored while standing still, and every bearing in it is then wrong by that
     * much at close range - which is the range a ward is read at.
     */
    public static Frame heldFrame(ServerPlayer player) {
        return new Frame(player.getX(), player.getY() + BODY_CENTRE, player.getZ(),
                player.getYRot(), player.getXRot(), 1.0F);
    }

    /**
     * The unit vector from the frame origin toward whatever dealt the blow.
     *
     * <p>Null when the source has no position - starvation, a fall, the void - so such a blow is
     * unwardable by construction rather than by a special case: there is no bearing to answer.
     */
    private static double[] approachBearing(ServerPlayer player, DamageSource source) {
        Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        Vec3 at = from != null ? from.position() : source.getSourcePosition();
        if (at == null) {
            return null;
        }
        double dx = at.x - player.getX();
        double dy = at.y - (player.getY() + BODY_CENTRE);
        double dz = at.z - player.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < NEAR_ZERO) {
            return null;
        }
        return new double[] {dx / length, dy / length, dz / length};
    }

    /**
     * Declared although it clears nothing, because {@code handlersDoNotShareScratchAcrossPlayers}
     * reflects over {@code getDeclaredMethods()} and a handler that omits it fails the build. That
     * is the right default: this class keeps no per-player scratch today, and the check is what
     * will notice on the day somebody gives it some.
     *
     * <p>Returning's recovery clock and the pool of spent Edge are the live half, which belongs to
     * the sword service and is deliberately never saved; its own {@code forget} is wired beside
     * {@code PileService.forget}.
     */
    @Override
    public void forget(UUID playerId) {
        // No scratch to drop.
    }
}
