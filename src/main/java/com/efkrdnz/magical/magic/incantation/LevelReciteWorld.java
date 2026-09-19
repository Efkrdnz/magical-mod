package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodDamageTypes;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * The world as a clause verse sees it: the questions {@link ReciteWorld} asks, answered off a
 * real player and level. This and {@link IncantationService} are the only files in the package
 * that know Minecraft; the Reciter never does.
 */
public final class LevelReciteWorld implements ReciteWorld {
    private final ServerPlayer player;
    private final PlayerMagicState state;
    private final int slot;

    public LevelReciteWorld(ServerPlayer player, PlayerMagicState state, int slot) {
        this.player = player;
        this.state = state;
        this.slot = slot;
    }

    @Override
    public int enemiesWithin(double blocks) {
        return SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), blocks).size();
    }

    /** Anything in flight counts, another wielder's bodies included: a crowded sky is a crowded sky. */
    @Override
    public int projectilesWithin(double blocks) {
        return player.serverLevel().getEntities(player, player.getBoundingBox().inflate(blocks),
                entity -> entity instanceof Projectile || entity instanceof VerseBodyEntity).size();
    }

    @Override
    public double healthFraction() {
        float max = player.getMaxHealth();
        return max <= 0.0F ? 1.0D : Math.max(0.0D, Math.min(1.0D, player.getHealth() / max));
    }

    @Override
    public boolean everyOtherSkipAndFlip() {
        return state.grimoire().everyOtherSkipAndFlip();
    }

    @Override
    public int random(int bound) {
        return bound <= 0 ? 0 : player.getRandom().nextInt(bound);
    }

    @Override
    public List<Verse> allVerses() {
        return List.copyOf(VerseContent.CATALOGUE.all());
    }

    @Override
    public boolean isKnown(ResourceLocation id) {
        return state.grimoire().knows(id);
    }

    /** The verses written in the other three slots, in slot order, each as many times as it is written. */
    @Override
    public List<Verse> otherIncantationVerses() {
        List<Verse> verses = new ArrayList<>();
        for (int other = 0; other < Grimoire.SLOTS; other++) {
            if (other == slot) {
                continue;
            }
            for (Incantation.Entry entry : state.grimoire().incantation(other).entries()) {
                Verse verse = VerseContent.get(entry.id());
                if (verse != null) {
                    verses.add(verse);
                }
            }
        }
        return verses;
    }

    /** Blood Toll pays in flesh through the Blood school's true damage: no armour, no resistance, no barrier. */
    @Override
    public void payHealth(double halfHearts) {
        if (halfHearts > 0.0D) {
            player.hurt(BloodDamageTypes.price(player), (float) halfHearts);
        }
    }
}
