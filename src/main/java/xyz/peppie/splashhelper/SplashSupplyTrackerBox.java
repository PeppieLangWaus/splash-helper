package xyz.peppie.splashhelper;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

public class SplashSupplyTrackerBox extends JPanel
{
	private static final int ITEMS_PER_ROW = 5;
	private static final int ITEM_SIZE = 36;

	private final JPanel itemContainer = new JPanel();
	private final JLabel titleLabel = new JLabel();
	private final JPanel boxTitle = new JPanel();
	private final ItemManager itemManager;

	public SplashSupplyTrackerBox(ItemManager itemManager, String title)
	{
		this.itemManager = itemManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		boxTitle.setLayout(new BorderLayout());
		boxTitle.setBorder(new EmptyBorder(5, 5, 5, 5));
		boxTitle.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

		titleLabel.setText(title);
		titleLabel.setFont(FontManager.getRunescapeSmallFont());
		titleLabel.setForeground(Color.WHITE);
		boxTitle.add(titleLabel, BorderLayout.WEST);

		itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		itemContainer.setBorder(new EmptyBorder(1, 1, 1, 1));

		add(boxTitle);
		add(itemContainer);
	}

	/**
	 * Build items using actual rune usage data.
	 * @param actualRuneUsage List of int[2] arrays: [itemId, amountPerCast] - excludes infinite runes
	 * @param spellsCast Number of spells cast
	 */
	public void buildItems(java.util.List<int[]> actualRuneUsage, int spellsCast)
	{
		setVisible(true);
		itemContainer.removeAll();

		int runeCount = (actualRuneUsage == null) ? 0 : actualRuneUsage.size();

		// Calculate rows needed (minimum 1 row for empty state)
		int rowSize = runeCount == 0 ? 1 : ((runeCount % ITEMS_PER_ROW == 0) ? 0 : 1) + runeCount / ITEMS_PER_ROW;
		itemContainer.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));
		
		// Set preferred height for item container based on rows (with padding)
		int containerHeight = rowSize * ITEM_SIZE + 8; // 20 for padding (10 top + 10 bottom)
		itemContainer.setPreferredSize(new Dimension(ITEMS_PER_ROW * ITEM_SIZE, containerHeight));
		itemContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, containerHeight));

		if (actualRuneUsage != null)
		{
			for (int[] runeData : actualRuneUsage)
			{
				int itemId = runeData[0];
				int amountPerCast = runeData[1];
				int totalUsed = spellsCast * amountPerCast;
				
				JPanel slotContainer = new JPanel();
				slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				slotContainer.setLayout(new BorderLayout());
				slotContainer.setPreferredSize(new Dimension(ITEM_SIZE, ITEM_SIZE));

				JLabel imageLabel = new JLabel();
				imageLabel.setVerticalAlignment(SwingConstants.CENTER);
				imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
				imageLabel.setToolTipText(buildRuneTooltip(itemId, totalUsed, amountPerCast));

				AsyncBufferedImage itemImage = itemManager.getImage(itemId, totalUsed, totalUsed > 1);
				itemImage.addTo(imageLabel);

				slotContainer.add(imageLabel, BorderLayout.CENTER);
				itemContainer.add(slotContainer);
			}
		}

		// Fill remaining slots if needed
		int remaining = (rowSize * ITEMS_PER_ROW) - runeCount;
		for (int i = 0; i < remaining; i++)
		{
			JPanel emptySlot = new JPanel();
			emptySlot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			emptySlot.setPreferredSize(new Dimension(ITEM_SIZE, ITEM_SIZE));
			itemContainer.add(emptySlot);
		}

		itemContainer.revalidate();
		itemContainer.repaint();
	}

	private String buildRuneTooltip(int itemId, int totalUsed, int amountPerCast)
	{
		String runeName = getRuneName(itemId);
		return String.format("<html>%s<br>Used: %s (%d per cast)</html>",
			runeName,
			QuantityFormatter.formatNumber(totalUsed),
			amountPerCast);
	}

	private String getRuneName(int itemId)
	{
		switch (itemId)
		{
			case SplashSpell.ItemID.MIND_RUNE: return "Mind rune";
			case SplashSpell.ItemID.WATER_RUNE: return "Water rune";
			case SplashSpell.ItemID.EARTH_RUNE: return "Earth rune";
			case SplashSpell.ItemID.FIRE_RUNE: return "Fire rune";
			case SplashSpell.ItemID.AIR_RUNE: return "Air rune";
			case SplashSpell.ItemID.BODY_RUNE: return "Body rune";
			case SplashSpell.ItemID.CHAOS_RUNE: return "Chaos rune";
			case SplashSpell.ItemID.DEATH_RUNE: return "Death rune";
			case SplashSpell.ItemID.BLOOD_RUNE: return "Blood rune";
			case SplashSpell.ItemID.WRATH_RUNE: return "Wrath rune";
			// Combination runes
			case 4695: return "Mist rune";
			case 4696: return "Dust rune";
			case 4697: return "Smoke rune";
			case 4698: return "Mud rune";
			case 4694: return "Steam rune";
			case 4699: return "Lava rune";
			default: return "Rune";
		}
	}
}
