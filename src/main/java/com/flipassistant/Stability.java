package com.flipassistant;

/** How steady an item's price has been over the last day. */
public enum Stability
{
	STABLE("Stable"),
	MODERATE("Moderate"),
	VOLATILE("Volatile"),
	/** Not enough price history yet to judge. */
	UNKNOWN("?");

	final String label;

	Stability(String label)
	{
		this.label = label;
	}

	/** Typical hour-to-hour price move at or below this % counts as Stable. */
	static final double STABLE_MAX_SWING = 1.5;
	/** Typical hour-to-hour price move at or below this % counts as Moderate; above is Volatile. */
	static final double MODERATE_MAX_SWING = 3.0;

	static Stability fromSwing(double swingPercent)
	{
		if (swingPercent <= STABLE_MAX_SWING)
		{
			return STABLE;
		}
		if (swingPercent <= MODERATE_MAX_SWING)
		{
			return MODERATE;
		}
		return VOLATILE;
	}
}
