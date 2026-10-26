package com.flipassistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns raw wiki price data into a ranked list of flip suggestions.
 *
 * This class has no RuneLite or network code in it, so the math can be tested on its own.
 */
public final class FlipCalculator
{
	/** Jagex caps the GE tax at 5M per item. */
	static final long TAX_CAP = 5_000_000L;

	/**
	 * How far (as a fraction) the latest price may drift from the 1-hour average before
	 * we treat it as a spike and skip the item. 0.15 means 15%.
	 */
	static final double MAX_DRIFT_FROM_AVERAGE = 0.15;

	/** The user's settings, copied out of the RuneLite config. */
	public static final class Settings
	{
		long cash;
		long maxPerItem;
		long minHourlyVolume;
		long maxPriceAgeSeconds;
		boolean includeMembers;
		int taxPercent;
		int maxRoiPercent;
		int maxResults;
	}

	private FlipCalculator()
	{
	}

	/** GE tax on selling one item at this price, rounded down and capped. */
	static int taxFor(int sellPrice, int taxPercent)
	{
		long tax = (long) sellPrice * taxPercent / 100;
		return (int) Math.min(tax, TAX_CAP);
	}

	public static List<FlipSuggestion> calculate(
		Map<Integer, ItemInfo> items,
		Map<Integer, LatestPrice> latest,
		Map<Integer, HourPrice> hourly,
		long nowSeconds,
		Settings s)
	{
		List<FlipSuggestion> results = new ArrayList<>();
		long budget = Math.min(s.cash, s.maxPerItem);
		if (budget <= 0)
		{
			return results;
		}

		for (ItemInfo item : items.values())
		{
			if (!s.includeMembers && item.members)
			{
				continue;
			}

			LatestPrice lp = latest.get(item.id);
			HourPrice hp = hourly.get(item.id);
			if (lp == null || hp == null
				|| lp.high == null || lp.low == null || lp.highTime == null || lp.lowTime == null
				|| hp.avgHighPrice == null || hp.avgLowPrice == null)
			{
				continue;
			}

			// Both sides must have traded recently, or the prices are stale.
			if (nowSeconds - lp.highTime > s.maxPriceAgeSeconds || nowSeconds - lp.lowTime > s.maxPriceAgeSeconds)
			{
				continue;
			}

			// Skip items whose latest prices are far from their hourly average (likely a spike or manipulation).
			if (drift(lp.low, hp.avgLowPrice) > MAX_DRIFT_FROM_AVERAGE || drift(lp.high, hp.avgHighPrice) > MAX_DRIFT_FROM_AVERAGE)
			{
				continue;
			}

			// Bid 1gp above the latest instant-sell price and ask 1gp below the latest instant-buy price,
			// so our offers sit at the front of the queue.
			int buy = lp.low + 1;
			int sell = lp.high - 1;
			if (sell <= buy)
			{
				continue;
			}

			int tax = taxFor(sell, s.taxPercent);
			int margin = sell - tax - buy;
			if (margin <= 0)
			{
				continue;
			}

			// Very large margins are usually traps: thin markets or manipulated prices.
			if ((double) margin / buy * 100.0 > s.maxRoiPercent)
			{
				continue;
			}

			long hourlyVolume = hp.highPriceVolume + hp.lowPriceVolume;
			if (hourlyVolume < s.minHourlyVolume)
			{
				continue;
			}

			// Quantity is the smallest of: the buy limit, what you can afford,
			// and about one hour of trading on the slower side of the market (so offers actually fill).
			long qty = budget / buy;
			if (item.limit != null)
			{
				qty = Math.min(qty, item.limit);
			}
			qty = Math.min(qty, Math.min(hp.highPriceVolume, hp.lowPriceVolume));
			if (qty < 1)
			{
				continue;
			}

			long total = (long) margin * qty;
			results.add(new FlipSuggestion(item.id, item.name, buy, sell, tax, margin, (int) qty, total, hourlyVolume, item.limit));
		}

		results.sort((a, b) -> Long.compare(b.totalProfit, a.totalProfit));
		if (results.size() > s.maxResults)
		{
			return new ArrayList<>(results.subList(0, s.maxResults));
		}
		return results;
	}

	/** How far a price is from its average, as a fraction (0.1 = 10%). */
	private static double drift(int price, int average)
	{
		if (average <= 0)
		{
			return Double.MAX_VALUE;
		}
		return Math.abs(price - average) / (double) average;
	}
}
