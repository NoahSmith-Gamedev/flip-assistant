package com.flipassistant;

/**
 * The most recent trades for one item, from the wiki's /latest endpoint.
 * "high" is the latest instant-buy price and "low" the latest instant-sell price.
 * Any field can be null if the wiki has no trade on that side.
 */
public final class LatestPrice
{
	final Long high;
	/** Unix time in seconds of the latest instant buy. */
	final Long highTime;
	final Long low;
	/** Unix time in seconds of the latest instant sell. */
	final Long lowTime;

	public LatestPrice(Long high, Long highTime, Long low, Long lowTime)
	{
		this.high = high;
		this.highTime = highTime;
		this.low = low;
		this.lowTime = lowTime;
	}
}
