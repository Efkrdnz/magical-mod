package com.efkrdnz.magical.boss.unwaking;

import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Owns only the brief ordinary QTE prompts belonging to this attack set. */
final class UnwakingCounterWindows {
    record Key(long attack, int contact, UUID player) {}
    private final Map<Key, UnwakingCounterThreatEntity> markers = new HashMap<>();
    private final Set<Key> offered = new HashSet<>();
    /** Bare-skill form: no attack kind behind it, so the prompt shows the skill's own rank and name. */
    void offer(long attack, int contact, ServerPlayer player, UnwakingGodEntity boss, MagicSkillDefinition incoming, int ticks, Runnable accepted, Vec3 clashAt) {
        offer(attack,contact,player,boss,incoming,null,ticks,accepted,null,null,clashAt,false);
    }
    void offer(long attack, int contact, ServerPlayer player, UnwakingGodEntity boss, UnwakingHazard.Kind kind, int ticks, Runnable accepted, Vec3 clashAt) {
        offer(attack,contact,player,boss,kind.counterSkill(),kind,ticks,accepted,null,null,clashAt,false);
    }
    /**
     * The dodge attacks: only Gluttony may answer, and if it cannot there is simply no prompt.
     *
     * <p>Every travelling body in the domain comes through here. They are movement checks, so the
     * ordinary window - which finds any skill with a favourable attribute and otherwise accepts the
     * key from anyone - would quietly convert them into reaction checks instead.
     */
    void offerDevour(long attack, int contact, ServerPlayer player, UnwakingGodEntity boss, UnwakingHazard.Kind kind, int ticks, Runnable accepted, Vec3 clashAt) {
        offer(attack,contact,player,boss,kind.counterSkill(),kind,ticks,accepted,null,null,clashAt,true);
    }
    void offerAimed(long attack, ServerPlayer player, UnwakingGodEntity boss, UnwakingHazard.Kind kind, int ticks, Runnable accepted, Vec3 target, Runnable failed, Vec3 clashAt) {
        offer(attack,0,player,boss,kind.counterSkill(),kind,ticks,accepted,target,failed,clashAt,false);
    }
    private void offer(long attack, int contact, ServerPlayer player, UnwakingGodEntity boss, MagicSkillDefinition incoming, UnwakingHazard.Kind kind, int ticks, Runnable accepted, Vec3 target, Runnable failed, Vec3 clashAt, boolean devourOnly) {
        Key key = new Key(attack, contact, player.getUUID());
        if (offered.contains(key) || ticks < 3 || ticks > 16 || MagicCounterService.hasActivePrompt(player)) return;
        // Asked before anything is spawned. A devour window usually does not prompt, and the rain
        // throws twenty bodies in eleven seconds - one discarded marker entity per body is not a
        // cost worth paying to learn the same answer.
        if (devourOnly && !MagicCounterService.canDevour(player)) return;
        var marker = UnwakingCounterThreatEntity.create(player, boss, incoming, ticks, accepted);
        marker.attack(kind);
        marker.aim(target,failed);
        if (!player.serverLevel().addFreshEntity(marker)) return;
        offered.add(key); markers.put(key, marker);
        // Out in front of the defender, along the line the attack came in on - not inside their own
        // head, where the clash renderer fades its two brightest layers for the viewer standing in it.
        Vec3 clash = clashAt != null ? clashAt : player.getEyePosition();
        if (devourOnly) {
            if (!MagicCounterService.offerGluttonyCounter(player, marker, clash, ticks)) marker.close();
            return;
        }
        if (!MagicCounterService.offerCounter(player, marker, clash, ticks))
            MagicCounterService.offerForcedCounter(player, marker, clash, ticks);
    }
    void clear() { markers.values().forEach(UnwakingCounterThreatEntity::close); markers.clear(); offered.clear(); }
    void retire(long attack) {
        markers.entrySet().removeIf(e -> { if (e.getKey().attack != attack) return false; e.getValue().close(); return true; });
        offered.removeIf(k -> k.attack == attack);
    }
    void remove(UUID player) {
        markers.entrySet().removeIf(e -> { if (!e.getKey().player.equals(player)) return false; e.getValue().close(); return true; });
        offered.removeIf(k -> k.player.equals(player));
    }
}
