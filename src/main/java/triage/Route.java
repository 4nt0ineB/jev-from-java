package triage;

import java.util.List;

public sealed interface Route {
    record Automatic(Department department) implements Route {}
    record HumanReview(List<String> reasons) implements Route {}
}
