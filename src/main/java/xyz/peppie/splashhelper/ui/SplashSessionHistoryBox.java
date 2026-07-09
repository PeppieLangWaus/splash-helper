package xyz.peppie.splashhelper.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import xyz.peppie.splashhelper.model.SplashSession;
import xyz.peppie.splashhelper.model.SplashSpell;
import xyz.peppie.splashhelper.model.SessionStatField;
import xyz.peppie.splashhelper.SplashHelperConfig;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;
import xyz.peppie.splashhelper.service.SessionManager;

public class SplashSessionHistoryBox extends JPanel
{
	public interface SessionDeleteListener
	{
		void onSessionDeleted(SplashSessionHistoryBox box);
	}

	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM");
	private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("dd MMM HH:mm");

	private final SplashSession session;
	private final SplashHelperConfig config;
	private final SessionManager sessionManager;
	private final SessionDeleteListener deleteListener;
	private final JPanel titlePanel;
	private final JLabel titleLabel;
	private final JPanel contentPanel;
	private final SplashSupplyTrackerBox runeBox;
	private boolean collapsed;

	public SplashSessionHistoryBox(SplashSession session, boolean startCollapsed, ItemManager itemManager, SplashHelperConfig config, SessionManager sessionManager, SessionDeleteListener deleteListener)
	{
		this.session = session;
		this.config = config;
		this.sessionManager = sessionManager;
		this.deleteListener = deleteListener;
		this.collapsed = startCollapsed;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(javax.swing.BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()));

		// Title panel (clickable to collapse/expand)
		titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		titlePanel.setBorder(new EmptyBorder(7, 10, 7, 10));
		titlePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		titleLabel = new JLabel();
		titleLabel.setForeground(Color.ORANGE);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titlePanel.add(titleLabel, BorderLayout.WEST);

		// Collapse indicator
		JLabel collapseIndicator = new JLabel();
		collapseIndicator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		collapseIndicator.setFont(FontManager.getRunescapeSmallFont());
		titlePanel.add(collapseIndicator, BorderLayout.EAST);

		titlePanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					toggleCollapse();
				}
				else if (SwingUtilities.isRightMouseButton(e))
				{
					showContextMenu(e);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				titlePanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
			}
		});

		add(titlePanel);

		// Content panel with session details
		contentPanel = new JPanel();
		contentPanel.setLayout(new GridLayout(0, 1, 0, 4));
		contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Add stat rows
		// Always show these fields (not configurable)
		addStatRow("Player:", session.getPlayerName() != null ? session.getPlayerName() : "-");
		addStatRow("World:", String.valueOf(session.getWorld()));
		addStatRow("Knight:", session.isStickyKnight() ? "STICKY" : "Normal");
		addStatRow("Time:", formatDuration(session.getSessionDurationSeconds()));
		
		// Conditionally show configurable fields
		if (config.sessionHistoryFields().contains(SessionStatField.SPELL))
			addStatRow("Spell:", session.getSpell() != null ? session.getSpell().getName() : "Unknown");
		if (config.sessionHistoryFields().contains(SessionStatField.CASTS))
			addStatRow("Casts:", String.valueOf(session.getSpellsCast()));
		if (config.sessionHistoryFields().contains(SessionStatField.XP_GAINED))
			addStatRow("XP Gained:", formatNumber(session.getMagicXpGained()));
		if (config.sessionHistoryFields().contains(SessionStatField.XP_PER_HOUR))
			addStatRow("XP/Hour:", formatNumber((int) session.getXpPerHour()) + "/hr");
		if (config.sessionHistoryFields().contains(SessionStatField.RUNE_COST))
			addStatRow("Rune Cost:", formatNumber((int) session.getRuneCostGp()) + " gp");
		if (config.sessionHistoryFields().contains(SessionStatField.NEARBY_PLAYERS))
			addStatRow("Avg Players:", String.format("%.1f", session.getAveragePlayerCount()));
		if (config.sessionHistoryFields().contains(SessionStatField.HIGHEST_PLAYERS))
			addStatRow("Highest Players:", String.valueOf(session.getHighestPlayerCount()));
		if (config.sessionHistoryFields().contains(SessionStatField.PLAYER_DEATHS))
			addStatRow("Deaths:", String.valueOf(session.getPlayerDeaths()));

		add(contentPanel);

		// Rune usage box - use actual rune usage from session (excludes infinite runes)
		runeBox = new SplashSupplyTrackerBox(itemManager, "Runes Used");
		List<int[]> runeUsage = session.getActualRuneUsage();
		if (runeUsage == null || runeUsage.isEmpty())
		{
			// Fallback to spell runes if no actual usage stored (for old sessions)
			runeUsage = getRuneUsageFromSpell(session.getSpell());
		}
		runeBox.buildItems(runeUsage, session.getSpellsCast());
		add(runeBox);

		// Set initial state
		updateTitle();
		contentPanel.setVisible(!collapsed);
		runeBox.setVisible(!collapsed);
	}

	private List<int[]> getRuneUsageFromSpell(SplashSpell spell)
	{
		List<int[]> runeUsage = new ArrayList<>();
		if (spell != null)
		{
			for (SplashSpell.RuneCost cost : spell.getRuneCosts())
			{
				runeUsage.add(new int[]{cost.getItemId(), cost.getAmount()});
			}
		}
		return runeUsage;
	}

	private void toggleCollapse()
	{
		collapsed = !collapsed;
		contentPanel.setVisible(!collapsed);
		runeBox.setVisible(!collapsed);
		updateTitle();
		revalidate();
		repaint();
	}

	public void expand()
	{
		if (collapsed)
		{
			collapsed = false;
			contentPanel.setVisible(true);
			runeBox.setVisible(true);
			updateTitle();
			revalidate();
			repaint();
		}
	}

	public void collapse()
	{
		if (!collapsed)
		{
			collapsed = true;
			contentPanel.setVisible(false);
			runeBox.setVisible(false);
			updateTitle();
			revalidate();
			repaint();
		}
	}

	public boolean isCollapsed()
	{
		return collapsed;
	}

	private void updateTitle()
	{
		if (collapsed)
		{
			// Collapsed: "<day> - <duration> session"
			LocalDateTime start = LocalDateTime.ofInstant(session.getStartTime(), ZoneId.systemDefault());
			String day = start.format(DAY_FORMAT);
			String duration = formatDuration(session.getSessionDurationSeconds());
			titleLabel.setText(day + " - " + duration + " session");
		}
		else
		{
			// Expanded: "<day> <start time> - <day> <end time>"
			LocalDateTime start = LocalDateTime.ofInstant(session.getStartTime(), ZoneId.systemDefault());
			Instant endInstant = session.getEndTime() != null ? session.getEndTime() : Instant.now();
			LocalDateTime end = LocalDateTime.ofInstant(endInstant, ZoneId.systemDefault());
			
			String startStr = start.format(FULL_FORMAT);
			String endStr = end.format(FULL_FORMAT);
			titleLabel.setText(startStr + " - " + endStr);
		}
	}

	private void addStatRow(String labelText, String value)
	{
		JPanel rowPanel = new JPanel(new BorderLayout());
		rowPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel label = new JLabel(labelText);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());

		JLabel valueLabel = new JLabel(value);
		valueLabel.setForeground(Color.GRAY);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());

		rowPanel.add(label, BorderLayout.WEST);
		rowPanel.add(valueLabel, BorderLayout.EAST);
		contentPanel.add(rowPanel);
	}

	private String formatDuration(long seconds)
	{
		if (seconds < 60)
		{
			return seconds + "s";
		}
		else if (seconds < 3600)
		{
			return String.format("%d:%02d", seconds / 60, seconds % 60);
		}
		else
		{
			return String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
		}
	}

	private String formatNumber(int number)
	{
		return QuantityFormatter.formatNumber(number);
	}

	public SplashSession getSession()
	{
		return session;
	}

	/**
	 * Show context menu with delete option
	 */
	private void showContextMenu(MouseEvent e)
	{
		javax.swing.JPopupMenu contextMenu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem deleteItem = new javax.swing.JMenuItem("Delete Session");
		
		deleteItem.addActionListener(event -> showDeleteConfirmation());
		contextMenu.add(deleteItem);
		
		contextMenu.show(this, e.getX(), e.getY());
	}

	/**
	 * Show confirmation dialog for deleting session
	 */
	private void showDeleteConfirmation()
	{
		String sessionInfo = String.format("%s - %s session", 
			session.getPlayerName() != null ? session.getPlayerName() : "Unknown",
			formatDuration(session.getSessionDurationSeconds()));
		
		int result = JOptionPane.showConfirmDialog(
			this,
			"Are you sure you want to delete this session?\n\n" + sessionInfo,
			"Delete Session",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE
		);
		
		if (result == JOptionPane.YES_OPTION)
		{
			sessionManager.deleteSession(session);
			// Notify parent to handle removal properly
			if (deleteListener != null)
			{
				deleteListener.onSessionDeleted(this);
			}
		}
	}
}