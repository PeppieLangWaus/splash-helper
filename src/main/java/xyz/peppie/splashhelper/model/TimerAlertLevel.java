package xyz.peppie.splashhelper.model;

/**
 * Alert level of the splash timer, derived from the remaining time and the
 * configured warning/critical thresholds. Drives both the timer overlay text
 * color and the visual notification tint so they always change together.
 */
public enum TimerAlertLevel
{
	/** No timer running. */
	NONE,
	/** Timer running with more than the warning threshold remaining. */
	NORMAL,
	/** At or below the warning threshold (orange). */
	WARNING,
	/** At or below the critical threshold (red). */
	CRITICAL,
	/** Timer has expired. */
	EXPIRED
}
