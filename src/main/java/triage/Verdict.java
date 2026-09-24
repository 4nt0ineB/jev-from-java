package triage;

import typed.decision.model.Probability;

/** A noul has no confidence, so a band around 0.5 stands in for "Jev is unsure". */
public enum Verdict {
    NO, UNSURE, YES;

    public static final double UNSURE_FROM = 0.35;
    static final double UNSURE_TO = 0.65;

    public static Verdict of(Probability yes) {
        if (yes.value() < UNSURE_FROM) return NO;
        if (yes.value() > UNSURE_TO) return YES;
        return UNSURE;
    }
}
