package fr.elias.oreoEssentials.modules.shop.dialog;

public enum SortField {
    NAME("Name"),
    BUY_PRICE("Buy Price"),
    SELL_PRICE("Sell Price");

    private final String label;

    SortField(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public SortField next() {
        SortField[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
