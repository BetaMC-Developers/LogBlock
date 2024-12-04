package de.diddiz.LogBlock;

public class Session
{
	public QueryParams lastQuery = null;
	public boolean toolEnabled = true;
	public QueryParams toolQuery;
	public ToolMode toolMode;
	public boolean toolBlockEnabled = true;
	public QueryParams toolBlockQuery;
	public ToolMode toolBlockMode;
	public LookupCacheElement[] lookupCache = null;
	public int page = 1;

	Session(LogBlock logblock) {
		toolQuery = logblock.getConfig().toolQuery.clone();
		toolBlockQuery = logblock.getConfig().toolBlockQuery.clone();
	}
}
