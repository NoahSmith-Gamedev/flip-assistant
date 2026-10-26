package com.flipassistant;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;

/**
 * Flip Assistant: suggests Grand Exchange flips using the OSRS Wiki's real-time prices.
 *
 * Rules this plugin follows on purpose:
 * - It only displays information. It never clicks, types, or enters prices into the game.
 * - It only downloads public price data from the wiki and never sends player data anywhere.
 * - It uses plain Java and the libraries that ship with RuneLite (no reflection, no extra dependencies).
 */
@Slf4j
@PluginDescriptor(
	name = "Flip Assistant",
	description = "Suggests Grand Exchange flips using OSRS Wiki real-time prices",
	tags = {"flipping", "grand exchange", "ge", "merching", "prices"}
)
public class FlipAssistantPlugin extends Plugin
{
	/** How often to download fresh prices. The wiki updates every minute or so; please don't go faster. */
	private static final long REFRESH_SECONDS = 60;
	/** Item names and buy limits barely change, so re-download them once a day. */
	private static final long MAPPING_REFRESH_MILLIS = TimeUnit.HOURS.toMillis(24);
	/** How many past hours of snapshots to keep for the stability meter. */
	static final int HISTORY_HOURS = 24;
	/** Pause between history downloads so we never hammer the wiki. */
	private static final long HISTORY_REQUEST_GAP_MILLIS = 1500;
	/** If the wiki has no data for an hour yet, wait this long before asking again. */
	private static final long EMPTY_HOUR_RETRY_SECONDS = 600;

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	@Inject
	private FlipAssistantConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	/**
	 * Our own background thread for downloads and math, so slow network calls never hold up
	 * RuneLite's shared threads. Everything that touches the data below runs on this thread.
	 */
	private ScheduledExecutorService worker;

	private WikiPriceClient priceClient;
	private FlipAssistantPanel panel;
	private NavigationButton navButton;
	private final AtomicBoolean refreshing = new AtomicBoolean(false);

	private Map<Integer, ItemInfo> items = Collections.emptyMap();
	private Map<Integer, LatestPrice> latest = Collections.emptyMap();
	private Map<Integer, HourPrice> lastHour = Collections.emptyMap();
	/** Hourly snapshots keyed by the hour's start time (Unix seconds). */
	private final TreeMap<Long, Map<Integer, HourPrice>> history = new TreeMap<>();
	/** Hours the wiki returned no data for, and when we last asked. */
	private final TreeMap<Long, Long> emptyHourAttempts = new TreeMap<>();
	private Map<Integer, ItemStats> stats = Collections.emptyMap();
	private long mappingFetchedAt = 0;

	@Override
	protected void startUp()
	{
		priceClient = new WikiPriceClient(okHttpClient, gson);
		panel = new FlipAssistantPanel(this::requestRefresh);

		navButton = NavigationButton.builder()
			.tooltip("Flip Assistant")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		worker = Executors.newSingleThreadScheduledExecutor();
		worker.scheduleWithFixedDelay(this::refresh, 0, REFRESH_SECONDS, TimeUnit.SECONDS);
		log.debug("Flip Assistant started");
	}

	@Override
	protected void shutDown()
	{
		if (worker != null)
		{
			worker.shutdownNow();
			worker = null;
		}
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		items = Collections.emptyMap();
		latest = Collections.emptyMap();
		lastHour = Collections.emptyMap();
		history.clear();
		emptyHourAttempts.clear();
		stats = Collections.emptyMap();
		mappingFetchedAt = 0;
		log.debug("Flip Assistant stopped");
	}

	@Provides
	FlipAssistantConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlipAssistantConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		ScheduledExecutorService w = worker;
		if (FlipAssistantConfig.GROUP.equals(event.getGroup()) && w != null)
		{
			// Settings changed: re-rank with the prices we already have, no need to re-download.
			w.execute(this::recalculate);
		}
	}

	/** Called by the "Refresh now" button. */
	private void requestRefresh()
	{
		ScheduledExecutorService w = worker;
		if (w != null)
		{
			w.execute(this::refresh);
		}
	}

	private void refresh()
	{
		// Never run two downloads at the same time.
		if (!refreshing.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			if (items.isEmpty() || System.currentTimeMillis() - mappingFetchedAt > MAPPING_REFRESH_MILLIS)
			{
				status("Downloading item list...");
				items = priceClient.fetchMapping();
				mappingFetchedAt = System.currentTimeMillis();
			}
			latest = priceClient.fetchLatest();
			lastHour = priceClient.fetchHourly();
			// Show suggestions straight away, then improve them once history is in.
			recalculate();
			if (updateHistory())
			{
				recalculate();
			}
		}
		catch (InterruptedException e)
		{
			// Plugin is shutting down.
			Thread.currentThread().interrupt();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Flip Assistant couldn't update prices", e);
			status("Couldn't reach the wiki, retrying in a minute");
		}
		finally
		{
			refreshing.set(false);
		}
	}

	/**
	 * Downloads any missing hourly snapshots from the last day, one at a time with a pause in between.
	 * The first run takes about half a minute; after that it's usually one download per hour.
	 *
	 * @return true if anything changed
	 */
	private boolean updateHistory() throws IOException, InterruptedException
	{
		long nowSeconds = System.currentTimeMillis() / 1000;
		long currentHourStart = nowSeconds / 3600 * 3600;
		long oldest = currentHourStart - 3600L * HISTORY_HOURS;
		boolean changed = history.headMap(oldest).size() > 0;
		history.headMap(oldest).clear();
		emptyHourAttempts.headMap(oldest).clear();

		boolean first = true;
		for (int k = 1; k <= HISTORY_HOURS; k++)
		{
			long hourStart = currentHourStart - 3600L * k;
			if (history.containsKey(hourStart))
			{
				continue;
			}
			// If the wiki had nothing for this hour recently, don't keep asking every minute.
			Long lastTry = emptyHourAttempts.get(hourStart);
			if (lastTry != null && nowSeconds - lastTry < EMPTY_HOUR_RETRY_SECONDS)
			{
				continue;
			}
			if (!first)
			{
				Thread.sleep(HISTORY_REQUEST_GAP_MILLIS);
			}
			first = false;

			Map<Integer, HourPrice> snapshot = priceClient.fetchHourly(hourStart);
			if (!snapshot.isEmpty())
			{
				history.put(hourStart, snapshot);
				emptyHourAttempts.remove(hourStart);
				changed = true;
			}
			else
			{
				emptyHourAttempts.put(hourStart, nowSeconds);
			}
			showHistoryProgress();
		}

		if (changed)
		{
			stats = StatsCalculator.compute(new ArrayList<>(history.values()));
		}
		showHistoryProgress();
		return changed;
	}

	private void recalculate()
	{
		FlipAssistantPanel p = panel;
		if (p == null || items.isEmpty() || latest.isEmpty())
		{
			return;
		}

		boolean overnight = config.overnightMode();
		FlipCalculator.Settings s = new FlipCalculator.Settings();
		s.cash = Math.max(0, config.cashStack());
		s.maxPerItem = Math.max(0, config.maxPerItem());
		s.minFillsPerHour = config.minHourlyVolume();
		s.maxPriceAgeSeconds = TimeUnit.MINUTES.toSeconds(config.maxPriceAgeMinutes());
		s.includeMembers = config.includeMembers();
		s.taxPercent = config.taxPercent();
		s.maxRoiPercent = config.maxRoiPercent();
		s.maxResults = config.maxSuggestions();
		s.marketSharePercent = config.marketSharePercent();
		s.maxFillMinutes = config.maxFillMinutes();
		s.priceBoostPercent = config.priceBoostPercent();
		s.stabilityFilter = config.stabilityFilter();
		s.skipFalling = config.skipFalling();
		s.overnight = overnight;
		s.overnightHours = config.overnightHours();

		long now = System.currentTimeMillis() / 1000;
		List<FlipSuggestion> suggestions = FlipCalculator.calculate(items, latest, lastHour, stats, now, s);

		String status = "Updated " + LocalTime.now().format(TIME_FORMAT)
			+ " · " + suggestions.size() + " flips from " + Format.gp(items.size()) + " items";
		p.showSuggestions(suggestions, status, overnight);
	}

	private void status(String text)
	{
		FlipAssistantPanel p = panel;
		if (p != null)
		{
			p.setStatus(text);
		}
	}

	private void showHistoryProgress()
	{
		FlipAssistantPanel p = panel;
		if (p != null)
		{
			p.setHistoryStatus(history.size(), HISTORY_HOURS);
		}
	}

	/** Draws a simple gold coin icon so the plugin doesn't need an image file. */
	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(226, 180, 40));
		g.fillOval(1, 1, 14, 14);
		g.setColor(new Color(150, 110, 20));
		g.setStroke(new BasicStroke(1.2f));
		g.drawOval(1, 1, 14, 14);
		// Small up-arrow for "profit"
		g.setColor(new Color(60, 40, 0));
		g.drawLine(8, 4, 8, 12);
		g.drawLine(8, 4, 5, 7);
		g.drawLine(8, 4, 11, 7);
		g.dispose();
		return image;
	}
}
