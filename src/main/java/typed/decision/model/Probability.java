package typed.decision.model;

public value record Probability(double value) {
    public Probability {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException("Probability out of [0, 1]: " + value);
        }
    }
}
