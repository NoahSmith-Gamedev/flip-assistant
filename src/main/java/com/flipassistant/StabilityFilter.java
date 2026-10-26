package com.flipassistant;

/** Which stability levels the user is willing to flip. Shown as a dropdown in the settings. */
public enum StabilityFilter
{
	ANY("Any"),
	MODERATE_OR_BETTER("Moderate or better"),
	STABLE_ONLY("Stable only");

	private final String label;

	StabilityFilter(String label)
	{
		this.label = label;
	}

	/** Items with no history yet are allowed unless the user wants stable only. */
	boolean allows(Stability s)
	{
		switch (this)
		{
			case STABLE_ONLY:
				return s == Stability.STABLE;
			case MODERATE_OR_BETTER:
				return s != Stability.VOLATILE;
			default:
				return true;
		}
	}

	@Override
	public String toString()
	{
		return label;
	}
}
