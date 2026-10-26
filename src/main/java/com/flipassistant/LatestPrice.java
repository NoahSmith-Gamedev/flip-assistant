package com.flipassistant;

/**
 * The most recent trades for one item, from the wiki's /latest endpoint.
 * "high" is the latest instant-buy price and "low" the latest instant-sell price.
 * Any field can be null if the wiki has no recent trade on that side.
 */
public final class LatestPrice
{
	final Integer high;
	/** Unix time in seconds of the latest instant buy. */
	final Long highTime;
	final Integer low;
	/** Unix time in seconds of the latest instant sell. */
	final Long lowTime;

	public LatestPrice(Integer high, Long highTime, Integer low, Long lowTime)
	{
		this.high = high;
		this.highTime = highTime;
		this.low = low;
		this.lowTime = lowTime;
	}
}
