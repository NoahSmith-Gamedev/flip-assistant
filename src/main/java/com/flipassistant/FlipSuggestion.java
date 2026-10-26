package com.flipassistant;

/**
 * One suggested flip: what to buy, at what price, how many, and what it should earn.
 */
public final class FlipSuggestion
{
	final int itemId;
	final String name;
	final int buyPrice;
	final int sellPrice;
	/** GE tax paid on each item when it sells. */
	final int taxEach;
	/** Profit per item after tax. */
	final int marginEach;
	final int quantity;
	final long totalProfit;
	final long hourlyVolume;
	/** GE buy limit, or null if unknown. */
	final Integer limit;

	public FlipSuggestion(int itemId, String name, int buyPrice, int sellPrice, int taxEach, int marginEach,
		int quantity, long totalProfit, long hourlyVolume, Integer limit)
	{
		this.itemId = itemId;
		this.name = name;
		this.buyPrice = buyPrice;
		this.sellPrice = sellPrice;
		this.taxEach = taxEach;
		this.marginEach = marginEach;
		this.quantity = quantity;
		this.totalProfit = totalProfit;
		this.hourlyVolume = hourlyVolume;
		this.limit = limit;
	}

	/** Gold you need to place the buy offer. */
	long investment()
	{
		return (long) buyPrice * quantity;
	}
}
