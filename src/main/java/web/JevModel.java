package web;

import api.typesafe.jev.JevClient;
import api.typesafe.jev.Price;
import java.util.Map;
import typed.decision.model.DecisionModel;
import typed.decision.model.Question;

/** Any server speaking Jev's wire format: TypeSafe's API, or the Laya sidecar at a zero price. */
public record JevModel(JevClient client, Price price) implements DecisionModel {
    @Override
    public Decision decide(String state, Map<String, Question> questions) throws InterruptedException {
        var result = client.ask(state, questions);
        if (result instanceof JevClient.Result.Success(var response)) {
            return new Decision.Answered(response.answers(), price.of(response.usage()));
        }
        return new Decision.Failed(Views.error(result));
    }
}
