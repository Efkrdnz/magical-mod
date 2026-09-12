package com.efkrdnz.magical.entity;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One counter window in front of one training-dummy cast.
 *
 * <p>It exists as an entity because {@code MagicCounterService.respond} resolves a prompt through
 * {@code level.getEntity(threatId)} and remembers every threat id it has already answered - so a
 * threat that is not a real entity can never be answered, and one that is reused can be answered
 * exactly once per session. A window per cast means a prompt per cast.
 *
 * <p>The cast is held rather than fired: the skill is handed over as a {@link Runnable} and runs
 * when the window closes unanswered. Offering the window after the spell is already in the air
 * would be a prompt with nothing behind it, which is the one thing a practice tool must not be.
 */
public final class TrainingThreatEntity extends Entity implements CounterableSkillThreat {

    /** The dummy's own sand, so an answered window clashes in the colour of the thing that threw it. */
    private static final int DUMMY_INK = 0xD9C9A8;

    private UUID defenderId;
    private TrainingDummyEntity dummy;
    private MagicSkillDefinition incoming = MagicContent.DIVINE_DIVIDER;
    private Runnable pending;
    private long expires;
    private boolean answered;

    public TrainingThreatEntity(EntityType<?> type, Level level) {
        super(type, level);
        setInvisible(true);
        noPhysics = true;
    }

    /**
     * @param window ticks the defender has to answer in; the cast runs at the end of it
     * @param pending what the dummy would have cast, run only if the window closes unanswered
     */
    public static TrainingThreatEntity create(ServerPlayer defender, TrainingDummyEntity dummy,
            MagicSkillDefinition incoming, int window, Runnable pending) {
        TrainingThreatEntity threat =
                new TrainingThreatEntity(MagicalEntities.TRAINING_THREAT.get(), defender.serverLevel());
        threat.defenderId = defender.getUUID();
        threat.dummy = dummy;
        threat.incoming = incoming;
        threat.pending = pending;
        threat.expires = defender.level().getGameTime() + Math.max(threat.minimumCounterWindowTicks(), window);
        threat.setPos(dummy.getEyePosition().lerp(defender.getEyePosition(), 0.5D));
        return threat;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer defender = level.getServer().getPlayerList().getPlayer(defenderId);
        if (defender == null || !defender.isAlive() || defender.level() != level
                || dummy == null || dummy.isRemoved()) {
            // The dummy is gone or the player left: drop the window and the cast with it.
            close();
            return;
        }
        setPos(dummy.getEyePosition().lerp(defender.getEyePosition(), 0.5D));
        long now = level.getGameTime();
        if (now >= expires) {
            MagicCounterService.expirePrompt(defender, this);
            Runnable landing = pending;
            pending = null;
            discard();
            if (landing != null) {
                landing.run();
            }
            return;
        }
        MagicCounterService.offerForcedCounter(defender, this, position(), (int) (expires - now));
    }

    private void close() {
        if (level() instanceof ServerLevel level && defenderId != null) {
            ServerPlayer defender = level.getServer().getPlayerList().getPlayer(defenderId);
            if (defender != null) {
                MagicCounterService.releaseThreat(defender, this);
            }
        }
        pending = null;
        discard();
    }

    @Override
    public boolean canBeCounteredBy(LivingEntity defender) {
        return !answered && !isRemoved() && pending != null && defender.isAlive()
                && defender.getUUID().equals(defenderId) && defender.level() == level()
                && level().getGameTime() < expires;
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public Entity counterOwner() {
        return dummy;
    }

    @Override
    public ResourceLocation counterSkillId() {
        return incoming.id();
    }

    @Override
    public MagicAttribute counterAttribute() {
        return incoming.attribute();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill,
            Vec3 clashPosition) {
        accept(level, defender, clashPosition, counterSkill.color());
    }

    @Override
    public void onForcedCounter(ServerLevel level, ServerPlayer defender, Vec3 clashPosition) {
        accept(level, defender, clashPosition, 0xFFD166);
    }

    @Override
    public void onGluttonyCountered(ServerLevel level, ServerPlayer defender, Vec3 clashPosition) {
        accept(level, defender, clashPosition, 0xAC64F5);
    }

    /** Answering cancels the cast. That is the whole payoff, so it happens before anything else. */
    private void accept(ServerLevel level, ServerPlayer defender, Vec3 clashPosition, int color) {
        if (!canBeCounteredBy(defender)) {
            return;
        }
        answered = true;
        pending = null;
        MagicCounterService.spawnClash(level, clashPosition, DUMMY_INK, color);
        defender.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);
        close();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
