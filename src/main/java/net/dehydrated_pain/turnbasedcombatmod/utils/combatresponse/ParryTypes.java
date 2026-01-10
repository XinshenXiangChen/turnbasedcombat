package net.dehydrated_pain.turnbasedcombatmod.utils.combatresponse;

public enum ParryTypes implements DefensiveReactionTypes {
    PARRY("E"),
    CROUCH("SHIFT");

    private final String actionName;

    ParryTypes(String actionName) {
        this.actionName = actionName;
    }

    @Override
    public String getActionName() {
        return actionName;
    }
}
