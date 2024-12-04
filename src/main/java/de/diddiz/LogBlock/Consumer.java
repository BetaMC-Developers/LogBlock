package de.diddiz.LogBlock;

import static de.diddiz.util.BukkitUtils.compressInventory;
import static de.diddiz.util.BukkitUtils.entityName;
import static de.diddiz.util.BukkitUtils.rawData;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.ContainerBlock;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Consumer extends TimerTask
{
	private final Queue<Row> queue = new LinkedBlockingQueue<Row>();
	private final Config config;
	private final Map<Integer, WorldConfig> worlds;
	private final Set<Integer> hiddenPlayers, hiddenBlocks;
	private final Map<Integer, Integer> lastAttackedEntity = new HashMap<Integer, Integer>();
	private final Map<Integer, Long> lastAttackTime = new HashMap<Integer, Long>();
	private final Logger log;
	private final LogBlock logblock;
	private final Map<Integer, Integer> players = new HashMap<Integer, Integer>();
	private final Lock lock = new ReentrantLock();

	Consumer(LogBlock logblock) {
		this.logblock = logblock;
		log = logblock.getServer().getLogger();
		config = logblock.getConfig();
		hiddenPlayers = config.hiddenPlayers;
		hiddenBlocks = config.hiddenBlocks;
		worlds = config.worlds;
	}

	/**
	 * Logs any block change. Don't try to combine broken and placed blocks. Queue two block changes or use the queueBLockReplace methods.
	 */
	public void queueBlock(String playerName, Location loc, int typeBefore, int typeAfter, byte data) {
		queueBlock(playerName, loc, typeBefore, typeAfter, data, null, null);
	}

	/**
	 * Logs a block break. The type afterwards is assumed to be o (air).
	 * 
	 * @param before
	 * Blockstate of the block before actually being destroyed.
	 */
	public void queueBlockBreak(String playerName, BlockState before) {
		queueBlockBreak(playerName, new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()), before.getTypeId(), before.getRawData());
	}

	/**
	 * Logs a block break. The block type afterwards is assumed to be o (air).
	 */
	public void queueBlockBreak(String playerName, Location loc, int typeBefore, byte dataBefore) {
		queueBlock(playerName, loc, typeBefore, 0, dataBefore);
	}

	/**
	 * Logs a block place. The block type before is assumed to be o (air).
	 * 
	 * @param after
	 * Blockstate of the block after actually being placed.
	 */
	public void queueBlockPlace(String playerName, BlockState after) {
		queueBlockPlace(playerName, new Location(after.getWorld(), after.getX(), after.getY(), after.getZ()), after.getTypeId(), after.getRawData());
	}

	/**
	 * Logs a block place. The block type before is assumed to be o (air).
	 */
	public void queueBlockPlace(String playerName, Location loc, int type, byte data) {
		queueBlock(playerName, loc, 0, type, data);
	}

	/**
	 * @param before
	 * Blockstate of the block before actually being destroyed.
	 * @param after
	 * Blockstate of the block after actually being placed.
	 */
	public void queueBlockReplace(String playerName, BlockState before, BlockState after) {
		queueBlockReplace(playerName, new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()), before.getTypeId(), before.getRawData(), after.getTypeId(), after.getRawData());
	}

	/**
	 * @param before
	 * Blockstate of the block before actually being destroyed.
	 */
	public void queueBlockReplace(String playerName, BlockState before, int typeAfter, byte dataAfter) {
		queueBlockReplace(playerName, new Location(before.getWorld(), before.getX(), before.getY(), before.getZ()), before.getTypeId(), before.getRawData(), typeAfter, dataAfter);
	}

	/**
	 * @param after
	 * Blockstate of the block after actually being placed.
	 */
	public void queueBlockReplace(String playerName, int typeBefore, byte dataBefore, BlockState after) {
		queueBlockReplace(playerName, new Location(after.getWorld(), after.getX(), after.getY(), after.getZ()), typeBefore, dataBefore, after.getTypeId(), after.getRawData());
	}

	public void queueBlockReplace(String playerName, Location loc, int typeBefore, byte dataBefore, int typeAfter, byte dataAfter) {
		if (dataBefore == 0)
			queueBlock(playerName, loc, typeBefore, typeAfter, dataAfter);
		else {
			queueBlockBreak(playerName, loc, typeBefore, dataBefore);
			queueBlockPlace(playerName, loc, typeAfter, dataAfter);
		}
	}

	/**
	 * @param container
	 * The respective container. Must be an instance of Chest, Dispencer or Furnace.
	 */
	public void queueChestAccess(String playerName, BlockState container, short itemType, short itemAmount, byte itemData) {
		if (!(container instanceof ContainerBlock))
			return;
		queueChestAccess(playerName, new Location(container.getWorld(), container.getX(), container.getY(), container.getZ()), container.getTypeId(), itemType, itemAmount, itemData);
	}

	/**
	 * @param type
	 * Type id of the container. Must be 63 or 68.
	 */
	public void queueChestAccess(String playerName, Location loc, int type, short itemType, short itemAmount, byte itemData) {
		queueBlock(playerName, loc, type, type, (byte)0, null, new ChestAccess(itemType, itemAmount, itemData));
	}

	/**
	 * Logs a container block break. The block type before is assumed to be o (air). All content is assumed to be taken.
	 * 
	 * @param container
	 * Must be instanceof ContainerBlock
	 */
	public void queueContainerBreak(String playerName, BlockState container) {
		if (!(container instanceof ContainerBlock))
			return;
		queueContainerBreak(playerName, new Location(container.getWorld(), container.getX(), container.getY(), container.getZ()), container.getTypeId(), container.getRawData(), ((ContainerBlock)container).getInventory());
	}

	/**
	 * Logs a container block break. The block type before is assumed to be o (air). All content is assumed to be taken.
	 */
	public void queueContainerBreak(String playerName, Location loc, int type, byte data, Inventory inv) {
		final ItemStack[] items = compressInventory(inv.getContents());
		for (final ItemStack item : items)
			queueChestAccess(playerName, loc, type, (short)item.getTypeId(), (short)(item.getAmount() * -1), rawData(item));
		queueBlockBreak(playerName, loc, type, data);
	}

	/**
	 * @param killer
	 * Can' be null
	 * @param victim
	 * Can' be null
	 */
	public void queueKill(Entity killer, Entity victim) {
		if (killer == null || victim == null)
			return;
		if (lastAttackedEntity.containsKey(killer.getEntityId()) && lastAttackedEntity.get(killer.getEntityId()) == victim.getEntityId() && System.currentTimeMillis() - lastAttackTime.get(killer.getEntityId()) < 5000)
			return;
		int weapon = 0;
		if (killer instanceof Player && ((Player)killer).getItemInHand() != null)
			weapon = ((Player)killer).getItemInHand().getTypeId();
		lastAttackedEntity.put(killer.getEntityId(), victim.getEntityId());
		lastAttackTime.put(killer.getEntityId(), System.currentTimeMillis());
		queueKill(victim.getWorld(), entityName(killer), entityName(victim), weapon);
	}

	/**
	 * @param world
	 * World the victim was inside.
	 * @param killerName
	 * Name of the killer. Can be null.
	 * @param victimName
	 * Name of the victim. Can't be null.
	 * @param weapon
	 * Item id of the weapon. 0 for no weapon.
	 */
	public void queueKill(World world, String killerName, String victimName, int weapon) {
		if (victimName == null || !worlds.containsKey(world.getName().hashCode()))
			return;
		killerName = killerName.replaceAll("[^a-zA-Z0-9_]", "");
		victimName = victimName.replaceAll("[^a-zA-Z0-9_]", "");
		queue.add(new KillRow(world.getName().hashCode(), killerName, victimName, weapon));
	}

	/**
	 * @param type
	 * Type of the sign. Must be 63 or 68.
	 * @param lines
	 * The four lines on the sign.
	 */
	public void queueSignBreak(String playerName, Location loc, int type, byte data, String[] lines) {
		if (type != 63 && type != 68 || lines == null || lines.length != 4)
			return;
		queueBlock(playerName, loc, type, 0, data, lines[0] + "\0" + lines[1] + "\0" + lines[2] + "\0" + lines[3], null);
	}

	public void queueSignBreak(String playerName, Sign sign) {
		queueSignBreak(playerName, new Location(sign.getWorld(), sign.getX(), sign.getY(), sign.getZ()), sign.getTypeId(), sign.getRawData(), sign.getLines());
	}

	/**
	 * @param type
	 * Type of the sign. Must be 63 or 68.
	 * @param lines
	 * The four lines on the sign.
	 */
	public void queueSignPlace(String playerName, Location loc, int type, byte data, String[] lines) {
		if (type != 63 && type != 68 || lines == null || lines.length != 4)
			return;
		queueBlock(playerName, loc, 0, type, data, lines[0] + "\0" + lines[1] + "\0" + lines[2] + "\0" + lines[3], null);
	}

	public void queueSignPlace(String playerName, Sign sign) {
		queueSignPlace(playerName, new Location(sign.getWorld(), sign.getX(), sign.getY(), sign.getZ()), sign.getTypeId(), sign.getRawData(), sign.getLines());
	}

	public void queueChat(String player, String message) {
		queue.add(new ChatRow(player, message.replace("\\", "\\\\").replace("'", "\\'")));
	}

	@Override
	public void run() {
		if (queue.isEmpty() || !lock.tryLock())
			return;
		final Connection conn = logblock.getConnection();
		Statement state = null;
		if (getQueueSize() > 1000)
			log.info("[LogBlock Consumer] Queue overloaded. Size: " + getQueueSize());
		try {
			if (conn == null)
				return;
			conn.setAutoCommit(false);
			state = conn.createStatement();
			final long start = System.currentTimeMillis();
			int count = 0;
			process: while (!queue.isEmpty() && (System.currentTimeMillis() - start < config.timePerRun || count < config.forceToProcessAtLeast)) {
				final Row r = queue.poll();
				if (r == null)
					continue;
				for (final String player : r.getPlayers())
					if (!players.containsKey(player.hashCode()))
						if (!addPlayer(state, player)) {
							log.warning("[LogBlock Consumer] Failed to add player " + player);
							continue process;
						}
				for (final String insert : r.getInserts())
					try {
						state.execute(insert);
					} catch (final SQLException ex) {
						log.log(Level.SEVERE, "[LogBlock Consumer] SQL exception on " + insert + ": ", ex);
						break process;
					}
				count++;
			}
			conn.commit();
		} catch (final SQLException ex) {
			log.log(Level.SEVERE, "[LogBlock Consumer] SQL exception", ex);
		} finally {
			try {
				if (state != null)
					state.close();
				if (conn != null)
					conn.close();
			} catch (final SQLException ex) {
				log.log(Level.SEVERE, "[LogBlock Consumer] SQL exception on close", ex);
			}
			lock.unlock();
		}
	}

	public void writeToFile() throws FileNotFoundException {
		final long time = System.currentTimeMillis();
		final Set<Integer> insertedPlayers = new HashSet<Integer>();
		int counter = 0;
		new File("plugins/LogBlock/import/").mkdirs();
		PrintWriter writer = new PrintWriter(new File("plugins/LogBlock/import/queue-" + time + "-0.sql"));
		while (!queue.isEmpty()) {
			final Row r = queue.poll();
			if (r == null)
				continue;
			for (final String player : r.getPlayers())
				if (!players.containsKey(player.hashCode()) && !insertedPlayers.contains(player.hashCode())) {
					writer.println("INSERT IGNORE INTO `lb-players` (playername) VALUES ('" + player + "');");
					insertedPlayers.add(player.hashCode());
				}
			for (final String insert : r.getInserts())
				writer.println(insert);
			counter++;
			if (counter % 1000 == 0) {
				writer.close();
				writer = new PrintWriter(new File("plugins/LogBlock/import/queue-" + time + "-" + counter / 1000 + ".sql"));
			}
		}
		writer.close();
	}

	int getQueueSize() {
		return queue.size();
	}

	boolean hide(Player player) {
		final int hash = player.getName().hashCode();
		if (hiddenPlayers.contains(hash)) {
			hiddenPlayers.remove(hash);
			return false;
		}
		hiddenPlayers.add(hash);
		return true;
	}

	private boolean addPlayer(Statement state, String playerName) throws SQLException {
		state.execute("INSERT IGNORE INTO `lb-players` (playername) VALUES ('" + playerName + "')");
		final ResultSet rs = state.executeQuery("SELECT playerid FROM `lb-players` WHERE playername = '" + playerName + "'");
		if (rs.next())
			players.put(playerName.hashCode(), rs.getInt(1));
		rs.close();
		return players.containsKey(playerName.hashCode());
	}

	private void queueBlock(String playerName, Location loc, int typeBefore, int typeAfter, byte data, String signtext, ChestAccess ca) {
		if (playerName == null || loc == null || typeBefore < 0 || typeAfter < 0 || hiddenPlayers.contains(playerName.hashCode()) || !worlds.containsKey(loc.getWorld().getName().hashCode()) || typeBefore != typeAfter && hiddenBlocks.contains(typeBefore) && hiddenBlocks.contains(typeAfter))
			return;
		playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");
		if (signtext != null)
			signtext = signtext.replace("\\", "\\\\").replace("'", "\\'");
		queue.add(new BlockRow(loc, playerName, typeBefore, typeAfter, data, signtext, ca));
	}

	private String playerID(String playerName) {
		if (playerName == null)
			return "NULL";
		final Integer id = players.get(playerName.hashCode());
		if (id != null)
			return id.toString();
		return "(SELECT playerid FROM `lb-players` WHERE playername = '" + playerName + "')";
	}

	private static interface Row
	{
		String[] getInserts();

		String[] getPlayers();
	}

	private class BlockRow extends BlockChange implements Row
	{
		public BlockRow(Location loc, String playerName, int replaced, int type, byte data, String signtext, ChestAccess ca) {
			super(System.currentTimeMillis() / 1000, loc, playerName, replaced, type, data, signtext, ca);
		}

		@Override
		public String[] getInserts() {
			final String table = worlds.get(loc.getWorld().getName().hashCode()).table;
			final String[] inserts = new String[ca != null || signtext != null ? 2 : 1];
			inserts[0] = "INSERT INTO `" + table + "` (date, playerid, replaced, type, data, x, y, z) VALUES (FROM_UNIXTIME(" + date + "), " + playerID(playerName) + ", " + replaced + ", " + type + ", " + data + ", '" + loc.getBlockX() + "', " + loc.getBlockY() + ", '" + loc.getBlockZ() + "');";
			if (signtext != null)
				inserts[1] = "INSERT INTO `" + table + "-sign` (id, signtext) values (LAST_INSERT_ID(), '" + signtext + "');";
			else if (ca != null)
				inserts[1] = "INSERT INTO `" + table + "-chest` (id, itemtype, itemamount, itemdata) values (LAST_INSERT_ID(), " + ca.itemType + ", " + ca.itemAmount + ", " + ca.itemData + ");";
			return inserts;
		}

		@Override
		public String[] getPlayers() {
			return new String[]{playerName};
		}
	}

	private class KillRow implements Row
	{
		final long date;
		final String killer, victim;
		final int weapon;
		final int worldHash;

		KillRow(int worldHash, String attacker, String defender, int weapon) {
			date = System.currentTimeMillis() / 1000;
			this.worldHash = worldHash;
			killer = attacker;
			victim = defender;
			this.weapon = weapon;
		}

		@Override
		public String[] getInserts() {
			return new String[]{"INSERT INTO `" + worlds.get(worldHash).table + "-kills` (date, killer, victim, weapon) VALUES (FROM_UNIXTIME(" + date + "), " + playerID(killer) + ", " + playerID(victim) + ", " + weapon + ");"};
		}

		@Override
		public String[] getPlayers() {
			return new String[]{killer, victim};
		}
	}

	private class ChatRow implements Row
	{
		final long date;
		final String player, message;

		ChatRow(String player, String message) {
			date = System.currentTimeMillis() / 1000;
			this.player = player;
			this.message = message;
		}

		@Override
		public String[] getInserts() {
			return new String[]{"INSERT INTO `lb-chat` (date, playerid, message) VALUES (FROM_UNIXTIME(" + date + "), " + playerID(player) + ", '" + message + "');"};
		}

		@Override
		public String[] getPlayers() {
			return new String[]{player};
		}
	}
}
