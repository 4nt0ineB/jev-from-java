package triage;

/** Declaration order is the score scale sent to Jev: index 0 is the least urgent. */
public enum Urgency {
    LOW("Can wait days, no impact on the customer's work"),
    NORMAL("Should be handled within a working day"),
    HIGH("Blocks the customer or costs them money right now"),
    CRITICAL("Outage, security issue or legal threat, needs someone now");

    final String description;

    Urgency(String description) {
        this.description = description;
    }

    public String label() {
        return name().toLowerCase();
    }
}
