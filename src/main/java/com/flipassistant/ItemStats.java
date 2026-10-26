package com.flipassistant;

/**
 * What the last day of hourly snapshots says about one item.
 * Built by {@link StatsCalculator}. Fields are null when there wasn't enough data.
 */
public final class ItemStats
{
	/** Number of hourly snapshots in which this item had a price. */
	final int hoursWithPrice;
	/** Average hour-to-hour price change, in %. */
	final Double swingPercent;
	/** Change from the start of the window to the latest hours, in % (negative = falling). */
	final Double trendPercent;
	final Stability stability;
	/** Low end of the day's typical instant-sell prices (25th percentile of hourly averages). */
	final Long dayLowPrice;
	/** High end of the day's typical instant-buy prices (75th percentile of hourly averages). */
	final Long dayHighPrice;
	/** Average instant sells per hour over the window, which is what fills your buy offers. */
	final double avgBuyFillsPerHour;
	/** Average instant buys per hour over the window, which is what fills your sell offers. */
	final double avgSellFillsPerHour;

	public ItemStats(int hoursWithPrice, Double swingPercent, Double trendPercent, Stability stability,
		Long dayLowPrice, Long dayHighPrice, double avgBuyFillsPerHour, double avgSellFillsPerHour)
	{
		this.hoursWithPrice = hoursWithPrice;
		this.swingPercent = swingPercent;
		this.trendPercent = trendPercent;
		this.stability = stability;
		this.dayLowPrice = dayLowPrice;
		this.dayHighPrice = dayHighPrice;
		this.avgBuyFillsPerHour = avgBuyFillsPerHour;
		this.avgSellFillsPerHour = avgSellFillsPerHour;
	}
}
