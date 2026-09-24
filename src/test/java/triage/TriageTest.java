package triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import typed.decision.model.Answer;
import typed.decision.model.Probability;

class TriageTest {
    private static final Probability CLEAR_NO = new Probability(0.34);
    private static final Probability UNSURE = new Probability(0.5);

    /** Urgency puts all its mass on CRITICAL to show it is display-only. */
    private static Triage triage(double departmentConfidence, Probability... signals) {
        return new Triage(Department.BILLING, Map.of(Department.BILLING, new Probability(departmentConfidence)),
            new Probability(departmentConfidence), Map.of(Urgency.CRITICAL, new Probability(1)),
            signals[0], signals[1], signals[2], signals[3]);
    }

    @ParameterizedTest
    @CsvSource({"0.34, NO", "0.35, UNSURE", "0.65, UNSURE", "0.66, YES"})
    void noulBand(double p, Verdict expected) {
        assertEquals(expected, Verdict.of(new Probability(p)));
    }

    @Test
    void clearCaseRoutesAutomatically() {
        assertEquals(new Route.Automatic(Department.BILLING), triage(0.85, CLEAR_NO, CLEAR_NO, CLEAR_NO, CLEAR_NO).route());
    }

    @Test
    void eachRiskEscalatesToHuman() {
        var risky = List.of(
            triage(0.84, CLEAR_NO, CLEAR_NO, CLEAR_NO, CLEAR_NO),
            triage(0.85, UNSURE, CLEAR_NO, CLEAR_NO, CLEAR_NO),
            triage(0.85, CLEAR_NO, UNSURE, CLEAR_NO, CLEAR_NO),
            triage(0.85, CLEAR_NO, CLEAR_NO, UNSURE, CLEAR_NO),
            triage(0.85, CLEAR_NO, CLEAR_NO, CLEAR_NO, UNSURE));

        risky.forEach(t -> assertEquals(1, ((Route.HumanReview) t.route()).reasons().size(), t::toString));
    }

    @Test
    void readsAnswersBackIntoTriageTerms() {
        var triage = Triage.of(Map.of(
            "department", new Answer.Choice("technical",
                Map.of("technical", new Probability(0.9), "billing", new Probability(0.1)), new Probability(0.9)),
            // expected value 1.5 would round to HIGH; the most probable level is LOW
            "urgency", new Answer.Score(1.5, Map.of(), Map.of(
                "0", new Probability(0.5), "1", new Probability(0), "2", new Probability(0), "3", new Probability(0.5 - 1e-9)),
                new Probability(0.1)),
            "critical", new Answer.Noul(UNSURE),
            "asks_refund", new Answer.Noul(UNSURE),
            "personal_data", new Answer.Noul(UNSURE),
            "unresolved", new Answer.Noul(UNSURE)));

        assertEquals(Department.TECHNICAL, triage.department());
        assertEquals(List.of(Department.BILLING, Department.TECHNICAL), List.copyOf(triage.departments().keySet()));
        assertEquals(Urgency.LOW, triage.likelyUrgency());
    }

    @Test
    void wrongAnswerTypeIsAContractBreach() {
        assertThrows(IllegalStateException.class, () -> Triage.of(Map.of(
            "department", new Answer.Noul(new Probability(0.5)))));
    }
}
