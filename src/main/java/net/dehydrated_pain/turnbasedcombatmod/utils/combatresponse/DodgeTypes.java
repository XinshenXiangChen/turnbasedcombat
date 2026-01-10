package net.dehydrated_pain.turnbasedcombatmod.utils.combatresponse;

public enum DodgeTypes implements DefensiveReactionTypes {
    JUMP("jump"),
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
