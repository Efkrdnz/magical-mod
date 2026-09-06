package com.efkrdnz.magical.classes;

import net.minecraft.nbt.CompoundTag;

public final class MagicalClassProgress {
    private boolean unlocked;
    private int xp;

    public boolean unlocked() {
        return unlocked;
    }

    public void unlock() {
        unlocked = true;
    }

    public int xp() {
        return xp;
    }

    public void addXp(int amount) {
        xp = Math.max(0, xp + amount);
    }

    /** Deduct a cost if the pool can cover it; returns false and changes nothing otherwise. */
    public boolean spendXp(int amount) {
        if (amount < 0 || xp < amount) {
            return false;
        }
        xp -= amount;
        return true;
    }

    public MagicalClassProgress copy() {
        MagicalClassProgress copy = new MagicalClassProgress();
        copy.unlocked = unlocked;
        copy.xp = xp;
        return copy;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("unlocked", unlocked);
        tag.putInt("xp", xp);
        return tag;
    }

    public static MagicalClassProgress load(CompoundTag tag) {
        MagicalClassProgress progress = new MagicalClassProgress();
        progress.unlocked = tag.getBoolean("unlocked");
        progress.xp = tag.getInt("xp");
        return progress;
    }
}
