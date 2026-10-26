package com.flipassistant;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads price data from the OSRS Wiki real-time prices API (v2).
 * See https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices
 *
 * This only ever downloads public price data. It never sends anything about the player.
 */
public class WikiPriceClient
{
	private static final String BASE_URL = "https://prices.runescape.wiki/api/v2/osrs";

	private final OkHttpClient httpClient;
	private final Gson gson;

	public WikiPriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/** Item names, IDs and buy limits. Changes rarely, so we fetch it about once a day. */
	public Map<Integer, ItemInfo> fetchMapping() throws IOException
	{
		JsonArray array = get("/mapping").getAsJsonArray();
		Map<Integer, ItemInfo> items = new HashMap<>();
		for (JsonElement element : array)
		{
			JsonObject o = element.getAsJsonObject();
			Long id = optLong(o, "id");
			JsonElement name = o.get("name");
			if (id == null || name == null || name.isJsonNull())
			{
				continue;
			}
			Long limit = optLong(o, "limit");
			JsonElement members = o.get("members");
			boolean isMembers = members != null && !members.isJsonNull() && members.getAsBoolean();
			items.put(id.intValue(), new ItemInfo(id.intValue(), name.getAsString(),
				limit == null ? null : (int) Math.min(limit, Integer.MAX_VALUE), isMembers));
		}
		return items;
	}

	/** Latest instant-buy and instant-sell price for every item. */
	public Map<Integer, LatestPrice> fetchLatest() throws IOException
	{
		JsonObject data = dataOf(get("/latest"));
		Map<Integer, LatestPrice> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			Integer id = parseId(entry.getKey());
			if (id == null || !entry.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject o = entry.getValue().getAsJsonObject();
			prices.put(id, new LatestPrice(optLong(o, "high"), optLong(o, "highTime"), optLong(o, "low"), optLong(o, "lowTime")));
		}
		return prices;
	}

	/** Average prices and volumes over the most recent hour for every item. */
	public Map<Integer, HourPrice> fetchHourly() throws IOException
	{
		return parseHourly(get("/1h"));
	}

	/**
	 * Average prices and volumes for the hour starting at {@code hourStart} (Unix seconds, on the hour).
	 * Returns an empty map if the wiki hasn't published that hour yet.
	 */
	public Map<Integer, HourPrice> fetchHourly(long hourStart) throws IOException
	{
		return parseHourly(get("/1h?timestamp=" + hourStart));
	}

	private Map<Integer, HourPrice> parseHourly(JsonElement json)
	{
		JsonObject data = dataOf(json);
		Map<Integer, HourPrice> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			Integer id = parseId(entry.getKey());
			if (id == null || !entry.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject o = entry.getValue().getAsJsonObject();
			Long highVol = optLong(o, "highPriceVolume");
			Long lowVol = optLong(o, "lowPriceVolume");
			prices.put(id, new HourPrice(
				optLong(o, "avgHighPrice"), highVol == null ? 0 : highVol,
				optLong(o, "avgLowPrice"), lowVol == null ? 0 : lowVol));
		}
		return prices;
	}

	private JsonElement get(String path) throws IOException
	{
		Request request = new Request.Builder()
			.url(BASE_URL + path)
			.header("User-Agent", WikiContact.USER_AGENT)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("Wiki returned HTTP " + response.code() + " for " + path);
			}
			ResponseBody body = response.body();
			if (body == null)
			{
				throw new IOException("Wiki returned an empty response for " + path);
			}
			JsonElement json = gson.fromJson(body.charStream(), JsonElement.class);
			if (json == null || json.isJsonNull())
			{
				throw new IOException("Wiki returned no data for " + path);
			}
			return json;
		}
	}

	private static JsonObject dataOf(JsonElement json)
	{
		if (json.isJsonObject())
		{
			JsonElement data = json.getAsJsonObject().get("data");
			if (data != null && data.isJsonObject())
			{
				return data.getAsJsonObject();
			}
		}
		return new JsonObject();
	}

	private static Integer parseId(String key)
	{
		try
		{
			return Integer.parseInt(key);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/** Reads a number that may be missing, null, or have decimals (v2 averages do), rounded to a whole number. */
	private static Long optLong(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		try
		{
			return Math.round(e.getAsDouble());
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}
}
