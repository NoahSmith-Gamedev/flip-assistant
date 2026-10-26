package com.flipassistant;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;
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
	private static final Color PROFIT_GREEN = new Color(90, 200, 90);

	private final JLabel statusLabel = new JLabel("Loading prices...");
	private final JPanel listPanel = new JPanel();

	FlipAssistantPanel(Runnable onRefresh)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(0, 4));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Flip Assistant");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.NORTH);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(statusLabel, BorderLayout.CENTER);

		JButton refresh = new JButton("Refresh now");
		refresh.setFocusable(false);
		refresh.addActionListener(e -> onRefresh.run());
		header.add(refresh, BorderLayout.SOUTH);

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

	void showSuggestions(List<FlipSuggestion> suggestions, String status)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(status);
			listPanel.removeAll();

			if (suggestions.isEmpty())
			{
				JLabel empty = new JLabel("<html>No flips match your settings right now. "
					+ "Try lowering 'Min trades per hour' or raising 'Max gold per item'.</html>");
				empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				listPanel.add(empty);
			}

			for (FlipSuggestion s : suggestions)
			{
				listPanel.add(buildRow(s));
				listPanel.add(Box.createVerticalStrut(6));
			}

			listPanel.revalidate();
			listPanel.repaint();
		});
	}

	private JPanel buildRow(FlipSuggestion s)
	{
		JPanel row = new JPanel(new GridLayout(0, 1, 0, 1));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, PROFIT_GREEN),
			new EmptyBorder(6, 8, 6, 8)));

		JLabel name = new JLabel(s.name);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		row.add(name);

		row.add(smallLabel("Buy " + Format.gp(s.buyPrice) + "  →  Sell " + Format.gp(s.sellPrice), Color.WHITE));
		row.add(smallLabel("Qty " + Format.gp(s.quantity) + "  ·  costs " + Format.short_(s.investment()), ColorScheme.LIGHT_GRAY_COLOR));
		row.add(smallLabel("+" + Format.gp(s.marginEach) + " each after " + Format.gp(s.taxEach) + " tax", ColorScheme.LIGHT_GRAY_COLOR));
		row.add(smallLabel("Profit +" + Format.short_(s.totalProfit), PROFIT_GREEN));
		row.add(smallLabel("Trades/hr " + Format.gp(s.hourlyVolume) + "  ·  limit "
			+ (s.limit == null ? "?" : Format.gp(s.limit)), ColorScheme.LIGHT_GRAY_COLOR));

		row.setToolTipText("Item ID " + s.itemId + ". Type these prices into the GE yourself.");
		return row;
	}

	private static JLabel smallLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}
}
