package com.flipassistant;

import java.text.NumberFormat;
import java.util.Locale;

/** Number formatting helpers for the panel. */
final class Format
{
	private Format()
	{
	}

	/** 1234567 -> "1,234,567" */
	static String gp(long value)
	{
		return NumberFormat.getIntegerInstance(Locale.US).format(value);
	}

	/** 1234567 -> "1.23M", 45600 -> "45.6K", 999 -> "999" */
	static String short_(long value)
	{
		long abs = Math.abs(value);
		if (abs >= 1_000_000_000L)
		{
			return trim(value / 1_000_000_000.0) + "B";
		}
		if (abs >= 1_000_000L)
		{
			return trim(value / 1_000_000.0) + "M";
		}
		if (abs >= 10_000L)
		{
			return trim(value / 1_000.0) + "K";
		}
		return gp(value);
	}

	private static String trim(double v)
	{
		String s = String.format(Locale.US, "%.2f", v);
		if (s.contains("."))
		{
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s;
	}
}
