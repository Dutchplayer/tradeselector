package dutchplayer.tradeselector.automation;


import com.mojang.authlib.minecraft.client.MinecraftClient;
import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.ModState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Text;

/**
 * Core automation state machine for trade rerolling
 */
public class AutomationStateMachine {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private static final int TICKS_PER_ACTION = 20; // 1 second between actions
    private static final int MAX_WAIT_TICKS = 60; // 3 seconds max wait for refresh
    
    private final ModState modState;
    private final VillagerBinder villagerBinder;
    private final TradeScanner tradeScanner;
    private final MinecraftClient client;
    
    private int actionTimer = 0;
    private int waitTimer = 0;
    private boolean hasLecternInInventory = false;
    
    public AutomationStateMachine(ModState modState, VillagerBinder villagerBinder, TradeScanner tradeScanner) {
        this.modState = modState;
        this.villagerBinder = villagerBinder;
        this.tradeScanner = tradeScanner;
        this.client = MinecraftClient.getInstance();
    }
    
    /**
     * Main tick method called from client tick event
     */
    public void tick() {
        if (!modState.isRunning()) {
            return;
        }
        
        actionTimer++;
        
        try {
            switch (modState.getCurrentState()) {
                case BOUND:
                    handleBoundState();
                    break;
                case BREAKING_JOB_BLOCK:
                    handleBreakingJobBlock();
                    break;
                case PLACING_JOB_BLOCK:
                    handlePlacingJobBlock();
                    break;
                case WAITING_FOR_REFRESH:
                    handleWaitingForRefresh();
                    break;
                case SCANNING_TRADES:
                    handleScanningTrades();
                    break;
                case FOUND_MATCH:
                    handleFoundMatch();
                    break;
                case ERROR:
                    handleError();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LOGGER.error("Error in automation tick: " + e.getMessage(), e);
            modState.setCurrentState(ModState.AutomationState.ERROR);
            modState.setErrorMessage(e.getMessage());
        }
    }
    
    /**
     * Start the automation process
     */
    public boolean start() {
        ModConfig config = ConfigManager.getConfig();
        
        // Validate configuration
        if (!config.boundVillager.isBound() || !config.boundJobBlock.isBound()) {
            modState.setErrorMessage("Must bind both villager and job block before starting");
            modState.setCurrentState(ModState.AutomationState.ERROR);
            return false;
        }
        
        // Check for lectern in inventory
        hasLecternInInventory = hasLecternInInventory();
        if (!hasLecternInInventory) {
            modState.setErrorMessage("No lectern in inventory");
            modState.setCurrentState(ModState.AutomationState.ERROR);
            return false;
        }
        
        // Reset state
        modState.reset();
        modState.setCurrentState(ModState.AutomationState.BOUND);
        modState.setStartTime(System.currentTimeMillis());
        
        LOGGER.info("Starting automation");
        return true;
    }
    
    /**
     * Stop the automation process
     */
    public void stop() {
        modState.setCurrentState(ModState.AutomationState.STOPPED);
        LOGGER.info("Automation stopped");
    }
    
    private void handleBoundState() {
        if (actionTimer >= TICKS_PER_ACTION) {
            actionTimer = 0;
            modState.setCurrentState(ModState.AutomationState.BREAKING_JOB_BLOCK);
            LOGGER.info("Starting to break job block");
        }
    }
    
    private void handleBreakingJobBlock() {
        if (actionTimer >= TICKS_PER_ACTION) {
            actionTimer = 0;
            
            ModConfig config = ConfigManager.getConfig();
            BlockPos lecternPos = new BlockPos(
                (int) config.boundJobBlock.position.x,
                (int) config.boundJobBlock.position.y,
                (int) config.boundJobBlock.position.z
            );
            
            // Check if lectern exists and break it
            if (client.world.getBlockState(lecternPos).getBlock() == Blocks.LECTERN) {
                // Look at the lectern
                lookAtBlock(lecternPos);
                
                // Try to break it (simulate player action)
                if (client.player != null) {
                    client.interactionManager.attackBlock(lecternPos, 
                        client.player.getHorizontalFacing().getOpposite());
                }
                
                modState.setCurrentState(ModState.AutomationState.PLACING_JOB_BLOCK);
                LOGGER.info("Broke lectern, now placing");
            } else {
                modState.setErrorMessage("Lectern not found at bound position");
                modState.setCurrentState(ModState.AutomationState.ERROR);
            }
        }
    }
    
    private void handlePlacingJobBlock() {
        if (actionTimer >= TICKS_PER_ACTION) {
            actionTimer = 0;
            
            ModConfig config = ConfigManager.getConfig();
            BlockPos lecternPos = new BlockPos(
                (int) config.boundJobBlock.position.x,
                (int) config.boundJobBlock.position.y,
                (int) config.boundJobBlock.position.z
            );
            
            // Check if lectern is not there and place it
            if (client.world.getBlockState(lecternPos).isAir()) {
                // Look at the lectern position
                lookAtBlock(lecternPos);
                
                // Try to place lectern
                if (client.player != null) {
                    PlayerInventory inventory = client.player.getInventory();
                    int lecternSlot = findLecternSlot(inventory);
                    
                    if (lecternSlot != -1) {
                        // Select lectern
                        inventory.selectedSlot = lecternSlot;
                        
                        // Place block (simulate right-click)
                        client.interactionManager.interactBlock(
                            client.player, 
                            client.player.getActiveHand(),
                            new BlockHitResult(
                                Vec3d.ofCenter(lecternPos),
                                client.player.getHorizontalFacing(),
                                lecternPos,
                                false
                            )
                        );
                        
                        modState.setCurrentState(ModState.AutomationState.WAITING_FOR_REFRESH);
                        waitTimer = 0;
                        LOGGER.info("Placed lectern, waiting for refresh");
                    } else {
                        modState.setErrorMessage("Lectern not found in inventory");
                        modState.setCurrentState(ModState.AutomationState.ERROR);
                    }
                }
            } else {
                modState.setCurrentState(ModState.AutomationState.WAITING_FOR_REFRESH);
                waitTimer = 0;
                LOGGER.info("Lectern already exists, waiting for refresh");
            }
        }
    }
    
    private void handleWaitingForRefresh() {
        waitTimer++;
        
        if (waitTimer >= MAX_WAIT_TICKS) {
            waitTimer = 0;
            modState.setCurrentState(ModState.AutomationState.SCANNING_TRADES);
            LOGGER.info("Wait complete, scanning trades");
        }
    }
    
    private void handleScanningTrades() {
        if (actionTimer >= TICKS_PER_ACTION) {
            actionTimer = 0;
            
            // Try to open villager trades
            ModConfig config = ConfigManager.getConfig();
            VillagerEntity villager = findVillagerAt(config.boundVillager.position);
            
            if (villager != null && villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
                // Look at villager and interact
                lookAtEntity(villager);
                
                if (client.player != null) {
                    client.interactionManager.interactEntity(
                        client.player, 
                        villager, 
                        client.player.getActiveHand()
                    );
                }
                
                // Check trades (this would be handled in a screen handler listener)
                // For now, simulate the check
                modState.incrementAttemptCount();
                
                boolean found = tradeScanner.checkForMatchingTrade(villager);
                if (found) {
                    modState.setCurrentState(ModState.AutomationState.FOUND_MATCH);
                    LOGGER.info("Found matching trade!");
                } else {
                    // Continue the loop
                    modState.setCurrentState(ModState.AutomationState.BOUND);
                    LOGGER.info("No matching trade, continuing (attempt " + modState.getAttemptCount() + ")");
                }
            } else {
                modState.setErrorMessage("Librarian villager not found at bound position");
                modState.setCurrentState(ModState.AutomationState.ERROR);
            }
        }
    }
    
    private void handleFoundMatch() {
        // Play success sound and message
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§a§lFound matching trade!"), false);
            
            if (ConfigManager.getConfig().settings.playSoundOnSuccess) {
                client.player.playSound(
                    net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_YES, 
                    1.0f, 
                    1.0f
                );
            }
        }
        
        modState.setCurrentState(ModState.AutomationState.STOPPED);
        LOGGER.info("Automation completed successfully");
    }
    
    private void handleError() {
        // Error state - wait for manual intervention
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("§cError: " + modState.getErrorMessage()), 
                false
            );
        }
    }
    
    private boolean hasLecternInInventory() {
        if (client.player == null) return false;
        
        PlayerInventory inventory = client.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == Items.LECTERN) {
                return true;
            }
        }
        return false;
    }
    
    private int findLecternSlot(PlayerInventory inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() == Items.LECTERN) {
                return i;
            }
        }
        return -1;
    }
    
    private VillagerEntity findVillagerAt(dutchplayer.tradeselector.util.Position position) {
        if (client.world == null) return null;
        
        BlockPos searchPos = new BlockPos(
            (int) position.x,
            (int) position.y,
            (int) position.z
        );
        
        return client.world.getEntitiesByClass(VillagerEntity.class, 
            searchPos, 
            entity -> entity.getBlockPos().equals(searchPos))
            .stream()
            .findFirst()
            .orElse(null);
    }
    
    private void lookAtBlock(BlockPos pos) {
        if (client.player == null) return;
        
        Vec3d playerPos = client.player.getEyePos();
        Vec3d blockCenter = Vec3d.ofCenter(pos);
        
        Vec3d lookDirection = blockCenter.subtract(playerPos).normalize();
        
        float yaw = (float) Math.toDegrees(Math.atan2(lookDirection.z, lookDirection.x)) - 90f;
        float pitch = (float) Math.toDegrees(-Math.asin(lookDirection.y));
        
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }
    
    private void lookAtEntity(VillagerEntity entity) {
        if (client.player == null) return;
        
        Vec3d playerPos = client.player.getEyePos();
        Vec3d entityPos = entity.getEyePos();
        
        Vec3d lookDirection = entityPos.subtract(playerPos).normalize();
        
        float yaw = (float) Math.toDegrees(Math.atan2(lookDirection.z, lookDirection.x)) - 90f;
        float pitch = (float) Math.toDegrees(-Math.asin(lookDirection.y));
        
        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }
}
