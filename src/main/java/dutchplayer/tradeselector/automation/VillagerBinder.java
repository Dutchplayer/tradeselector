package dutchplayer.tradeselector.automation;

import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.PlayerMessages;
import dutchplayer.tradeselector.util.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.UUID;

public class VillagerBinder {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private static final double LOOK_FALLBACK_MAX_DISTANCE_BLOCKS = 3.0;
    private static final double LOOK_FALLBACK_MIN_ALIGNMENT = 0.25;
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
            PlayerMessages.send(client.player, "Look at a librarian villager first");
            return false;
        }

        Entity entity = entityHit.getEntity();
        if (!(entity instanceof Villager villager)) {
            PlayerMessages.send(client.player, "Look at a villager first");
            return false;
        }

        if (villager.getVillagerData().getProfession() != VillagerProfession.LIBRARIAN) {
            PlayerMessages.send(client.player, "That villager is not a librarian");
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
        PlayerMessages.send(client.player, "Bound librarian at " + position);
        LOGGER.info("Bound villager at {}", position);
        return true;
    }

    public boolean bindJobBlock() {
        if (client.player == null || client.level == null) {
            return false;
        }

        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            PlayerMessages.send(client.player, "Look at a lectern first");
            return false;
        }

        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = client.level.getBlockState(blockPos);
        if (!blockState.is(Blocks.LECTERN)) {
            PlayerMessages.send(client.player, "The selected block is not a lectern");
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
        PlayerMessages.send(client.player, "Bound lectern at " + position);
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
        if (!config.boundVillager.isBound() || client.level == null || client.player == null) {
            return null;
        }

        Villager villagerByUuid = getBoundVillagerByUuid(config.boundVillager.uuid);
        if (villagerByUuid != null && villagerByUuid.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
            return villagerByUuid;
        }

        return getFallbackVillagerInLookDirection();
    }

    private Villager getFallbackVillagerInLookDirection() {
        if (client.level == null || client.player == null) {
            return null;
        }

        Vec3 eyePosition = client.player.getEyePosition();
        Vec3 lookDirection = client.player.getLookAngle().normalize();
        AABB searchBox = client.player.getBoundingBox().inflate(LOOK_FALLBACK_MAX_DISTANCE_BLOCKS);

        return client.level.getEntities(
                        EntityTypeTest.forClass(Villager.class),
                        searchBox,
                        villager -> villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
                )
                .stream()
                .filter(villager -> {
                    Vec3 toVillager = villager.getEyePosition().subtract(eyePosition);
                    double distance = toVillager.length();
                    if (distance <= 1.0E-6 || distance > LOOK_FALLBACK_MAX_DISTANCE_BLOCKS) {
                        return false;
                    }

                    double alignment = lookDirection.dot(toVillager.normalize());
                    return alignment >= LOOK_FALLBACK_MIN_ALIGNMENT;
                })
                .min(Comparator.comparingDouble(villager -> villager.distanceToSqr(client.player)))
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
