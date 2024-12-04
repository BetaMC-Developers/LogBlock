package de.diddiz.LogBlock;

import static de.diddiz.util.BukkitUtils.friendlyWorldname;
import static de.diddiz.util.Utils.parseTimeSpec;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.DataFormatException;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.util.config.Configuration;

public class Config
{
	public final Map<Integer, WorldConfig> worlds;
	public final String url, user, password;
	public final int delayBetweenRuns, forceToProcessAtLeast, timePerRun;
	public final boolean useBukkitScheduler;
	public final int keepLogDays;
	public final boolean dumpDeletedLog;
	public boolean logBlockPlacings, logBlockBreaks, logSignTexts, logExplosions, logFire, logLeavesDecay, logLavaFlow, logWaterFlow, logChestAccess, logButtonsAndLevers, logKills, logChat, logSnowForm, logSnowFade;
	public final boolean logCreeperExplosionsAsPlayerWhoTriggeredThese;
	public final LogKillsLevel logKillsLevel;
	public final Set<Integer> dontRollback, replaceAnyway;
	public final int rollbackMaxTime, rollbackMaxArea;
	public final QueryParams toolQuery, toolBlockQuery;
	public final int defaultDist, defaultTime;
	public final int linesPerPage, linesLimit;
	public final int toolID, toolblockID;
	public final boolean askRollbacks, askRedos, askClearLogs, askClearLogAfterRollback;
	public final Set<Integer> hiddenPlayers, hiddenBlocks;

	public static enum LogKillsLevel {
		PLAYERS, MONSTERS, ANIMALS
	}

	Config(LogBlock logblock) throws DataFormatException, IOException {
		final Map<String, Object> def = new HashMap<String, Object>();
		def.put("version", logblock.getDescription().getVersion());
		def.put("loggedWorlds", Arrays.asList("world", "world_nether"));
		def.put("mysql.host", "localhost");
		def.put("mysql.port", 3306);
		def.put("mysql.database", "minecraft");
		def.put("mysql.user", "username");
		def.put("mysql.password", "pass");
		def.put("consumer.delayBetweenRuns", 6);
		def.put("consumer.forceToProcessAtLeast", 20);
		def.put("consumer.timePerRun", 200);
		def.put("consumer.useBukkitScheduler", true);
		def.put("clearlog.dumpDeletedLog", false);
		def.put("clearlog.keepLogDays", -1);
		def.put("logging.logCreeperExplosionsAsPlayerWhoTriggeredThese", false);
		def.put("logging.logKillsLevel", "PLAYERS");
		def.put("logging.hiddenPlayers", new ArrayList<String>());
		def.put("logging.hiddenBlocks", Arrays.asList(0));
		def.put("rollback.dontRollback", Arrays.asList(10, 11, 46, 51));
		def.put("rollback.replaceAnyway", Arrays.asList(8, 9, 10, 11, 51));
		def.put("rollback.maxTime", "2 days");
		def.put("rollback.maxArea", 50);
		def.put("lookup.defaultDist", 20);
		def.put("lookup.defaultTime", "30 minutes");
		def.put("lookup.toolID", 270);
		def.put("lookup.toolblockID", 7);
		def.put("lookup.toolQuery", "area 0 all sum none limit 15 desc silent");
		def.put("lookup.toolBlockQuery", "area 0 all sum none limit 15 desc silent");
		def.put("lookup.linesPerPage", 15);
		def.put("lookup.linesLimit", 1500);
		def.put("questioner.askRollbacks", true);
		def.put("questioner.askRedos", true);
		def.put("questioner.askClearLogs", true);
		def.put("questioner.askClearLogAfterRollback", true);
		final Configuration config = logblock.getConfiguration();
		config.load();
		for (final Entry<String, Object> e : def.entrySet())
			if (config.getProperty(e.getKey()) == null)
				config.setProperty(e.getKey(), e.getValue());
		if (!config.save())
			throw new IOException("Error while writing to config.yml");
		url = "jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getString("mysql.port") + "/" + config.getString("mysql.database");
		user = config.getString("mysql.user");
		password = config.getString("mysql.password");
		delayBetweenRuns = config.getInt("consumer.delayBetweenRuns", 6);
		forceToProcessAtLeast = config.getInt("consumer.forceToProcessAtLeast", 0);
		timePerRun = config.getInt("consumer.timePerRun", 100);
		useBukkitScheduler = config.getBoolean("consumer.useBukkitScheduler", true);
		keepLogDays = config.getInt("clearlog.keepLogDays", -1);
		if (keepLogDays * 86400000L > System.currentTimeMillis())
			throw new DataFormatException("Too large timespan for keepLogDays. Must be shorter than " + (int)(System.currentTimeMillis() / 86400000L) + " days.");
		dumpDeletedLog = config.getBoolean("clearlog.dumpDeletedLog", false);
		logCreeperExplosionsAsPlayerWhoTriggeredThese = config.getBoolean("logging.logCreeperExplosionsAsPlayerWhoTriggeredThese", false);
		try {
			logKillsLevel = LogKillsLevel.valueOf(config.getString("logging.logKillsLevel"));
		} catch (final IllegalArgumentException ex) {
			throw new DataFormatException("lookup.toolblockID doesn't appear to be a valid log level. Allowed are 'PLAYERS', 'MONSTERS' and 'ANIMALS'");
		}
		hiddenPlayers = new HashSet<Integer>();
		for (final String playerName : config.getStringList("logging.hiddenPlayers", new ArrayList<String>()))
			hiddenPlayers.add(playerName.hashCode());
		hiddenBlocks = new HashSet<Integer>();
		for (final String blocktype : config.getStringList("logging.hiddenBlocks", new ArrayList<String>())) {
			final Material mat = Material.matchMaterial(blocktype);
			if (mat != null)
				hiddenBlocks.add(mat.getId());
			else
				throw new DataFormatException("Not a valid material: '" + blocktype + "'");
		}
		dontRollback = new HashSet<Integer>(config.getIntList("rollback.dontRollback", null));
		replaceAnyway = new HashSet<Integer>(config.getIntList("rollback.replaceAnyway", null));
		rollbackMaxTime = parseTimeSpec(config.getString("rollback.maxTime").split(" "));
		rollbackMaxArea = config.getInt("rollback.maxArea", 50);
		try {
			toolQuery = new QueryParams(logblock);
			toolQuery.prepareToolQuery = true;
			toolQuery.parseArgs(new ConsoleCommandSender(logblock.getServer()), Arrays.asList(config.getString("lookup.toolQuery").split(" ")));
		} catch (final IllegalArgumentException ex) {
			throw new DataFormatException("Error at lookup.toolQuery: " + ex.getMessage());
		}
		try {
			toolBlockQuery = new QueryParams(logblock);
			toolBlockQuery.prepareToolQuery = true;
			toolBlockQuery.parseArgs(new ConsoleCommandSender(logblock.getServer()), Arrays.asList(config.getString("lookup.toolBlockQuery").split(" ")));
		} catch (final IllegalArgumentException ex) {
			throw new DataFormatException("Error at lookup.toolBlockQuery: " + ex.getMessage());
		}
		defaultDist = config.getInt("lookup.defaultDist", 20);
		defaultTime = parseTimeSpec(config.getString("lookup.defaultTime").split(" "));
		toolID = config.getInt("lookup.toolID", 270);
		if (Material.getMaterial(toolID) == null || Material.getMaterial(toolID).isBlock())
			throw new DataFormatException("lookup.toolID doesn't appear to be a valid item id");
		toolblockID = config.getInt("lookup.toolblockID", 7);
		if (Material.getMaterial(toolblockID) == null || !Material.getMaterial(toolblockID).isBlock() || toolblockID == 0)
			throw new DataFormatException("lookup.toolblockID doesn't appear to be a valid block id");
		linesPerPage = config.getInt("lookup.linesPerPage", 15);
		linesLimit = config.getInt("lookup.linesLimit", 1500);
		askRollbacks = config.getBoolean("questioner.askRollbacks", true);
		askRedos = config.getBoolean("questioner.askRedos", true);
		askClearLogs = config.getBoolean("questioner.askClearLogs", true);
		askClearLogAfterRollback = config.getBoolean("questioner.askClearLogAfterRollback", true);
		final List<String> worldNames = config.getStringList("loggedWorlds", null);
		worlds = new HashMap<Integer, WorldConfig>();
		if (worldNames == null || worldNames.size() == 0)
			throw new DataFormatException("No worlds configured");
		for (final String world : worldNames)
			worlds.put(world.hashCode(), new WorldConfig(new File("plugins/LogBlock/" + friendlyWorldname(world) + ".yml")));
		for (final WorldConfig wcfg : worlds.values()) {
			if (wcfg.logBlockPlacings)
				logBlockPlacings = true;
			if (wcfg.logBlockBreaks)
				logBlockBreaks = true;
			if (wcfg.logSignTexts)
				logSignTexts = true;
			if (wcfg.logExplosions)
				logExplosions = true;
			if (wcfg.logFire)
				logFire = true;
			if (wcfg.logLeavesDecay)
				logLeavesDecay = true;
			if (wcfg.logLavaFlow)
				logLavaFlow = true;
			if (wcfg.logWaterFlow)
				logWaterFlow = true;
			if (wcfg.logChestAccess)
				logChestAccess = true;
			if (wcfg.logButtonsAndLevers)
				logButtonsAndLevers = true;
			if (wcfg.logKills)
				logKills = true;
			if (wcfg.logChat)
				logChat = true;
			if (wcfg.logSnowForm)
				logSnowForm = true;
			if (wcfg.logSnowFade)
				logSnowFade = true;
		}
	}
}

class WorldConfig
{
	public final String table;
	public final boolean logBlockPlacings, logBlockBreaks, logSignTexts, logExplosions, logFire, logLeavesDecay, logLavaFlow, logWaterFlow, logChestAccess, logButtonsAndLevers, logKills, logChat, logSnowForm, logSnowFade;

	public WorldConfig(File file) {
		final Map<String, Object> def = new HashMap<String, Object>();
		def.put("table", "lb-" + file.getName().substring(0, file.getName().length() - 4));
		def.put("logBlockCreations", true);
		def.put("logBlockDestroyings", true);
		def.put("logSignTexts", true);
		def.put("logExplosions", true);
		def.put("logFire", true);
		def.put("logLeavesDecay", false);
		def.put("logLavaFlow", false);
		def.put("logWaterFlow", false);
		def.put("logChestAccess", false);
		def.put("logButtonsAndLevers", false);
		def.put("logKills", false);
		def.put("logChat", false);
		def.put("logSnowForm", false);
		def.put("logSnowFade", false);
		final Configuration config = new Configuration(file);
		config.load();
		for (final Entry<String, Object> e : def.entrySet())
			if (config.getProperty(e.getKey()) == null)
				config.setProperty(e.getKey(), e.getValue());
		config.save();
		table = config.getString("table");
		logBlockPlacings = config.getBoolean("logBlockCreations", true);
		logBlockBreaks = config.getBoolean("logBlockDestroyings", true);
		logSignTexts = config.getBoolean("logSignTexts", false);
		logExplosions = config.getBoolean("logExplosions", false);
		logFire = config.getBoolean("logFire", false);
		logLeavesDecay = config.getBoolean("logLeavesDecay", false);
		logLavaFlow = config.getBoolean("logLavaFlow", false);
		logWaterFlow = config.getBoolean("logWaterFlow", false);
		logChestAccess = config.getBoolean("logChestAccess", false);
		logButtonsAndLevers = config.getBoolean("logButtonsAndLevers", false);
		logKills = config.getBoolean("logKills", false);
		logChat = config.getBoolean("logChat", false);
		logSnowForm = config.getBoolean("logSnowForm", false);
		logSnowFade = config.getBoolean("logSnowFade", false);
	}
}
