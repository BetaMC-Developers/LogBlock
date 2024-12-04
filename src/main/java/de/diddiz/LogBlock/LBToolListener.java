package de.diddiz.LogBlock;

import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;

class LBToolListener extends PlayerListener
{
	private final CommandsHandler handler;
	private final LogBlock logblock;
	private final int toolID;
	private final int toolblockID;
	private final Map<Integer, WorldConfig> worlds;

	LBToolListener(LogBlock logblock) {
		this.logblock = logblock;
		handler = logblock.getCommandsHandler();
		toolID = logblock.getConfig().toolID;
		toolblockID = logblock.getConfig().toolblockID;
		worlds = logblock.getConfig().worlds;
	}

	@Override
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (!event.isCancelled() && event.getMaterial() != null) {
			final Action action = event.getAction();
			final int type = event.getMaterial().getId();
			if (type == toolID && action == Action.RIGHT_CLICK_BLOCK || type == toolblockID && (action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK)) {
				final Player player = event.getPlayer();
				final Session session = logblock.getSession(player.getName());
				if (type == toolID && session.toolEnabled && logblock.hasPermission(player, "logblock.tool") || type == toolblockID && session.toolBlockEnabled && logblock.hasPermission(player, "logblock.toolblock"))
					if (worlds.get(player.getWorld().getName().hashCode()) != null) {
						final Block block = event.getClickedBlock();
						if (!(type == toolID && block.getTypeId() == 26))
							event.setCancelled(true);
						final QueryParams params = type == toolID ? session.toolQuery : session.toolBlockQuery;
						final ToolMode mode = type == toolID ? session.toolMode : session.toolBlockMode;
						params.loc = null;
						params.sel = null;
						if (type == toolblockID && action == Action.RIGHT_CLICK_BLOCK)
							params.setLocation(block.getRelative(event.getBlockFace()).getLocation());
						else if (block.getTypeId() != 54 || params.radius != 0)
							params.setLocation(block.getLocation());
						else {
							for (final BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
								if (block.getRelative(face).getTypeId() == 54)
									params.setSelection(new CuboidSelection(player.getWorld(), block.getLocation(), block.getRelative(face).getLocation()));
							if (params.sel == null)
								params.setLocation(block.getLocation());
						}
						try {
							if (mode == ToolMode.ROLLBACK)
								handler.new CommandRollback(player, params, true);
							else if (mode == ToolMode.REDO)
								handler.new CommandRedo(player, params, true);
							else if (mode == ToolMode.CLEARLOG)
								handler.new CommandClearLog(player, params, true);
							else if (mode == ToolMode.WRITELOGFILE)
								handler.new CommandWriteLogFile(player, params, true);
							else
								handler.new CommandLookup(player, params, true);
						} catch (final Exception ex) {
							player.sendMessage(ChatColor.RED + ex.getMessage());
						}
					} else
						player.sendMessage("This world isn't logged");
			}
		}
	}
}
