package com.earthdawn.model.enums;

public enum AttributeType {
    DEXTERITY("Geschicklichkeit"),
    STRENGTH("Stärke"),
    TOUGHNESS("Zähigkeit"),
    PERCEPTION("Wahrnehmung"),
    WILLPOWER("Willenskraft"),
    CHARISMA("Charisma");

    private final String displayName;

    AttributeType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
