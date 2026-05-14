package dutchplayer.tradeselector.automation;

import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class VillagerBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private final Minecraft client;

    public VillagerBinder() {
        this.client = Minecraft.getInstance();
    }

    public boolean bindVillager() {
        if (client.player == null || client.level == null) {
            return false;
        }

        HitResult hit = client.hitResult;
        if (!(hit instanceof EntityHitResult entityHit)) {
            client.player.displayClientMessage(Component.literal("Look at a librarian villager first"), false);
            return false;
        }

        Entity entity = entityHit.getEntity();
        if (!(entity instanceof Villager villager)) {
            client.player.displayClientMessage(Component.literal("Look at a villager first"), false);
            return false;
        }

        if (villager.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN) {
            client.player.displayClientMessage(Component.literal("That villager is not a librarian"), false);
            return false;
        }

        Position position = Position.fromBlockPos(villager.blockPosition());
        ModConfig config = ConfigManager.getConfig();
        ModConfig newConfig = new ModConfig(
                config.targetTrade,
                new ModConfig.BoundVillagerConfig(position, villager.getUUID().toString()),
                config.boundJobBlock,
                config.settings
        );

        ConfigManager.updateConfig(newConfig);
        client.player.displayClientMessage(Component.literal("Bound librarian at " + position), false);
        LOGGER.info("Bound villager at {}", position);
        return true;
    }

    public boolean bindJobBlock() {
        if (client.player == null || client.level == null) {
            return false;
        }

        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            client.player.displayClientMessage(Component.literal("Look at a lectern first"), false);
            return false;
        }

        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = client.level.getBlockState(blockPos);
        if (!blockState.is(Blocks.LECTERN)) {
            client.player.displayClientMessage(Component.literal("The selected block is not a lectern"), false);
            return false;
        }

        Position position = Position.fromBlockPos(blockPos);
        ModConfig config = ConfigManager.getConfig();
        ModConfig newConfig = new ModConfig(
                config.targetTrade,
                config.boundVillager,
                new ModConfig.BoundJobBlockConfig(position),
                config.settings
        );

        ConfigManager.updateConfig(newConfig);
        client.player.displayClientMessage(Component.literal("Bound lectern at " + position), false);
        LOGGER.info("Bound lectern at {}", position);
        return true;
    }

    public boolean validateVillager() {
        return getBoundVillager() != null;
    }

    public boolean validateJobBlock() {
        ModConfig config = ConfigManager.getConfig();
        if (!config.boundJobBlock.isBound() || client.level == null) {
            return false;
        }

        return client.level.getBlockState(config.boundJobBlock.position.toBlockPos()).is(Blocks.LECTERN);
    }

    public Villager getBoundVillager() {
        ModConfig config = ConfigManager.getConfig();
        if (!config.boundVillager.isBound() || client.level == null) {
            return null;
        }

        Villager villagerByUuid = getBoundVillagerByUuid(config.boundVillager.uuid);
        if (villagerByUuid != null && villagerByUuid.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
            return villagerByUuid;
        }

        BlockPos blockPos = config.boundVillager.position.toBlockPos();
        AABB searchBox = new AABB(blockPos).inflate(3.0);
        return client.level.getEntities(
                        EntityTypeTest.forClass(Villager.class),
                        searchBox,
                        villager -> villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Villager getBoundVillagerByUuid(String uuidText) {
        if (uuidText == null || uuidText.isBlank() || client.level == null || client.player == null) {
            return null;
        }

        try {
            UUID uuid = UUID.fromString(uuidText);
            for (Villager villager : client.level.getEntities(
                    EntityTypeTest.forClass(Villager.class),
                    client.player.getBoundingBox().inflate(32.0),
                    villager -> villager.getUUID().equals(uuid))) {
                return villager;
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        return null;
    }
}
