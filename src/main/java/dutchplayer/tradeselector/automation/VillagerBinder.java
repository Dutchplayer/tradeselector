package dutchplayer.tradeselector.automation;


import com.mojang.authlib.minecraft.client.MinecraftClient;
import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.Position;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Text;

/**
 * Handles binding of villagers and job blocks
 */
public class VillagerBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private final MinecraftClient client;
    
    public VillagerBinder() {
        this.client = MinecraftClient.getInstance();
    }
    
    /**
     * Binds the villager the player is currently looking at
     */
    public boolean bindVillager() {
        if (client.player == null || client.world == null) {
            return false;
        }
        
        // Get what the player is looking at
        HitResult hit = client.player.raycast(5.0, 0.0f, false);
        
        if (hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            
            if (entityHit.getEntity() instanceof VillagerEntity villager) {
                // Check if it's a librarian
                if (villager.getVillagerData().getProfession() == 
                    net.minecraft.village.VillagerProfession.LIBRARIAN) {
                    
                    Position position = new Position(
                        villager.getX(),
                        villager.getY(),
                        villager.getZ()
                    );
                    
                    // Update config
                    ModConfig config = ConfigManager.getConfig();
                    ModConfig newConfig = new ModConfig(
                        config.targetTrade,
                        new ModConfig.BoundVillagerConfig(),
                        config.boundJobBlock,
                        config.settings
                    );
                    newConfig.boundVillager.position = position;
                    
                    ConfigManager.updateConfig(newConfig);
                    
                    // Notify player
                    client.player.sendMessage(
                        Text.literal("§aBound librarian villager at " + position), 
                        false
                    );
                    
                    LOGGER.info("Bound villager at position: " + position);
                    return true;
                } else {
                    client.player.sendMessage(
                        Text.literal("§cVillager is not a librarian!"), 
                        false
                    );
                }
            }
        } else {
            client.player.sendMessage(
                Text.literal("§cNot looking at a villager!"), 
                false
            );
        }
        
        return false;
    }
    
    /**
     * Binds the job block (lectern) the player is currently looking at
     */
    public boolean bindJobBlock() {
        if (client.player == null || client.world == null) {
            return false;
        }
        
        // Get what the player is looking at
        HitResult hit = client.player.raycast(5.0, 0.0f, false);
        
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d hitPos = hit.getPos();
            BlockPos blockPos = BlockPos.ofFloored(hitPos);
            BlockState blockState = client.world.getBlockState(blockPos);
            
            if (blockState.getBlock() == Blocks.LECTERN) {
                Position position = new Position(
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ()
                );
                
                // Update config
                ModConfig config = ConfigManager.getConfig();
                ModConfig newConfig = new ModConfig(
                    config.targetTrade,
                    config.boundVillager,
                    new ModConfig.BoundJobBlockConfig(),
                    config.settings
                );
                newConfig.boundJobBlock.position = position;
                
                ConfigManager.updateConfig(newConfig);
                
                // Notify player
                client.player.sendMessage(
                    Text.literal("§aBound lectern at " + position), 
                    false
                );
                
                LOGGER.info("Bound job block at position: " + position);
                return true;
            } else {
                client.player.sendMessage(
                    Text.literal("§cBlock is not a lectern!"), 
                    false
                );
            }
        } else {
            client.player.sendMessage(
                Text.literal("§cNot looking at a block!"), 
                false
            );
        }
        
        return false;
    }
    
    /**
     * Validates that the bound villager still exists and is in the correct position
     */
    public boolean validateVillager() {
        ModConfig config = ConfigManager.getConfig();
        
        if (!config.boundVillager.isBound()) {
            return false;
        }
        
        if (client.world == null) {
            return false;
        }
        
        Position pos = config.boundVillager.position;
        BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
        
        return client.world.getEntitiesByClass(VillagerEntity.class, 
            blockPos, 
            entity -> entity.getBlockPos().equals(blockPos) &&
                     entity.getVillagerData().getProfession() == 
                     net.minecraft.village.VillagerProfession.LIBRARIAN)
            .stream()
            .findFirst()
            .isPresent();
    }
    
    /**
     * Validates that the bound job block still exists and is a lectern
     */
    public boolean validateJobBlock() {
        ModConfig config = ConfigManager.getConfig();
        
        if (!config.boundJobBlock.isBound()) {
            return false;
        }
        
        if (client.world == null) {
            return false;
        }
        
        Position pos = config.boundJobBlock.position;
        BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
        
        return client.world.getBlockState(blockPos).getBlock() == Blocks.LECTERN;
    }
    
    /**
     * Gets the bound villager entity if it exists
     */
    public VillagerEntity getBoundVillager() {
        ModConfig config = ConfigManager.getConfig();
        
        if (!config.boundVillager.isBound() || client.world == null) {
            return null;
        }
        
        Position pos = config.boundVillager.position;
        BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
        
        return client.world.getEntitiesByClass(VillagerEntity.class, 
            blockPos, 
            entity -> entity.getBlockPos().equals(blockPos))
            .stream()
            .findFirst()
            .orElse(null);
    }
}
