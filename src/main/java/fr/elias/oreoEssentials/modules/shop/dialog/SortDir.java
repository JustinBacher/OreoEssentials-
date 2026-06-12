package fr.elias.oreoEssentials.modules.shop.dialog;

public enum SortDir {
    ASC("↑ Asc"), DESC("↓ Desc");

    private final String label;

    SortDir(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public SortDir toggle() {
        return this == ASC ? DESC : ASC;
    }
}
