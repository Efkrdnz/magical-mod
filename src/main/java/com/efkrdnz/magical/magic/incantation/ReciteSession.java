package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The three piles between presses, {@code first_shot} and the rest carried until the next rest.
 * Never saved: rebuilt from the incantation on an edit, login, respawn or a change of dimension.
 *
 * <p>Where {@code gun.lua} reassigns a pile ({@code hand = {}}, {@code deck = {}},
 * {@code discarded = {}}) this replaces the list object; where it inserts or removes, the live list
 * is mutated. A verse that captured a list keeps the old one, which is what {@code ipairs} does.
 */
public final class ReciteSession {

    private List<VerseCard> deck = new ArrayList<>();
    private List<VerseCard> hand = new ArrayList<>();
    private List<VerseCard> discard = new ArrayList<>();
    private final List<VerseCard> all = new ArrayList<>();
    private boolean firstShot = true;
    private int restCarry;

    private ReciteSession() {
    }

    /** {@code _add_card_to_deck} for every entry; an id the catalogue no longer holds is skipped, its index kept. */
    public static ReciteSession of(Incantation incantation, VerseCatalogue catalogue) {
        ReciteSession session = new ReciteSession();
        List<Incantation.Entry> entries = incantation.entries();
        for (int i = 0; i < entries.size(); i++) {
            Verse verse = catalogue.get(entries.get(i).id());
            if (verse == null) {
                continue;
            }
            VerseCard card = new VerseCard(verse, i, entries.get(i).usesRemaining());
            session.deck.add(card);
            session.all.add(card);
        }
        return session;
    }

    public List<VerseCard> deck() {
        return deck;
    }

    public List<VerseCard> hand() {
        return hand;
    }

    public List<VerseCard> discard() {
        return discard;
    }

    /** {@code hand = {}}. */
    public void replaceHand() {
        hand = new ArrayList<>();
    }

    /** {@code deck = {}}. */
    public void replaceDeck() {
        deck = new ArrayList<>();
    }

    /** {@code move_discarded_to_deck}: append every discarded card to the deck, then {@code discarded = {}}. */
    public void moveDiscardToDeck() {
        deck.addAll(discard);
        discard = new ArrayList<>();
    }

    /** {@code order_deck} with shuffling off: a stable sort by deck index. */
    public void orderDeck() {
        deck.sort(Comparator.comparingInt(VerseCard::deckIndex));
    }

    public boolean firstShot() {
        return firstShot;
    }

    public void firstShotDone() {
        firstShot = false;
    }

    public int restCarry() {
        return restCarry;
    }

    public void setRestCarry(int ticks) {
        restCarry = ticks;
    }

    /** Every card ever built for this session, whether or not a pile still holds it. */
    public List<VerseCard> cards() {
        return List.copyOf(all);
    }

    public void writeBack(Incantation incantation) {
        for (VerseCard card : all) {
            incantation.setUses(card.deckIndex(), card.usesRemaining());
        }
    }

    public int unreadCount() {
        return deck.size();
    }

    /** The verse the next press starts with, for the HUD line; null when the deck is empty. */
    public ResourceLocation nextUnread() {
        return deck.isEmpty() ? null : deck.get(0).id();
    }
}
