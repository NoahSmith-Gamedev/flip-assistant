package com.flipassistant;

/**
 * Average prices and trade volume for one item over one hour, from the wiki's /1h endpoint.
 *
 * "High" trades are instant buys (other players buying), which is what fills YOUR sell offers.
 * "Low" trades are instant sells (other players selling), which is what fills YOUR buy offers.
 * The averages can be null when nothing traded on that side during the hour.
 */
public final class HourPrice
{
	final Long avgHighPrice;
	final long highPriceVolume;
	final Long avgLowPrice;
	final long lowPriceVolume;

	public HourPrice(Long avgHighPrice, long highPriceVolume, Long avgLowPrice, long lowPriceVolume)
	{
		this.avgHighPrice = avgHighPrice;
		this.highPriceVolume = highPriceVolume;
		this.avgLowPrice = avgLowPrice;
		this.lowPriceVolume = lowPriceVolume;
	}
}
