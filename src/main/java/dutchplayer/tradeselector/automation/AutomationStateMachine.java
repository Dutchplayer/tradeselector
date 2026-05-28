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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

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
    private static Field inventorySelectedSlotField;
    private static boolean inventorySelectedSlotFieldResolved;

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
    private boolean pausedForFocusLoss;
    private Object originalInactivityFpsLimit;
    private Object inactivityFpsOption;
    private boolean fpsLimitOverrideActive;

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

        if (shouldPauseForLostFocus()) {
            enterLostFocusPauseIfNeeded();
            return;
        }

        if (pausedForFocusLoss) {
            pausedForFocusLoss = false;
            actionTimer = 0;
            if (client.player != null) {
                PlayerMessages.send(client.player, "Automation resumed");
            }
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
        pausedForFocusLoss = false;
        fpsLimitOverrideActive = true;
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
        pausedForFocusLoss = false;
        fpsLimitOverrideActive = false;
        LOGGER.info("Automation stopped");
    }

    public boolean shouldOverrideInactiveFpsLimit() {
        return fpsLimitOverrideActive && modState.isRunning();
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
            originalSelectedHotbarSlot = getSelectedHotbarSlot(inventory);
        }

        boolean switchedSlot = false;
        if (Inventory.isHotbarSlot(lecternSlot)) {
            if (getSelectedHotbarSlot(inventory) != lecternSlot) {
                setSelectedHotbarSlot(inventory, lecternSlot);
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
        client.gameMode.useItemOn(
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
        refreshMerchantOffersForFoundMatch();

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

    private void refreshMerchantOffersForFoundMatch() {
        if (client.player == null || client.gameMode == null) {
            return;
        }

        Villager villager = villagerBinder.getBoundVillager();
        if (villager == null) {
            return;
        }

        if (client.screen instanceof MerchantScreen) {
            client.player.closeContainer();
        }

        lookAt(villager.getEyePosition());
        client.gameMode.interact(client.player, villager, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
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

    private Object invokeOptionsAccessorNoArgs(String... accessorNames) {
        if (client.options == null) {
            return null;
        }

        for (String accessorName : accessorNames) {
            try {
                return client.options.getClass().getMethod(accessorName).invoke(client.options);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private Object findLikelyInactivityFpsLimitOption() {
        List<Object> candidates = allOptionLikeCandidates();
        for (Object candidate : candidates) {
            Object value = readOptionValue(candidate);
            if (!(value instanceof Enum<?> enumValue)) {
                continue;
            }

            Object[] constants = enumValue.getDeclaringClass().getEnumConstants();
            if (constants == null || constants.length < 2) {
                continue;
            }

            int minFps = Integer.MAX_VALUE;
            int maxFps = Integer.MIN_VALUE;
            int measured = 0;
            for (Object constant : constants) {
                Integer fps = readInactivityFpsCandidate(constant);
                if (fps == null) {
                    continue;
                }
                measured++;
                minFps = Math.min(minFps, fps);
                maxFps = Math.max(maxFps, fps);
            }

            if (measured >= 2 && minFps <= 15 && maxFps >= 30) {
                return candidate;
            }
        }

        return null;
    }

    private Object findLikelyFramerateLimitOption() {
        List<Object> candidates = allOptionLikeCandidates();
        Object best = null;
        int bestFps = Integer.MIN_VALUE;
        for (Object candidate : candidates) {
            Object value = readOptionValue(candidate);
            if (!(value instanceof Number number)) {
                continue;
            }

            int fps = number.intValue();
            if (fps > bestFps) {
                bestFps = fps;
                best = candidate;
            }
        }

        return best;
    }

    private List<Object> allOptionLikeCandidates() {
        List<Object> candidates = new ArrayList<>();
        if (client.options == null) {
            return candidates;
        }

        for (Method method : client.options.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }

            try {
                Object candidate = method.invoke(client.options);
                if (isOptionLikeCandidate(candidate)) {
                    candidates.add(candidate);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (Field field : client.options.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                Object candidate = field.get(client.options);
                if (isOptionLikeCandidate(candidate)) {
                    candidates.add(candidate);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return candidates;
    }

    private boolean isOptionLikeCandidate(Object candidate) {
        if (candidate == null) {
            return false;
        }

        return isSimpleOptionValue(readOptionValue(candidate));
    }

    private Object pauseOnLostFocusOption() {
        if (client.options == null) {
            return null;
        }

        Object option = invokeOptionsAccessorNoArgs("pauseOnLostFocus", "pauseWhenUnfocused");
        if (option != null) {
            return option;
        }

        return getOption("pauseOnLostFocus");
    }

    private Object inactivityFpsLimitOption() {
        if (client.options == null) {
            return null;
        }

        Object option = invokeOptionsAccessorNoArgs("inactivityFpsLimit", "afkFpsLimit", "inactiveFpsLimit");
        if (option != null) {
            return option;
        }

        option = getOption("inactivityFpsLimit");
        if (option != null) {
            return option;
        }

        return findLikelyInactivityFpsLimitOption();
    }

    private Object framerateLimitOption() {
        if (client.options == null) {
            return null;
        }

        Object option = invokeOptionsAccessorNoArgs("framerateLimit", "maxFps");
        if (option != null) {
            return option;
        }

        option = getOption("framerateLimit");
        if (option != null) {
            return option;
        }

        return findLikelyFramerateLimitOption();
    }

    private Object toggleSprintOption() {
        if (client.options == null) {
            return null;
        }

        Object option = invokeOptionsAccessorNoArgs("toggleSprint");
        if (option != null) {
            return option;
        }

        return getOption("toggleSprint");
    }

    private Object toggleCrouchOption() {
        if (client.options == null) {
            return null;
        }

        Object option = invokeOptionsAccessorNoArgs("toggleCrouch", "toggleSneak");
        if (option != null) {
            return option;
        }

        return getOption("toggleCrouch");
    }

    private Object toggleSneakOption() {
        if (client.options == null) {
            return null;
        }

        Object option = invokeOptionsAccessorNoArgs("toggleSneak", "toggleCrouch");
        if (option != null) {
            return option;
        }

        return getOption("toggleSneak");
    }

    private boolean shouldPauseForLostFocus() {
        return isPauseOnLostFocusEnabled() && !isWindowActive();
    }

    private void enterLostFocusPauseIfNeeded() {
        if (pausedForFocusLoss) {
            return;
        }

        pausedForFocusLoss = true;
        actionTimer = 0;
        stopDestroyAction();
        setRecoveryMovementKeys(false, false);
        if (client.player != null) {
            PlayerMessages.send(client.player, "Automation paused while game is unfocused");
        }
    }

    private boolean isPauseOnLostFocusEnabled() {
        return readBooleanOption(pauseOnLostFocusOption());
    }

    private boolean isWindowActive() {
        try {
            Method method = client.getClass().getMethod("isWindowActive");
            Object value = method.invoke(client);
            return value instanceof Boolean active ? active : true;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = client.getClass().getMethod("isWindowFocused");
            Object value = method.invoke(client);
            return value instanceof Boolean active ? active : true;
        } catch (ReflectiveOperationException ignored) {
        }

        return true;
    }

    private boolean isToggleSprintEnabled() {
        return readBooleanOption(toggleSprintOption());
    }

    private boolean isToggleSneakEnabled() {
        return readBooleanOption(toggleCrouchOption()) || readBooleanOption(toggleSneakOption());
    }

    private Object getOption(String optionName) {
        if (client.options == null) {
            return null;
        }

        try {
            return client.options.getClass().getMethod(optionName).invoke(client.options);
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            return client.options.getClass().getField(optionName).get(client.options);
        } catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    private void applyAutomationFpsLimitOverride() {
        Object option = inactivityFpsLimitOption();
        Object currentValue = readOptionValue(option);

        if (option == null || currentValue == null) {
            LOGGER.info("Automation FPS override skipped: inactivity option not resolved");
            return;
        }

        Object overrideValue = bestNoThrottleInactivityFpsValue(currentValue);
        if (overrideValue == null || overrideValue.equals(currentValue)) {
            LOGGER.info("Automation FPS override skipped: no better inactivity value found (current={})", currentValue);
            return;
        }

        if (setOptionValue(option, overrideValue)) {
            inactivityFpsOption = option;
            originalInactivityFpsLimit = currentValue;
            LOGGER.info("Automation FPS override applied: {} -> {}", currentValue, overrideValue);
            return;
        }

        LOGGER.info("Automation FPS override failed: setter invocation did not succeed");
    }

    private void restoreAutomationFpsLimitOverride() {
        if (inactivityFpsOption == null || originalInactivityFpsLimit == null) {
            return;
        }

        setOptionValue(inactivityFpsOption, originalInactivityFpsLimit);
        inactivityFpsOption = null;
        originalInactivityFpsLimit = null;
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
        Object value = readOptionValue(optionInstance);
        return value instanceof Boolean bool && bool;
    }

    private Object readOptionValue(Object optionInstance) {
        if (optionInstance == null) {
            return null;
        }

        if (optionInstance instanceof Boolean) {
            return optionInstance;
        }

        if (optionInstance.getClass().isEnum()) {
            return optionInstance;
        }

        if (optionInstance instanceof Number || optionInstance instanceof CharSequence) {
            return optionInstance;
        }

        Object value = invokeOptionGetterByName(optionInstance, "get", "getValue");
        if (isSimpleOptionValue(value)) {
            return value;
        }

        value = invokeOptionGetterHeuristic(optionInstance);
        if (isSimpleOptionValue(value)) {
            return value;
        }

        value = readOptionValueFromFields(optionInstance);
        if (isSimpleOptionValue(value)) {
            return value;
        }

        return null;
    }

    private boolean setOptionValue(Object optionInstance, Object value) {
        if (optionInstance == null || value == null) {
            return false;
        }

        for (Method method : optionInstance.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class) {
                continue;
            }

            if (!(method.getName().equals("set") || method.getName().equals("setValue")) || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!isAssignableForOptionSet(parameterType, value)) {
                continue;
            }

            try {
                method.invoke(optionInstance, value);
                Object newValue = readOptionValue(optionInstance);
                if (newValue != null && newValue.equals(value)) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (Method method : optionInstance.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!isAssignableForOptionSet(parameterType, value)) {
                continue;
            }

            try {
                method.invoke(optionInstance, value);
                Object newValue = readOptionValue(optionInstance);
                if (newValue != null && newValue.equals(value)) {
                    return true;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return setOptionValueByField(optionInstance, value);
    }

    private Object invokeOptionGetterByName(Object optionInstance, String... names) {
        for (String name : names) {
            try {
                Method method = optionInstance.getClass().getMethod(name);
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0) {
                    continue;
                }

                Object result = method.invoke(optionInstance);
                if (isSimpleOptionValue(result)) {
                    return result;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private Object invokeOptionGetterHeuristic(Object optionInstance) {
        for (Method method : optionInstance.getClass().getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }

            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            if (method.getName().equals("hashCode") || method.getName().equals("toString") || method.getName().equals("getClass")) {
                continue;
            }

            try {
                Object result = method.invoke(optionInstance);
                if (isSimpleOptionValue(result)) {
                    return result;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private Object readOptionValueFromFields(Object optionInstance) {
        Class<?> type = optionInstance.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(optionInstance);
                    if (isSimpleOptionValue(fieldValue)) {
                        return fieldValue;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }

        return null;
    }

    private boolean setOptionValueByField(Object optionInstance, Object value) {
        Class<?> type = optionInstance.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                    continue;
                }

                if (!isAssignableForOptionSet(field.getType(), value)) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    field.set(optionInstance, value);
                    Object newValue = readOptionValue(optionInstance);
                    if (newValue != null && newValue.equals(value)) {
                        return true;
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }

        return false;
    }

    private boolean isSimpleOptionValue(Object value) {
        return value instanceof Boolean
                || value instanceof Number
                || value instanceof CharSequence
                || (value != null && value.getClass().isEnum());
    }

    private boolean isAssignableForOptionSet(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }

        if (parameterType.isInstance(value)) {
            return true;
        }

        if (parameterType.isPrimitive()) {
            Class<?> boxed = boxType(parameterType);
            return boxed != null && boxed.isInstance(value);
        }

        return false;
    }

    private Class<?> boxType(Class<?> primitive) {
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == byte.class) {
            return Byte.class;
        }
        if (primitive == short.class) {
            return Short.class;
        }
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        if (primitive == char.class) {
            return Character.class;
        }
        return null;
    }

    private Object bestNoThrottleInactivityFpsValue(Object currentValue) {
        if (currentValue instanceof Number currentNumber) {
            Object target = readOptionValue(framerateLimitOption());
            if (target instanceof Number targetNumber && targetNumber.intValue() > currentNumber.intValue()) {
                return targetNumber;
            }
            return currentValue;
        }

        if (!(currentValue instanceof Enum<?> enumValue)) {
            return null;
        }

        Class<?> enumClass = enumValue.getDeclaringClass();
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) {
            return null;
        }

        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name().toLowerCase();
            if (name.contains("off") || name.contains("none") || name.contains("disable") || name.contains("unlimit") || name.contains("max")) {
                return constant;
            }
        }

        Object bestByName = null;
        for (Object constant : constants) {
            String name = ((Enum<?>) constant).name().toLowerCase();
            if (name.contains("afk") || name.contains("inactive") || name.contains("minimized") || name.contains("low") || name.contains("battery")) {
                continue;
            }
            bestByName = constant;
        }
        if (bestByName != null) {
            return bestByName;
        }

        int bestFps = Integer.MIN_VALUE;
        Object best = currentValue;
        for (Object constant : constants) {
            Integer fps = readInactivityFpsCandidate(constant);
            if (fps != null && fps > bestFps) {
                bestFps = fps;
                best = constant;
            }
        }

        return best;
    }

    private Integer readInactivityFpsCandidate(Object value) {
        if (value == null) {
            return null;
        }

        String[] methodCandidates = new String[] {"getValue", "getFpsLimit", "fpsLimit", "value"};
        for (String methodName : methodCandidates) {
            try {
                Method method = value.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0) {
                    continue;
                }

                Object result = method.invoke(value);
                if (result instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
    }

    private void restoreSelectedHotbarSlot() {
        if (client.player == null || originalSelectedHotbarSlot < 0) {
            return;
        }

        if (Inventory.isHotbarSlot(originalSelectedHotbarSlot)) {
            setSelectedHotbarSlot(client.player.getInventory(), originalSelectedHotbarSlot);
        }

        originalSelectedHotbarSlot = -1;
    }

    private int getSelectedHotbarSlot(Inventory inventory) {
        if (inventory == null) {
            return -1;
        }

        Field selectedField = resolveInventorySelectedSlotField(inventory);
        if (selectedField == null) {
            return -1;
        }

        try {
            int slot = selectedField.getInt(inventory);
            return Inventory.isHotbarSlot(slot) ? slot : -1;
        } catch (IllegalAccessException ignored) {
            return -1;
        }
    }

    private void setSelectedHotbarSlot(Inventory inventory, int slot) {
        if (inventory == null || !Inventory.isHotbarSlot(slot)) {
            return;
        }

        Field selectedField = resolveInventorySelectedSlotField(inventory);
        if (selectedField == null) {
            return;
        }

        try {
            selectedField.setInt(inventory, slot);
        } catch (IllegalAccessException ignored) {
        }
    }

    private Field resolveInventorySelectedSlotField(Inventory inventory) {
        if (inventorySelectedSlotFieldResolved) {
            return inventorySelectedSlotField;
        }

        inventorySelectedSlotFieldResolved = true;
        Class<?> inventoryClass = inventory.getClass();

        String[] candidateNames = new String[] {"selected", "selectedSlot", "field_7545"};
        for (String candidateName : candidateNames) {
            try {
                Field candidate = inventoryClass.getDeclaredField(candidateName);
                if (candidate.getType() == int.class) {
                    candidate.setAccessible(true);
                    inventorySelectedSlotField = candidate;
                    return candidate;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        for (Field field : inventoryClass.getDeclaredFields()) {
            if (field.getType() != int.class) {
                continue;
            }

            String name = field.getName().toLowerCase();
            if (name.contains("selected") || name.contains("slot")) {
                try {
                    field.setAccessible(true);
                    inventorySelectedSlotField = field;
                    return field;
                } catch (Exception ignored) {
                }
            }
        }

        return null;
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
        pausedForFocusLoss = false;
        fpsLimitOverrideActive = false;
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
