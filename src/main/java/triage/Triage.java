package triage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.stream.Collectors;
import typed.decision.model.Answer;
import typed.decision.model.Probability;
import typed.decision.model.Question;

/**
 * The answers to {@link #QUESTIONS}, read back into triage terms. {@code urgency} ranks the queue and is only
 * displayed; the routing decision uses the {@code critical} noul.
 */
public record Triage(
    Department department,
    Map<Department, Probability> departments,
    Probability departmentConfidence,
    Map<Urgency, Probability> urgency,
    Probability critical,
    Probability asksRefund,
    Probability personalData,
    Probability unresolved) {

    /** Threshold from TypeSafe's docs: 0.85 to act alone on a high-stakes decision. */
    static final double AUTO_ROUTE_CONFIDENCE = 0.85;

    public static final Map<String, Question> QUESTIONS = Map.of(
        "department", new Question.Choice(
            "Which team should handle this customer message?",
            Arrays.stream(Department.values()).collect(Collectors.toMap(Department::label, d -> d.description))),
        "urgency", new Question.Score(
            "How urgently does this message need a reply?",
            Arrays.stream(Urgency.values()).map(u -> u.description).toList()),
        "critical", new Question.Noul(
            "Is this critical: an outage, a security issue or a legal threat that needs someone now?"),
        "asks_refund", new Question.Noul("Does the customer ask for a refund or a reimbursement?"),
        "personal_data", new Question.Noul(
            "Does the message contain personal data such as a card number, IBAN, address, phone number or ID number?"),
        "unresolved", new Question.Noul(
            "Has the problem lasted a long time, or did the customer already report it without getting it resolved?"));

    /** Maps keep enum order, which is the display order. */
    public Triage {
        departments = Collections.unmodifiableMap(new EnumMap<>(departments));
        urgency = Collections.unmodifiableMap(new EnumMap<>(urgency));
    }

    /** Throws if an answer is missing or of the wrong type, see {@link Answer#get}. */
    public static Triage of(Map<String, Answer> answers) {
        var department = Answer.get(answers, "department", Answer.Choice.class);
        var departments = new EnumMap<Department, Probability>(Department.class);
        department.probabilities().forEach((label, p) -> departments.put(Department.fromLabel(label), p));
        var urgency = new EnumMap<Urgency, Probability>(Urgency.class);
        Answer.get(answers, "urgency", Answer.Score.class).probabilities()
            .forEach((level, p) -> urgency.put(Urgency.values()[Integer.parseInt(level)], p));
        return new Triage(
            Department.fromLabel(department.choice()),
            departments,
            department.confidence(),
            urgency,
            Answer.get(answers, "critical", Answer.Noul.class).noul(),
            Answer.get(answers, "asks_refund", Answer.Noul.class).noul(),
            Answer.get(answers, "personal_data", Answer.Noul.class).noul(),
            Answer.get(answers, "unresolved", Answer.Noul.class).noul());
    }

    /** Most probable level. Jev's own score is an expected value, which can land on a level it barely considered. */
    public Urgency likelyUrgency() {
        return urgency.entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().value())).orElseThrow().getKey();
    }

    /** The yes/no signals that can send a message to a human, labelled for display. */
    public SequencedMap<String, Probability> signals() {
        var signals = new LinkedHashMap<String, Probability>();
        signals.put("Critical", critical);
        signals.put("Asks for a refund", asksRefund);
        signals.put("Contains personal data", personalData);
        signals.put("Long-standing or unresolved", unresolved);
        return signals;
    }

    /** Anything other than a confident, clear-cut case goes to a human; YES and UNSURE escalate alike. */
    public Route route() {
        var reasons = new ArrayList<String>();
        if (departmentConfidence.value() < AUTO_ROUTE_CONFIDENCE) {
            reasons.add("Department unsure (confidence %.2f)".formatted(departmentConfidence.value()));
        }
        signals().forEach((signal, yes) -> {
            var verdict = Verdict.of(yes);
            if (verdict != Verdict.NO) {
                reasons.add("%s: %s (p=%.2f)".formatted(signal, verdict, yes.value()));
            }
        });
        return reasons.isEmpty() ? new Route.Automatic(department) : new Route.HumanReview(reasons);
    }
}
