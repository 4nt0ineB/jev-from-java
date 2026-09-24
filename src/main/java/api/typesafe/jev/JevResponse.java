package api.typesafe.jev;

import java.util.Map;
import typed.decision.model.Answer;

public record JevResponse(String model, Map<String, Answer> answers, Usage usage) {}
