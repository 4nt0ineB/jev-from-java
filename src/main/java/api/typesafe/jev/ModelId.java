package api.typesafe.jev;

public value record ModelId(String value) {
    public ModelId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Model id is blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
