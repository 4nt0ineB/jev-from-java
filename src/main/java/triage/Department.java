package triage;

public enum Department {
    BILLING("Payments, invoices, charges, refunds"),
    TECHNICAL("Bugs, outages, errors, integrations, how to use the product"),
    SALES("Pricing, plans, upgrades, new accounts, quotes"),
    OTHER("Anything that fits none of the other teams");

    final String description;

    Department(String description) {
        this.description = description;
    }

    public String label() {
        return name().toLowerCase();
    }

    static Department fromLabel(String label) {
        return valueOf(label.toUpperCase());
    }
}
