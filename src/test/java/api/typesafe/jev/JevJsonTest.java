package api.typesafe.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import typed.decision.model.Answer;
import typed.decision.model.Probability;
import typed.decision.model.Question;

class JevJsonTest {
    private final tools.jackson.databind.ObjectMapper mapper = JevJson.mapper();

    @Test
    void readsNoulAnswer() {
        var response = mapper.readValue("""
            {"model":"jev-1.13.0","answers":{"is_urgent":{"type":"noul","noul":0.95}},
             "usage":{"input_tokens":307,"output_tokens":20}}""", JevResponse.class);

        assertEquals(new Answer.Noul(new Probability(0.95)), response.answers().get("is_urgent"));
        assertEquals(new Usage(307, 20), response.usage());
    }

    @Test
    void readsChoiceAnswer() {
        var response = mapper.readValue("""
            {"model":"jev-1.13.0","answers":{"department":{"type":"choice","choice":"billing",
             "probabilities":{"billing":0.88,"technical":0.12,"sales":0.0},"confidence":0.81}},
             "usage":{"input_tokens":318,"output_tokens":34}}""", JevResponse.class);

        var choice = (Answer.Choice) response.answers().get("department");
        assertEquals("billing", choice.choice());
        assertEquals(new Probability(0.88), choice.probabilities().get("billing"));
        assertEquals(new Probability(0.81), choice.confidence());
    }

    @Test
    void readsScoreAnswer() {
        var response = mapper.readValue("""
            {"model":"jev-1.13.0","answers":{"frustration":{"type":"score","score":1.05,
             "legend":{"0":"Calm","1":"Frustrated","2":"Very angry"},
             "probabilities":{"0":0.0,"1":0.95,"2":0.05},"confidence":0.92}},
             "usage":{"input_tokens":304,"output_tokens":18}}""", JevResponse.class);

        var score = (Answer.Score) response.answers().get("frustration");
        assertEquals(1.05, score.score());
        assertEquals("Frustrated", score.legend().get("1"));
        assertEquals(new Probability(0.92), score.confidence());
    }

    @Test
    void rejectsProbabilityOutOfRange() {
        assertThrows(JacksonException.class, () -> mapper.readValue("""
            {"model":"m","answers":{"x":{"type":"noul","noul":1.5}},"usage":{"input_tokens":1,"output_tokens":1}}""",
            JevResponse.class));
    }

    @Test
    void rejectsUnknownAnswerType() {
        assertThrows(JacksonException.class, () -> mapper.readValue("""
            {"model":"m","answers":{"x":{"type":"rank"}},"usage":{"input_tokens":1,"output_tokens":1}}""",
            JevResponse.class));
    }

    @Test
    void writesRequestInWireShape() {
        var json = mapper.writeValueAsString(new JevRequest("msg", new ModelId("jev-1.13.0"), Map.of(
            "urgency", new Question.Score("How urgent?", List.of("low", "high")))));

        assertEquals(mapper.readTree("""
            {"state":"msg","model":"jev-1.13.0","questions":{"urgency":
             {"type":"score","instructions":"How urgent?","criteria":["low","high"]}}}"""), mapper.readTree(json));
    }
}
