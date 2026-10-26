package com.flipassistant;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar panel. It only displays information; it never interacts with the game.
 */
class FlipAssistantPanel extends PluginPanel
{
	static final Color GOOD = new Color(90, 200, 90);
	static final Color WARN = new Color(230, 170, 50);
	static final Color BAD = new Color(220, 80, 70);

	/** Above this % of the market's hourly trades, your order is big enough to move the price noticeably. */
	private static final double HIGH_IMPACT_PERCENT = 25.0;

	private final JLabel statusLabel = new JLabel("Loading prices...");
	private final JLabel modeLabel = new JLabel(" ");
	private final JLabel historyLabel = new JLabel(" ");
	private final JPanel listPanel = new JPanel();

	FlipAssistantPanel(Runnable onRefresh)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new GridLayout(0, 1, 0, 3));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Flip Assistant");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title);

		for (JLabel l : new JLabel[]{modeLabel, statusLabel, historyLabel})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			header.add(l);
		}

		JButton refresh = new JButton("Refresh now");
		refresh.setFocusable(false);
		refresh.addActionListener(e -> onRefresh.run());
		header.add(refresh);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listPanel.setBorder(new EmptyBorder(8, 0, 0, 0));

		add(header, BorderLayout.NORTH);
		add(listPanel, BorderLayout.CENTER);
	}

	void setStatus(String text)
	{
		SwingUtilities.invokeLater(() -> statusLabel.setText(text));
	}

	void setHistoryStatus(int hours, int target)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (hours >= target)
			{
				historyLabel.setText("Price history: last " + hours + " hours");
				historyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
			else
			{
				historyLabel.setText("Loading price history: " + hours + "/" + target + " hours");
				historyLabel.setForeground(WARN);
			}
		});
	}

	void showSuggestions(List<FlipSuggestion> suggestions, String status, boolean overnight)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(status);
			modeLabel.setText(overnight ? "Mode: Overnight (patient prices)" : "Mode: Active flipping");
			modeLabel.setForeground(overnight ? WARN : ColorScheme.LIGHT_GRAY_COLOR);
			listPanel.removeAll();

			if (overnight)
			{
				listPanel.add(note("Game updates usually land Wednesday mornings (UK time) and can move prices. "
					+ "Be careful with overnight offers on Tuesday nights.", WARN));
				listPanel.add(Box.createVerticalStrut(6));
			}

			if (suggestions.isEmpty())
			{
				listPanel.add(note(overnight
					? "No items are stable enough for overnight right now, or price history is still loading."
					: "No flips match your settings right now. Try lowering 'Min trades per hour' or raising 'Target fill time'.",
					ColorScheme.LIGHT_GRAY_COLOR));
			}

			for (FlipSuggestion s : suggestions)
			{
				listPanel.add(buildRow(s, overnight));
				listPanel.add(Box.createVerticalStrut(6));
			}

			listPanel.revalidate();
			listPanel.repaint();
		});
	}

	private JPanel buildRow(FlipSuggestion s, boolean overnight)
	{
		JPanel row = new JPanel(new GridLayout(0, 1, 0, 1));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, stabilityColor(s.stability)),
			new EmptyBorder(6, 8, 6, 8)));

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		JLabel name = new JLabel(s.name);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		JLabel stab = smallLabel(s.stability.label, stabilityColor(s.stability));
		top.add(name, BorderLayout.CENTER);
		top.add(stab, BorderLayout.EAST);
		row.add(top);

		row.add(smallLabel("Buy " + Format.gp(s.buyPrice) + "  →  Sell " + Format.gp(s.sellPrice), Color.WHITE));
		row.add(smallLabel("Qty " + Format.gp(s.quantity) + "  ·  costs " + Format.short_(s.investment()), ColorScheme.LIGHT_GRAY_COLOR));
		row.add(smallLabel("+" + Format.gp(s.marginEach) + " each after " + Format.gp(s.taxEach) + " tax", ColorScheme.LIGHT_GRAY_COLOR));

		if (overnight)
		{
			row.add(smallLabel("Profit +" + Format.short_(s.totalProfit) + " if both offers fill", GOOD));
		}
		else
		{
			row.add(smallLabel("Profit +" + Format.short_(s.totalProfit) + "  ·  ≈ +" + Format.short_(s.profitPerHour) + "/hr", GOOD));
		}

		row.add(smallLabel("Est. fill: buy " + Format.duration(s.estBuyMinutes) + ", sell " + Format.duration(s.estSellMinutes),
			ColorScheme.LIGHT_GRAY_COLOR));

		String trend = s.trendPercent == null ? "24h trend ?" : "24h " + arrow(s.trendPercent) + " "
			+ String.format(Locale.US, "%.1f%%", Math.abs(s.trendPercent));
		row.add(smallLabel(trend + "  ·  " + Format.gp(Math.round(s.buyFillsPerHour)) + " sells/hr",
			s.trendPercent != null && s.trendPercent < 0 ? WARN : ColorScheme.LIGHT_GRAY_COLOR));

		boolean highImpact = s.marketImpactPercent > HIGH_IMPACT_PERCENT;
		row.add(smallLabel("Your order ≈ " + String.format(Locale.US, "%.0f%%", s.marketImpactPercent)
			+ " of an hour's sells" + (highImpact ? " (may move price)" : ""), highImpact ? WARN : ColorScheme.LIGHT_GRAY_COLOR));

		row.add(smallLabel(s.limit == null ? "Buy limit unknown: check in-game" : "Buy limit " + Format.gp(s.limit),
			s.limit == null ? WARN : ColorScheme.LIGHT_GRAY_COLOR));

		row.setToolTipText("Item ID " + s.itemId + ". Type these prices into the GE yourself.");
		return row;
	}

	private static String arrow(double trend)
	{
		if (trend > 0.5)
		{
			return "▲";
		}
		if (trend < -0.5)
		{
			return "▼";
		}
		return "▬";
	}

	static Color stabilityColor(Stability s)
	{
		switch (s)
		{
			case STABLE:
				return GOOD;
			case MODERATE:
				return WARN;
			case VOLATILE:
				return BAD;
			default:
				return ColorScheme.LIGHT_GRAY_COLOR;
		}
	}

	private static JLabel note(String text, Color color)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	private static JLabel smallLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}
}
