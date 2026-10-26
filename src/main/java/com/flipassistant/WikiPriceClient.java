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
 * Downloads price data from the OSRS Wiki real-time prices API.
 * See https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices
 *
 * This only ever downloads public price data. It never sends anything about the player.
 */
public class WikiPriceClient
{
	private static final String BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";

	/**
	 * The wiki asks every tool to identify itself. Replace the contact part with your own
	 * Discord name (or remove it) so the wiki team can reach you if something goes wrong.
	 */
	private static final String USER_AGENT = "Flip Assistant RuneLite plugin - contact: thetanburrito";

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
			Integer id = optInt(o, "id");
			JsonElement name = o.get("name");
			if (id == null || name == null || name.isJsonNull())
			{
				continue;
			}
			JsonElement members = o.get("members");
			boolean isMembers = members != null && !members.isJsonNull() && members.getAsBoolean();
			items.put(id, new ItemInfo(id, name.getAsString(), optInt(o, "limit"), isMembers));
		}
		return items;
	}

	/** Latest instant-buy and instant-sell price for every item. */
	public Map<Integer, LatestPrice> fetchLatest() throws IOException
	{
		JsonObject data = get("/latest").getAsJsonObject().getAsJsonObject("data");
		Map<Integer, LatestPrice> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			int id;
			try
			{
				id = Integer.parseInt(entry.getKey());
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			JsonObject o = entry.getValue().getAsJsonObject();
			prices.put(id, new LatestPrice(optInt(o, "high"), optLong(o, "highTime"), optInt(o, "low"), optLong(o, "lowTime")));
		}
		return prices;
	}

	/** Average prices and volumes over the last hour for every item. */
	public Map<Integer, HourPrice> fetchHourly() throws IOException
	{
		JsonObject data = get("/1h").getAsJsonObject().getAsJsonObject("data");
		Map<Integer, HourPrice> prices = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			int id;
			try
			{
				id = Integer.parseInt(entry.getKey());
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			JsonObject o = entry.getValue().getAsJsonObject();
			Long highVol = optLong(o, "highPriceVolume");
			Long lowVol = optLong(o, "lowPriceVolume");
			prices.put(id, new HourPrice(
				optInt(o, "avgHighPrice"), highVol == null ? 0 : highVol,
				optInt(o, "avgLowPrice"), lowVol == null ? 0 : lowVol));
		}
		return prices;
	}

	private JsonElement get(String path) throws IOException
	{
		Request request = new Request.Builder()
			.url(BASE_URL + path)
			.header("User-Agent", USER_AGENT)
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

	/** Reads a whole number that may be missing, null, or written with decimals. */
	private static Integer optInt(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		return (int) Math.round(e.getAsDouble());
	}

	private static Long optLong(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		return Math.round(e.getAsDouble());
	}
}
