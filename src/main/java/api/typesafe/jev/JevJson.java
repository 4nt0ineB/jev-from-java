package api.typesafe.jev;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import typed.decision.model.Answer;
import typed.decision.model.Probability;
import typed.decision.model.Question;

/** Wire format of the Jev API, attached through mix-ins so the model packages stay free of Jackson. */
public final class JevJson {
    private JevJson() {}

    public static ObjectMapper mapper() {
        return JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .addMixIn(Question.class, QuestionMixin.class)
            .addMixIn(Answer.class, AnswerMixin.class)
            .addMixIn(Probability.class, ProbabilityMixin.class)
            .addMixIn(ModelId.class, ModelIdMixin.class)
            .build();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Question.Noul.class, name = "noul"),
        @JsonSubTypes.Type(value = Question.Choice.class, name = "choice"),
        @JsonSubTypes.Type(value = Question.Score.class, name = "score"),
    })
    private interface QuestionMixin {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Answer.Noul.class, name = "noul"),
        @JsonSubTypes.Type(value = Answer.Choice.class, name = "choice"),
        @JsonSubTypes.Type(value = Answer.Score.class, name = "score"),
    })
    private interface AnswerMixin {}

    private abstract static class ProbabilityMixin {
        @JsonCreator
        ProbabilityMixin(double value) {}

        @JsonValue
        abstract double value();
    }

    private abstract static class ModelIdMixin {
        @JsonCreator
        ModelIdMixin(String value) {}

        @JsonValue
        abstract String value();
    }
}
