package api.typesafe.jev;

import java.util.Map;
import typed.decision.model.Question;

public record JevRequest(String state, ModelId model, Map<String, Question> questions) {}
