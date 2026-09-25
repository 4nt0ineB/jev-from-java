package api.typesafe.jev;

public record ApiKey(String value) {
    public ApiKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("API key is blank");
        }
    }

    @Override
    public String toString() {
        return "ApiKey[***]";
    }
}
