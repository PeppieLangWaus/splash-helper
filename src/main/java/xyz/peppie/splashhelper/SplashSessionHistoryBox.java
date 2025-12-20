package xyz.peppie.splashhelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

public class SplashSessionHistoryBox extends JPanel
{
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE");
	private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("EEE HH:mm");

	private final SplashSession session;
	private final ItemManager itemManager;
	private final List<int[]> runeUsage;
	private final JPanel titlePanel;
	private final JLabel titleLabel;
	private final JLabel collapseIndicator;
	private final JPanel contentPanel;
	private SplashSupplyTrackerBox supplyBox;
	private boolean collapsed;

	public SplashSessionHistoryBox(SplashSession session, ItemManager itemManager, List<int[]> runeUsage, boolean startCollapsed)
	{
		this.session = session;
		this.itemManager = itemManager;
		this.runeUsage = runeUsage;
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
		collapseIndicator = new JLabel();
		collapseIndicator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		collapseIndicator.setFont(FontManager.getRunescapeBoldFont());
		titlePanel.add(collapseIndicator, BorderLayout.EAST);

		titlePanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleCollapse();
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
		addStatRow("Player:", session.getPlayerName() != null ? session.getPlayerName() : "-");
		addStatRow("Spell:", session.getSpell() != null ? session.getSpell().getName() : "Unknown");
		addStatRow("World:", String.valueOf(session.getWorld()));
		addStatRow("Knight:", session.isStickyKnight() ? "STICKY" : "Normal");
		addStatRow("Time:", formatDuration(session.getSessionDurationSeconds()));
		addStatRow("Casts:", String.valueOf(session.getSpellsCast()));
		addStatRow("XP Gained:", formatNumber(session.getMagicXpGained()));
		addStatRow("XP/Hour:", formatNumber((int) session.getXpPerHour()) + "/hr");

		add(contentPanel);

		// Add supply box for runes used
		supplyBox = new SplashSupplyTrackerBox(itemManager, "Runes Used");
		if (runeUsage != null && !runeUsage.isEmpty())
		{
			supplyBox.buildItems(runeUsage, session.getSpellsCast());
		}
		add(supplyBox);

		// Set initial state
		updateTitle();
		updateCaretIndicator();
		contentPanel.setVisible(!collapsed);
		supplyBox.setVisible(!collapsed);
	}

	private void toggleCollapse()
	{
		collapsed = !collapsed;
		contentPanel.setVisible(!collapsed);
		supplyBox.setVisible(!collapsed);
		updateTitle();
		updateCaretIndicator();
		revalidate();
		repaint();
	}

	public void expand()
	{
		if (collapsed)
		{
			collapsed = false;
			contentPanel.setVisible(true);
			supplyBox.setVisible(true);
			updateTitle();
			updateCaretIndicator();
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
			supplyBox.setVisible(false);
			updateTitle();
			updateCaretIndicator();
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
		LocalDateTime start = LocalDateTime.ofInstant(session.getStartTime(), ZoneId.systemDefault());
		Instant endInstant = session.getEndTime() != null ? session.getEndTime() : Instant.now();
		LocalDateTime end = LocalDateTime.ofInstant(endInstant, ZoneId.systemDefault());
		LocalDate today = LocalDate.now();
		LocalDate sessionDate = start.toLocalDate();
		boolean isSameDay = sessionDate.equals(today);

		if (collapsed)
		{
			if (isSameDay)
			{
				// Same day collapsed: "<start time> - <duration>"
				String startTime = start.format(TIME_FORMAT);
				String duration = formatDuration(session.getSessionDurationSeconds());
				titleLabel.setText(startTime + " - " + duration);
			}
			else
			{
				// Different day collapsed: "<day> - <duration> session"
				String day = start.format(DAY_FORMAT);
				String duration = formatDuration(session.getSessionDurationSeconds());
				titleLabel.setText(day + " - " + duration + " session");
			}
		}
		else
		{
			// Expanded: "<day> <start time> - <day> <end time>"
			String startStr = start.format(FULL_FORMAT);
			String endStr = end.format(FULL_FORMAT);
			titleLabel.setText(startStr + " - " + endStr);
		}
	}

	private void updateCaretIndicator()
	{
		if (collapsed)
		{
			collapseIndicator.setText("▸"); // Right-pointing caret
		}
		else
		{
			collapseIndicator.setText("▾"); // Down-pointing caret
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
}
