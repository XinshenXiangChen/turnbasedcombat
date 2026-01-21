package net.dehydrated_pain.turnbasedcombatmod.combat;

import net.dehydrated_pain.turnbasedcombatmod.network.EndCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.EndPlayerTurnPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.PlayerTurnPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTERequestPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.QTEResponsePacket;
import net.dehydrated_pain.turnbasedcombatmod.network.StartCombatPacket;
import net.dehydrated_pain.turnbasedcombatmod.network.TriggerEpicFightAttackPacket;
import net.dehydrated_pain.turnbasedcombatmod.turnbasedcombatanimations.AnimationMappings;
import net.dehydrated_pain.turnbasedcombatmod.utils.playerturn.EnemyInfo;
import net.dehydrated_pain.turnbasedcombatmod.ui.CombatUIConfig;
import net.dehydrated_pain.turnbasedcombatmod.utils.combat.ParryTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lwjgl.glfw.GLFW;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.LOGGER;
import static net.dehydrated_pain.turnbasedcombatmod.TurnBasedCombatMod.MODID;


@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class CombatInstanceClient {
    public static boolean inCombat = false;
    public static boolean isPlayerTurn = false;
    
    // Selection states - two step process: ability first, then enemy
    public static boolean isSelectingAbility = false;
    public static boolean isSelectingEnemy = false;
    
    // Enemy selection state
    public static List<EnemyInfo> enemyInfoList = new ArrayList<>();
    public static int selectedEnemyIndex = 0;
    
    // Ability selection state
    private static int selectedAbilityIndex = 0;
    private static final String[] ABILITIES = {"Attack", "Skill", "Item"};  // U, I, O
    
    // Camera settings for ABILITY selection (behind player, offset right)
    private static final double ABILITY_CAMERA_DISTANCE = -2.7;  // Distance behind player
    private static final double ABILITY_CAMERA_HEIGHT = 1.4;     // slightly below eye level
    private static final double ABILITY_CAMERA_RIGHT_OFFSET = 1.5;  // Player appears on left of screen
    
    // Camera settings for ENEMY selection (looking at enemy)
    private static final double ENEMY_CAMERA_OFFSET_X = 0.0;   // Same X as enemy (centered)
    private static final double ENEMY_CAMERA_OFFSET_Y = 1.5;   // Eye level
    private static final double ENEMY_CAMERA_OFFSET_Z = -2.0;  // Distance in front of enemy
    
    // Ability selection UI layout settings
    private static final int ABILITY_BAR_WIDTH = 90;
    private static final int ABILITY_BAR_HEIGHT = 15;
    private static final int ABILITY_BAR_X_OFFSET = -10;  // Offset from center of screen
    private static final int ABILITY_BUTTON_SPACING = 25; // barHeight + 10
    private static final String[] KEY_LABELS = {"[U]", "[I]", "[O]"};


    // Each client only handles their own player, so static is safe here
    private static final double PARRY_TIMEOUT_TIME = 0.15;
    private static final double DODGE_TIMEOUT_TIME = 0.2;
    private static double reactionTimer = 0.0;
    private static boolean parried = false;
    private static boolean qteActive = false;
    private static ParryTypes activeParryType = null;
    private static long qteStartTime = 0;


    // Shared cooldown for both parry and dodge
    private static double DEFENSE_COOLDOWN_TIME = 1;
    private static double defenseCooldownTime = 0;
    private static boolean defenseOnCooldown = false;



    private static final ResourceKey<Level> COMBAT_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MODID, "combatdim"));



    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (inCombat) {
            ResourceKey<Level> currentDim = mc.player.level().dimension();
            if (!currentDim.equals(COMBAT_DIMENSION)) {
                endCombat();
            }
        }

        if (qteActive && !parried) {
            double elapsedSeconds = (System.currentTimeMillis() - qteStartTime) / 1000.0;
            reactionTimer = elapsedSeconds;
            
            if (reactionTimer >= DODGE_TIMEOUT_TIME) {
                handleQTETimeout();
            }
        }

        // Update defense cooldown (shared for parry and dodge)
        if (defenseOnCooldown && inCombat) {
            defenseCooldownTime -= 1.0 / 20.0; // Decrement by 1 tick (assuming 20 TPS)
            if (defenseCooldownTime <= 0) {
                defenseOnCooldown = false;
                defenseCooldownTime = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!inCombat) return;
        if (!isSelectingAbility && !isSelectingEnemy) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        if (isSelectingAbility) {
            // Ability selection: camera behind player
            float playerYaw = mc.player.getYRot();
            float pitch = 5.0f;
            event.setYaw(playerYaw);
            event.setPitch(pitch);
        } else if (isSelectingEnemy && !enemyInfoList.isEmpty()) {
            // Enemy selection: camera looking at enemy
            EnemyInfo selectedEnemy = enemyInfoList.get(selectedEnemyIndex);
            BlockPos enemyPos = selectedEnemy.enemyPosition();
            
            Vec3 cameraPos = new Vec3(
                enemyPos.getX() + 0.5 + ENEMY_CAMERA_OFFSET_X,
                enemyPos.getY() + ENEMY_CAMERA_OFFSET_Y,
                enemyPos.getZ() + 0.5 + ENEMY_CAMERA_OFFSET_Z
            );
            
            Vec3 targetPos = new Vec3(
                enemyPos.getX() + 0.5,
                enemyPos.getY() + 1.0,
                enemyPos.getZ() + 0.5
            );
            
            Vec3 direction = targetPos.subtract(cameraPos).normalize();
            double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            float yRot = (float) (Math.atan2(-direction.x, direction.z) * (180.0 / Math.PI));
            float xRot = (float) (Math.atan2(-direction.y, horizontalDistance) * (180.0 / Math.PI));
            
            event.setYaw(yRot);
            event.setPitch(xRot);
        }
    }

    @SubscribeEvent
    public static void onCalculateCameraDistanceSelection(CalculateDetachedCameraDistanceEvent event) {
        if (!inCombat) return;
        if (!isSelectingAbility && !isSelectingEnemy) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        Camera camera = event.getCamera();
        
        if (isSelectingAbility) {
            // Ability selection: camera behind player, offset to right
            Vec3 playerPos = mc.player.position();
            float yawRad = (float) Math.toRadians(mc.player.getYRot());
            
            double behindX = Math.sin(yawRad) * ABILITY_CAMERA_DISTANCE;
            double behindZ = -Math.cos(yawRad) * ABILITY_CAMERA_DISTANCE;
            
            double rightX = -Math.cos(yawRad) * ABILITY_CAMERA_RIGHT_OFFSET;
            double rightZ = -Math.sin(yawRad) * ABILITY_CAMERA_RIGHT_OFFSET;
            
            Vec3 cameraPos = new Vec3(
                playerPos.x + behindX + rightX,
                playerPos.y + ABILITY_CAMERA_HEIGHT,
                playerPos.z + behindZ + rightZ
            );
            camera.setPosition(cameraPos);
        } else if (isSelectingEnemy && !enemyInfoList.isEmpty()) {
            // Enemy selection: camera in front of enemy
            EnemyInfo selectedEnemy = enemyInfoList.get(selectedEnemyIndex);
            BlockPos enemyPos = selectedEnemy.enemyPosition();
            
            Vec3 cameraPos = new Vec3(
                enemyPos.getX() + 0.5 + ENEMY_CAMERA_OFFSET_X,
                enemyPos.getY() + ENEMY_CAMERA_OFFSET_Y,
                enemyPos.getZ() + 0.5 + ENEMY_CAMERA_OFFSET_Z
            );
            camera.setPosition(cameraPos);
        }
    }

    public static void selectEnemy(int index) {
        if (enemyInfoList.isEmpty()) return;

        selectedEnemyIndex = Math.max(0, Math.min(index, enemyInfoList.size() - 1));

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("Selected enemy " + (selectedEnemyIndex + 1) + "/" + enemyInfoList.size()));
        }
    }

    public static void selectPreviousEnemy() {
        if (enemyInfoList.isEmpty() || selectedEnemyIndex <= 0) return;
        selectEnemy(selectedEnemyIndex - 1);
    }

    public static void selectNextEnemy() {
        if (enemyInfoList.isEmpty() || selectedEnemyIndex >= enemyInfoList.size() - 1) return;
        selectEnemy(selectedEnemyIndex + 1);
    }

    public static EnemyInfo getSelectedEnemy() {
        if (enemyInfoList.isEmpty() || selectedEnemyIndex < 0 || selectedEnemyIndex >= enemyInfoList.size()) {
            return null;
        }
        return enemyInfoList.get(selectedEnemyIndex);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!inCombat) return;

        // Completely zero out all movement inputs
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().leftImpulse = 0.0F;
        event.getInput().up = false;
        event.getInput().down = false;
        event.getInput().left = false;
        event.getInput().right = false;

        // this is only for the animation, the damage handling is in keypressed event

        if (defenseOnCooldown) {
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }




    public static void startCombatNetworkHandler(final StartCombatPacket pkt, final IPayloadContext context) {
        // Network thread: we only set flags or read packet data if needed

        // Main thread work
        context.enqueueWork(() -> {
                    CombatInstanceClient.inCombat = true;
                    Minecraft mc = Minecraft.getInstance();
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                })
                .exceptionally(e -> {
                    // Handle exception, optional
                    context.disconnect(net.minecraft.network.chat.Component.literal("Failed to start combat: " + e.getMessage()));
                    return null;
                });
    }
    
    public static void endCombatNetworkHandler(final EndCombatPacket pkt, final IPayloadContext context) {
        // Main thread work
        context.enqueueWork(() -> {
                    endCombat();
                })
                .exceptionally(e -> {
                    // Handle exception, optional
                    context.disconnect(net.minecraft.network.chat.Component.literal("Failed to end combat: " + e.getMessage()));
                    return null;
                });
    }

    public static void playerTurnNetworkHandler(final PlayerTurnPacket pkt, final IPayloadContext context) {
        // Main thread work
        context.enqueueWork(() -> {
                    isPlayerTurn = true;
                    isSelectingEnemy = false; // Reset selection mode when turn starts
                    enemyInfoList = new ArrayList<>(pkt.enemyInfoList());
                    selectedEnemyIndex = 0; // Start with first enemy (leftmost)
                })
                .exceptionally(e -> {
                    context.disconnect(Component.literal("Failed to handle player turn: " + e.getMessage()));
                    return null;
                });
    }
    // TODO: create a handler for abilities, where the network handler transfeers also the skill number and if it is a skill
    public static void triggerEpicFightAttack(Player player) {
        PlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        Item heldItem = player.getMainHandItem().getItem();
                                                                                                                                                                                                                                
        if (patch != null) {
            patch.playAnimationInstantly(AnimationMappings.animationMappings.get(heldItem).animation());
        }
    }

    public static void triggerEpicFightAttackNetworkHandler(final TriggerEpicFightAttackPacket pkt, final IPayloadContext context) {
        // Main thread work
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                triggerEpicFightAttack(mc.player);
            }
        });
    }

    public static void qteRequesteNetworkHandler(final QTERequestPacket pkt, final IPayloadContext context) {
        // Main thread work
        context.enqueueWork(() -> {
            ParryTypes requiredParryType = pkt.parryType();
            String actionName = requiredParryType.getActionName();
            
            // Start the QTE timer
            activeParryType = requiredParryType;
            qteActive = true;
            parried = false;
            reactionTimer = 0.0;
            qteStartTime = System.currentTimeMillis();

        }).exceptionally(e -> {
 
            context.disconnect(Component.literal("Failed to handle QTE request: " + e.getMessage()));
            return null;
        });
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        if (!inCombat) return;
        
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        
        int key = event.getKey();
        
        // Step 1: Ability selection with U, I, O keys
        if (isSelectingAbility) {
            if (key == GLFW.GLFW_KEY_U) {
                selectedAbilityIndex = 0;  // Top - Attack
                confirmAbilitySelection();
                return;
            } else if (key == GLFW.GLFW_KEY_I) {
                selectedAbilityIndex = 1;  // Middle - Skill
                confirmAbilitySelection();
                return;
            } else if (key == GLFW.GLFW_KEY_O) {
                selectedAbilityIndex = 2;  // Bottom - Item
                confirmAbilitySelection();
                return;
            }
        }
        
        // Step 2: Enemy selection with M, N keys
        if (isSelectingEnemy) {
            if (key == GLFW.GLFW_KEY_M) {
                selectPreviousEnemy();
                return;
            } else if (key == GLFW.GLFW_KEY_N) {
                selectNextEnemy();
                return;
            }
        }
        
        Boolean isParry = getReactionTypeForKey(key);
        
        // If it's a parry/dodge key, check if we should trigger cooldown
        if (isParry != null) {
            if (defenseOnCooldown) {
                return;
            }

            if (!qteActive) {
                defenseOnCooldown = true;
                defenseCooldownTime = DEFENSE_COOLDOWN_TIME;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String actionType = isParry ? "Parry" : "Dodge";
                    mc.player.sendSystemMessage(Component.literal(actionType + " used! Cooldown: " + DEFENSE_COOLDOWN_TIME + "s"));
                }
                return;
            }
            
            // If in QTE, handle the QTE response
            if (qteActive && !parried) {
                ParryTypes requiredType = activeParryType;
                if (requiredType == null) return;
                
                String requiredAction = requiredType.getActionName();
                
                boolean isValidKey = false;
                
                if (isParry == null) {
                    return;
                } else if (!isParry) {
                    isValidKey = true;
                } else {
                    isValidKey = isKeyForAction(key, requiredAction);
                }
                
                if (isValidKey) {
                    boolean isDodgeAction = !isParry; // If isParry is false, it's a dodge
                    double timeoutWindow = isDodgeAction ? DODGE_TIMEOUT_TIME : PARRY_TIMEOUT_TIME;
                    
                    // Check if within the timeout window
                    if (reactionTimer < timeoutWindow) {
                        handleDefenseSuccess(isParry);
                    }
                }
            }
        }
    }

    private static Boolean getReactionTypeForKey(int key) {

        if (key == GLFW.GLFW_KEY_E || 
            key == GLFW.GLFW_KEY_LEFT_SHIFT || 
            key == GLFW.GLFW_KEY_RIGHT_SHIFT || 
            key == GLFW.GLFW_KEY_SPACE) {
            return true;
        }
        
        // Dodge keys
        if (key == GLFW.GLFW_KEY_Q) {
            return false;
        }
        
        return null; // Not a defensive reaction key
    }
    
    /**
     * Checks if the pressed key matches the required action name
     */
    private static boolean isKeyForAction(int key, String actionName) {
        if (actionName.equalsIgnoreCase("E") && key == GLFW.GLFW_KEY_E) return true;
        if (actionName.equalsIgnoreCase("SHIFT") && (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)) return true;
        if (actionName.equalsIgnoreCase("jump") && key == GLFW.GLFW_KEY_SPACE) return true;
        if (actionName.equalsIgnoreCase("Q") && key == GLFW.GLFW_KEY_Q) return true;
        
        return false;
    }
    

    private static void handleDefenseSuccess(boolean isParry) {
        parried = true;
        qteActive = false;
        
        // Set cooldown after successful defense
        defenseOnCooldown = false;
        defenseCooldownTime = 0;

        
        // Send response packet to server (success = true, isParry indicates the type)
        sendParrySuccess(parried, isParry);
        

        clearQTE();
    }

    

    private static void handleQTETimeout() {
        parried = false;
        qteActive = false;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("Parry Failed! You didn't respond in time."));
        }
        
        boolean isParry = true; 
        clearQTE();
        
        sendParrySuccess(parried, isParry);
    }
    

    private static void clearQTE() {
        activeParryType = null;
        reactionTimer = 0.0;
        qteStartTime = 0;
    }

    private static void confirmAbilitySelection() {
        // Move from ability selection to enemy selection
        isSelectingAbility = false;
        isSelectingEnemy = true;
        selectedEnemyIndex = 0;
    }
    
    public static void endCombat() {
        Minecraft mc = Minecraft.getInstance();
        inCombat = false;
        isPlayerTurn = false;
        isSelectingAbility = false;
        isSelectingEnemy = false;
        enemyInfoList.clear();
        selectedEnemyIndex = 0;
        selectedAbilityIndex = 0;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        
        // Clear QTE state when combat ends
        clearQTE();
        parried = false;
        qteActive = false;
        
        defenseOnCooldown = false;
        defenseCooldownTime = 0;
    }
    
    /**
     * Gets whether the player successfully parried
     */
    public static boolean hasParried() {
        return parried;
    }
    
    /**
     * Gets whether a QTE is currently active
     */
    public static boolean isQTEActive() {
        return qteActive;
    }

    private static void sendParrySuccess(Boolean success, boolean isParry) {
        PacketDistributor.sendToServer(new QTEResponsePacket(success, isParry));
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!inCombat || !isPlayerTurn) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        GuiGraphics guiGraphics = event.getGuiGraphics();
        int screenWidth = event.getGuiGraphics().guiWidth();
        int screenHeight = event.getGuiGraphics().guiHeight();
        
        // Show turn indicator button when no selection mode is active
        if (!isSelectingAbility && !isSelectingEnemy) {
            CombatUIConfig.TurnIndicatorConfig config = CombatUIConfig.getTurnIndicator();
            
            int x = (screenWidth - config.width) / 2;
            int y = (screenHeight - config.height) / 2;

            guiGraphics.blit(config.image, x, y, 0, 0, config.width, config.height, config.width, config.height);
        }
        
        // Step 1: Ability selection UI
        if (isSelectingAbility) {
            renderAbilitySelectionUI(guiGraphics, mc, screenWidth, screenHeight);
        }
        
        // Step 2: Enemy selection UI
        if (isSelectingEnemy && !enemyInfoList.isEmpty()) {
            String selectionText = "Select Enemy: " + (selectedEnemyIndex + 1) + "/" + enemyInfoList.size() + " [M] <- -> [N] | Right-click to confirm";
            int textWidth = mc.font.width(selectionText);
            guiGraphics.drawString(mc.font, selectionText, (screenWidth - textWidth) / 2, screenHeight - 40, 0xFFFFFFFF);
        }
    }
    
    private static void renderAbilitySelectionUI(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, int screenHeight) {
        CombatUIConfig.TurnIndicatorConfig config = CombatUIConfig.getTurnIndicator();
        
        // Calculate positions using class attributes
        int barX = screenWidth / 2 + ABILITY_BAR_X_OFFSET;
        int startY = (screenHeight - (ABILITY_BAR_HEIGHT * 3 + 20)) / 2;
        
        for (int i = 0; i < ABILITIES.length; i++) {
            int barY = startY + i * ABILITY_BUTTON_SPACING;
            
            guiGraphics.blit(config.image, barX, barY, ABILITY_BAR_WIDTH, ABILITY_BAR_HEIGHT, 0, 0, config.width, config.height, config.width, config.height);
            
            String label = KEY_LABELS[i] + " " + ABILITIES[i];
            int labelX = barX + 8;
            int labelY = barY + (ABILITY_BAR_HEIGHT - 8) / 2;
            guiGraphics.drawString(mc.font, label, labelX, labelY, 0xFFFFFFFF);
        }
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!inCombat || !isPlayerTurn) return;
        
        if (event.isAttack()) {
            
            // Step 0: Start ability selection
            if (!isSelectingAbility && !isSelectingEnemy) {
                isSelectingAbility = true;
                selectedAbilityIndex = 0;
                event.setCanceled(true);
                return;
            }
            
            // Step 2: Confirm enemy selection and end turn
            if (isSelectingEnemy) {
                isPlayerTurn = false;
                isSelectingAbility = false;
                isSelectingEnemy = false;
                // Send the selected ability and enemy to the server
                PacketDistributor.sendToServer(new EndPlayerTurnPacket(selectedAbilityIndex, selectedEnemyIndex));
            }
            event.setCanceled(true);
        }
    }
}
