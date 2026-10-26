package com.flipassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out stability, trend, the day's price range and average volumes from hourly snapshots.
 * Pure math with no RuneLite or network code, so it can be tested on its own.
 */
public final class StatsCalculator
{
	/** Need at least this many hours with a price before judging stability or trend. */
	static final int MIN_HOURS = 6;
	/** How many hours at each end of the window are averaged when measuring the trend. */
	static final int TREND_HOURS = 3;

	private StatsCalculator()
	{
	}

	/**
	 * @param snapshots hourly snapshots, oldest first. An item missing from a snapshot had no trades that hour.
	 */
	public static Map<Integer, ItemStats> compute(List<Map<Integer, HourPrice>> snapshots)
	{
		Map<Integer, ItemStats> result = new HashMap<>();
		int hours = snapshots.size();
		if (hours == 0)
		{
			return result;
		}

		Set<Integer> ids = new HashSet<>();
		for (Map<Integer, HourPrice> snap : snapshots)
		{
			ids.addAll(snap.keySet());
		}

		for (int id : ids)
		{
			List<Double> mids = new ArrayList<>();
			List<Long> lows = new ArrayList<>();
			List<Long> highs = new ArrayList<>();
			long buyFills = 0;
			long sellFills = 0;
			Double previousMid = null;
			double swingTotal = 0;
			int swingCount = 0;

			for (Map<Integer, HourPrice> snap : snapshots)
			{
				HourPrice hp = snap.get(id);
				if (hp == null)
				{
					// No trades this hour; breaks the hour-to-hour chain.
					previousMid = null;
					continue;
				}
				buyFills += hp.lowPriceVolume;
				sellFills += hp.highPriceVolume;
				if (hp.avgLowPrice != null)
				{
					lows.add(hp.avgLowPrice);
				}
				if (hp.avgHighPrice != null)
				{
					highs.add(hp.avgHighPrice);
				}

				Double mid = mid(hp);
				if (mid == null)
				{
					previousMid = null;
					continue;
				}
				mids.add(mid);
				if (previousMid != null && previousMid > 0)
				{
					swingTotal += Math.abs(mid - previousMid) / previousMid * 100.0;
					swingCount++;
				}
				previousMid = mid;
			}

			Double swing = null;
			Double trend = null;
			Stability stability = Stability.UNKNOWN;
			if (mids.size() >= MIN_HOURS && swingCount > 0)
			{
				swing = swingTotal / swingCount;
				stability = Stability.fromSwing(swing);

				double oldAvg = average(mids.subList(0, TREND_HOURS));
				double newAvg = average(mids.subList(mids.size() - TREND_HOURS, mids.size()));
				if (oldAvg > 0)
				{
					trend = (newAvg - oldAvg) / oldAvg * 100.0;
				}
			}

			Long dayLow = lows.size() >= MIN_HOURS ? percentile(lows, 0.25) : null;
			Long dayHigh = highs.size() >= MIN_HOURS ? percentile(highs, 0.75) : null;

			result.put(id, new ItemStats(mids.size(), swing, trend, stability, dayLow, dayHigh,
				(double) buyFills / hours, (double) sellFills / hours));
		}
		return result;
	}

	/** Middle of the hour's average buy and sell prices, or whichever side traded. */
	private static Double mid(HourPrice hp)
	{
		if (hp.avgHighPrice != null && hp.avgLowPrice != null)
		{
			return (hp.avgHighPrice + hp.avgLowPrice) / 2.0;
		}
		if (hp.avgHighPrice != null)
		{
			return hp.avgHighPrice.doubleValue();
		}
		if (hp.avgLowPrice != null)
		{
			return hp.avgLowPrice.doubleValue();
		}
		return null;
	}

	private static double average(List<Double> values)
	{
		double total = 0;
		for (double v : values)
		{
			total += v;
		}
		return values.isEmpty() ? 0 : total / values.size();
	}

	/** Nearest-rank percentile, e.g. 0.25 for the 25th percentile. */
	static long percentile(List<Long> values, double fraction)
	{
		List<Long> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int index = (int) Math.ceil(fraction * sorted.size()) - 1;
		index = Math.max(0, Math.min(sorted.size() - 1, index));
		return sorted.get(index);
	}
}
