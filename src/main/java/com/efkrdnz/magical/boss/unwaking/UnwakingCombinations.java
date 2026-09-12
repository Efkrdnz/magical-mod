package com.efkrdnz.magical.boss.unwaking;

import java.util.List;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind.*;

/** A score of separately announced strikes. Offsets never change after a combination starts. */
public final class UnwakingCombinations {
    public record Cue(int at, UnwakingHazard.Kind kind, int variant, boolean finisher) {}
    public record Score(String id, int end, List<Cue> cues) {
        public Score { cues = List.copyOf(cues); }
    }
    private static Cue cue(int at, UnwakingHazard.Kind kind, int variant, boolean finish) { return new Cue(at,kind,variant,finish); }
    public static Score sleeping(int index) {
        return switch (Math.floorMod(index,3)) {
            case 0 -> new Score("absent_procession",160,List.of(cue(0,PROCESSION_CUT,0,false),cue(88,PROCESSION_CUT,1,true)));
            case 1 -> new Score("between_heartbeats",180,List.of(cue(0,ARRIVAL,0,false),cue(16,PALM,0,false),cue(52,ECHO,0,false),cue(92,ARRIVAL,1,false),cue(112,THRUST,0,true)));
            default -> new Score("earth_remembers",180,List.of(cue(0,EARTH_FRONT,0,false),cue(106,ARRIVAL,2,false),cue(126,PROCESSION_CUT,1,true)));
        };
    }
    public static Score awake(int index) {
        return switch (Math.floorMod(index,4)) {
            case 0 -> new Score("heaven_divided",240,List.of(cue(0,WORLD_CUT,index/4%2,false),cue(90,WORLD_CUT,1+index/4%2,false),cue(176,ARRIVAL,0,false),cue(194,THRUST,0,true)));
            case 1 -> new Score("horizon_kneels",220,List.of(cue(0,HORIZON_HAND,index/4%2,false),cue(116,ARRIVAL,1,false),cue(148,PROCESSION_CUT,1,true)));
            case 2 -> new Score("white_becomes_law",240,List.of(cue(0,LAW_FRONT,0,false),cue(88,LAW_FRONT,1,false),cue(176,DECREE,0,true)));
            default -> new Score("star_dismissed",240,List.of(cue(0,FALLEN_STAR,0,false),cue(76,STAR_FRONT,0,false),cue(154,ARRIVAL,0,false),cue(178,THRUST,0,true)));
        };
    }
    public static Score closing(int index) {
        return switch (Math.floorMod(index,3)) {
            case 0 -> new Score("last_heaven",220,List.of(cue(0,WORLD_CUT,0,false),cue(92,ARRIVAL,0,false),cue(116,PALM,0,false),cue(156,ECHO,0,true)));
            case 1 -> new Score("last_horizon",220,List.of(cue(0,HORIZON_HAND,1,false),cue(126,PROCESSION_CUT,1,false),cue(176,THRUST,1,true)));
            default -> new Score("last_law",232,List.of(cue(0,LAW_FRONT,0,false),cue(88,PROCESSION_CUT,0,false),cue(132,PROCESSION_CUT,1,false),cue(176,DECREE,0,true)));
        };
    }
    public static Score trial(UnwakingPhase phase) {
        return switch (phase) {
            case TRIAL_SKY -> new Score("moving_horizon",500,List.of(cue(20,WORLD_CUT,0,false),cue(170,WORLD_CUT,1,false),cue(320,WORLD_CUT,2,false)));
            case TRIAL_BREATH -> new Score("inverted_world",500,List.of(cue(20,LAW_FRONT,0,false),cue(110,LAW_FRONT,1,false),cue(260,LAW_FRONT,0,false),cue(350,LAW_FRONT,1,false)));
            case TRIAL_CHIME -> new Score("his_attention",500,List.of(cue(20,DECREE,0,false),cue(68,DECREE,1,true),cue(112,WORLD_CUT,0,false),cue(180,DECREE,2,false),cue(228,DECREE,3,true),cue(272,WORLD_CUT,1,false),cue(320,DECREE,4,false),cue(368,DECREE,5,true),cue(412,WORLD_CUT,2,false)));
            default -> throw new IllegalArgumentException("Not a domain passage: " + phase);
        };
    }
    private UnwakingCombinations() {}
}
