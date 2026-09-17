package com.efkrdnz.magical.magic.mana;

import com.efkrdnz.magical.entity.domain.ManaWeaveEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * What the Weaves standing over a caster have to say about the cast they are about to make.
 *
 * <p>This is the half of the Authority of Mana that does the work, and it is a <em>read</em>, not a
 * push. A subspace reaches out every tick and moves what it finds; a Weave sits still and is
 * consulted at the one moment that matters. So the cast path asks here, and a player with no Weave
 * over them pays exactly what they always paid.
 *
 * <p><b>Two Weaves cancel.</b> When more than one Weave binds the same aspect for the same caster
 * and they do not agree, the aspect goes neutral across the overlap - a dead zone where magic
 * behaves ordinarily and neither wielder is legislating. That is not a special case bolted on; it
 * falls out of two gods giving the same order differently, and it is the cleanest in-fiction
 * argument for one wielder per Authority.
 */
public final class WeaveLaw {

    private WeaveLaw() {}

    /** Every Weave whose region the caster is standing in, theirs or anyone else's. */
    public static List<ManaWeaveEntity> over(Player caster) {
        List<ManaWeaveEntity> found = new ArrayList<>();
        if (caster == null || caster.level() == null) {
            return found;
        }
        for (ManaWeaveEntity weave : caster.level().getEntitiesOfClass(ManaWeaveEntity.class,
                caster.getBoundingBox().inflate(ManaWeaveEntity.MAX_RADIUS))) {
            if (weave.isAlive() && weave.covers(caster)) {
                found.add(weave);
            }
        }
        return found;
    }

    /**
     * The operation ruling this aspect for this caster, or null when nothing does - either because
     * no Weave binds them, or because two disagreed and cancelled each other out.
     */
    public static WeaveOperation ruling(Player caster, WeaveAspect aspect) {
        UUID id = caster == null ? null : caster.getUUID();
        WeaveOperation agreed = null;
        for (ManaWeaveEntity weave : over(caster)) {
            if (!weave.binds(aspect, id)) {
                continue;
            }
            WeaveOperation operation = weave.operationOn(aspect);
            if (agreed == null) {
                agreed = operation;
            } else if (agreed != operation) {
                return null;
            }
        }
        return agreed;
    }

    /**
     * What this caster actually pays, after the Weave has had its say. INVERT gives the mana back
     * rather than taking it, which is why the factor is allowed to go negative.
     */
    public static int cost(Player caster, int baseCost) {
        WeaveOperation ruling = ruling(caster, WeaveAspect.COST);
        if (ruling == null || ruling == WeaveOperation.LOCK) {
            return baseCost;
        }
        return Math.round(baseCost * ruling.factor());
    }

    /** What this caster actually waits, after the Weave has had its say. */
    public static int cooldown(Player caster, int baseTicks) {
        WeaveOperation ruling = ruling(caster, WeaveAspect.COOLDOWN);
        if (ruling == null || ruling == WeaveOperation.LOCK) {
            return baseTicks;
        }
        return Math.max(0, Math.round(baseTicks * Math.abs(ruling.factor())));
    }

    /**
     * True when no magic of this caster's may function here at all.
     *
     * <p>This is anti-magic, and it is not a skill - it is SCHOOL set to ZERO, one cell of an
     * ordinary grammar, sitting in the same menu as raising a cost. That is what makes it
     * frightening rather than a button: the wielder chose it out of thirty-six other things.
     */
    public static boolean silenced(Player caster) {
        return ruling(caster, WeaveAspect.SCHOOL) == WeaveOperation.ZERO;
    }

    /** True when a cast may be paid for and started but will resolve into nothing. */
    public static boolean unmanifested(Player caster) {
        return ruling(caster, WeaveAspect.MANIFESTATION) == WeaveOperation.ZERO;
    }

    /** Convenience for the server paths that already hold a ServerPlayer. */
    public static boolean silenced(ServerPlayer caster) {
        return silenced((Player) caster);
    }
}
