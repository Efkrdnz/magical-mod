package com.efkrdnz.magical.magic.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SongRunTest {

    private static final long START = 100L;

    /** A kick every four steps, and a melody note on the first of each bar. */
    private static Score score() {
        Score score = Score.empty();
        for (int step = 0; step < Score.STEPS; step += 4) {
            score = score.set(Track.PERCUSSION, 0, step, true).score();
        }
        for (int bar = 0; bar < Score.BARS; bar++) {
            score = score.set(Track.MELODY, 2, bar * Score.BAR, true).score();
        }
        return score;
    }

    private static double tickOf(int index) {
        return SongRun.tickOf(START, Tempo.STEADY, index);
    }

    @Test
    void anActionWithinTheWindowOfANoteIsOnTheBeat() {
        SongRun run = new SongRun(score(), START);
        SongRun.Judgement judgement = run.act(SongAction.CROUCH, tickOf(4) + SongRun.WINDOW);
        assertEquals(SongRun.Kind.ON_BEAT, judgement.kind());
        assertEquals(Set.of(Track.PERCUSSION), judgement.tracks());
        assertEquals(1, judgement.streak());
    }

    @Test
    void aRestBesideANoteNeverStealsTheHit() {
        SongRun run = new SongRun(score(), START);
        assertEquals(4L, run.noteNear(tickOf(4) + SongRun.WINDOW), "nearer step 5, but step 5 is a rest");
        assertEquals(4L, run.noteNear(tickOf(4) - SongRun.WINDOW));
    }

    @Test
    void anActionPastTheWindowOrOnARestBreaksTheStreak() {
        SongRun run = new SongRun(score(), START);
        run.act(SongAction.CROUCH, tickOf(4));
        assertEquals(SongRun.Kind.OFF_BEAT, run.act(SongAction.CROUCH, tickOf(8) + SongRun.WINDOW + 0.5).kind());
        assertEquals(0, run.streak());
        run.act(SongAction.CROUCH, tickOf(12));
        assertEquals(SongRun.Kind.OFF_BEAT, run.act(SongAction.SWING, tickOf(13)).kind(), "step 13 is a rest");
        assertEquals(0, run.streak());
    }

    @Test
    void everyTrackOnTheStepAnswers() {
        SongRun run = new SongRun(score(), START);
        assertEquals(Set.of(Track.PERCUSSION, Track.MELODY), run.act(SongAction.SWING, tickOf(16)).tracks());
    }

    @Test
    void aStepIsTakenOncePerActionAndARepeatNeitherPaysNorBreaks() {
        SongRun run = new SongRun(score(), START);
        run.act(SongAction.CROUCH, tickOf(4));
        SongRun.Judgement again = run.act(SongAction.CROUCH, tickOf(4) + 1);
        assertEquals(SongRun.Kind.ALREADY_TAKEN, again.kind());
        assertEquals(1, run.streak());
        assertTrue(run.act(SongAction.SWING, tickOf(4)).onBeat(), "a swing on the same step is its own action");
        assertEquals(2, run.streak());
    }

    @Test
    void aFullStreakMakesThemDanceAndStartsAgain() {
        SongRun run = new SongRun(score(), START);
        SongRun.Judgement last = null;
        for (int i = 0; i < SongRun.MAX_STREAK; i++) {
            last = run.act(SongAction.CROUCH, tickOf(i * 4));
            assertEquals(i == SongRun.MAX_STREAK - 1, last.dance(), "dance only on the eighth");
        }
        assertEquals(SongRun.MAX_STREAK, last.streak());
        assertEquals(0, run.streak());
    }

    @Test
    void theStreakRunsAcrossTheLoop() {
        SongRun run = new SongRun(score(), START);
        run.act(SongAction.CROUCH, tickOf(60));
        assertTrue(run.act(SongAction.CROUCH, tickOf(64)).onBeat(), "step 0 of the second loop");
        assertEquals(2, run.streak());
    }

    @Test
    void aClaimIsBelievedOnlyNearTheServerClock() {
        SongRun run = new SongRun(score(), START);
        long now = (long) tickOf(8) + 3;
        assertTrue(run.claim(SongAction.CROUCH, 8, now).onBeat());
        assertEquals(SongRun.Kind.NOT_BELIEVED, run.claim(SongAction.CROUCH, 40, now).kind(), "a step far in the future");
        assertEquals(SongRun.Kind.NOT_BELIEVED, run.claim(SongAction.CROUCH, 9, now).kind(), "a step with no note");
        assertEquals(1, run.streak(), "a claim that is not believed does not break the streak");
    }

    @Test
    void potencyDoublesAtAFullStreak() {
        assertEquals(1.0F, SongEffect.potency(0));
        assertEquals(2.0F, SongEffect.potency(SongRun.MAX_STREAK));
        assertEquals(0, SongEffect.amplifier(SongEffect.STRONG_STREAK - 1));
        assertEquals(1, SongEffect.amplifier(SongEffect.STRONG_STREAK));
        for (Track track : Track.values()) {
            for (SongAction action : SongAction.values()) {
                assertEquals(action == SongAction.CROUCH, SongEffect.of(track, action).onWielder());
            }
        }
        assertFalse(SongEffect.STAGGER.onWielder());
    }
}
