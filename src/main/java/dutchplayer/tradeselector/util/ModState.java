package dutchplayer.tradeselector.util;

/**
 * Runtime state for the automation system
 */
public class ModState {
    private AutomationState currentState = AutomationState.IDLE;
    private int attemptCount = 0;
    private long startTime = 0;
    private String lastScanResult = "";
    private String errorMessage = "";
    
    public ModState() {}
    
    public AutomationState getCurrentState() {
        return currentState;
    }
    
    public void setCurrentState(AutomationState state) {
        this.currentState = state;
    }
    
    public int getAttemptCount() {
        return attemptCount;
    }
    
    public void incrementAttemptCount() {
        attemptCount++;
    }
    
    public void resetAttemptCount() {
        attemptCount = 0;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public String getLastScanResult() {
        return lastScanResult;
    }
    
    public void setLastScanResult(String result) {
        this.lastScanResult = result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String error) {
        this.errorMessage = error;
    }
    
    public void reset() {
        currentState = AutomationState.IDLE;
        attemptCount = 0;
        startTime = 0;
        lastScanResult = "";
        errorMessage = "";
    }
    
    public boolean isRunning() {
        return currentState != AutomationState.IDLE && 
               currentState != AutomationState.STOPPED && 
               currentState != AutomationState.ERROR;
    }
    
    public boolean hasError() {
        return currentState == AutomationState.ERROR;
    }
    
    /**
     * Gets the elapsed time in seconds since automation started
     */
    public long getElapsedSeconds() {
        if (startTime == 0) return 0;
        return (System.currentTimeMillis() - startTime) / 1000;
    }
    
    @Override
    public String toString() {
        return String.format("ModState{state=%s, attempts=%d, elapsed=%ds, error='%s'}", 
                           currentState, attemptCount, getElapsedSeconds(), errorMessage);
    }
    
    /**
     * Automation state enumeration
     */
    public enum AutomationState {
        IDLE("Idle"),
        BOUND("Ready"),
        BREAKING_JOB_BLOCK("Breaking Lectern"),
        PLACING_JOB_BLOCK("Placing Lectern"),
        WAITING_FOR_REFRESH("Waiting for Refresh"),
        SCANNING_TRADES("Scanning Trades"),
        FOUND_MATCH("Found Match!"),
        STOPPED("Stopped"),
        ERROR("Error");
        
        private final String displayName;
        
        AutomationState(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
