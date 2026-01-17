package net.dehydrated_pain.turnbasedcombatmod.utils.combat;

public enum DodgeTypes implements DefensiveReactionTypes {
    DODGE("Q");

    private final String actionName;

    DodgeTypes(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public String getActionName() {
        return actionName;
    }
}
