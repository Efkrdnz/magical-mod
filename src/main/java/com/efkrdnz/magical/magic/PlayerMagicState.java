package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalConfig;
import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClassProgress;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerMagicState {
    public static final long SIN_CURSE_DISPEL_WAIT_MILLIS = 5L * 60L * 1000L;
    public static final int MAX_SIN_GAUGE = 600;
    public static final int MAX_GREED_HOARD = 1200;

    private int mana = MagicalConfig.MAX_MANA.get();
    private int barrier = MagicalConfig.MAX_BARRIER.get();
    private int proficiencyXp;
    private int manaVault;
    private int maxManaBonus;
    private int maxBarrierBonus;
    private int manaBoostPurchases;
    private int barrierBoostPurchases;
    private ResourceLocation authorityId;
    private int activeSubspaceEntityId = -1;
    private String anchorSigilDimension = "";
    private int anchorSigilX;
    private int anchorSigilY;
    private int anchorSigilZ;
    private int anchorSigilTicks;
    private int mirrorDecoyEntityId = -1;
    private int nullThreadEntityId = -1;
    private int activeMagicBarrageEntityId = -1;
    private int blackFlamesImbueTicks;
    private float blackFlamesImbueDamage;
    private float blackFlamesImbueKnockback;
    private String soulBondDimension = "";
    private UUID soulBondEntityUuid;
    private UUID soulBondCollapseEntityUuid;
    private int soulBondCollapseTicks;
    private int prideGauge;
    private int wrathGauge;
    private int greedHoard;
    private int slothStillness;
    private int restedStillnessTicks;
    private int manaChargeTicks;
    private int manaChargeLevel;
    private int gluttonyCooldownTicks;
    private int slothBedTicks;
    /**
     * Class-passive contributions to the pools, recomputed every slow tick by
     * {@link com.efkrdnz.magical.magic.passive.ClassPassiveEffects}. Derived, so never persisted.
     */
    private int classMaxManaBonus;
    private int classMaxBarrierBonus;
    private long lastSlothRestDay = -1L;
    private long lastSlothPenaltyDay = -1L;
    /** Bumped when the skill roster is replaced wholesale; saves below it get a one-time re-grant on load. */
    public static final int ROSTER_VERSION = 2;
    private final Set<ResourceLocation> unlockedSkills = new LinkedHashSet<>();
    private final ResourceLocation[] equippedSkills = new ResourceLocation[MagicContent.LOADOUT_SIZE];
    private final Set<ResourceLocation> wheelSkills = new LinkedHashSet<>();
    private final Set<ResourceLocation> unlockedPassives = new LinkedHashSet<>();
    private final Set<ResourceLocation> disabledPassives = new LinkedHashSet<>();
    private final Set<ResourceLocation> activeCurses = new LinkedHashSet<>();
    private final Map<ResourceLocation, Long> passiveDisabledAtMillis = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> passiveLevels = new LinkedHashMap<>();
    private final Map<ResourceLocation, MagicSkillTuning> tuning = new LinkedHashMap<>();
    private final Map<ResourceLocation, MagicalClassProgress> classProgress = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> skillCooldowns = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> cooldownEchoCounters = new LinkedHashMap<>();
    /**
     * Long-lived scratch values for class passives, keyed by the passive id that owns them: a kill
     * tally, a tolerance level, a cooldown. Short-lived windows (cast streaks, a few seconds of
     * stacks) live in the handlers instead and are deliberately not persisted.
     */
    private final Map<ResourceLocation, Integer> passiveCounters = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> envyProgress = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> towerClears = new LinkedHashMap<>();
    private final Set<String> claimedTowerRewards = new LinkedHashSet<>();
    private final List<SpaceWaypoint> spaceWaypoints = new ArrayList<>();
    private final int[] slotCooldowns = new int[MagicContent.LOADOUT_SIZE];

    public int mana() {
        return mana;
    }

    public int barrier() {
        return barrier;
    }

    public int proficiencyXp() {
        return proficiencyXp;
    }

    public int maxMana() {
        return MagicalConfig.MAX_MANA.get() + Math.max(0, maxManaBonus) + Math.max(0, classMaxManaBonus);
    }

    public int maxBarrier() {
        return MagicalConfig.MAX_BARRIER.get() + Math.max(0, maxBarrierBonus) + Math.max(0, classMaxBarrierBonus);
    }

    /** Set by the class-passive slow tick; these are derived from gear and counters, never saved. */
    public void setClassPoolBonuses(int manaBonus, int barrierBonus) {
        classMaxManaBonus = Math.max(0, manaBonus);
        classMaxBarrierBonus = Math.max(0, barrierBonus);
        // Taking armour off shrinks the ceiling, so re-clamp or the pools sit above their own maximum.
        setMana(mana);
        setBarrier(barrier);
    }

    public int manaVault() {
        return manaVault;
    }

    public int maxManaBonus() {
        return maxManaBonus;
    }

    public int maxBarrierBonus() {
        return maxBarrierBonus;
    }

    public void addMaxManaBonus(int amount) {
        maxManaBonus = Math.max(0, maxManaBonus + Math.max(0, amount));
        setMana(mana);
    }

    public void addMaxBarrierBonus(int amount) {
        maxBarrierBonus = Math.max(0, maxBarrierBonus + Math.max(0, amount));
        setBarrier(barrier);
    }

    public int manaBoostPurchases() {
        return manaBoostPurchases;
    }

    public int barrierBoostPurchases() {
        return barrierBoostPurchases;
    }

    public int greedShopTier() {
        int purchases = manaBoostPurchases + barrierBoostPurchases;
        if (purchases >= 50) {
            return 4;
        }
        if (purchases >= 25) {
            return 3;
        }
        if (purchases >= 10) {
            return 2;
        }
        return 1;
    }

    public ResourceLocation authorityId() {
        return authorityId;
    }

    public boolean hasAuthority(ResourceLocation id) {
        return id != null && id.equals(authorityId);
    }

    public int activeSubspaceEntityId() {
        return activeSubspaceEntityId;
    }

    public boolean hasAnchorSigil() {
        return anchorSigilTicks > 0 && !anchorSigilDimension.isEmpty();
    }

    public String anchorSigilDimension() {
        return anchorSigilDimension;
    }

    public BlockPos anchorSigilPos() {
        return new BlockPos(anchorSigilX, anchorSigilY, anchorSigilZ);
    }

    public int mirrorDecoyEntityId() {
        return mirrorDecoyEntityId;
    }

    public int nullThreadEntityId() {
        return nullThreadEntityId;
    }

    public int activeMagicBarrageEntityId() {
        return activeMagicBarrageEntityId;
    }

    public void setActiveMagicBarrageEntityId(int entityId) {
        activeMagicBarrageEntityId = entityId;
    }

    public int blackFlamesImbueTicks() {
        return blackFlamesImbueTicks;
    }

    public float blackFlamesImbueDamage() {
        return blackFlamesImbueDamage;
    }

    public float blackFlamesImbueKnockback() {
        return blackFlamesImbueKnockback;
    }

    public boolean hasBlackFlamesImbue() {
        return blackFlamesImbueTicks > 0;
    }

    public boolean hasSoulBond() {
        return soulBondEntityUuid != null && !soulBondDimension.isEmpty();
    }

    public String soulBondDimension() {
        return soulBondDimension;
    }

    public UUID soulBondEntityUuid() {
        return soulBondEntityUuid;
    }

    public int soulBondCollapseTicks() {
        return soulBondCollapseTicks;
    }

    public UUID soulBondCollapseEntityUuid() {
        return soulBondCollapseEntityUuid;
    }

    public int proficiencyLevel() {
        return MagicContent.levelForXp(proficiencyXp);
    }

    public int prideGauge() {
        return prideGauge;
    }

    public int wrathGauge() {
        return wrathGauge;
    }

    public int greedHoard() {
        return greedHoard;
    }

    public int slothStillness() {
        return slothStillness;
    }

    public int restedStillnessTicks() {
        return restedStillnessTicks;
    }

    public int manaChargeTicks() {
        return manaChargeTicks;
    }

    public int manaChargeLevel() {
        return manaChargeLevel;
    }

    public int gluttonyCooldownTicks() {
        return gluttonyCooldownTicks;
    }

    public long lastSlothRestDay() {
        return lastSlothRestDay;
    }

    public long lastSlothPenaltyDay() {
        return lastSlothPenaltyDay;
    }

    public Set<ResourceLocation> unlockedSkills() {
        return unlockedSkills;
    }

    public ResourceLocation equippedSkill(int slot) {
        return slot >= 0 && slot < equippedSkills.length ? equippedSkills[slot] : null;
    }

    public Map<ResourceLocation, MagicSkillTuning> tuning() {
        return tuning;
    }

    public Set<ResourceLocation> wheelSkills() {
        return wheelSkills;
    }

    public Set<ResourceLocation> unlockedPassives() {
        return unlockedPassives;
    }

    public Set<ResourceLocation> disabledPassives() {
        return disabledPassives;
    }

    public Set<ResourceLocation> activeCurses() {
        return activeCurses;
    }

    public Map<ResourceLocation, Long> passiveDisabledAtMillis() {
        return passiveDisabledAtMillis;
    }

    public Map<ResourceLocation, Integer> passiveLevels() {
        return passiveLevels;
    }

    public Map<ResourceLocation, MagicalClassProgress> classProgress() {
        return classProgress;
    }

    public Map<ResourceLocation, Integer> cooldownEchoCounters() {
        return cooldownEchoCounters;
    }

    public Map<ResourceLocation, Integer> passiveCounters() {
        return passiveCounters;
    }

    public int passiveCounter(ResourceLocation passiveId) {
        return passiveCounters.getOrDefault(passiveId, 0);
    }

    public void setPassiveCounter(ResourceLocation passiveId, int value) {
        if (value == 0) {
            passiveCounters.remove(passiveId);
        } else {
            passiveCounters.put(passiveId, value);
        }
    }

    /** Adds to a counter and clamps it, returning the new value. */
    public int bumpPassiveCounter(ResourceLocation passiveId, int delta, int min, int max) {
        int next = Math.max(min, Math.min(max, passiveCounter(passiveId) + delta));
        setPassiveCounter(passiveId, next);
        return next;
    }

    public Map<ResourceLocation, Integer> envyProgress() {
        return envyProgress;
    }

    public List<SpaceWaypoint> spaceWaypoints() {
        return List.copyOf(spaceWaypoints);
    }

    public MagicSkillTuning tuningFor(ResourceLocation skillId) {
        return tuning.computeIfAbsent(skillId, ignored -> MagicSkillTuning.DEFAULT);
    }

    public boolean hasUnlocked(ResourceLocation skillId) {
        return unlockedSkills.contains(skillId);
    }

    public boolean hasPassive(ResourceLocation passiveId) {
        return unlockedPassives.contains(passiveId);
    }

    public boolean isPassiveEnabled(ResourceLocation passiveId) {
        return hasPassive(passiveId) && !disabledPassives.contains(passiveId);
    }

    public boolean isSinEnabled(ResourceLocation passiveId) {
        return MagicPassiveContent.isSinPassive(passiveId) && isPassiveEnabled(passiveId);
    }

    public int passiveLevel(ResourceLocation passiveId) {
        if (!hasPassive(passiveId)) {
            return 0;
        }
        MagicPassiveDefinition definition = MagicPassiveContent.get(passiveId);
        int max = definition == null ? 1 : definition.maxLevel();
        return clamp(passiveLevels.getOrDefault(passiveId, 1), 1, max);
    }

    public float passiveReduction(ResourceLocation passiveId) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(passiveId);
        if (definition == null || !isPassiveEnabled(passiveId)) {
            return 0.0F;
        }
        return Math.min(1.0F, passiveLevel(passiveId) * definition.reductionPerLevel());
    }

    public boolean hasCurse(ResourceLocation curseId) {
        return activeCurses.contains(curseId);
    }

    public boolean hasClass(ResourceLocation classId) {
        MagicalClassProgress progress = classProgress.get(classId);
        return progress != null && progress.unlocked();
    }

    public boolean hasAnyRootClass() {
        for (MagicalClassDefinition definition : MagicalClasses.roots()) {
            if (hasClass(definition.id())) {
                return true;
            }
        }
        return false;
    }

    public MagicalClassProgress classProgressFor(ResourceLocation classId) {
        return classProgress.computeIfAbsent(classId, ignored -> new MagicalClassProgress());
    }

    public boolean unlockClass(ResourceLocation classId) {
        return unlockClass(null, classId);
    }

    public boolean unlockClass(ServerPlayer player, ResourceLocation classId) {
        if (MagicalClasses.get(classId) == null) {
            return false;
        }
        MagicalClassProgress progress = classProgressFor(classId);
        if (progress.unlocked()) {
            return false;
        }
        progress.unlock();
        unlockClassRewards(MagicalClasses.get(classId), player);
        return true;
    }

    public boolean chooseStartingClass(ServerPlayer player, ResourceLocation classId) {
        if (!MagicalClasses.isStartingRoot(classId) || hasAnyRootClass()) {
            return false;
        }
        return unlockClass(player, classId);
    }

    public boolean evolveClass(ResourceLocation classId) {
        return evolveClass(null, classId);
    }

    /**
     * Take a node of a class evolution tree. Branches converge, so owning <em>any one</em> parent is
     * enough. The XP cost is spent from the tree's base pool rather than merely compared against it,
     * which is what stops a player eventually owning every node.
     */
    public boolean evolveClass(ServerPlayer player, ResourceLocation classId) {
        MagicalClassDefinition definition = MagicalClasses.get(classId);
        if (definition == null || definition.isBase() || hasClass(classId)) {
            return false;
        }
        if (definition.parents().stream().noneMatch(this::hasClass)) {
            return false;
        }
        MagicalClassProgress pool = classProgressFor(MagicalClasses.baseOf(classId));
        if (!pool.spendXp(definition.xpCost())) {
            return false;
        }
        classProgressFor(classId).unlock();
        unlockClassRewards(definition, player);
        return true;
    }

    /** True when the node can be taken right now: not owned, a parent owned, and the pool covers the cost. */
    public boolean canEvolveClass(ResourceLocation classId) {
        MagicalClassDefinition definition = MagicalClasses.get(classId);
        return definition != null
                && !definition.isBase()
                && !hasClass(classId)
                && definition.parents().stream().anyMatch(this::hasClass)
                && classProgressFor(MagicalClasses.baseOf(classId)).xp() >= definition.xpCost();
    }

    /** XP always pools at the base of the tree, so callers may pass any node of it. */
    public void addClassXp(ResourceLocation classId, int amount) {
        ResourceLocation baseId = MagicalClasses.baseOf(classId);
        if (hasClass(baseId)) {
            classProgressFor(baseId).addXp(amount);
        }
    }

    /** The spendable XP of the tree the given node belongs to. */
    public int classXpPool(ResourceLocation classId) {
        return classProgressFor(MagicalClasses.baseOf(classId)).xp();
    }

    public boolean unlock(ResourceLocation skillId) {
        if (MagicContent.get(skillId) == null) {
            return false;
        }
        tuning.computeIfAbsent(skillId, ignored -> MagicSkillTuning.DEFAULT);
        boolean changed = unlockedSkills.add(skillId);
        if (MagicContent.GABRIEL.id().equals(skillId)) {
            for (MagicSkillDefinition subSkill : MagicContent.gabrielSubSkills()) {
                tuning.computeIfAbsent(subSkill.id(), ignored -> MagicSkillTuning.DEFAULT);
                unlockedSkills.add(subSkill.id());
            }
        }
        if (MagicContent.BLACK_FLAMES.id().equals(skillId)) {
            for (MagicSkillDefinition subSkill : MagicContent.blackFlamesSubSkills()) {
                tuning.computeIfAbsent(subSkill.id(), ignored -> MagicSkillTuning.DEFAULT);
                unlockedSkills.add(subSkill.id());
            }
        }
        if (MagicContent.SOUL_VOW.id().equals(skillId)) {
            for (MagicSkillDefinition subSkill : MagicContent.soulVowSubSkills()) {
                tuning.computeIfAbsent(subSkill.id(), ignored -> MagicSkillTuning.DEFAULT);
                unlockedSkills.add(subSkill.id());
            }
        }
        if (MagicContent.SPATIAL_ARSENAL.id().equals(skillId)) {
            for (MagicSkillDefinition subSkill : MagicContent.spatialArsenalSubSkills()) {
                tuning.computeIfAbsent(subSkill.id(), ignored -> MagicSkillTuning.DEFAULT);
                unlockedSkills.add(subSkill.id());
            }
        }
        return changed;
    }

    public boolean setAuthority(ResourceLocation authorityId) {
        AuthorityDefinition definition = AuthorityContent.get(authorityId);
        if (definition == null) {
            return false;
        }
        if (authorityId.equals(this.authorityId)) {
            return false;
        }
        clearAuthoritySkills();
        this.authorityId = authorityId;
        definition.skillIds().forEach(this::unlock);
        return true;
    }

    public void clearAuthority() {
        clearAuthoritySkills();
        authorityId = null;
        activeSubspaceEntityId = -1;
        clearSoulBond();
    }

    public void setActiveSubspaceEntityId(int entityId) {
        activeSubspaceEntityId = entityId;
    }

    public void setAnchorSigil(String dimension, BlockPos pos, int ticks) {
        anchorSigilDimension = dimension == null ? "" : dimension;
        anchorSigilX = pos.getX();
        anchorSigilY = pos.getY();
        anchorSigilZ = pos.getZ();
        anchorSigilTicks = Math.max(1, ticks);
    }

    public void clearAnchorSigil() {
        anchorSigilDimension = "";
        anchorSigilTicks = 0;
    }

    public void setMirrorDecoyEntityId(int entityId) {
        mirrorDecoyEntityId = entityId;
    }

    public void setNullThreadEntityId(int entityId) {
        nullThreadEntityId = entityId;
    }

    public void setBlackFlamesImbue(int ticks, float damage, float knockback) {
        blackFlamesImbueTicks = Math.max(0, ticks);
        blackFlamesImbueDamage = Math.max(0.0F, damage);
        blackFlamesImbueKnockback = Math.max(0.0F, knockback);
    }

    public void clearBlackFlamesImbue() {
        blackFlamesImbueTicks = 0;
        blackFlamesImbueDamage = 0.0F;
        blackFlamesImbueKnockback = 0.0F;
    }

    public void setSoulBond(String dimension, UUID entityUuid) {
        soulBondDimension = dimension == null ? "" : dimension;
        soulBondEntityUuid = entityUuid;
        soulBondCollapseEntityUuid = null;
        soulBondCollapseTicks = 0;
    }

    public void clearSoulBond() {
        soulBondDimension = "";
        soulBondEntityUuid = null;
        soulBondCollapseEntityUuid = null;
        soulBondCollapseTicks = 0;
    }

    public void startSoulBondCollapse(UUID fallenEntityUuid, int ticks) {
        soulBondCollapseEntityUuid = fallenEntityUuid;
        soulBondCollapseTicks = Math.max(soulBondCollapseTicks, ticks);
    }

    private void clearAuthoritySkills() {
        if (authorityId == null) {
            return;
        }
        AuthorityDefinition current = AuthorityContent.get(authorityId);
        if (current != null) {
            for (ResourceLocation skillId : current.skillIds()) {
                removeSkill(skillId);
            }
        }
    }

    public boolean removeSkill(ResourceLocation skillId) {
        if (MagicContent.get(skillId) == null) {
            return false;
        }
        boolean removed = unlockedSkills.remove(skillId);
        if (!removed) {
            return false;
        }
        if (MagicContent.GABRIEL.id().equals(skillId)) {
            MagicContent.gabrielSubSkills().forEach(subSkill -> removeSkill(subSkill.id()));
        }
        if (MagicContent.BLACK_FLAMES.id().equals(skillId)) {
            MagicContent.blackFlamesSubSkills().forEach(subSkill -> removeSkill(subSkill.id()));
        }
        if (MagicContent.SOUL_VOW.id().equals(skillId)) {
            MagicContent.soulVowSubSkills().forEach(subSkill -> removeSkill(subSkill.id()));
            clearSoulBond();
        }
        if (MagicContent.SPATIAL_ARSENAL.id().equals(skillId)) {
            MagicContent.spatialArsenalSubSkills().forEach(subSkill -> removeSkill(subSkill.id()));
        }
        for (int slot = 0; slot < equippedSkills.length; slot++) {
            if (skillId.equals(equippedSkills[slot])) {
                equippedSkills[slot] = null;
                slotCooldowns[slot] = 0;
            }
        }
        wheelSkills.remove(skillId);
        tuning.remove(skillId);
        skillCooldowns.remove(skillId);
        cooldownEchoCounters.remove(skillId);
        envyProgress.remove(skillId);
        return true;
    }

    public void unlockAll(Set<ResourceLocation> ids) {
        ids.forEach(this::unlock);
    }

    public List<ResourceLocation> unlockStarterAwakening(ServerPlayer player) {
        List<ResourceLocation> unlocked = new ArrayList<>();
        for (ResourceLocation skillId : MagicContent.STARTER_UNLOCKS) {
            if (unlock(skillId)) {
                unlocked.add(skillId);
            }
        }
        ResourceLocation bonus = MagicContent.randomStarterBonusSkill(unlockedSkills, player == null ? net.minecraft.util.RandomSource.create() : player.getRandom());
        if (bonus != null && unlock(bonus)) {
            unlocked.add(bonus);
        }
        return unlocked;
    }

    public boolean unlockPassive(ResourceLocation passiveId) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(passiveId);
        if (definition == null || definition.curse()) {
            return false;
        }
        disabledPassives.remove(passiveId);
        passiveDisabledAtMillis.remove(passiveId);
        passiveLevels.put(passiveId, Math.max(1, passiveLevels.getOrDefault(passiveId, 1)));
        ResourceLocation linkedCurse = MagicPassiveContent.linkedCurseForSinPassive(passiveId);
        if (linkedCurse != null) {
            activeCurses.add(linkedCurse);
        }
        if (MagicPassiveContent.SIN_GREED.id().equals(passiveId)) {
            unlock(MagicContent.VAULT_OF_AVARICE.id());
        }
        return unlockedPassives.add(passiveId);
    }

    public boolean removePassive(ResourceLocation passiveId) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(passiveId);
        if (definition == null || definition.curse()) {
            return false;
        }
        boolean removed = unlockedPassives.remove(passiveId);
        if (!removed) {
            return false;
        }
        disabledPassives.remove(passiveId);
        passiveDisabledAtMillis.remove(passiveId);
        passiveLevels.remove(passiveId);
        ResourceLocation linkedCurse = MagicPassiveContent.linkedCurseForSinPassive(passiveId);
        if (linkedCurse != null) {
            activeCurses.remove(linkedCurse);
        }
        if (MagicPassiveContent.SIN_GREED.id().equals(passiveId)) {
            removeSkill(MagicContent.VAULT_OF_AVARICE.id());
        }
        clearPassiveRuntimeState(passiveId);
        return true;
    }

    public boolean setPassiveLevel(ResourceLocation passiveId, int level) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(passiveId);
        if (definition == null || definition.curse()) {
            return false;
        }
        unlockPassive(passiveId);
        passiveLevels.put(passiveId, clamp(level, 1, definition.maxLevel()));
        return true;
    }

    public void unlockPassives(Set<ResourceLocation> ids) {
        ids.forEach(this::unlockPassive);
    }

    public int towerClearedFloor(ResourceLocation towerId) {
        return Math.max(0, towerClears.getOrDefault(towerId, 0));
    }

    public boolean markTowerFloorCleared(ResourceLocation towerId, int floor) {
        int safeFloor = Math.max(0, floor);
        if (safeFloor <= towerClearedFloor(towerId)) {
            return false;
        }
        towerClears.put(towerId, safeFloor);
        return true;
    }

    public Map<ResourceLocation, Integer> towerClears() {
        return Map.copyOf(towerClears);
    }

    public boolean hasClaimedTowerReward(ResourceLocation towerId, int floor) {
        return claimedTowerRewards.contains(towerRewardKey(towerId, floor));
    }

    public boolean claimTowerReward(ResourceLocation towerId, int floor) {
        return claimedTowerRewards.add(towerRewardKey(towerId, floor));
    }

    private static String towerRewardKey(ResourceLocation towerId, int floor) {
        return towerId + "#" + Math.max(0, floor);
    }

    public void togglePassive(ResourceLocation passiveId) {
        if (!hasPassive(passiveId)) {
            return;
        }
        if (disabledPassives.remove(passiveId)) {
            passiveDisabledAtMillis.remove(passiveId);
            ResourceLocation linkedCurse = MagicPassiveContent.linkedCurseForSinPassive(passiveId);
            if (linkedCurse != null) {
                activeCurses.add(linkedCurse);
            }
        } else {
            disabledPassives.add(passiveId);
            passiveDisabledAtMillis.put(passiveId, System.currentTimeMillis());
        }
    }

    public boolean addCurse(ResourceLocation curseId) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(curseId);
        return definition != null && definition.curse() && activeCurses.add(curseId);
    }

    public boolean canDispelCurse(ResourceLocation curseId) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(curseId);
        ResourceLocation linkedSinPassive = MagicPassiveContent.linkedSinPassiveForCurse(curseId);
        if (linkedSinPassive != null) {
            return definition != null
                    && definition.curse()
                    && hasCurse(curseId)
                    && hasPassive(linkedSinPassive)
                    && disabledPassives.contains(linkedSinPassive)
                    && sinCurseDispelRemainingMillis(curseId) <= 0L;
        }
        return definition != null
                && definition.curse()
                && hasCurse(curseId)
                && proficiencyLevel() >= definition.requiredProficiencyToDispel()
                && mana >= definition.dispelManaCost();
    }

    public long sinCurseDispelRemainingMillis(ResourceLocation curseId) {
        ResourceLocation linkedSinPassive = MagicPassiveContent.linkedSinPassiveForCurse(curseId);
        if (linkedSinPassive == null || !hasPassive(linkedSinPassive) || !disabledPassives.contains(linkedSinPassive)) {
            return SIN_CURSE_DISPEL_WAIT_MILLIS;
        }
        long disabledAt = passiveDisabledAtMillis.getOrDefault(linkedSinPassive, System.currentTimeMillis());
        return Math.max(0L, SIN_CURSE_DISPEL_WAIT_MILLIS - (System.currentTimeMillis() - disabledAt));
    }

    public ResourceLocation linkedSinPassiveForCurse(ResourceLocation curseId) {
        return MagicPassiveContent.linkedSinPassiveForCurse(curseId);
    }

    public boolean dispelCurse(ResourceLocation curseId) {
        if (!canDispelCurse(curseId)) {
            return false;
        }
        MagicPassiveDefinition definition = MagicPassiveContent.get(curseId);
        if (MagicPassiveContent.linkedSinPassiveForCurse(curseId) == null) {
            mana -= definition.dispelManaCost();
        }
        return activeCurses.remove(curseId);
    }

    public void setMana(int mana) {
        this.mana = clamp(mana, 0, maxMana());
    }

    public void setBarrier(int barrier) {
        this.barrier = clamp(barrier, 0, maxBarrier());
    }

    public void addMana(int amount) {
        setMana(mana + amount);
    }

    public void addBarrier(int amount) {
        setBarrier(barrier + amount);
    }

    public void addPride(int amount) {
        prideGauge = clamp(prideGauge + amount, 0, MAX_SIN_GAUGE);
    }

    public void addWrath(int amount) {
        wrathGauge = clamp(wrathGauge + amount, 0, MAX_SIN_GAUGE);
    }

    public void addGreedHoard(int amount) {
        greedHoard = clamp(greedHoard + amount, 0, MAX_GREED_HOARD);
    }

    public void addSlothStillness(int amount) {
        slothStillness = clamp(slothStillness + amount, 0, MAX_SIN_GAUGE);
    }

    public void reduceSlothStillness(int amount) {
        slothStillness = clamp(slothStillness - amount, 0, MAX_SIN_GAUGE);
    }

    public void markSlothRested(long day, int durationTicks) {
        lastSlothRestDay = day;
        lastSlothPenaltyDay = Math.max(lastSlothPenaltyDay, day);
        restedStillnessTicks = Math.max(restedStillnessTicks, durationTicks);
        slothBedTicks = 0;
    }

    public void markSlothPenalty(long day) {
        lastSlothPenaltyDay = day;
    }

    public void incrementSlothBedTicks() {
        slothBedTicks++;
    }

    public void resetSlothBedTicks() {
        slothBedTicks = 0;
    }

    public int slothBedTicks() {
        return slothBedTicks;
    }

    public void addManaCharge(int level, int durationTicks) {
        manaChargeLevel = clamp(Math.max(manaChargeLevel, level), 1, 5);
        manaChargeTicks = Math.max(manaChargeTicks, durationTicks);
    }

    public void setGluttonyCooldown(int ticks) {
        gluttonyCooldownTicks = Math.max(gluttonyCooldownTicks, ticks);
    }

    public float consumeWrathPower() {
        float power = Math.min(0.45F, wrathGauge / (float) MAX_SIN_GAUGE * 0.45F);
        wrathGauge = 0;
        return power;
    }

    public float consumePridePower() {
        float power = Math.min(0.35F, prideGauge / (float) MAX_SIN_GAUGE * 0.35F);
        prideGauge = Math.max(0, prideGauge / 3);
        return power;
    }

    public void addProficiency(int amount) {
        addProficiency(null, amount);
    }

    public void addProficiency(ServerPlayer player, int amount) {
        int previousLevel = proficiencyLevel();
        proficiencyXp = Math.max(0, proficiencyXp + amount);
        grantProficiencyRewards(player, previousLevel, proficiencyLevel());
    }

    public void setProficiencyXp(int xp) {
        setProficiencyXp(null, xp);
    }

    public void setProficiencyXp(ServerPlayer player, int xp) {
        int previousLevel = proficiencyLevel();
        proficiencyXp = Math.max(0, xp);
        grantProficiencyRewards(player, previousLevel, proficiencyLevel());
    }

    public boolean spendMana(int amount) {
        if (mana < amount) {
            return false;
        }
        mana -= amount;
        return true;
    }

    public boolean spendManaWithGreedHoard(int amount) {
        if (spendMana(amount)) {
            return true;
        }
        if (!isSinEnabled(MagicPassiveContent.SIN_GREED.id()) || greedHoard <= 0) {
            return false;
        }
        int missing = amount - mana;
        int manaFromHoard = Math.min(missing, greedHoard / 2);
        if (mana + manaFromHoard < amount) {
            return false;
        }
        greedHoard -= manaFromHoard * 2;
        mana = Math.max(0, mana + manaFromHoard - amount);
        return true;
    }

    public boolean spendManaWithBarrierConversion(int amount) {
        if (spendMana(amount)) {
            return true;
        }
        if (!isPassiveEnabled(MagicPassiveContent.BARRIER_CONVERSION.id()) || barrier <= 0) {
            return false;
        }
        int missing = amount - mana;
        int level = passiveLevel(MagicPassiveContent.BARRIER_CONVERSION.id());
        int barrierPerMana = Math.max(2, 7 - level);
        int barrierNeeded = missing * barrierPerMana;
        if (barrier < barrierNeeded) {
            return false;
        }
        barrier -= barrierNeeded;
        mana = 0;
        return true;
    }

    public boolean spendManaWithVaultTap(int amount) {
        if (!isPassiveEnabled(MagicPassiveContent.VAULT_TAP.id()) || mana >= amount) {
            return false;
        }
        int missing = amount - mana;
        int vaultCost = missing * 3;
        if (manaVault < vaultCost) {
            return false;
        }
        manaVault -= vaultCost;
        mana = 0;
        return true;
    }

    public int depositAllManaToVault() {
        int deposited = mana;
        if (deposited <= 0) {
            return 0;
        }
        manaVault = Math.max(0, manaVault + deposited);
        mana = 0;
        return deposited;
    }

    public boolean buyMaxManaUpgrade() {
        int price = greedUpgradePrice(manaBoostPurchases);
        if (manaVault < price) {
            return false;
        }
        manaVault -= price;
        manaBoostPurchases++;
        maxManaBonus += 10;
        setMana(mana);
        return true;
    }

    public boolean buyMaxBarrierUpgrade() {
        int price = greedUpgradePrice(barrierBoostPurchases);
        if (manaVault < price) {
            return false;
        }
        manaVault -= price;
        barrierBoostPurchases++;
        maxBarrierBonus += 4;
        setBarrier(barrier);
        return true;
    }

    public boolean buyShopPassive(ResourceLocation passiveId) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(passiveId);
        if (definition == null || definition.curse() || definition.shopTier() <= 0 || definition.shopCost() <= 0 || hasPassive(passiveId)) {
            return false;
        }
        if (greedShopTier() < definition.shopTier() || manaVault < definition.shopCost()) {
            return false;
        }
        manaVault -= definition.shopCost();
        unlockPassive(passiveId);
        return true;
    }

    public static int greedUpgradePrice(int existingPurchases) {
        int next = Math.max(1, existingPurchases + 1);
        if (next <= 10) {
            return next * 1000;
        }
        if (next <= 14) {
            return 10000 + (next - 10) * 2500;
        }
        if (next <= 20) {
            return 20000 + (next - 14) * 5000;
        }
        return 50000 + (next - 20) * 10000;
    }

    public void refillMana() {
        mana = maxMana();
    }

    public boolean refillBarrierFromMana() {
        if (barrier >= maxBarrier()) {
            return false;
        }
        int manaPerStep = MagicalConfig.BARRIER_REFILL_MANA_COST.get();
        int barrierPerStep = MagicalConfig.BARRIER_PER_REFILL.get();
        if (mana < manaPerStep) {
            return false;
        }
        mana -= manaPerStep;
        barrier = Math.min(maxBarrier(), barrier + barrierPerStep);
        return true;
    }

    public float absorbDamage(float amount) {
        if (amount <= 0.0F || barrier <= 0) {
            return amount;
        }
        float absorbed = Math.min(amount, barrier);
        barrier -= Math.round(absorbed);
        return amount - absorbed;
    }

    public void equip(int slot, ResourceLocation skillId) {
        if (slot < 0 || slot >= equippedSkills.length || !hasUnlocked(skillId)) {
            return;
        }
        equippedSkills[slot] = skillId;
        slotCooldowns[slot] = skillCooldown(skillId);
    }

    public void clearSlot(int slot) {
        if (slot < 0 || slot >= equippedSkills.length) {
            return;
        }
        equippedSkills[slot] = null;
        slotCooldowns[slot] = 0;
    }

    public void adjustTuning(ResourceLocation skillId, MagicTuningStat stat, int delta) {
        if (!hasUnlocked(skillId)) {
            return;
        }
        tuning.put(skillId, tuningFor(skillId).adjust(stat, delta, tuningLimit()));
    }

    public int tuningLimit() {
        return Math.min(MagicSkillTuning.ABSOLUTE_MAX, MagicSkillTuning.MAX + proficiencyLevel() * 2);
    }

    public boolean hasWheelSkill(ResourceLocation skillId) {
        return wheelSkills.contains(skillId);
    }

    public void addWheelSkill(ResourceLocation skillId) {
        if (hasUnlocked(skillId)) {
            wheelSkills.add(skillId);
        }
    }

    public void removeWheelSkill(ResourceLocation skillId) {
        wheelSkills.remove(skillId);
    }

    public void removeWheelSkillAt(int index) {
        if (index < 0 || index >= wheelSkills.size()) {
            return;
        }
        wheelSkills.remove(new ArrayList<>(wheelSkills).get(index));
    }

    public boolean addSpaceWaypoint(SpaceWaypoint waypoint) {
        if (waypoint == null || spaceWaypoints.size() >= 24) {
            return false;
        }
        spaceWaypoints.add(waypoint);
        return true;
    }

    public boolean removeSpaceWaypoint(int index) {
        if (index < 0 || index >= spaceWaypoints.size()) {
            return false;
        }
        spaceWaypoints.remove(index);
        return true;
    }

    public SpaceWaypoint spaceWaypoint(int index) {
        return index < 0 || index >= spaceWaypoints.size() ? null : spaceWaypoints.get(index);
    }

    public void moveWheelSkill(int index, int delta) {
        List<ResourceLocation> ordered = new ArrayList<>(wheelSkills);
        int target = index + delta;
        if (index < 0 || index >= ordered.size() || target < 0 || target >= ordered.size()) {
            return;
        }
        ResourceLocation moved = ordered.remove(index);
        ordered.add(target, moved);
        wheelSkills.clear();
        wheelSkills.addAll(ordered);
    }

    public void toggleWheelSkill(ResourceLocation skillId) {
        if (wheelSkills.contains(skillId)) {
            wheelSkills.remove(skillId);
        } else if (hasUnlocked(skillId)) {
            wheelSkills.add(skillId);
        }
    }

    public int cooldown(int slot) {
        return slot >= 0 && slot < slotCooldowns.length ? slotCooldowns[slot] : 0;
    }

    public boolean isOnCooldown(int slot) {
        return cooldown(slot) > 0;
    }

    public void setCooldown(int slot, int ticks) {
        if (slot >= 0 && slot < slotCooldowns.length) {
            slotCooldowns[slot] = Math.max(0, ticks);
        }
    }

    public int skillCooldown(ResourceLocation skillId) {
        return skillId == null ? 0 : Math.max(0, skillCooldowns.getOrDefault(skillId, 0));
    }

    public boolean isSkillOnCooldown(ResourceLocation skillId) {
        return skillCooldown(skillId) > 0;
    }

    public void setSkillCooldown(ResourceLocation skillId, int ticks) {
        if (skillId == null) {
            return;
        }
        int cooldown = Math.max(0, ticks);
        if (cooldown == 0) {
            skillCooldowns.remove(skillId);
        } else {
            skillCooldowns.put(skillId, cooldown);
        }
        mirrorSkillCooldownToLoadout(skillId, cooldown);
    }

    public void clearCooldowns() {
        skillCooldowns.clear();
        cooldownEchoCounters.clear();
        Arrays.fill(slotCooldowns, 0);
    }

    public boolean consumeCooldownEcho(ResourceLocation skillId) {
        if (skillId == null || !isPassiveEnabled(MagicPassiveContent.COOLDOWN_ECHO.id())) {
            return false;
        }
        int level = passiveLevel(MagicPassiveContent.COOLDOWN_ECHO.id());
        int threshold = Math.max(3, 6 - level);
        int count = cooldownEchoCounters.getOrDefault(skillId, 0) + 1;
        if (count >= threshold) {
            cooldownEchoCounters.remove(skillId);
            return true;
        }
        cooldownEchoCounters.put(skillId, count);
        return false;
    }

    public boolean tickTimedSinEffects(ServerPlayer player) {
        boolean changed = false;
        if (gluttonyCooldownTicks > 0) {
            gluttonyCooldownTicks--;
            changed = true;
        }
        if (restedStillnessTicks > 0) {
            restedStillnessTicks--;
            changed = true;
        }
        if (manaChargeTicks > 0) {
            manaChargeTicks--;
            if (player.tickCount % Math.max(8, 34 - manaChargeLevel * 5) == 0 && mana < maxMana()) {
                mana = Math.min(maxMana(), mana + Math.max(1, manaChargeLevel));
            }
            if (manaChargeTicks <= 0) {
                manaChargeLevel = 0;
            }
            changed = true;
        }
        if (wrathGauge > 0 && player.tickCount % 40 == 0) {
            wrathGauge = Math.max(0, wrathGauge - 2);
            changed = true;
        }
        if (prideGauge > 0 && player.tickCount % 60 == 0) {
            prideGauge = Math.max(0, prideGauge - 1);
            changed = true;
        }
        return changed;
    }

    public void recordEnvyExposure(ServerPlayer player, ResourceLocation skillId, float damage) {
        if (skillId == null || hasUnlocked(skillId) || !isSinEnabled(MagicPassiveContent.SIN_ENVY.id())) {
            return;
        }
        MagicSkillDefinition definition = MagicContent.get(skillId);
        if (definition == null || definition.tier() >= 5) {
            return;
        }
        int required = envyRequired(definition);
        int gained = Math.max(1, Math.round(Math.max(1.0F, damage) * (2.0F + Math.max(0, definition.tier()))));
        int progress = Math.min(required, envyProgress.getOrDefault(skillId, 0) + gained);
        if (progress >= required && envyAwakens(player, definition)) {
            unlock(skillId);
            envyProgress.remove(skillId);
            player.displayClientMessage(Component.translatable("message.magical.envy_awakened", Component.translatable(definition.nameKey())), false);
            return;
        }
        envyProgress.put(skillId, progress);
    }

    private boolean envyAwakens(ServerPlayer player, MagicSkillDefinition definition) {
        int tier = Math.max(0, definition.tier());
        int chance = switch (tier) {
            case 0, 1, 2 -> 100;
            case 3 -> 70;
            case 4 -> 35;
            default -> 0;
        };
        return player.getRandom().nextInt(100) < chance;
    }

    private int envyRequired(MagicSkillDefinition definition) {
        int tier = Math.max(0, definition.tier());
        return 80 + tier * tier * 80 + Math.max(0, definition.baseManaCost());
    }

    public boolean tickServer(ServerPlayer player) {
        boolean changed = false;
        if (player.tickCount % MagicalConfig.MANA_REGEN_INTERVAL_TICKS.get() == 0 && mana < maxMana()) {
            mana = Math.min(maxMana(), mana + MagicalConfig.MANA_PER_REGEN.get());
            changed = true;
        }
        for (int i = 0; i < slotCooldowns.length; i++) {
            if (slotCooldowns[i] > 0) {
                slotCooldowns[i]--;
            }
        }
        if (!skillCooldowns.isEmpty()) {
            skillCooldowns.replaceAll((id, cooldown) -> Math.max(0, cooldown - 1));
            skillCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
            refreshLoadoutCooldownsFromSkills();
        }
        if (MagicalConfig.STARTER_UNLOCK_ON_LOGIN.get() && unlockedSkills.isEmpty()) {
            for (ResourceLocation skillId : unlockStarterAwakening(player)) {
                MagicSkillDefinition skill = MagicContent.get(skillId);
                if (skill != null) {
                    player.displayClientMessage(Component.translatable("message.magical.skill_unlocked", Component.translatable(skill.nameKey())), false);
                }
            }
            changed = true;
        }
        if (MagicalConfig.STARTER_UNLOCK_ON_LOGIN.get() && unlockedPassives.isEmpty()) {
            unlockPassives(MagicPassiveContent.STARTER_PASSIVES);
            changed = true;
        }
        // No starting class yet: put the chooser on screen and keep it there. Re-opening from the
        // server is what actually enforces the choice, since a client can close a container itself.
        if (MagicalConfig.STARTER_UNLOCK_ON_LOGIN.get() && !hasAnyRootClass()
                && player.tickCount > 40 && player.tickCount % 20 == 0
                && player.containerMenu == player.inventoryMenu) {
            ClassSelectService.open(player);
        }
        if (tickTimedSinEffects(player)) {
            changed = true;
        }
        if (anchorSigilTicks > 0) {
            anchorSigilTicks--;
            if (anchorSigilTicks <= 0) {
                clearAnchorSigil();
                changed = true;
            }
        }
        if (blackFlamesImbueTicks > 0) {
            blackFlamesImbueTicks--;
            if (blackFlamesImbueTicks <= 0) {
                clearBlackFlamesImbue();
                changed = true;
            }
        }
        if (soulBondCollapseTicks > 0) {
            soulBondCollapseTicks--;
            if (soulBondCollapseTicks <= 0) {
                soulBondCollapseEntityUuid = null;
            }
            changed = true;
        }
        if (ensureSinCursesForEnabledPassives()) {
            changed = true;
        }
        if (changed) {
            sync(player);
        }
        return changed;
    }

    public PlayerMagicState copy() {
        PlayerMagicState copy = new PlayerMagicState();
        copy.mana = mana;
        copy.barrier = barrier;
        copy.proficiencyXp = proficiencyXp;
        copy.manaVault = manaVault;
        copy.maxManaBonus = maxManaBonus;
        copy.maxBarrierBonus = maxBarrierBonus;
        copy.manaBoostPurchases = manaBoostPurchases;
        copy.barrierBoostPurchases = barrierBoostPurchases;
        copy.authorityId = authorityId;
        copy.activeSubspaceEntityId = activeSubspaceEntityId;
        copy.anchorSigilDimension = anchorSigilDimension;
        copy.anchorSigilX = anchorSigilX;
        copy.anchorSigilY = anchorSigilY;
        copy.anchorSigilZ = anchorSigilZ;
        copy.anchorSigilTicks = anchorSigilTicks;
        copy.mirrorDecoyEntityId = mirrorDecoyEntityId;
        copy.nullThreadEntityId = nullThreadEntityId;
        copy.activeMagicBarrageEntityId = activeMagicBarrageEntityId;
        copy.blackFlamesImbueTicks = blackFlamesImbueTicks;
        copy.blackFlamesImbueDamage = blackFlamesImbueDamage;
        copy.blackFlamesImbueKnockback = blackFlamesImbueKnockback;
        copy.soulBondDimension = soulBondDimension;
        copy.soulBondEntityUuid = soulBondEntityUuid;
        copy.soulBondCollapseEntityUuid = soulBondCollapseEntityUuid;
        copy.soulBondCollapseTicks = soulBondCollapseTicks;
        copy.unlockedSkills.addAll(unlockedSkills);
        System.arraycopy(equippedSkills, 0, copy.equippedSkills, 0, equippedSkills.length);
        copy.wheelSkills.addAll(wheelSkills);
        copy.prideGauge = prideGauge;
        copy.wrathGauge = wrathGauge;
        copy.greedHoard = greedHoard;
        copy.slothStillness = slothStillness;
        copy.restedStillnessTicks = restedStillnessTicks;
        copy.manaChargeTicks = manaChargeTicks;
        copy.manaChargeLevel = manaChargeLevel;
        copy.gluttonyCooldownTicks = gluttonyCooldownTicks;
        copy.slothBedTicks = slothBedTicks;
        copy.lastSlothRestDay = lastSlothRestDay;
        copy.lastSlothPenaltyDay = lastSlothPenaltyDay;
        copy.unlockedPassives.addAll(unlockedPassives);
        copy.disabledPassives.addAll(disabledPassives);
        copy.activeCurses.addAll(activeCurses);
        copy.passiveDisabledAtMillis.putAll(passiveDisabledAtMillis);
        copy.passiveLevels.putAll(passiveLevels);
        tuning.forEach((id, value) -> copy.tuning.put(id, value));
        classProgress.forEach((id, progress) -> copy.classProgress.put(id, progress.copy()));
        copy.skillCooldowns.putAll(skillCooldowns);
        copy.cooldownEchoCounters.putAll(cooldownEchoCounters);
        copy.passiveCounters.putAll(passiveCounters);
        copy.envyProgress.putAll(envyProgress);
        copy.towerClears.putAll(towerClears);
        copy.claimedTowerRewards.addAll(claimedTowerRewards);
        copy.spaceWaypoints.addAll(spaceWaypoints);
        System.arraycopy(slotCooldowns, 0, copy.slotCooldowns, 0, slotCooldowns.length);
        return copy;
    }

    public void sync(ServerPlayer player) {
        player.setData(MagicalAttachments.MAGIC_STATE.get(), copy());
        MagicalNetwork.syncMagicState(player, this);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("mana", mana);
        tag.putInt("barrier", barrier);
        tag.putInt("proficiencyXp", proficiencyXp);
        tag.putInt("manaVault", manaVault);
        tag.putInt("maxManaBonus", maxManaBonus);
        tag.putInt("maxBarrierBonus", maxBarrierBonus);
        tag.putInt("manaBoostPurchases", manaBoostPurchases);
        tag.putInt("barrierBoostPurchases", barrierBoostPurchases);
        if (authorityId != null) {
            tag.putString("authorityId", authorityId.toString());
        }
        tag.putInt("activeSubspaceEntityId", activeSubspaceEntityId);
        tag.putString("anchorSigilDimension", anchorSigilDimension);
        tag.putInt("anchorSigilX", anchorSigilX);
        tag.putInt("anchorSigilY", anchorSigilY);
        tag.putInt("anchorSigilZ", anchorSigilZ);
        tag.putInt("anchorSigilTicks", anchorSigilTicks);
        tag.putInt("mirrorDecoyEntityId", mirrorDecoyEntityId);
        tag.putInt("nullThreadEntityId", nullThreadEntityId);
        tag.putInt("activeMagicBarrageEntityId", activeMagicBarrageEntityId);
        tag.putInt("blackFlamesImbueTicks", blackFlamesImbueTicks);
        tag.putFloat("blackFlamesImbueDamage", blackFlamesImbueDamage);
        tag.putFloat("blackFlamesImbueKnockback", blackFlamesImbueKnockback);
        tag.putString("soulBondDimension", soulBondDimension);
        if (soulBondEntityUuid != null) {
            tag.putUUID("soulBondEntityUuid", soulBondEntityUuid);
        }
        if (soulBondCollapseEntityUuid != null) {
            tag.putUUID("soulBondCollapseEntityUuid", soulBondCollapseEntityUuid);
        }
        tag.putInt("soulBondCollapseTicks", soulBondCollapseTicks);
        tag.putInt("prideGauge", prideGauge);
        tag.putInt("wrathGauge", wrathGauge);
        tag.putInt("greedHoard", greedHoard);
        tag.putInt("slothStillness", slothStillness);
        tag.putInt("restedStillnessTicks", restedStillnessTicks);
        tag.putInt("manaChargeTicks", manaChargeTicks);
        tag.putInt("manaChargeLevel", manaChargeLevel);
        tag.putInt("gluttonyCooldownTicks", gluttonyCooldownTicks);
        tag.putLong("lastSlothRestDay", lastSlothRestDay);
        tag.putLong("lastSlothPenaltyDay", lastSlothPenaltyDay);

        ListTag unlocked = new ListTag();
        for (ResourceLocation id : unlockedSkills) {
            unlocked.add(StringTag.valueOf(id.toString()));
        }
        tag.put("unlockedSkills", unlocked);

        ListTag equipped = new ListTag();
        for (ResourceLocation id : equippedSkills) {
            equipped.add(StringTag.valueOf(id == null ? "" : id.toString()));
        }
        tag.put("equippedSkills", equipped);

        ListTag wheel = new ListTag();
        for (ResourceLocation id : wheelSkills) {
            wheel.add(StringTag.valueOf(id.toString()));
        }
        tag.put("wheelSkills", wheel);

        ListTag passives = new ListTag();
        for (ResourceLocation id : unlockedPassives) {
            CompoundTag passiveTag = new CompoundTag();
            passiveTag.putString("id", id.toString());
            passiveTag.putInt("level", passiveLevel(id));
            passives.add(passiveTag);
        }
        tag.put("unlockedPassives", passives);

        ListTag disabled = new ListTag();
        for (ResourceLocation id : disabledPassives) {
            disabled.add(StringTag.valueOf(id.toString()));
        }
        tag.put("disabledPassives", disabled);

        CompoundTag disabledAtTag = new CompoundTag();
        passiveDisabledAtMillis.forEach((id, disabledAt) -> disabledAtTag.putLong(id.toString(), Math.max(0L, disabledAt)));
        tag.put("passiveDisabledAtMillis", disabledAtTag);

        ListTag curses = new ListTag();
        for (ResourceLocation id : activeCurses) {
            curses.add(StringTag.valueOf(id.toString()));
        }
        tag.put("activeCurses", curses);

        CompoundTag tuningTag = new CompoundTag();
        tuning.forEach((id, value) -> tuningTag.put(id.toString(), value.save()));
        tag.put("tuning", tuningTag);
        tag.putInt("rosterVersion", ROSTER_VERSION);

        CompoundTag classesTag = new CompoundTag();
        classProgress.forEach((id, value) -> classesTag.put(id.toString(), value.save()));
        tag.put("classes", classesTag);

        CompoundTag echoTag = new CompoundTag();
        cooldownEchoCounters.forEach((id, count) -> echoTag.putInt(id.toString(), Math.max(0, count)));
        tag.put("cooldownEchoCounters", echoTag);

        CompoundTag passiveCounterTag = new CompoundTag();
        passiveCounters.forEach((id, count) -> passiveCounterTag.putInt(id.toString(), count));
        tag.put("passiveCounters", passiveCounterTag);

        CompoundTag envyTag = new CompoundTag();
        envyProgress.forEach((id, progress) -> envyTag.putInt(id.toString(), Math.max(0, progress)));
        tag.put("envyProgress", envyTag);

        CompoundTag towerTag = new CompoundTag();
        towerClears.forEach((id, floor) -> towerTag.putInt(id.toString(), Math.max(0, floor)));
        tag.put("towerClears", towerTag);

        ListTag claimedTowerRewardsTag = new ListTag();
        for (String key : claimedTowerRewards) {
            claimedTowerRewardsTag.add(StringTag.valueOf(key));
        }
        tag.put("claimedTowerRewards", claimedTowerRewardsTag);

        ListTag waypointTags = new ListTag();
        for (SpaceWaypoint waypoint : spaceWaypoints) {
            waypointTags.add(waypoint.save());
        }
        tag.put("spaceWaypoints", waypointTags);
        return tag;
    }

    public static PlayerMagicState load(CompoundTag tag) {
        PlayerMagicState state = new PlayerMagicState();
        state.mana = tag.contains("mana") ? tag.getInt("mana") : MagicalConfig.MAX_MANA.get();
        state.barrier = tag.contains("barrier") ? tag.getInt("barrier") : MagicalConfig.MAX_BARRIER.get();
        state.proficiencyXp = tag.getInt("proficiencyXp");
        state.manaVault = tag.getInt("manaVault");
        state.maxManaBonus = tag.getInt("maxManaBonus");
        state.maxBarrierBonus = tag.getInt("maxBarrierBonus");
        state.manaBoostPurchases = tag.getInt("manaBoostPurchases");
        state.barrierBoostPurchases = tag.getInt("barrierBoostPurchases");
        if (tag.contains("authorityId")) {
            ResourceLocation loadedAuthority = ResourceLocation.parse(tag.getString("authorityId"));
            if (AuthorityContent.get(loadedAuthority) != null) {
                state.authorityId = loadedAuthority;
            }
        }
        state.activeSubspaceEntityId = tag.contains("activeSubspaceEntityId") ? tag.getInt("activeSubspaceEntityId") : -1;
        state.anchorSigilDimension = tag.getString("anchorSigilDimension");
        state.anchorSigilX = tag.getInt("anchorSigilX");
        state.anchorSigilY = tag.getInt("anchorSigilY");
        state.anchorSigilZ = tag.getInt("anchorSigilZ");
        state.anchorSigilTicks = Math.max(0, tag.getInt("anchorSigilTicks"));
        state.mirrorDecoyEntityId = tag.contains("mirrorDecoyEntityId") ? tag.getInt("mirrorDecoyEntityId") : -1;
        state.nullThreadEntityId = tag.contains("nullThreadEntityId") ? tag.getInt("nullThreadEntityId") : -1;
        state.activeMagicBarrageEntityId = tag.contains("activeMagicBarrageEntityId") ? tag.getInt("activeMagicBarrageEntityId") : -1;
        state.blackFlamesImbueTicks = Math.max(0, tag.getInt("blackFlamesImbueTicks"));
        state.blackFlamesImbueDamage = Math.max(0.0F, tag.getFloat("blackFlamesImbueDamage"));
        state.blackFlamesImbueKnockback = Math.max(0.0F, tag.getFloat("blackFlamesImbueKnockback"));
        state.soulBondDimension = tag.getString("soulBondDimension");
        if (tag.hasUUID("soulBondEntityUuid")) {
            state.soulBondEntityUuid = tag.getUUID("soulBondEntityUuid");
        }
        if (tag.hasUUID("soulBondCollapseEntityUuid")) {
            state.soulBondCollapseEntityUuid = tag.getUUID("soulBondCollapseEntityUuid");
        }
        state.soulBondCollapseTicks = Math.max(0, tag.getInt("soulBondCollapseTicks"));
        if (state.soulBondCollapseTicks <= 0) {
            state.soulBondCollapseEntityUuid = null;
        }
        state.mana = clamp(state.mana, 0, state.maxMana());
        state.barrier = clamp(state.barrier, 0, state.maxBarrier());
        state.prideGauge = tag.getInt("prideGauge");
        state.wrathGauge = tag.getInt("wrathGauge");
        state.greedHoard = tag.getInt("greedHoard");
        state.slothStillness = tag.getInt("slothStillness");
        state.restedStillnessTicks = tag.getInt("restedStillnessTicks");
        state.manaChargeTicks = tag.getInt("manaChargeTicks");
        state.manaChargeLevel = tag.getInt("manaChargeLevel");
        state.gluttonyCooldownTicks = tag.getInt("gluttonyCooldownTicks");
        state.lastSlothRestDay = tag.contains("lastSlothRestDay") ? tag.getLong("lastSlothRestDay") : -1L;
        state.lastSlothPenaltyDay = tag.contains("lastSlothPenaltyDay") ? tag.getLong("lastSlothPenaltyDay") : -1L;

        ListTag unlocked = tag.getList("unlockedSkills", Tag.TAG_STRING);
        for (Tag entry : unlocked) {
            state.unlock(ResourceLocation.parse(entry.getAsString()));
        }
        AuthorityDefinition loadedAuthority = AuthorityContent.get(state.authorityId);
        if (loadedAuthority != null) {
            loadedAuthority.skillIds().forEach(state::unlock);
        }

        ListTag equipped = tag.getList("equippedSkills", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(equipped.size(), state.equippedSkills.length); i++) {
            String raw = equipped.getString(i);
            ResourceLocation id = raw.isEmpty() ? null : ResourceLocation.parse(raw);
            state.equippedSkills[i] = id != null && state.hasUnlocked(id) && MagicContent.get(id) != null ? id : null;
        }

        ListTag wheel = tag.getList("wheelSkills", Tag.TAG_STRING);
        for (Tag entry : wheel) {
            ResourceLocation id = ResourceLocation.parse(entry.getAsString());
            if (state.hasUnlocked(id) && MagicContent.get(id) != null) {
                state.wheelSkills.add(id);
            }
        }

        ListTag passives = tag.getList("unlockedPassives", Tag.TAG_STRING);
        for (Tag entry : passives) {
            state.unlockPassive(ResourceLocation.parse(entry.getAsString()));
        }
        ListTag leveledPassives = tag.getList("unlockedPassives", Tag.TAG_COMPOUND);
        for (Tag entry : leveledPassives) {
            CompoundTag passiveTag = (CompoundTag) entry;
            ResourceLocation id = ResourceLocation.parse(passiveTag.getString("id"));
            state.setPassiveLevel(id, passiveTag.contains("level") ? passiveTag.getInt("level") : 1);
        }

        ListTag disabled = tag.getList("disabledPassives", Tag.TAG_STRING);
        for (Tag entry : disabled) {
            ResourceLocation id = ResourceLocation.parse(entry.getAsString());
            if (state.hasPassive(id)) {
                state.disabledPassives.add(id);
            }
        }

        CompoundTag disabledAtTag = tag.getCompound("passiveDisabledAtMillis");
        for (String key : disabledAtTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(key);
            if (state.disabledPassives.contains(id)) {
                state.passiveDisabledAtMillis.put(id, Math.max(0L, disabledAtTag.getLong(key)));
            }
        }
        long now = System.currentTimeMillis();
        for (ResourceLocation id : state.disabledPassives) {
            if (MagicPassiveContent.isSinPassive(id)) {
                state.passiveDisabledAtMillis.putIfAbsent(id, now);
            }
        }

        ListTag curses = tag.getList("activeCurses", Tag.TAG_STRING);
        for (Tag entry : curses) {
            state.addCurse(ResourceLocation.parse(entry.getAsString()));
        }

        int rosterVersion = tag.contains("rosterVersion") ? tag.getInt("rosterVersion") : 1;
        CompoundTag tuningTag = tag.getCompound("tuning");
        for (String key : tuningTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(key);
            if (MagicContent.get(id) != null) {
                state.tuning.put(id, MagicSkillTuning.load(tuningTag.getCompound(key)));
            }
        }
        CompoundTag classesTag = tag.getCompound("classes");
        for (String key : classesTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(key);
            if (MagicalClasses.get(id) != null) {
                state.classProgress.put(id, MagicalClassProgress.load(classesTag.getCompound(key)));
            }
        }
        CompoundTag echoTag = tag.getCompound("cooldownEchoCounters");
        for (String key : echoTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(key);
            if (MagicContent.get(id) != null) {
                state.cooldownEchoCounters.put(id, Math.max(0, echoTag.getInt(key)));
            }
        }
        CompoundTag passiveCounterTag = tag.getCompound("passiveCounters");
        for (String key : passiveCounterTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(key);
            // Only keep counters whose passive still exists, so a removed passive cannot leak into saves.
            if (MagicPassiveContent.get(id) != null) {
                state.setPassiveCounter(id, passiveCounterTag.getInt(key));
            }
        }
        CompoundTag envyTag = tag.getCompound("envyProgress");
        for (String key : envyTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.parse(key);
            if (MagicContent.get(id) != null && !state.hasUnlocked(id)) {
                state.envyProgress.put(id, Math.max(0, envyTag.getInt(key)));
            }
        }
        CompoundTag towerTag = tag.getCompound("towerClears");
        for (String key : towerTag.getAllKeys()) {
            state.towerClears.put(ResourceLocation.parse(key), Math.max(0, towerTag.getInt(key)));
        }
        ListTag claimedTowerRewardsTag = tag.getList("claimedTowerRewards", Tag.TAG_STRING);
        for (Tag entry : claimedTowerRewardsTag) {
            String key = entry.getAsString();
            if (!key.isBlank()) {
                state.claimedTowerRewards.add(key);
            }
        }
        ListTag waypointTags = tag.getList("spaceWaypoints", Tag.TAG_COMPOUND);
        for (Tag entry : waypointTags) {
            if (state.spaceWaypoints.size() >= 24) {
                break;
            }
            state.spaceWaypoints.add(SpaceWaypoint.load((CompoundTag) entry));
        }
        for (ResourceLocation classId : state.classProgress.keySet()) {
            if (state.hasClass(classId)) {
                state.unlockClassRewards(MagicalClasses.get(classId), null);
            }
        }
        if (rosterVersion < ROSTER_VERSION && !state.unlockedSkills.isEmpty() && state.unlockedSkills.stream().noneMatch(PlayerMagicState::isProgressionSkill)) {
            // the old roster was scrapped under this save: grant the new starter awakening once
            state.unlockStarterAwakening(null);
        }
        state.ensureSinCursesForEnabledPassives();
        return state;
    }

    /** A normal-progression (non-class, non-created, non-sub, non-hidden) skill. */
    private static boolean isProgressionSkill(ResourceLocation id) {
        MagicSkillDefinition definition = MagicContent.get(id);
        return definition != null && definition.tier() >= 0
                && !MagicContent.CLASS_REWARD_SKILLS.contains(id)
                && !MagicContent.CREATED_SKILLS.contains(id)
                && !MagicContent.isSubSkill(id);
    }

    private boolean ensureSinCursesForEnabledPassives() {
        boolean changed = false;
        for (ResourceLocation passiveId : unlockedPassives) {
            if (!disabledPassives.contains(passiveId)) {
                ResourceLocation linkedCurse = MagicPassiveContent.linkedCurseForSinPassive(passiveId);
                if (linkedCurse != null) {
                    changed |= activeCurses.add(linkedCurse);
                }
            }
        }
        return changed;
    }

    private void clearPassiveRuntimeState(ResourceLocation passiveId) {
        if (MagicPassiveContent.SIN_PRIDE.id().equals(passiveId)) {
            prideGauge = 0;
        } else if (MagicPassiveContent.SIN_GREED.id().equals(passiveId)) {
            greedHoard = 0;
        } else if (MagicPassiveContent.SIN_ENVY.id().equals(passiveId)) {
            envyProgress.clear();
        } else if (MagicPassiveContent.SIN_GLUTTONY.id().equals(passiveId)) {
            manaChargeTicks = 0;
            manaChargeLevel = 0;
            gluttonyCooldownTicks = 0;
        } else if (MagicPassiveContent.SIN_WRATH.id().equals(passiveId)) {
            wrathGauge = 0;
        } else if (MagicPassiveContent.SIN_SLOTH.id().equals(passiveId)) {
            slothStillness = 0;
            restedStillnessTicks = 0;
            slothBedTicks = 0;
        } else if (MagicPassiveContent.MANA_FLIGHT.id().equals(passiveId)) {
            // Flight ability flags are corrected on the next player tick.
        }
    }

    private void mirrorSkillCooldownToLoadout(ResourceLocation skillId, int cooldown) {
        for (int i = 0; i < equippedSkills.length; i++) {
            if (skillId.equals(equippedSkills[i])) {
                slotCooldowns[i] = cooldown;
            }
        }
    }

    private void refreshLoadoutCooldownsFromSkills() {
        for (int i = 0; i < equippedSkills.length; i++) {
            slotCooldowns[i] = equippedSkills[i] == null ? 0 : skillCooldown(equippedSkills[i]);
        }
    }

    private void grantProficiencyRewards(ServerPlayer player, int previousLevel, int newLevel) {
        if (newLevel <= previousLevel) {
            return;
        }
        for (int level = previousLevel + 1; level <= newLevel; level++) {
            ResourceLocation reward = MagicContent.randomProficiencyReward(unlockedSkills, player == null ? net.minecraft.util.RandomSource.create() : player.getRandom());
            if (reward != null && unlock(reward) && player != null) {
                player.displayClientMessage(Component.translatable("message.magical.skill_unlocked", Component.translatable(MagicContent.get(reward).nameKey())), false);
            }
        }
    }

    private void unlockClassRewards(MagicalClassDefinition definition, ServerPlayer player) {
        if (definition != null) {
            for (ResourceLocation reward : definition.rewardSkills()) {
                if (unlock(reward) && player != null) {
                    player.displayClientMessage(Component.translatable("message.magical.skill_unlocked", Component.translatable(MagicContent.get(reward).nameKey())), false);
                }
            }
            for (ResourceLocation passiveId : definition.rewardPassives()) {
                MagicPassiveDefinition passive = MagicPassiveContent.get(passiveId);
                if (passive != null && unlockPassive(passiveId) && player != null) {
                    player.displayClientMessage(Component.translatable("message.magical.passive_unlocked", Component.translatable(passive.nameKey())), false);
                }
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "PlayerMagicState{" +
                "mana=" + mana +
                ", barrier=" + barrier +
                ", proficiencyXp=" + proficiencyXp +
                ", unlockedSkills=" + unlockedSkills +
                ", unlockedPassives=" + unlockedPassives +
                ", passiveLevels=" + passiveLevels +
                ", activeCurses=" + activeCurses +
                ", equippedSkills=" + Arrays.toString(equippedSkills) +
                '}';
    }
}
