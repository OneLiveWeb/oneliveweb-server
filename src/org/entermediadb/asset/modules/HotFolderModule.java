package org.entermediadb.asset.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.sources.AssetSource;
import org.entermediadb.scripts.ScriptLogger;
import org.openedit.Data;
import org.openedit.MultiValued;
import org.openedit.WebPageRequest;
import org.openedit.data.Searcher;

public class HotFolderModule extends BaseMediaModule
{
	public void loadSources(WebPageRequest inReq)  throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);		
		Collection folders = archive.getAssetManager().getAssetSources();
		Collection hotfolders = new ArrayList();
		for (Iterator iterator = folders.iterator(); iterator.hasNext();)
		{
			AssetSource source = (AssetSource) iterator.next();
			if( source.isHotFolder() )
			{
				hotfolders.add(source);
			}
		}
		inReq.putPageValue("sources", hotfolders);
	}	
	
	public AssetSource loadSource(WebPageRequest inReq)  throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);
		String id = inReq.getRequestParameter("id");
		if( id==null || "new".equals(id ))
		{
			return null;
		}
		AssetSource source = archive.getAssetManager().getSourceById(id);
		inReq.putPageValue("data", source.getConfig());
		return source;
	}	
	
	public void removeHotFolder(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);
		String id = inReq.getRequestParameter("id");
		Searcher searcher = getSearcherManager().getSearcher(archive.getCatalogId(),"hotfolder");
		Data data = (Data)searcher.searchById(id);
		searcher.delete(data, null);
		archive.getAssetManager().reloadSources();
		
	}
	public void saveFolder(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);

		String[] fields = inReq.getRequestParameters("field");
		Searcher searcher = getSearcherManager().getSearcher(archive.getCatalogId(),"hotfolder");
		String id = inReq.getRequestParameter("id");
		Data data = null;
		if( id.equals("new") )
		{
			data = searcher.createNewData();
		}
		else
		{
			data = (Data)searcher.searchById(id);
		}
		searcher.updateData(inReq, fields, data);			
		searcher.saveData(data);
		
		archive.getAssetManager().reloadSources();
	}
	
	
	public void scanFolders(WebPageRequest inReq) throws Exception
	{
		ScriptLogger inLog = (ScriptLogger)inReq.getPageValue("log");
		MediaArchive archive = getMediaArchive(inReq);
		archive.getAssetManager().scanSources(inLog);
		
	}
	
	public void importFolder(WebPageRequest inReq) throws Exception
	{
		MediaArchive archive = getMediaArchive(inReq);

		AssetSource source = loadSource(inReq);
		int found = archive.getAssetManager().importHotFolder(source, null);
		inReq.putPageValue("found", found);
	}
	
	
	public void attachCredentials(WebPageRequest inReq) {
		MediaArchive archive = getMediaArchive(inReq);

		String state = inReq.getRequestParameter("state");
		if(state == null) {
			return;
			
		}
		if(state.startsWith("hotfolder")) {
			String folderid = state.substring("hotfolder".length());
			Data folder = archive.getData("hotfolder", folderid);
			if(folder != null) {
				folder.setValue("accesstoken", inReq.getPageValue("accessToken"));
				folder.setValue("refreshtoken", inReq.getPageValue("refresh"));				
				folder.setValue("useraccount", inReq.getPageValue("useraccount"));				
				folder.setValue("googleconnected", true);
			}
			archive.saveData("hotfolder", folder);		
			AssetSource source = archive.getAssetManager().getSourceById(folderid);
			source.setConfig((MultiValued) folder);
		}
		
		
		
		
		
	}
	
	
	
	
}
