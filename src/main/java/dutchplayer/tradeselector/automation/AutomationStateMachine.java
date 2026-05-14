package dutchplayer.tradeselector.automation;

import dutchplayer.tradeselector.config.ConfigManager;
import dutchplayer.tradeselector.config.ModConfig;
import dutchplayer.tradeselector.util.ModState;
import dutchplayer.tradeselector.util.PlayerMessages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
    private static final int TICKS_PER_ACTION = 6;
    private static final int BREAK_TIMEOUT_TICKS = 80;
    private static final int REFRESH_WAIT_TICKS = 45;
    private static final int OPEN_TRADE_WAIT_TICKS = 8;
    private static final int OPEN_TRADE_WAIT_INCREMENT_TICKS = 8;
    private static final int RETRY_DELAY_TICKS = 20;
    private static final int MAX_OPEN_RETRIES = 5;
    private static final int BLOCK_CONFIRM_TICKS = 3;
    private static final int BREAK_SETTLE_TICKS = 4;
    private static final int BREAK_RETRY_DELAY_TICKS = 6;
    private static final int MAX_BREAK_TIMEOUT_RETRIES = 3;
    private static final int SLOT_SYNC_DELAY_TICKS = 1;
    private static final int PLACE_RETRY_DELAY_TICKS = 6;
    private static final int MAX_PLACE_RETRIES = 5;
    private static final int RECOVERY_MOVE_MAX_TICKS = 30;
    private static final double RECOVERY_FORWARD_DISTANCE_BLOCKS = 0.9;
    private static final double RECOVERY_RETURN_STOP_DISTANCE_BLOCKS = 0.20;
    private static final int RECOVERY_RETURN_SETTLE_TICKS = 2;
    private static final float SCAN_LOOK_DRIFT_TOLERANCE_DEGREES = 2.0f;

    private final ModState modState;
    private final VillagerBinder villagerBinder;
    private final TradeScanner tradeScanner;
    private final Minecraft client;

    private int actionTimer;
    private int waitTimer;
    private int openTradeRetryCount;
    private int retryDelayTicks;
    private boolean precheckPending;
    private int breakConfirmationTicks;
    private int placeConfirmationTicks;
    private int placeRetryCount;
    private int placeRetryDelayTicks;
    private int originalSelectedHotbarSlot = -1;
    private boolean recoveryMoveActive;
    private boolean recoveryMoveReturning;
    private boolean recoveryMoveAttemptedThisCycle;
    private int recoveryMoveTicks;
    private int recoveryReturnSettleTicks;
    private Vec3 recoveryMoveStartPos;
    private Vec3 recoveryReturnTarget;
    private boolean recoveryForwardKeyExpected;
    private boolean recoveryBackwardKeyExpected;
    private boolean scanLookMonitorActive;
    private float scanExpectedYaw;
    private float scanExpectedPitch;
    private int breakSettleTicks;
    private int breakRetryDelayTicks;
    private int breakTimeoutRetryCount;
    private int slotSyncDelayTicks;

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

        String movementFailMessage = movementInputFailMessage();
        if (movementFailMessage != null) {
            fail(movementFailMessage);
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

        modState.reset();
        modState.setCurrentState(ModState.AutomationState.SCANNING_TRADES);
        modState.setStartTime(System.currentTimeMillis());
        actionTimer = 0;
        waitTimer = 0;
        openTradeRetryCount = 0;
        retryDelayTicks = 0;
        precheckPending = true;
        resetPlacementTracking();
        originalSelectedHotbarSlot = -1;
        LOGGER.info("Automation started (precheck first)");
        return true;
    }

    public void stop() {
        stopDestroyAction();
        modState.setCurrentState(ModState.AutomationState.STOPPED);
        actionTimer = 0;
        waitTimer = 0;
        openTradeRetryCount = 0;
        retryDelayTicks = 0;
        precheckPending = false;
        restoreSelectedHotbarSlot();
        resetPlacementTracking();
        stopRecoveryMovement();
        stopScanLookMonitor();
        LOGGER.info("Automation stopped");
    }

    private void handleBoundState() {
        if (actionTimer < TICKS_PER_ACTION) {
            return;
        }

        actionTimer = 0;
        waitTimer = 0;
        resetPlacementTracking();
        modState.setCurrentState(ModState.AutomationState.BREAKING_JOB_BLOCK);
    }

    private void handleBreakingJobBlock() {
        BlockPos lecternPos = ConfigManager.getConfig().boundJobBlock.position.toBlockPos();

        if (client.level == null || client.gameMode == null || client.player == null) {
            fail("Client is not ready");
            return;
        }

        if (client.level.isEmptyBlock(lecternPos)) {
            stopDestroyAction();
            breakConfirmationTicks++;
            if (breakConfirmationTicks < BLOCK_CONFIRM_TICKS) {
                return;
            }

            breakSettleTicks++;
            if (breakSettleTicks < BREAK_SETTLE_TICKS) {
                return;
            }

            actionTimer = 0;
            breakConfirmationTicks = 0;
            breakSettleTicks = 0;
            breakRetryDelayTicks = 0;
            breakTimeoutRetryCount = 0;
            placeConfirmationTicks = 0;
            placeRetryCount = 0;
            placeRetryDelayTicks = 0;
            modState.setCurrentState(ModState.AutomationState.PLACING_JOB_BLOCK);
            return;
        }

        breakConfirmationTicks = 0;
        breakSettleTicks = 0;

        if (breakRetryDelayTicks > 0) {
            breakRetryDelayTicks--;
            return;
        }

        if (!client.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
            fail("Bound block is no longer a lectern");
            return;
        }

        lookAt(Vec3.atCenterOf(lecternPos));
        if (actionTimer == 1) {
            client.gameMode.startDestroyBlock(lecternPos, directionFromPlayerTo(lecternPos));
        } else {
            client.gameMode.continueDestroyBlock(lecternPos, directionFromPlayerTo(lecternPos));
        }
        client.player.swing(InteractionHand.MAIN_HAND);

        if (actionTimer > BREAK_TIMEOUT_TICKS) {
            stopDestroyAction();
            actionTimer = 0;
            breakTimeoutRetryCount++;
            breakRetryDelayTicks = BREAK_RETRY_DELAY_TICKS;

            if (breakTimeoutRetryCount > MAX_BREAK_TIMEOUT_RETRIES) {
                fail("Timed out breaking lectern after " + MAX_BREAK_TIMEOUT_RETRIES + " retries");
            }
        }
    }

    private void handlePlacingJobBlock() {
        BlockPos lecternPos = ConfigManager.getConfig().boundJobBlock.position.toBlockPos();

        if (client.level == null || client.gameMode == null || client.player == null) {
            fail("Client is not ready");
            return;
        }

        if (recoveryMoveActive) {
            tickRecoveryMove();
            return;
        }

        if (client.level.getBlockState(lecternPos).is(Blocks.LECTERN)) {
            placeConfirmationTicks++;
            if (placeConfirmationTicks < BLOCK_CONFIRM_TICKS) {
                return;
            }

            placeConfirmationTicks = 0;
            modState.setCurrentState(ModState.AutomationState.WAITING_FOR_REFRESH);
            waitTimer = 0;
            openTradeRetryCount = 0;
            retryDelayTicks = 0;
            restoreSelectedHotbarSlot();
            resetPlacementTracking();
            return;
        }

        placeConfirmationTicks = 0;

        if (!client.level.isEmptyBlock(lecternPos)) {
            fail("Bound lectern spot is occupied");
            return;
        }

        if (placeRetryDelayTicks > 0) {
            placeRetryDelayTicks--;
            return;
        }

        if (slotSyncDelayTicks > 0) {
            slotSyncDelayTicks--;
            if (slotSyncDelayTicks > 0) {
                return;
            }
        }

        if (actionTimer < TICKS_PER_ACTION) {
            return;
        }

        actionTimer = 0;

        Inventory inventory = client.player.getInventory();
        int lecternSlot = findLecternSlot(inventory);
        if (lecternSlot == -1) {
            if (ConfigManager.getConfig().settings.enableLecternRecoveryWalk && !recoveryMoveAttemptedThisCycle) {
                if (startRecoveryMove()) {
                    recoveryMoveAttemptedThisCycle = true;
                    actionTimer = 0;
                    return;
                }
            }
            fail("No lectern found in your inventory");
            restoreSelectedHotbarSlot();
            return;
        }

        recoveryMoveAttemptedThisCycle = false;

        if (originalSelectedHotbarSlot == -1) {
            originalSelectedHotbarSlot = inventory.selected;
        }

        boolean switchedSlot = false;
        if (Inventory.isHotbarSlot(lecternSlot)) {
            if (inventory.selected != lecternSlot) {
                inventory.selected = lecternSlot;
                switchedSlot = true;
            }
        } else {
            inventory.pickSlot(lecternSlot);
            switchedSlot = true;
        }

        if (switchedSlot) {
            slotSyncDelayTicks = SLOT_SYNC_DELAY_TICKS;
            actionTimer = TICKS_PER_ACTION;
            return;
        }

        if (!client.player.getMainHandItem().is(Items.LECTERN)) {
            slotSyncDelayTicks = SLOT_SYNC_DELAY_TICKS;
            actionTimer = TICKS_PER_ACTION;
            return;
        }

        BlockPos supportPos = lecternPos.below();
        lookAt(Vec3.atCenterOf(lecternPos));
        InteractionResult placeResult = client.gameMode.useItemOn(
                client.player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(supportPos), Direction.UP, supportPos, false)
        );
        client.player.swing(InteractionHand.MAIN_HAND);

        placeRetryCount++;
        if (placeRetryCount > MAX_PLACE_RETRIES) {
            fail("Failed to place lectern after " + MAX_PLACE_RETRIES + " retries");
            return;
        }

        if (!placeResult.consumesAction()) {
            placeRetryDelayTicks = PLACE_RETRY_DELAY_TICKS;
            return;
        }

        placeRetryDelayTicks = PLACE_RETRY_DELAY_TICKS;
    }

    private void handleWaitingForRefresh() {
        waitTimer++;
        if (waitTimer >= REFRESH_WAIT_TICKS) {
            waitTimer = 0;
            actionTimer = 0;
            openTradeRetryCount = 0;
            retryDelayTicks = 0;
            modState.setCurrentState(ModState.AutomationState.SCANNING_TRADES);
        }
    }

    private void handleScanningTrades() {
        if (retryDelayTicks > 0) {
            retryDelayTicks--;
            return;
        }

        if (actionTimer < TICKS_PER_ACTION) {
            return;
        }

        if (scanLookMonitorActive && hasScanLookDrift()) {
            fail("Manual look movement detected while opening villager offers");
            return;
        }

        Villager villager = villagerBinder.getBoundVillager();
        if (villager == null) {
            fail("Bound librarian was not found");
            return;
        }

        if (waitTimer == 0) {
            actionTimer = 0;
            if (client.player != null && client.screen instanceof MerchantScreen) {
                client.player.closeContainer();
            }
            lookAt(villager.getEyePosition());
            startScanLookMonitor();
            if (client.player != null && client.gameMode != null) {
                client.gameMode.interact(client.player, villager, InteractionHand.MAIN_HAND);
                client.player.swing(InteractionHand.MAIN_HAND);
            }
            if (!precheckPending) {
                modState.incrementAttemptCount();
            }
            waitTimer = 1;
            return;
        }

        waitTimer++;
        int requiredWaitTicks = requiredTradeOpenWaitTicks();
        if (waitTimer < requiredWaitTicks) {
            return;
        }

        MerchantOffers offers = null;
        if (client.screen instanceof MerchantScreen merchantScreen) {
            offers = merchantScreen.getMenu().getOffers();
        }

        if (offers == null || offers.isEmpty()) {
            if (client.player != null) {
                client.player.closeContainer();
            }

            stopScanLookMonitor();

            openTradeRetryCount++;
            if (openTradeRetryCount >= MAX_OPEN_RETRIES) {
                fail("Failed to open villager offers after " + MAX_OPEN_RETRIES + " tries");
                return;
            }

            waitTimer = 0;
            actionTimer = 0;
            retryDelayTicks = RETRY_DELAY_TICKS;
            return;
        }

        openTradeRetryCount = 0;
        retryDelayTicks = 0;
        stopScanLookMonitor();

        boolean found = tradeScanner.checkForMatchingTrade(offers);
        if (found) {
            modState.setCurrentState(ModState.AutomationState.FOUND_MATCH);
        } else {
            if (client.player != null) {
                client.player.closeContainer();
            }

            if (precheckPending) {
                precheckPending = false;
                waitTimer = 0;
                actionTimer = 0;
                modState.setCurrentState(ModState.AutomationState.BOUND);
                return;
            }

            waitTimer = 0;
            actionTimer = 0;
            modState.setCurrentState(ModState.AutomationState.BOUND);
        }
    }

    private int requiredTradeOpenWaitTicks() {
        return OPEN_TRADE_WAIT_TICKS + (openTradeRetryCount * OPEN_TRADE_WAIT_INCREMENT_TICKS);
    }

    private void handleFoundMatch() {
        if (client.player != null) {
            PlayerMessages.send(client.player, "Found matching trade");
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

    private int findLecternSlotOrFail(Inventory inventory) {
        int slot = findLecternSlot(inventory);
        if (slot == -1) {
            fail("No lectern found in your inventory");
        }
        return slot;
    }

    private void resetPlacementTracking() {
        breakConfirmationTicks = 0;
        breakSettleTicks = 0;
        breakRetryDelayTicks = 0;
        breakTimeoutRetryCount = 0;
        slotSyncDelayTicks = 0;
        placeConfirmationTicks = 0;
        placeRetryCount = 0;
        placeRetryDelayTicks = 0;
        recoveryMoveAttemptedThisCycle = false;
        recoveryMoveTicks = 0;
        recoveryReturnSettleTicks = 0;
        recoveryMoveStartPos = null;
        recoveryReturnTarget = null;
    }

    private boolean startRecoveryMove() {
        if (client.player == null) {
            return false;
        }

        recoveryMoveActive = true;
        recoveryMoveReturning = false;
        recoveryMoveTicks = 0;
        recoveryReturnSettleTicks = 0;
        recoveryMoveStartPos = client.player.position();
        recoveryReturnTarget = Vec3.atCenterOf(BlockPos.containing(client.player.position()));
        setRecoveryMovementKeys(true, false);
        return true;
    }

    private void tickRecoveryMove() {
        if (client.player == null || recoveryMoveStartPos == null || recoveryReturnTarget == null) {
            stopRecoveryMovement();
            return;
        }

        recoveryMoveTicks++;
        double horizontalDistance = horizontalDistanceFromRecoveryStart();

        if (!recoveryMoveReturning) {
            setRecoveryMovementKeys(true, false);
            if (horizontalDistance >= RECOVERY_FORWARD_DISTANCE_BLOCKS || recoveryMoveTicks >= RECOVERY_MOVE_MAX_TICKS) {
                recoveryMoveReturning = true;
                recoveryMoveTicks = 0;
            }
            return;
        }

        double returnDistance = horizontalDistanceTo(recoveryReturnTarget);

        if (returnDistance <= RECOVERY_RETURN_STOP_DISTANCE_BLOCKS) {
            setRecoveryMovementKeys(false, false);
            recoveryReturnSettleTicks++;

            if (recoveryReturnSettleTicks >= RECOVERY_RETURN_SETTLE_TICKS) {
                stopRecoveryMovement();
                actionTimer = 0;
                placeRetryDelayTicks = PLACE_RETRY_DELAY_TICKS;
            }
            return;
        }

        recoveryReturnSettleTicks = 0;
        setRecoveryMovementKeys(false, true);
        if (recoveryMoveTicks >= RECOVERY_MOVE_MAX_TICKS) {
            stopRecoveryMovement();
            actionTimer = 0;
            placeRetryDelayTicks = PLACE_RETRY_DELAY_TICKS;
        }
    }

    private double horizontalDistanceTo(Vec3 target) {
        if (client.player == null || target == null) {
            return 0.0;
        }

        double dx = client.player.getX() - target.x;
        double dz = client.player.getZ() - target.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double horizontalDistanceFromRecoveryStart() {
        if (client.player == null || recoveryMoveStartPos == null) {
            return 0.0;
        }

        double dx = client.player.getX() - recoveryMoveStartPos.x;
        double dz = client.player.getZ() - recoveryMoveStartPos.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void stopRecoveryMovement() {
        recoveryMoveActive = false;
        recoveryMoveReturning = false;
        recoveryMoveTicks = 0;
        recoveryReturnSettleTicks = 0;
        recoveryMoveStartPos = null;
        recoveryReturnTarget = null;
        setRecoveryMovementKeys(false, false);
    }

    private void setRecoveryMovementKeys(boolean forward, boolean backward) {
        if (client.options == null) {
            return;
        }

        recoveryForwardKeyExpected = forward;
        recoveryBackwardKeyExpected = backward;

        client.options.keyUp.setDown(forward);
        client.options.keyDown.setDown(backward);
    }

    private boolean hasManualMovementInput() {
        if (client.options == null) {
            return false;
        }

        boolean forwardPressed = client.options.keyUp.isDown();
        boolean backwardPressed = client.options.keyDown.isDown();

        if (forwardPressed && !recoveryForwardKeyExpected) {
            return true;
        }

        if (backwardPressed && !recoveryBackwardKeyExpected) {
            return true;
        }

        boolean sprintKeyPressed = client.options.keySprint.isDown();
        if (sprintKeyPressed) {
            return true;
        }

        boolean sneakKeyPressed = client.options.keyShift.isDown();
        if (sneakKeyPressed) {
            return true;
        }

        return client.options.keyLeft.isDown()
                || client.options.keyRight.isDown()
                || client.options.keyJump.isDown();
    }

    private String movementInputFailMessage() {
        if (isToggleSprintEnabled()) {
            return "Toggle sprint is enabled, please disable it. Stopped automation";
        }

        if (isToggleSneakEnabled()) {
            return "Toggle sneak is enabled, please disable it. Stopped automation";
        }

        if (hasManualMovementInput()) {
            return "Manual movement input detected. Stopped automation";
        }

        return null;
    }

    private boolean isToggleSprintEnabled() {
        return isToggleOptionEnabled("toggleSprint");
    }

    private boolean isToggleSneakEnabled() {
        return isToggleOptionEnabled("toggleCrouch") || isToggleOptionEnabled("toggleSneak");
    }

    private boolean isToggleOptionEnabled(String optionName) {
        if (client.options == null) {
            return false;
        }

        try {
            Object option = client.options.getClass().getMethod(optionName).invoke(client.options);
            return readBooleanOption(option);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object option = client.options.getClass().getField(optionName).get(client.options);
            return readBooleanOption(option);
        } catch (ReflectiveOperationException ignored) {
        }

        return false;
    }

    private void startScanLookMonitor() {
        if (client.player == null) {
            scanLookMonitorActive = false;
            return;
        }

        scanExpectedYaw = client.player.getYRot();
        scanExpectedPitch = client.player.getXRot();
        scanLookMonitorActive = true;
    }

    private void stopScanLookMonitor() {
        scanLookMonitorActive = false;
    }

    private boolean hasScanLookDrift() {
        if (!scanLookMonitorActive || client.player == null) {
            return false;
        }

        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        float yawDelta = Math.abs(wrapDegrees(currentYaw - scanExpectedYaw));
        float pitchDelta = Math.abs(currentPitch - scanExpectedPitch);
        return yawDelta > SCAN_LOOK_DRIFT_TOLERANCE_DEGREES || pitchDelta > SCAN_LOOK_DRIFT_TOLERANCE_DEGREES;
    }

    private float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private boolean readBooleanOption(Object optionInstance) {
        if (optionInstance == null) {
            return false;
        }

        if (optionInstance instanceof Boolean booleanValue) {
            return booleanValue;
        }

        try {
            Object value = optionInstance.getClass().getMethod("get").invoke(optionInstance);
            return value instanceof Boolean booleanValue && booleanValue;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Object value = optionInstance.getClass().getMethod("getValue").invoke(optionInstance);
            return value instanceof Boolean booleanValue && booleanValue;
        } catch (ReflectiveOperationException ignored) {
        }

        return false;
    }

    private void restoreSelectedHotbarSlot() {
        if (client.player == null || originalSelectedHotbarSlot < 0) {
            return;
        }

        if (Inventory.isHotbarSlot(originalSelectedHotbarSlot)) {
            client.player.getInventory().selected = originalSelectedHotbarSlot;
        }

        originalSelectedHotbarSlot = -1;
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

    private void stopDestroyAction() {
        if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }

    private void fail(String message) {
        stopDestroyAction();
        stopRecoveryMovement();
        restoreSelectedHotbarSlot();
        resetPlacementTracking();
        stopScanLookMonitor();
        String baseMessage = message == null || message.isBlank() ? "Unknown automation error" : message;
        String contextMessage = "[TradeSelector] " + baseMessage;
        modState.setErrorMessage(contextMessage);
        modState.setCurrentState(ModState.AutomationState.ERROR);
        if (client.player != null) {
            PlayerMessages.send(client.player, baseMessage);
            client.player.playSound(SoundEvents.END_PORTAL_SPAWN, 1.0f, 1.0f);
        }
    }
}
