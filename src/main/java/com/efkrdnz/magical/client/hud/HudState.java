package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.arcane.ArcanePlayerData;
import com.efkrdnz.magical.arcane.ArcaneSpellResolver;
import com.efkrdnz.magical.arcane.SpellPreset;
import com.efkrdnz.magical.client.ClientArcaneState;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.ClientStatusState;
import com.efkrdnz.magical.client.MagicalKeyMappings;
import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.client.hud.HudSnapshot.Announcement;
import com.efkrdnz.magical.client.hud.HudSnapshot.Card;
import com.efkrdnz.magical.client.hud.HudSnapshot.Chip;
import com.efkrdnz.magical.client.hud.HudSnapshot.GaugeLine;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.HudSnapshot.Line;
import com.efkrdnz.magical.client.hud.HudSnapshot.Satellite;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.AuthorityDefinition;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.DarkService;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.race.MagicalRace;
import com.efkrdnz.magical.race.MagicalRaces;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The tick side of the HUD: owns the {@link HudSnapshot}, rebuilds it only when something it
 * depends on changed, and integrates every moving value one step per client tick.
 *
 * <p>What can change is enumerated: the player state packet, the cooldown clock, the statuses,
 * the announcer, the key bindings, the window, the language, the options, and the level. Each has
 * a version or an identity compared here. Idle at full mana that is zero rebuilds a second;
 * regenerating, one. The only per-tick string work is the seconds numeral over a cooling card,
 * reshaped when the second changes. Nothing in the render path allocates, formats or measures.
 */
public final class HudState {
    /** Sins in registration order; each has a fixed seat on the crown and a readout line. */
    private static final MagicPassiveDefinition[] SINS = {
            MagicPassiveContent.SIN_PRIDE, MagicPassiveContent.SIN_GREED, MagicPassiveContent.SIN_LUST,
            MagicPassiveContent.SIN_ENVY, MagicPassiveContent.SIN_GLUTTONY, MagicPassiveContent.SIN_WRATH,
            MagicPassiveContent.SIN_SLOTH};
    private static final String[] SIN_KEYS = {"pride", "greed", "lust", "envy", "gluttony", "wrath", "sloth"};
    /** The readout tween index of the mana charge, after the seven seats; the vault's is one beyond. */
    public static final int CHARGE_TWEEN = SINS.length;
    /** A satellite stays on its seat this long after its gauge empties, so it does not flicker. */
    private static final int SIN_LINGER_TICKS = 40;
    /** Gluttony's devour cooldown, as MagicSinService sets it. */
    private static final int GLUTTONY_DEVOUR_TICKS = 280;
    /** Below this fraction the mana ring goes to the danger tint and pulses. */
    public static final float LOW_MANA = 0.2F;
    /** Arcane stability under which a spell can misfire; the arcane line goes to the danger tint. */
    private static final int ARCANE_UNSTABLE_BELOW = 65;
    private static final int JOIN_FADE_TICKS = 24;
    private static final int KEY_LABEL_MAX_W = 10;

    private static HudOptions options = HudOptions.DEFAULTS;
    private static HudSnapshot snapshot;
    private static int builds;
    private static int buildsThisSecond;
    private static int buildsLastSecond;
    private static long debugSecond = -1L;
    private static Label debugLabel;

    private static final HudTween MANA = new HudTween();
    private static final HudTween BARRIER = new HudTween();
    private static final HudTween VESSEL = new HudTween();
    private static final HudTween CORRUPTION = new HudTween();
    private static final HudTween XP = new HudTween();
    private static final HudTween HALO = new HudTween();
    private static final HudTween FADE = new HudTween();
    private static final HudTween[] SIN_GAUGES = new HudTween[SINS.length];
    private static final long[] SIN_HIDE_AT = new long[SINS.length];
    private static final long[] READY_AT = new long[MagicContent.LOADOUT_SIZE];
    private static final Label[] CARD_SECONDS = new Label[MagicContent.LOADOUT_SIZE];
    private static final int[] CARD_SECONDS_VALUE = new int[MagicContent.LOADOUT_SIZE];
    private static final int[] STATUS_INITIAL = new int[MagicStatus.values().length];
    private static final long[] STATUS_SEEN_AT = new long[MagicStatus.values().length];
    private static final InputConstants.Key[] KEYS = new InputConstants.Key[MagicContent.LOADOUT_SIZE];

    private static ClientLevel lastLevel;
    private static int magicVersion = -1;
    private static int cooldownVersion = -1;
    private static int statusVersion = -1;
    private static int announcerVersion = -1;
    private static int sinVisibleMask;
    private static boolean chargeVisible;
    private static int guiWidth;
    private static int guiHeight;
    private static Language language;
    private static boolean dirty = true;

    static {
        for (int i = 0; i < SINS.length; i++) {
            SIN_GAUGES[i] = new HudTween();
            SIN_HIDE_AT[i] = -1L;
        }
    }

    private HudState() {}

    public static HudSnapshot snapshot() {
        return snapshot;
    }

    public static HudOptions options() {
        return options;
    }

    /** The config listener calls this; anything else is a test. */
    public static void setOptions(HudOptions value) {
        options = value == null ? HudOptions.DEFAULTS : value;
        dirty = true;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static int builds() {
        return builds;
    }

    /** The cost line for the corner, when the debug option is on; rebuilt once a second. */
    public static Label debugLabel() {
        return options.debug() ? debugLabel : null;
    }

    /** The seconds left over a cooling card, or null while it is ready. Reshaped only when the second changes. */
    public static Label cardSeconds(int slot) {
        return CARD_SECONDS[slot];
    }

    public static HudTween mana() {
        return MANA;
    }

    public static HudTween barrier() {
        return BARRIER;
    }

    public static HudTween vessel() {
        return VESSEL;
    }

    public static HudTween corruption() {
        return CORRUPTION;
    }

    public static HudTween xp() {
        return XP;
    }

    public static HudTween halo() {
        return HALO;
    }

    /** The whole HUD's opacity envelope: the fade-in on joining a world. */
    public static HudTween fade() {
        return FADE;
    }

    public static HudTween sinGauge(int seat) {
        return SIN_GAUGES[seat];
    }

    /** Game time plus the partial tick, the clock every extrapolation in the renderer reads. */
    public static float now(float partialTick) {
        return nowTicks() + partialTick;
    }

    public static long nowTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    /** Runs at the end of every client tick, after every input handler. */
    public static void tick(Minecraft minecraft) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("magical_hud_tick");
        try {
            if (minecraft.level != lastLevel) {
                reset();
                lastLevel = minecraft.level;
                if (minecraft.level != null) {
                    FADE.snap(0.0F);
                    FADE.ramp(1.0F, options.reducedMotion() ? 6 : JOIN_FADE_TICKS);
                }
            }
            if (minecraft.player == null || minecraft.level == null) {
                snapshot = null;
                return;
            }
            long now = minecraft.level.getGameTime();
            HudAnnouncer.tick(now);
            RuleFlash.tick(now);
            noteFinishedCooldowns(now);
            if (dirty || environmentChanged(minecraft) || stateChanged()) {
                rebuild(minecraft, now);
            }
            tickTweens();
            tickCardSeconds(minecraft.font, now);
            tickDebug(minecraft, now);
        } finally {
            profiler.pop();
        }
    }

    private static void reset() {
        snapshot = null;
        dirty = true;
        magicVersion = -1;
        cooldownVersion = -1;
        statusVersion = -1;
        announcerVersion = -1;
        HudAnnouncer.reset();
        RuleFlash.reset();
        sinVisibleMask = 0;
        chargeVisible = false;
        for (int i = 0; i < SINS.length; i++) {
            SIN_HIDE_AT[i] = -1L;
            SIN_GAUGES[i].snap(0.0F);
        }
        java.util.Arrays.fill(READY_AT, Long.MIN_VALUE);
        java.util.Arrays.fill(CARD_SECONDS, null);
        java.util.Arrays.fill(CARD_SECONDS_VALUE, 0);
        java.util.Arrays.fill(STATUS_INITIAL, 0);
        java.util.Arrays.fill(STATUS_SEEN_AT, 0L);
        MANA.snap(0.0F);
        BARRIER.snap(0.0F);
        VESSEL.snap(0.0F);
        CORRUPTION.snap(0.0F);
        XP.snap(0.0F);
        HALO.snap(0.0F);
        ClientCooldowns.reset();
        com.efkrdnz.magical.client.ClientForgeCombo.clear();
    }

    private static void noteFinishedCooldowns(long now) {
        HudSnapshot current = snapshot;
        if (current == null) {
            return;
        }
        for (Card card : current.cards()) {
            if (card.skill() != null && ClientCooldowns.justFinished(card.skill())) {
                READY_AT[card.slot()] = now;
                dirty = true;
            }
        }
    }

    private static boolean environmentChanged(Minecraft minecraft) {
        boolean changed = false;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width != guiWidth || height != guiHeight) {
            guiWidth = width;
            guiHeight = height;
            changed = true;
        }
        for (int i = 0; i < KEYS.length; i++) {
            InputConstants.Key key = MagicalKeyMappings.CAST_SLOTS[i].getKey();
            if (!key.equals(KEYS[i])) {
                KEYS[i] = key;
                changed = true;
            }
        }
        if (Language.getInstance() != language) {
            language = Language.getInstance();
            changed = true;
        }
        return changed;
    }

    private static boolean stateChanged() {
        boolean changed = false;
        if (ClientMagicState.version() != magicVersion) {
            magicVersion = ClientMagicState.version();
            changed = true;
        }
        if (ClientCooldowns.version() != cooldownVersion) {
            cooldownVersion = ClientCooldowns.version();
            changed = true;
        }
        if (ClientStatusState.version() != statusVersion) {
            statusVersion = ClientStatusState.version();
            changed = true;
        }
        if (HudAnnouncer.version() != announcerVersion) {
            announcerVersion = HudAnnouncer.version();
            changed = true;
        }
        return changed;
    }

    private static void tickDebug(Minecraft minecraft, long now) {
        long second = now / 20L;
        if (second == debugSecond) {
            return;
        }
        debugSecond = second;
        buildsLastSecond = buildsThisSecond;
        buildsThisSecond = 0;
        if (options.debug()) {
            debugLabel = label(minecraft.font, Component.translatable("hud.magical.debug",
                    snapshot == null ? 0 : snapshot.version(), SigilRenderer.lastQuads(), SigilRenderer.lastTextDraws(), buildsLastSecond),
                    HudPalette.TEXT_MUTED);
        } else {
            debugLabel = null;
        }
    }

    /** The numeral over a cooling card: whole seconds, rounded up, reshaped only when it changes. */
    private static void tickCardSeconds(Font font, long now) {
        HudSnapshot current = snapshot;
        for (int slot = 0; slot < CARD_SECONDS.length; slot++) {
            int seconds = 0;
            if (current != null && slot < current.cards().length && current.cards()[slot].onCooldown()) {
                Card card = current.cards()[slot];
                long left = card.cooldownRemaining() - (now - card.cooldownStart());
                seconds = left > 0L ? (int) ((left + 19L) / 20L) : 0;
            }
            if (seconds != CARD_SECONDS_VALUE[slot]) {
                CARD_SECONDS_VALUE[slot] = seconds;
                CARD_SECONDS[slot] = seconds > 0 ? label(font, Component.literal(seconds + "s"), HudPalette.TEXT_PRIMARY) : null;
            }
        }
    }

    private static void tickTweens() {
        MANA.tick();
        BARRIER.tick();
        VESSEL.tick();
        CORRUPTION.tick();
        XP.tick();
        HALO.tick();
        FADE.tick();
        for (HudTween gauge : SIN_GAUGES) {
            gauge.tick();
        }
        // Sin satellites appear when a gauge rises and linger a while after it empties; a change
        // in which seats are occupied is a rebuild, because the seats are in the snapshot.
        int mask = 0;
        long now = nowTicks();
        for (int i = 0; i < SINS.length; i++) {
            boolean lit = SIN_GAUGES[i].target() > 0.0F;
            if (lit) {
                SIN_HIDE_AT[i] = -1L;
            } else if (SIN_HIDE_AT[i] < 0L && SIN_GAUGES[i].current() > 0.0F) {
                SIN_HIDE_AT[i] = now + SIN_LINGER_TICKS;
            }
            if (lit || (SIN_HIDE_AT[i] >= 0L && now < SIN_HIDE_AT[i])) {
                mask |= 1 << i;
            }
        }
        boolean charge = HALO.target() > 0.0F || HALO.current() > 0.0F;
        if (mask != sinVisibleMask || charge != chargeVisible) {
            sinVisibleMask = mask;
            chargeVisible = charge;
            dirty = true;
        }
    }

    // ---- the build -------------------------------------------------------------------------------

    private static void rebuild(Minecraft minecraft, long now) {
        dirty = false;
        builds++;
        buildsThisSecond++;
        PlayerMagicState state = ClientMagicState.get();
        Font font = minecraft.font;
        HudLayout layout = HudLayout.of(guiWidth, guiHeight, options.anchor(), options.layoutScale());

        MagicSchool school = dominantSchool(state);
        SchoolMaterial material = SchoolMaterial.of(school);
        Palette manaPalette = HudPalette.mana(school);

        // pools
        MANA.set(fraction(state.mana(), state.maxMana()), now);
        BARRIER.set(fraction(state.barrier(), state.maxBarrier()), now);
        boolean vessel = BloodService.isBloodMage(state);
        boolean corruption = DarkService.isDarkMage(state);
        boolean noticed = EldritchService.isEldritchMage(state);
        VESSEL.set(vessel ? fraction(state.bloodVessel(), PlayerMagicState.MAX_BLOOD_VESSEL) : 0.0F, now);
        CORRUPTION.set(corruption ? state.corruptionFraction() : 0.0F, now);
        int xp = state.proficiencyXp();
        int into = MagicContent.xpIntoLevel(xp);
        int toNext = MagicContent.xpForNextLevel(xp);
        boolean maxLevel = toNext <= 0;
        XP.set(maxLevel ? 1.0F : into / (float) Math.max(1, into + toNext), now);
        int chargeLevel = state.manaChargeLevel();
        HALO.set(state.manaChargeTicks() > 0 && chargeLevel > 0
                ? Math.min(1.0F, state.manaChargeTicks() / (20.0F * (18 + chargeLevel * 8)))
                : 0.0F, now);
        boolean lowMana = MANA.target() < LOW_MANA;
        int manaColor = lowMana ? Palette.mix(manaPalette.base(), HudPalette.DANGER, 0.7F) : manaPalette.base();
        boolean vault = state.hasPassive(MagicPassiveContent.SIN_GREED.id());

        // the core: the current mana over the current barrier; the level in its tag under the sigil
        Label manaLabel = label(font, Component.literal(Integer.toString(state.mana())), lowMana ? HudPalette.DANGER : HudPalette.textTint(manaPalette.bright()));
        Label barrierLabel = label(font, Component.literal(Integer.toString(state.barrier())), HudPalette.textTint(HudPalette.BARRIER));
        Line[] coreLines = {
                new Line(manaLabel, layout.coreLine(0, 2, manaLabel.width())),
                new Line(barrierLabel, layout.coreLine(1, 2, barrierLabel.width()))};
        Label levelLabel = label(font, Component.translatable("hud.magical.level_chip", state.proficiencyLevel()), maxLevel ? HudPalette.TEXT_PRIMARY : HudPalette.XP);
        Line level = new Line(levelLabel, layout.levelTag(levelLabel.width()));

        // cards
        Card[] cards = new Card[MagicContent.LOADOUT_SIZE];
        for (int slot = 0; slot < cards.length; slot++) {
            cards[slot] = card(state, slot, font, layout);
        }

        // crown and readouts
        boolean showSins = options.showSins() && !options.compact();
        Satellite[] satellites = showSins ? satellites(state, layout, now) : HudSnapshot.NO_SATELLITES;
        GaugeLine[] gauges = options.compact() ? HudSnapshot.NO_GAUGES : gauges(state, school, vault, satellites, font, layout);

        // chips
        Chip[] chips = options.showStatuses() ? chips(layout, now) : HudSnapshot.NO_CHIPS;

        // the announcement on screen, if any
        Announcement[] announcements = announcement(font, layout);

        // captions: the loadout's name, then the vessel and corruption numerals and the arcane preset, as room allows
        List<Label> captions = new ArrayList<>(HudLayout.CAPTIONS_MAX);
        if (!options.compact()) {
            String name = state.activeLoadout() == null ? "" : state.activeLoadout().name();
            captions.add(label(font, Component.literal(font.plainSubstrByWidth(name, HudLayout.CAPTION_W - 4)), HudPalette.TEXT_PRIMARY));
            if (vessel) {
                captions.add(label(font, Component.translatable("hud.magical.vessel_line", state.bloodVessel(), PlayerMagicState.MAX_BLOOD_VESSEL),
                        HudPalette.textTint(HudPalette.vessel().bright())));
            }
            if (corruption) {
                boolean ledger = state.isPassiveEnabled(MagicPassiveContent.LEDGER.id()) && state.corruption() < PlayerMagicState.MAX_CORRUPTION;
                int next = Math.min(PlayerMagicState.MAX_CORRUPTION, (DarkService.threshold(state) + 1) * DarkService.THRESHOLD_STEP);
                Component text = ledger
                        ? Component.translatable("hud.magical.corruption_next", state.corruption(), PlayerMagicState.MAX_CORRUPTION, next)
                        : Component.translatable("hud.magical.corruption_line", state.corruption(), PlayerMagicState.MAX_CORRUPTION);
                captions.add(label(font, text, HudPalette.textTint(HudPalette.corruption().bright())));
            }
            if (noticed && captions.size() < HudLayout.CAPTIONS_MAX) {
                captions.add(label(font, Component.translatable("hud.magical.notice_line", state.notice(), PlayerMagicState.MAX_NOTICE),
                        HudPalette.textTint(HudPalette.corruption().bright())));
            }
            ArcanePlayerData arcane = ClientArcaneState.get();
            SpellPreset preset = arcane == null ? null : arcane.activePreset();
            if (preset != null && captions.size() < HudLayout.CAPTIONS_MAX) {
                boolean unstable = ArcaneSpellResolver.resolve(preset.recipe()).stability() < ARCANE_UNSTABLE_BELOW;
                Component line = Component.translatable("hud.magical.arcane_line", preset.name(), arcane.mana());
                captions.add(label(font, Component.literal(font.plainSubstrByWidth(line.getString(), HudLayout.CAPTION_W - 4)),
                        unstable ? HudPalette.DANGER : HudPalette.textTint(MagicSchool.ARCANE.color())));
            }
        }
        Line[] captionLines = new Line[Math.min(captions.size(), HudLayout.CAPTIONS_MAX)];
        for (int i = 0; i < captionLines.length; i++) {
            captionLines[i] = new Line(captions.get(i), layout.captionLine(i));
        }

        snapshot = new HudSnapshot(
                magicVersion ^ (cooldownVersion << 8) ^ (statusVersion << 16), now, options, layout,
                school, manaColor, manaPalette.hot(), notchesFor(material.defaultBand()),
                material.defaultCore().id(), manaPalette.bright(),
                maxLevel, vessel, corruption,
                coreLines, level, cards, satellites, gauges, chips, announcements, captionLines,
                ClientMagicState.receivedAtTick() + state.loadoutSwapLockTicks());
    }

    private static Card card(PlayerMagicState state, int slot, Font font, HudLayout layout) {
        ResourceLocation skill = state.equippedSkill(slot);
        MagicSkillDefinition definition = skill == null ? null : MagicContent.get(skill);
        boolean empty = definition == null;
        int cell = empty ? -1 : HudGlyphs.skillCell(skill);
        int color = empty ? HudPalette.TEXT_MUTED : HudPalette.cardTint(VisualProfiles.of(skill));
        boolean forbidden = !empty && definition.school().isForbidden();
        String keyName = MagicalKeyMappings.CAST_SLOTS[slot].getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        Label key = label(font, Component.literal(font.plainSubstrByWidth(keyName, KEY_LABEL_MAX_W)), empty ? HudPalette.TEXT_MUTED : HudPalette.XP);
        long cooldownStart = 0L;
        int cooldownRemaining = 0;
        int cooldownTotal = 0;
        if (!empty) {
            CooldownClock.Entry entry = ClientCooldowns.entry(skill);
            if (entry != null) {
                cooldownStart = entry.startTick();
                cooldownRemaining = entry.remaining();
                cooldownTotal = entry.total();
            }
        }
        return new Card(slot, skill, cell, color, forbidden, empty, cooldownStart, cooldownRemaining, cooldownTotal,
                READY_AT[slot], key, layout.card(slot), layout.keyTag(slot, key.width()), layout.cardText(slot));
    }

    private static Satellite[] satellites(PlayerMagicState state, HudLayout layout, long now) {
        List<Satellite> list = new ArrayList<>(SINS.length);
        for (int seat = 0; seat < SINS.length; seat++) {
            MagicPassiveDefinition sin = SINS[seat];
            boolean enabled = state.isSinEnabled(sin.id());
            float gauge = enabled ? sinGauge(state, seat) : 0.0F;
            SIN_GAUGES[seat].set(gauge, now);
            boolean lingering = SIN_HIDE_AT[seat] >= 0L && now < SIN_HIDE_AT[seat];
            if (!enabled || (gauge <= 0.0F && !lingering && SIN_GAUGES[seat].current() <= 0.0F)) {
                continue;
            }
            boolean rested = sin == MagicPassiveContent.SIN_SLOTH && state.restedStillnessTicks() > 0;
            list.add(new Satellite(seat, sin.id(), HudGlyphs.sinCell(sin.id()), sin.color(), rested, layout.crownSeat(seat)));
        }
        return list.isEmpty() ? HudSnapshot.NO_SATELLITES : list.toArray(new Satellite[0]);
    }

    /** The vault, one readout per lit satellite in crown order, then the mana charge while it runs - as many as fit. */
    private static GaugeLine[] gauges(PlayerMagicState state, MagicSchool school, boolean vault, Satellite[] satellites, Font font, HudLayout layout) {
        int max = layout.gaugeLinesMax();
        List<GaugeLine> list = new ArrayList<>(satellites.length + 2);
        if (vault && max > 0) {
            list.add(new GaugeLine(CHARGE_TWEEN + 1, label(font, Component.translatable("hud.magical.gauge.vault", compact(state.manaVault())),
                    HudPalette.textTint(MagicPassiveContent.SIN_GREED.color())), layout.gaugeLine(0)));
        }
        for (Satellite satellite : satellites) {
            if (list.size() >= max) {
                break;
            }
            Component text = gaugeText(state, satellite.seat());
            if (text == null) {
                continue;
            }
            int color = satellite.rested() ? HudPalette.lift(satellite.color(), 0.35F) & 0xFFFFFF : satellite.color();
            list.add(new GaugeLine(satellite.seat(), label(font, text, HudPalette.textTint(color)), layout.gaugeLine(list.size())));
        }
        if (chargeVisible && list.size() < max && state.manaChargeLevel() > 0) {
            list.add(new GaugeLine(CHARGE_TWEEN, label(font, Component.translatable("hud.magical.gauge.charge", state.manaChargeLevel()),
                    HudPalette.textTint(HudPalette.mana(school).bright())), layout.gaugeLine(list.size())));
        }
        return list.isEmpty() ? HudSnapshot.NO_GAUGES : list.toArray(new GaugeLine[0]);
    }

    private static Component gaugeText(PlayerMagicState state, int seat) {
        String key = "hud.magical.gauge." + SIN_KEYS[seat];
        return switch (seat) {
            case 0 -> Component.translatable(key, percent(state.prideGauge(), PlayerMagicState.MAX_SIN_GAUGE));
            case 1 -> Component.translatable(key, state.greedHoard());
            case 3 -> Component.translatable(key, Math.round(envyGauge(state) * 100.0F));
            case 4 -> Component.translatable(key, (state.gluttonyCooldownTicks() + 19) / 20);
            case 5 -> Component.translatable(key, percent(state.wrathGauge(), PlayerMagicState.MAX_SIN_GAUGE));
            case 6 -> Component.translatable(state.restedStillnessTicks() > 0 ? "hud.magical.gauge.sloth_rested" : key,
                    percent(state.slothStillness(), PlayerMagicState.MAX_SIN_GAUGE));
            default -> null;   // Lust has no gauge
        };
    }

    private static float sinGauge(PlayerMagicState state, int seat) {
        return switch (seat) {
            case 0 -> fraction(state.prideGauge(), PlayerMagicState.MAX_SIN_GAUGE);
            case 1 -> fraction(state.greedHoard(), PlayerMagicState.MAX_GREED_HOARD);
            case 3 -> envyGauge(state);
            case 4 -> fraction(state.gluttonyCooldownTicks(), GLUTTONY_DEVOUR_TICKS);
            case 5 -> fraction(state.wrathGauge(), PlayerMagicState.MAX_SIN_GAUGE);
            case 6 -> fraction(state.slothStillness(), PlayerMagicState.MAX_SIN_GAUGE);
            default -> 0.0F;   // Lust has no gauge; its seat stays empty
        };
    }

    private static float envyGauge(PlayerMagicState state) {
        float best = 0.0F;
        for (Map.Entry<ResourceLocation, Integer> entry : state.envyProgress().entrySet()) {
            MagicSkillDefinition definition = MagicContent.get(entry.getKey());
            if (definition == null) {
                continue;
            }
            best = Math.max(best, fraction(entry.getValue(), state.envyRequired(definition)));
        }
        return best;
    }

    private static Chip[] chips(HudLayout layout, long now) {
        int count = ClientStatusState.activeCount();
        if (count == 0) {
            return HudSnapshot.NO_CHIPS;
        }
        List<Chip> list = new ArrayList<>(Math.min(count, HudLayout.STATUS_CHIPS_MAX));
        int shown = Math.min(count, HudLayout.STATUS_CHIPS_MAX);
        ClientStatusState.forEachActive((status, remaining, amplifier, value) -> {
            int index = status.ordinal();
            if (STATUS_SEEN_AT[index] == 0L || remaining > STATUS_INITIAL[index]) {
                STATUS_SEEN_AT[index] = now;
                STATUS_INITIAL[index] = remaining;
            }
            if (list.size() < shown) {
                boolean harmful = status != MagicStatus.REVEALED && status != MagicStatus.IMMOVABLE;
                list.add(new Chip(status, HudGlyphs.statusCell(status), HudPalette.status(status), harmful, amplifier,
                        now, Math.max(remaining, STATUS_INITIAL[index]), layout.statusChip(list.size(), shown)));
            }
        });
        for (MagicStatus status : MagicStatus.values()) {
            if (!ClientStatusState.has(status)) {
                STATUS_SEEN_AT[status.ordinal()] = 0L;
                STATUS_INITIAL[status.ordinal()] = 0;
            }
        }
        return list.toArray(new Chip[0]);
    }

    private static Announcement[] announcement(Font font, HudLayout layout) {
        HudAnnouncer.Announcement head = HudAnnouncer.head();
        if (head == null) {
            return HudSnapshot.NO_ANNOUNCEMENTS;
        }
        ResourceLocation id = head.id();
        int cell;
        int color;
        Component name;
        boolean ink = false;
        switch (head.kind()) {
            case SKILL -> {
                MagicSkillDefinition skill = MagicContent.get(id);
                cell = HudGlyphs.skillCell(id);
                color = skill == null ? HudPalette.TEXT_MUTED : HudPalette.cardTint(VisualProfiles.of(id));
                name = skill == null ? Component.literal(id.getPath()) : Component.translatable(skill.nameKey());
            }
            case PASSIVE -> {
                MagicPassiveDefinition passive = MagicPassiveContent.get(id);
                cell = HudGlyphs.sinCell(id);
                color = passive == null ? HudPalette.TEXT_MUTED : HudPalette.textTint(passive.color());
                name = passive == null ? Component.literal(id.getPath()) : Component.translatable(passive.nameKey());
            }
            case CURSE -> {
                MagicPassiveDefinition curse = MagicPassiveContent.get(id);
                cell = HudGlyphs.curseCell();
                color = HudPalette.corruption().bright();
                name = curse == null ? Component.literal(id.getPath()) : Component.translatable(curse.nameKey());
                ink = true;
            }
            case CLASS -> {
                MagicalClassDefinition definition = MagicalClasses.get(id);
                cell = HudGlyphs.classCell();
                color = HudPalette.XP;
                name = definition == null ? Component.literal(id.getPath()) : Component.translatable(definition.nameKey());
            }
            case AUTHORITY -> {
                AuthorityDefinition authority = AuthorityContent.get(id);
                cell = HudGlyphs.authorityCell();
                color = authority == null ? HudPalette.XP : HudPalette.textTint(authority.color());
                name = authority == null ? Component.literal(id.getPath()) : Component.translatable(authority.nameKey());
                ink = true;
            }
            default -> {
                MagicalRace race = MagicalRaces.get(id);
                cell = HudGlyphs.raceCell(id);
                color = race == null ? HudPalette.TEXT_PRIMARY : HudPalette.textTint(race.color());
                name = race == null ? Component.literal(id.getPath()) : Component.translatable(race.nameKey());
            }
        }
        Component line = Component.translatable("hud.magical.announce." + head.kind().name().toLowerCase(Locale.ROOT), name);
        // The plate and emblem carry the colour; the words stay white so they read on any tint.
        Label title = label(font, Component.literal(font.plainSubstrByWidth(line.getString(), HudLayout.ANNOUNCE_TEXT_W)), HudPalette.TEXT_PRIMARY);
        return new Announcement[] {new Announcement(cell, color, ink, title, head.startTick(), HudAnnouncer.LIFETIME_TICKS,
                layout.announceEmblem(), layout.announceText())};
    }

    /** The school with the most equipped skills; a tie goes to the lowest slot; none is Arcane. */
    static MagicSchool dominantSchool(PlayerMagicState state) {
        MagicSchool[] schools = MagicSchool.values();
        int[] counts = new int[schools.length];
        MagicSchool best = null;
        int bestCount = 0;
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            ResourceLocation skill = state.equippedSkill(slot);
            MagicSkillDefinition definition = skill == null ? null : MagicContent.get(skill);
            if (definition == null) {
                continue;
            }
            int count = ++counts[definition.school().ordinal()];
            if (count > bestCount) {
                bestCount = count;
                best = definition.school();
            }
        }
        return best == null ? MagicSchool.ARCANE : best;
    }

    /** How many notches the mana ring's track carries: the school's band pattern, in tick marks. */
    static int notchesFor(GlyphKind band) {
        return switch (band) {
            case TICK_BAND -> 24;
            case DASHED_RING, TOOTH_BAND -> 16;
            case WAVE_BAND, PETAL_BAND, RUNE_BAND -> 12;
            case CHAIN_BAND -> 10;
            case FACET_BAND -> 9;
            case BRAID_BAND, STAMP_BAND -> 8;
            default -> 0;
        };
    }

    /** Vault numerals: 999, 12k, 99k. */
    static String compact(int value) {
        if (value < 1000) {
            return Integer.toString(value);
        }
        return Math.min(99, value / 1000) + "k";
    }

    private static int percent(int value, int max) {
        return Math.round(value * 100.0F / Math.max(1, max));
    }

    private static float fraction(int value, int max) {
        return max <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, value / (float) max));
    }

    private static Label label(Font font, Component text, int color) {
        FormattedCharSequence shaped = text.getVisualOrderText();
        return new Label(shaped, font.width(shaped), color);
    }
}
