package com.flipassistant;

/**
 * One suggested flip: what to buy, at what price, how many, and what it should earn.
 */
public final class FlipSuggestion
{
	final int itemId;
	final String name;
	final long buyPrice;
	final long sellPrice;
	/** GE tax paid on each item when it sells. */
	final long taxEach;
	/** Profit per item after tax. */
	final long marginEach;
	final int quantity;
	final long totalProfit;
	/** Expected profit per hour of waiting (buy time + sell time). */
	final long profitPerHour;
	/** Estimated minutes for the buy offer to fill, assuming your share of the market. */
	final double estBuyMinutes;
	/** Estimated minutes for the sell offer to fill. */
	final double estSellMinutes;
	/** Your buy quantity as a % of the market's instant sells per hour (how much you'd move the market). */
	final double marketImpactPercent;
	/** Instant sells per hour used for the estimate (what fills buy offers). */
	final double buyFillsPerHour;
	/** Instant buys per hour used for the estimate (what fills sell offers). */
	final double sellFillsPerHour;
	/** GE buy limit, or null if unknown. */
	final Integer limit;
	final Stability stability;
	/** 24h price trend in %, or null if not enough history. */
	final Double trendPercent;

	public FlipSuggestion(int itemId, String name, long buyPrice, long sellPrice, long taxEach, long marginEach,
		int quantity, long totalProfit, long profitPerHour, double estBuyMinutes, double estSellMinutes,
		double marketImpactPercent, double buyFillsPerHour, double sellFillsPerHour, Integer limit,
		Stability stability, Double trendPercent)
	{
		this.itemId = itemId;
		this.name = name;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.taxEach = taxEach;
		this.marginEach = marginEach;
		this.quantity = quantity;
		this.totalProfit = totalProfit;
		this.profitPerHour = profitPerHour;
		this.estBuyMinutes = estBuyMinutes;
		this.estSellMinutes = estSellMinutes;
		this.marketImpactPercent = marketImpactPercent;
		this.buyFillsPerHour = buyFillsPerHour;
		this.sellFillsPerHour = sellFillsPerHour;
		this.limit = limit;
		this.stability = stability;
		this.trendPercent = trendPercent;
	}

	/** Gold you need to place the buy offer. */
	long investment()
	{
		return buyPrice * quantity;
	}
}
