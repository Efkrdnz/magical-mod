package com.efkrdnz.magical.boss.unwaking;

import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A short-lived server threat using the same response route as ordinary spell counters. */
public final class UnwakingCounterThreatEntity extends Entity implements CounterableSkillThreat {
    /** The bone white the attack bodies are drawn in, so the clash matches what was parried. */
    private static final int BODY_INK = 0xE9E7E2;
    private UUID runId, defenderId;
    private MagicSkillDefinition incoming = MagicContent.DIVINE_DIVIDER;
    private UnwakingGodEntity owner;
    private long expires, openedAt;
    private Runnable response;
    private boolean answered;
    private UnwakingHazard.Kind attack;
    private Vec3 aimTarget;
    private Runnable aimFailed;

    public UnwakingCounterThreatEntity(EntityType<?> type, Level level) { super(type, level); setInvisible(true); }
    static UnwakingCounterThreatEntity create(ServerPlayer player, UnwakingGodEntity owner, MagicSkillDefinition incoming, int window, Runnable response) {
        var marker = new UnwakingCounterThreatEntity(MagicalEntities.UNWAKING_COUNTER.get(), player.serverLevel());
        marker.defenderId = player.getUUID(); marker.runId = owner.runId(); marker.owner = owner;
        marker.incoming = incoming; marker.response = response; marker.openedAt=player.level().getGameTime(); marker.expires = marker.openedAt + window;
        marker.setPos(player.position()); return marker;
    }
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && (response == null || level.getGameTime() > expires || owner == null || owner.isRemoved()
                || !UnwakingEncounterService.get(level.getServer()).participant(defenderId))) close();
    }
    void close() {
        if (level() instanceof ServerLevel level && defenderId != null) {
            ServerPlayer defender = level.getServer().getPlayerList().getPlayer(defenderId);
            if (defender != null) MagicCounterService.releaseThreat(defender, this);
        }
        response = null; discard();
    }
    @Override public boolean canBeCounteredBy(LivingEntity defender) {
        return !answered && !isRemoved() && response != null && defender.isAlive() && defender.getUUID().equals(defenderId)
                && defender.level() == level() && level().getGameTime() <= expires && owner != null && runId.equals(owner.runId());
    }
    @Override public Entity counterEntity() { return this; }
    @Override public Entity counterOwner() { return owner; }
    @Override public ResourceLocation counterSkillId() { return incoming.id(); }
    @Override public MagicAttribute counterAttribute() { return incoming.attribute(); }
    void attack(UnwakingHazard.Kind attack) { this.attack = attack; }
    void aim(Vec3 target, Runnable failed) { aimTarget=target; aimFailed=failed; }
    Vec3 aimPoint() { return aimTarget; }
    @Override public boolean validateCounterResponse(ServerPlayer player) {
        boolean valid=aimTarget==null||UnwakingAssaultGeometry.aimed(player.getEyePosition(),player.getLookAngle(),aimTarget);
        if(!valid && aimFailed!=null) aimFailed.run();
        return valid;
    }
    @Override public int counterTier() { return attack == null ? incoming.tier() : attack.powerTier(); }
    @Override public String counterNameKey() { return attack == null ? "" : attack.nameKey(); }
    @Override public void onCountered(ServerLevel level, ServerPlayer player, MagicSkillDefinition skill, Vec3 position) { accept(level, player, position, skill.color()); }
    @Override public void onForcedCounter(ServerLevel level, ServerPlayer player, Vec3 position) { accept(level, player, position, 0xFFD166); }
    @Override public void onGluttonyCountered(ServerLevel level, ServerPlayer player, Vec3 position) { accept(level, player, position, 0xAC64F5); }
    private void accept(ServerLevel level, ServerPlayer player, Vec3 position, int color) {
        if (!canBeCounteredBy(player)) return;
        answered = true; Runnable accepted = response;
        // The same clash every other counterable threat spawns. Without it a parry here was one
        // quiet chime heard only by the person who pressed the key.
        MagicCounterService.spawnClash(level, position, BODY_INK, color);
        player.playNotifySound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.HOSTILE, 0.6F, 0.6F);
        accepted.run(); close();
    }
    @Override public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) { return false; }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
