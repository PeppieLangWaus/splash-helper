package xyz.peppie.splashhelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
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
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

public class SplashSessionHistoryBox extends JPanel
{
	private static final String CARET_RIGHT = "\u25B6"; // ▶
	private static final String CARET_DOWN = "\u25BC";  // ▼
	
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE");
	private static final DateTimeFormatter FULL_FORMAT = DateTimeFormatter.ofPattern("EEE HH:mm");

	private final SplashSession session;
	private final LocalDate sessionDate;
	private final JPanel titlePanel;
	private final JLabel caretLabel;
	private final JLabel titleLabel;
	private final JPanel contentPanel;
	private final SplashSupplyTrackerBox supplyBox;
	private boolean collapsed;

	public SplashSessionHistoryBox(SplashSession session, boolean startCollapsed, ItemManager itemManager, List<int[]> runeUsage)
	{
		this.session = session;
		this.collapsed = startCollapsed;
		this.sessionDate = LocalDateTime.ofInstant(session.getStartTime(), ZoneId.systemDefault()).toLocalDate();

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(javax.swing.BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()));

		// Title panel (clickable to collapse/expand)
		titlePanel = new JPanel(new BorderLayout());
		titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		titlePanel.setBorder(new EmptyBorder(7, 10, 7, 10));
		titlePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		// Caret indicator on the left
		caretLabel = new JLabel();
		caretLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		caretLabel.setFont(FontManager.getRunescapeSmallFont());
		caretLabel.setBorder(new EmptyBorder(0, 0, 0, 5));

		titleLabel = new JLabel();
		titleLabel.setForeground(Color.ORANGE);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());

		JPanel leftPanel = new JPanel(new BorderLayout());
		leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
		leftPanel.add(caretLabel, BorderLayout.WEST);
		leftPanel.add(titleLabel, BorderLayout.CENTER);
		titlePanel.add(leftPanel, BorderLayout.WEST);

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
				leftPanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
				leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());
			}
		});

		add(titlePanel);

		// Content panel with session details
		contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
		contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		// Stats panel
		JPanel statsPanel = new JPanel(new GridLayout(0, 1, 0, 4));
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

		// Add stat rows
		addStatRow(statsPanel, "Player:", session.getPlayerName() != null ? session.getPlayerName() : "-");
		addStatRow(statsPanel, "Spell:", session.getSpell() != null ? session.getSpell().getName() : "Unknown");
		addStatRow(statsPanel, "World:", String.valueOf(session.getWorld()));
		addStatRow(statsPanel, "Knight:", session.isStickyKnight() ? "STICKY" : "Normal");
		addStatRow(statsPanel, "Time:", formatDuration(session.getSessionDurationSeconds()));
		addStatRow(statsPanel, "Casts:", String.valueOf(session.getSpellsCast()));
		addStatRow(statsPanel, "XP Gained:", formatNumber(session.getMagicXpGained()));
		addStatRow(statsPanel, "XP/Hour:", formatNumber((int) session.getXpPerHour()) + "/hr");

		contentPanel.add(statsPanel);

		// Supply box for runes used
		supplyBox = new SplashSupplyTrackerBox(itemManager, "Runes Used");
		supplyBox.buildItems(runeUsage, session.getSpellsCast());
		contentPanel.add(supplyBox);

		add(contentPanel);

		// Set initial state
		updateTitle();
		contentPanel.setVisible(!collapsed);
	}

	private void toggleCollapse()
	{
		collapsed = !collapsed;
		contentPanel.setVisible(!collapsed);
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
		// Update caret
		caretLabel.setText(collapsed ? CARET_RIGHT : CARET_DOWN);

		LocalDate today = LocalDate.now();
		boolean isSameDay = sessionDate.equals(today);
		LocalDateTime start = LocalDateTime.ofInstant(session.getStartTime(), ZoneId.systemDefault());

		if (isSameDay)
		{
			// Same day: "<start time> - <duration>"
			String startTime = start.format(TIME_FORMAT);
			String duration = formatDuration(session.getSessionDurationSeconds());
			titleLabel.setText(startTime + " - " + duration);
		}
		else if (collapsed)
		{
			// Collapsed (different day): "<day> - <duration> session"
			String day = start.format(DAY_FORMAT);
			String duration = formatDuration(session.getSessionDurationSeconds());
			titleLabel.setText(day + " - " + duration + " session");
		}
		else
		{
			// Expanded (different day): "<day> <start time> - <day> <end time>"
			Instant endInstant = session.getEndTime() != null ? session.getEndTime() : Instant.now();
			LocalDateTime end = LocalDateTime.ofInstant(endInstant, ZoneId.systemDefault());
			
			String startStr = start.format(FULL_FORMAT);
			String endStr = end.format(FULL_FORMAT);
			titleLabel.setText(startStr + " - " + endStr);
		}
	}

	private void addStatRow(JPanel panel, String labelText, String value)
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
		panel.add(rowPanel);
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
