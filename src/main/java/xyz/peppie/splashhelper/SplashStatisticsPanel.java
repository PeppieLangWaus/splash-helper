package xyz.peppie.splashhelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

public class SplashStatisticsPanel extends PluginPanel
{
	private final SplashHelperPlugin plugin;
	private final SplashHelperConfig config;

	private final JPanel statsPanel = new JPanel();
	private final PluginErrorPanel errorPanel = new PluginErrorPanel();

	// Labels for stats
	private final JLabel playerLabel = new JLabel();
	private final JLabel spellLabel = new JLabel();
	private final JLabel worldLabel = new JLabel();
	private final JLabel stickyLabel = new JLabel();
	private final JLabel timeLabel = new JLabel();
	private final JLabel castsLabel = new JLabel();
	private final JLabel remainingLabel = new JLabel();
	private final JLabel xpGainedLabel = new JLabel();
	private final JLabel xpHourLabel = new JLabel();
	private final JLabel pickpocketersLabel = new JLabel();
	private final JLabel avgPlayersLabel = new JLabel();
	private final JLabel knightMovesLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();

	public SplashStatisticsPanel(SplashHelperPlugin plugin, SplashHelperConfig config)
	{
		super(false);
		this.plugin = plugin;
		this.config = config;

		setBorder(new EmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout());

		// Main layout panel
		final JPanel layoutPanel = new JPanel();
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		layoutPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(layoutPanel, BorderLayout.NORTH);

		// Stats panel setup
		statsPanel.setLayout(new GridLayout(0, 1, 0, 5));
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(10, 10, 10, 10)
		));
		statsPanel.setVisible(false);

		// Add title
		JLabel titleLabel = new JLabel("Splash Statistics");
		titleLabel.setForeground(Color.CYAN);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		statsPanel.add(titleLabel);

		// Add stat rows
		addStatRow(statsPanel, "Player:", playerLabel);
		addStatRow(statsPanel, "Spell:", spellLabel);
		addStatRow(statsPanel, "World:", worldLabel);
		addStatRow(statsPanel, "Knight:", stickyLabel);
		addStatRow(statsPanel, "Time:", timeLabel);
		addStatRow(statsPanel, "Casts:", castsLabel);
		addStatRow(statsPanel, "Remaining:", remainingLabel);
		addStatRow(statsPanel, "XP Gained:", xpGainedLabel);
		addStatRow(statsPanel, "XP/Hour:", xpHourLabel);
		addStatRow(statsPanel, "Pickpocketers:", pickpocketersLabel);
		addStatRow(statsPanel, "Avg Players:", avgPlayersLabel);
		addStatRow(statsPanel, "Knight Moves:", knightMovesLabel);
		addStatRow(statsPanel, "Status:", statusLabel);

		layoutPanel.add(statsPanel);

		// Error panel (shown when no session)
		errorPanel.setContent("Splash Statistics", "No active splash session.");
		add(errorPanel);
	}

	private void addStatRow(JPanel panel, String labelText, JLabel valueLabel)
	{
		JPanel rowPanel = new JPanel(new BorderLayout());
		rowPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());

		valueLabel.setForeground(Color.WHITE);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());

		rowPanel.add(label, BorderLayout.WEST);
		rowPanel.add(valueLabel, BorderLayout.EAST);
		panel.add(rowPanel);
	}

	public void updatePanel()
	{
		if (!config.enableStatistics())
		{
			return;
		}

		SplashSession session = plugin.getCurrentSession();

		SwingUtilities.invokeLater(() ->
		{
			if (session == null || !session.isActive())
			{
				if (statsPanel.isVisible())
				{
					statsPanel.setVisible(false);
					add(errorPanel);
					revalidate();
					repaint();
				}
				return;
			}

			// Show stats panel
			if (!statsPanel.isVisible())
			{
				statsPanel.setVisible(true);
				remove(errorPanel);
				revalidate();
			}

			// Update labels
			playerLabel.setText(session.getPlayerName());
			
			if (session.getSpell() != null)
			{
				spellLabel.setText(session.getSpell().getName());
				spellLabel.setForeground(Color.YELLOW);
			}
			else
			{
				spellLabel.setText("Unknown");
				spellLabel.setForeground(Color.GRAY);
			}

			worldLabel.setText(String.valueOf(session.getWorld()));

			if (session.isStickyKnight())
			{
				stickyLabel.setText("STICKY");
				stickyLabel.setForeground(Color.GREEN);
			}
			else
			{
				stickyLabel.setText("Normal");
				stickyLabel.setForeground(Color.WHITE);
			}

			// Time
			long seconds = session.getSessionDurationSeconds();
			long minutes = seconds / 60;
			long hours = minutes / 60;
			String timeStr;
			if (hours > 0)
			{
				timeStr = String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
			}
			else
			{
				timeStr = String.format("%d:%02d", minutes, seconds % 60);
			}
			timeLabel.setText(timeStr);
			timeLabel.setForeground(Color.GREEN);

			// Casts
			castsLabel.setText(String.valueOf(session.getSpellsCast()));

			// Remaining
			int remaining = session.getRemainingCasts();
			remainingLabel.setText(String.valueOf(remaining));
			if (remaining > 100)
			{
				remainingLabel.setForeground(Color.GREEN);
			}
			else if (remaining > 20)
			{
				remainingLabel.setForeground(Color.YELLOW);
			}
			else
			{
				remainingLabel.setForeground(Color.RED);
			}

			// XP
			xpGainedLabel.setText(formatNumber(session.getMagicXpGained()));
			xpHourLabel.setText(formatNumber((int) session.getXpPerHour()));
			xpHourLabel.setForeground(Color.CYAN);

			// Pickpocketers
			pickpocketersLabel.setText(String.valueOf(session.getPickpocketerCount()));

			// Avg players
			avgPlayersLabel.setText(String.format("%.1f", session.getAveragePlayerCount()));

			// Knight movements
			knightMovesLabel.setText(String.valueOf(session.getKnightMovements()));

			// Status
			if (plugin.isHasEscaped())
			{
				statusLabel.setText("ESCAPED!");
				statusLabel.setForeground(Color.RED);
			}
			else
			{
				statusLabel.setText("Active");
				statusLabel.setForeground(Color.GREEN);
			}

			repaint();
		});
	}

	private String formatNumber(int number)
	{
		if (number >= 1000000)
		{
			return String.format("%.1fM", number / 1000000.0);
		}
		else if (number >= 1000)
		{
			return String.format("%.1fK", number / 1000.0);
		}
		return String.valueOf(number);
	}
}
