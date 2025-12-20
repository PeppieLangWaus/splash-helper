package xyz.peppie.splashhelper;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

public class SplashStatisticsPanel extends PluginPanel
{
    private static final String ERROR_PANEL = "ERROR";
    private static final String DATA_PANEL = "DATA";

    private final SplashHelperPlugin plugin;
    private final SplashHelperConfig config;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel container = new JPanel(cardLayout);
    private final PluginErrorPanel errorPanel = new PluginErrorPanel();

    // Overall statistics
    private final JPanel overallPanel = new JPanel();
    private final JLabel overallSessionsLabel = new JLabel("0");
    private final JLabel overallTimeLabel = new JLabel("0:00");
    private final JLabel overallCastsLabel = new JLabel("0");
    private final JLabel overallXpLabel = new JLabel("0");
    private final JLabel overallRemainingLabel = new JLabel("0");
    private final JLabel overallStatusLabel = new JLabel("-");

    // Current session
    private final JPanel currentPanel = new JPanel();
    private final JLabel playerLabel = new JLabel("-");
    private final JLabel spellLabel = new JLabel("-");
    private final JLabel worldLabel = new JLabel("-");
    private final JLabel stickyLabel = new JLabel("-");
    private final JLabel timeLabel = new JLabel("0:00");
    private final JLabel castsLabel = new JLabel("0");
    private final JLabel xpGainedLabel = new JLabel("0");
    private final JLabel xpHourLabel = new JLabel("0");

    // Supply tracker
    private final SplashSupplyTrackerBox supplyBox;

    public SplashStatisticsPanel(SplashHelperPlugin plugin, SplashHelperConfig config, ItemManager itemManager)
    {
        super(false);
        this.plugin = plugin;
        this.config = config;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Error panel
        errorPanel.setContent("Splash Statistics", "Start splashing to see statistics.");

        // Data panel with stats
        JPanel dataPanel = new JPanel();
        dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.Y_AXIS));
        dataPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        dataPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Build overall panel
        overallPanel.setLayout(new GridLayout(0, 1, 0, 4));
        overallPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        overallPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()),
            new EmptyBorder(10, 10, 10, 10)));

        JLabel overallTitle = new JLabel("Overall Statistics");
        overallTitle.setForeground(Color.CYAN);
        overallTitle.setFont(FontManager.getRunescapeBoldFont());
        overallPanel.add(overallTitle);

        addStatRow(overallPanel, "Sessions:", overallSessionsLabel);
        addStatRow(overallPanel, "Total Time:", overallTimeLabel);
        addStatRow(overallPanel, "Total Casts:", overallCastsLabel);
        addStatRow(overallPanel, "Total XP:", overallXpLabel);
        addStatRow(overallPanel, "Remaining:", overallRemainingLabel);
        addStatRow(overallPanel, "Status:", overallStatusLabel);
        dataPanel.add(overallPanel);

        // Spacer
        dataPanel.add(javax.swing.Box.createVerticalStrut(10));

        // Build current session panel
        currentPanel.setLayout(new GridLayout(0, 1, 0, 4));
        currentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        currentPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR.darker()),
            new EmptyBorder(10, 10, 10, 10)));

        JLabel currentTitle = new JLabel("Current Session");
        currentTitle.setForeground(Color.ORANGE);
        currentTitle.setFont(FontManager.getRunescapeBoldFont());
        currentPanel.add(currentTitle);

        addStatRow(currentPanel, "Player:", playerLabel);
        addStatRow(currentPanel, "Spell:", spellLabel);
        addStatRow(currentPanel, "World:", worldLabel);
        addStatRow(currentPanel, "Knight:", stickyLabel);
        addStatRow(currentPanel, "Time:", timeLabel);
        addStatRow(currentPanel, "Casts:", castsLabel);
        addStatRow(currentPanel, "XP Gained:", xpGainedLabel);
        addStatRow(currentPanel, "XP/Hour:", xpHourLabel);
        dataPanel.add(currentPanel);

        // Spacer
        dataPanel.add(javax.swing.Box.createVerticalStrut(10));

        // Supply tracker box
        supplyBox = new SplashSupplyTrackerBox(itemManager, "Runes Used");
        dataPanel.add(supplyBox);

        // Add panels to card layout
        container.add(errorPanel, ERROR_PANEL);
        container.add(dataPanel, DATA_PANEL);

        add(container, BorderLayout.NORTH);

        // Show error panel initially
        cardLayout.show(container, ERROR_PANEL);
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

        SwingUtilities.invokeLater(() -> {
            SplashSession session = plugin.getCurrentSession();
            java.util.List<SplashSession> history = plugin.getSessionHistory();

            boolean hasActiveSession = session != null && session.isActive();
            boolean hasHistory = !history.isEmpty();
            boolean hasData = hasActiveSession || hasHistory;

            if (!hasData)
            {
                cardLayout.show(container, ERROR_PANEL);
                return;
            }

            // Show data panel
            cardLayout.show(container, DATA_PANEL);

            // Calculate totals
            int totalSessions = history.size() + (hasActiveSession ? 1 : 0);
            long totalSeconds = history.stream().mapToLong(SplashSession::getSessionDurationSeconds).sum();
            int totalCasts = history.stream().mapToInt(SplashSession::getSpellsCast).sum();
            int totalXp = history.stream().mapToInt(SplashSession::getMagicXpGained).sum();

            if (hasActiveSession)
            {
                totalSeconds += session.getSessionDurationSeconds();
                totalCasts += session.getSpellsCast();
                totalXp += session.getMagicXpGained();
            }

            // Update overall stats
            overallSessionsLabel.setText(String.valueOf(totalSessions));
            overallTimeLabel.setText(formatDuration(totalSeconds));
            overallCastsLabel.setText(formatNumber(totalCasts));
            overallXpLabel.setText(formatNumber(totalXp));

            // Update remaining casts (using cached value from client thread)
            int remaining = plugin.getCachedRemainingCasts();
            overallRemainingLabel.setText(String.valueOf(remaining));
            if (remaining > 100)
            {
                overallRemainingLabel.setForeground(Color.GREEN);
            }
            else if (remaining > 20)
            {
                overallRemainingLabel.setForeground(Color.YELLOW);
            }
            else
            {
                overallRemainingLabel.setForeground(Color.RED);
            }

            // Update status
            if (hasActiveSession)
            {
                if (plugin.isHasEscaped())
                {
                    overallStatusLabel.setText("ESCAPED!");
                    overallStatusLabel.setForeground(Color.RED);
                }
                else
                {
                    overallStatusLabel.setText("Splashing");
                    overallStatusLabel.setForeground(Color.GREEN);
                }
            }
            else
            {
                overallStatusLabel.setText("Idle");
                overallStatusLabel.setForeground(Color.GRAY);
            }

            // Update current session
            if (hasActiveSession)
            {
                currentPanel.setVisible(true);
                supplyBox.setVisible(true);

                playerLabel.setText(session.getPlayerName() != null ? session.getPlayerName() : "-");

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

                timeLabel.setText(formatDuration(session.getSessionDurationSeconds()));
                timeLabel.setForeground(Color.GREEN);

                castsLabel.setText(String.valueOf(session.getSpellsCast()));

                xpGainedLabel.setText(formatNumber(session.getMagicXpGained()));
                xpHourLabel.setText(formatNumber((int) session.getXpPerHour()) + "/hr");
                xpHourLabel.setForeground(Color.CYAN);

                // Update supply box with actual rune usage (combo runes, excludes infinite)
                java.util.List<int[]> actualRuneUsage = plugin.getCachedActualRuneUsage();
                supplyBox.buildItems(actualRuneUsage, session.getSpellsCast());
            }
            else
            {
                currentPanel.setVisible(false);
                supplyBox.setVisible(false);
            }

            revalidate();
            repaint();
        });
    }

    private String formatDuration(long seconds)
    {
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0)
        {
            return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
        return String.format("%d:%02d", minutes, seconds % 60);
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
