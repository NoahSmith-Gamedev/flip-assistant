package com.flipassistant;

/**
 * Average prices and trade volume over the last hour, from the wiki's /1h endpoint.
 * The averages can be null when nothing traded on that side during the hour.
 */
public final class HourPrice
{
	final Integer avgHighPrice;
	final long highPriceVolume;
	final Integer avgLowPrice;
	final long lowPriceVolume;

	public HourPrice(Integer avgHighPrice, long highPriceVolume, Integer avgLowPrice, long lowPriceVolume)
	{
		this.avgHighPrice = avgHighPrice;
		this.highPriceVolume = highPriceVolume;
		this.avgLowPrice = avgLowPrice;
		this.lowPriceVolume = lowPriceVolume;
	}
}
