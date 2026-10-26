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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
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

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	@Inject
	private FlipAssistantConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	private WikiPriceClient priceClient;
	private FlipAssistantPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refreshTask;
	private final AtomicBoolean refreshing = new AtomicBoolean(false);

	private volatile Map<Integer, ItemInfo> items = Collections.emptyMap();
	private volatile Map<Integer, LatestPrice> latest = Collections.emptyMap();
	private volatile Map<Integer, HourPrice> hourly = Collections.emptyMap();
	private volatile long mappingFetchedAt = 0;

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

		refreshTask = executor.scheduleWithFixedDelay(this::refresh, 0, REFRESH_SECONDS, TimeUnit.SECONDS);
		log.debug("Flip Assistant started");
	}

	@Override
	protected void shutDown()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		items = Collections.emptyMap();
		latest = Collections.emptyMap();
		hourly = Collections.emptyMap();
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
		if (FlipAssistantConfig.GROUP.equals(event.getGroup()))
		{
			// Settings changed: re-rank with the prices we already have, no need to re-download.
			executor.execute(this::recalculate);
		}
	}

	/** Called by the "Refresh now" button. */
	private void requestRefresh()
	{
		executor.execute(this::refresh);
	}

	private void refresh()
	{
		// Never run two downloads at the same time.
		if (!refreshing.compareAndSet(false, true))
		{
			return;
		}
		FlipAssistantPanel p = panel;
		try
		{
			if (items.isEmpty() || System.currentTimeMillis() - mappingFetchedAt > MAPPING_REFRESH_MILLIS)
			{
				if (p != null)
				{
					p.setStatus("Downloading item list...");
				}
				items = priceClient.fetchMapping();
				mappingFetchedAt = System.currentTimeMillis();
			}
			latest = priceClient.fetchLatest();
			hourly = priceClient.fetchHourly();
			recalculate();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Flip Assistant couldn't update prices", e);
			if (p != null)
			{
				p.setStatus("Couldn't reach the wiki, retrying in a minute");
			}
		}
		finally
		{
			refreshing.set(false);
		}
	}

	private void recalculate()
	{
		FlipAssistantPanel p = panel;
		if (p == null || items.isEmpty() || latest.isEmpty())
		{
			return;
		}

		FlipCalculator.Settings s = new FlipCalculator.Settings();
		s.cash = Math.max(0, config.cashStack());
		s.maxPerItem = Math.max(0, config.maxPerItem());
		s.minHourlyVolume = config.minHourlyVolume();
		s.maxPriceAgeSeconds = TimeUnit.MINUTES.toSeconds(config.maxPriceAgeMinutes());
		s.includeMembers = config.includeMembers();
		s.taxPercent = config.taxPercent();
		s.maxRoiPercent = config.maxRoiPercent();
		s.maxResults = config.maxSuggestions();

		long now = System.currentTimeMillis() / 1000;
		List<FlipSuggestion> suggestions = FlipCalculator.calculate(items, latest, hourly, now, s);

		String status = "Updated " + LocalTime.now().format(TIME_FORMAT)
			+ " · " + suggestions.size() + " flips from " + Format.gp(items.size()) + " items";
		p.showSuggestions(suggestions, status);
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
