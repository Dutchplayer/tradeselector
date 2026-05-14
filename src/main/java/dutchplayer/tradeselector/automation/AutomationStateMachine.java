package dutchplayer.tradeselector.automation;

import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.ModState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutomationStateMachine {
    private static final Logger LOGGER = LoggerFactory.getLogger("TradeSelector");
    private static final int TICKS_PER_ACTION = 10;
    private static final int BREAK_TIMEOUT_TICKS = 80;
    private static final int REFRESH_WAIT_TICKS = 60;

    private final ModState modState;
    private final VillagerBinder villagerBinder;
    private final TradeScanner tradeScanner;
    private final Minecraft client;

    private int actionTimer;
    private int waitTimer;

    public AutomationStateMachine(ModState modState, VillagerBinder villagerBinder, TradeScanner tradeScanner) {
        this.modState = modState;
        this.villagerBinder = villagerBinder;
        this.tradeScanner = tradeScanner;
        this.client = Minecraft.getInstance();
    }

    public void tick() {
        if (!modState.isRunning()) {
            return;
        }

        actionTimer++;

        try {
            switch (modState.getCurrentState()) {
                case BOUND -> handleBoundState();
                case BREAKING_JOB_BLOCK -> handleBreakingJobBlock();
                case PLACING_JOB_BLOCK -> handlePlacingJobBlock();
                case WAITING_FOR_REFRESH -> handleWaitingForRefresh();
                case SCANNING_TRADES -> handleScanningTrades();
                case FOUND_MATCH -> handleFoundMatch();
                case ERROR -> handleError();
                default -> {}
            }
        } catch (Exception e) {
            LOGGER.error("Automation failed", e);
            fail(e.getMessage());
        }
    }

    public boolean start() {
        if (client.player == null || client.level == null || client.gameMode == null) {
            fail("Join a world before starting automation");
            return false;
        }

        ModConfig config = ConfigManager.getConfig();
        if (!config.boundVillager.isBound() || !config.boundJobBlock.isBound()) {
            fail("Bind both a librarian and lectern before starting");
            return false;
        }

        if (!villagerBinder.validateVillager()) {
            fail("Bound librarian is missing or no longer a librarian");
            return false;
        }

        if (!villagerBinder.validateJobBlock()) {
            fail("Bound lectern is missing");
            return false;
        }

        if (findLecternSlot(client.player.getInventory()) == -1) {
            fail("No lectern found in your inventory");
            return false;
        }

        modState.reset();
        modState.setCurrentState(ModState.AutomationState.BOUND);
        modState.setStartTime(System.currentTimeMillis());
        actionTimer = 0;
        waitTimer = 0;
        LOGGER.info("Automation started");
        return true;
    }

    public void stop() {
        modState.setCurrentState(ModState.AutomationState.STOPPED);
        actionTimer = 0;
        waitTimer = 0;
        LOGGER.info("Automation stopped");
    }

    private void handleBoundState() {
        if (actionTimer < TICKS_PER_ACTION) {
            return;
        }

        actionTimer = 0;
        waitTimer = 0;
        modState.setCurrentState(ModState.AutomationState.BREAKING_JOB_BLOCK);
    }

    private void handleBreakingJobBlock() {
        BlockPos lecternPos = ConfigManager.getConfig().boundJobBlock.position.toBlockPos();

        if (client.level == null || client.gameMode == null || client.player == null) {
            fail("Client is not ready");
            return;
        }

        if (client.level.isEmptyBlock(lecternPos)) {
            actionTimer = 0;
            modState.setCurrentState(ModState.AutomationState.PLACING_JOB_BLOCK);
            return;
        }

        if (!client.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
            fail("Bound block is no longer a lectern");
            return;
        }

        lookAt(Vec3.atCenterOf(lecternPos));
        if (actionTimer == 1) {
            client.gameMode.startDestroyBlock(lecternPos, directionFromPlayerTo(lecternPos));
            client.player.swing(InteractionHand.MAIN_HAND);
        } else {
            client.gameMode.continueDestroyBlock(lecternPos, directionFromPlayerTo(lecternPos));
        }

        if (actionTimer > BREAK_TIMEOUT_TICKS) {
            client.gameMode.destroyBlock(lecternPos);
        }
    }

    private void handlePlacingJobBlock() {
        if (actionTimer < TICKS_PER_ACTION) {
            return;
        }

        actionTimer = 0;
        BlockPos lecternPos = ConfigManager.getConfig().boundJobBlock.position.toBlockPos();

        if (client.level == null || client.gameMode == null || client.player == null) {
            fail("Client is not ready");
            return;
        }

        if (!client.level.isEmptyBlock(lecternPos)) {
            modState.setCurrentState(ModState.AutomationState.WAITING_FOR_REFRESH);
            waitTimer = 0;
            return;
        }

        Inventory inventory = client.player.getInventory();
        int lecternSlot = findLecternSlot(inventory);
        if (lecternSlot == -1) {
            fail("No lectern found in your inventory");
            return;
        }

        if (Inventory.isHotbarSlot(lecternSlot)) {
            inventory.selected = lecternSlot;
        } else {
            inventory.pickSlot(lecternSlot);
        }

        BlockPos supportPos = lecternPos.below();
        lookAt(Vec3.atCenterOf(lecternPos));
        client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(supportPos), Direction.UP, supportPos, false)
        );
        client.player.swing(InteractionHand.MAIN_HAND);

        modState.setCurrentState(ModState.AutomationState.WAITING_FOR_REFRESH);
        waitTimer = 0;
    }

    private void handleWaitingForRefresh() {
        waitTimer++;
        if (waitTimer >= REFRESH_WAIT_TICKS) {
            waitTimer = 0;
            actionTimer = 0;
            modState.setCurrentState(ModState.AutomationState.SCANNING_TRADES);
        }
    }

    private void handleScanningTrades() {
        if (actionTimer < TICKS_PER_ACTION) {
            return;
        }

        Villager villager = villagerBinder.getBoundVillager();
        if (villager == null) {
            fail("Bound librarian was not found");
            return;
        }

        if (waitTimer == 0) {
            actionTimer = 0;
            lookAt(villager.getEyePosition());
            if (client.player != null && client.gameMode != null) {
                client.gameMode.interact(client.player, villager, InteractionHand.MAIN_HAND);
                client.player.swing(InteractionHand.MAIN_HAND);
            }
            modState.incrementAttemptCount();
            waitTimer = 1;
            return;
        }

        waitTimer++;
        if (waitTimer < 12) {
            return;
        }

        MerchantOffers offers = null;
        if (client.screen instanceof MerchantScreen merchantScreen) {
            offers = merchantScreen.getMenu().getOffers();
        }
        if (offers == null || offers.isEmpty()) {
            offers = villager.getOffers();
        }

        boolean found = tradeScanner.checkForMatchingTrade(offers);
        if (found) {
            modState.setCurrentState(ModState.AutomationState.FOUND_MATCH);
        } else {
            if (client.player != null) {
                client.player.closeContainer();
            }
            waitTimer = 0;
            actionTimer = 0;
            modState.setCurrentState(ModState.AutomationState.BOUND);
        }
    }

    private void handleFoundMatch() {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("Found matching trade"), false);
            SoundEvent sound = successSound();
            if (sound != null) {
                client.player.playSound(sound, 1.0f, 1.0f);
            }
        }

        stop();
    }

    private SoundEvent successSound() {
        return switch (ConfigManager.getConfig().settings.getSuccessSound()) {
            case NONE -> null;
            case VILLAGER_YES -> SoundEvents.VILLAGER_YES;
            case LEVEL_UP -> SoundEvents.PLAYER_LEVELUP;
            case EXPERIENCE_ORB -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case AMETHYST_CHIME -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case CHALLENGE_COMPLETE -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
        };
    }

    private void handleError() {
        stop();
    }

    private int findLecternSlot(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.LECTERN)) {
                return i;
            }
        }
        return -1;
    }

    private void lookAt(Vec3 target) {
        if (client.player == null) {
            return;
        }

        Vec3 playerPos = client.player.getEyePosition();
        Vec3 direction = target.subtract(playerPos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0f;
        float pitch = (float) Math.toDegrees(-Math.asin(direction.y));
        client.player.setYRot(yaw);
        client.player.setXRot(pitch);
    }

    private Direction directionFromPlayerTo(BlockPos pos) {
        if (client.player == null) {
            return Direction.UP;
        }

        Vec3 delta = Vec3.atCenterOf(pos).subtract(client.player.getEyePosition());
        return Direction.getNearest(delta.x, delta.y, delta.z).getOpposite();
    }

    private void fail(String message) {
        modState.setErrorMessage(message == null || message.isBlank() ? "Unknown automation error" : message);
        modState.setCurrentState(ModState.AutomationState.ERROR);
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(modState.getErrorMessage()), false);
        }
    }
}
