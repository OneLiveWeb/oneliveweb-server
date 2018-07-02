package org.entermediadb.asset.modules;

import org.entermediadb.asset.Asset;
import org.entermediadb.asset.EnterMedia;
import org.entermediadb.asset.MediaArchive;
import org.openedit.WebPageRequest;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.modules.BaseModule;
import org.openedit.profile.UserProfile;
import org.openedit.users.GroupSearcher;
import org.openedit.users.UserManager;
import org.openedit.users.UserSearcher;
import org.openedit.util.PathUtilities;

public class BaseMediaModule extends BaseModule
{
	public EnterMedia getEnterMedia(String inApplicationId)
	{
		EnterMedia matt = (EnterMedia) getModuleManager().getBean(inApplicationId, "enterMedia");
		matt.setApplicationId(inApplicationId);
		return matt;
	}

	public EnterMedia getEnterMedia(WebPageRequest inReq)
	{
		String appid = inReq.findValue("applicationid");
		EnterMedia matt = getEnterMedia(appid);
		inReq.putPageValue("enterMedia", matt); //do not use
		inReq.putPageValue("entermedia", matt);
		inReq.putPageValue("applicationid", appid);
		inReq.putPageValue("apphome", "/" + appid);
		
		String prefix = inReq.getContentProperty("themeprefix");
		UserProfile profile = inReq.getUserProfile();
		if( profile != null)
		{
			prefix = profile.replaceUserVariable(prefix);
		}
		inReq.putPageValue("themeprefix", prefix);

		return matt;
	}

	public String loadApplicationId(WebPageRequest inReq) throws Exception
	{
		String applicationid = inReq.findValue("applicationid");
		inReq.putPageValue("applicationid", applicationid);
		inReq.putPageValue("apphome", "/" + applicationid);

		
		
		String prefix = inReq.getContentProperty("themeprefix");
		UserProfile profile = inReq.getUserProfile();
		if( profile != null)
		{
			prefix = profile.replaceUserVariable(prefix);
		}
		inReq.putPageValue("themeprefix", prefix);
		return applicationid;
	}

	public MediaArchive getMediaArchive(String inCatalogid)
	{
		if (inCatalogid == null)
		{
			return null;
		}
		MediaArchive archive = (MediaArchive) getModuleManager().getBean(inCatalogid, "mediaArchive");
		return archive;
	}

	public MediaArchive getMediaArchive(WebPageRequest inReq)
	{
		MediaArchive archive = (MediaArchive)inReq.getPageValue("mediaarchive");
		String catalogid = null;
		if( archive == null)
		{
			catalogid = inReq.findValue("catalogid");
			if (catalogid == null || "$catalogid".equals(catalogid))
			{
				return null;
			}
		}
		if( archive == null)
		{
			archive = getMediaArchive(catalogid);
		}
		inReq.putPageValue("mediaarchive", archive);
		inReq.putPageValue("cataloghome", archive.getCatalogHome());
		String mediadb = archive.getMediaDbId();
		inReq.putPageValue("mediadbappid", mediadb);
		inReq.putPageValue("catalogid", archive.getCatalogId()); // legacy
		return archive;
	}
	public SearcherManager getSearcherManager()
	{
		return (SearcherManager)getModuleManager().getBean("searcherManager");
	}
	
	
	public Asset getAsset(WebPageRequest inReq)
	{
		Object found = inReq.getPageValue("asset");
		if( found instanceof Asset)
		{
			return (Asset)found;
		}
		
		MediaArchive archive = getMediaArchive(inReq);
		Asset asset = null;

		String assetid = inReq.getRequestParameter("assetid");
			
		if( assetid != null )
		{
			Asset data = archive.getAsset(assetid, inReq);
			inReq.putPageValue("asset", data);
			inReq.putPageValue("data", data);
			return (Asset) data;
		}

		if( Boolean.parseBoolean( inReq.getContentProperty("assetpageid") ) ) 
		{
			String id = PathUtilities.extractPageName(inReq.getPath());
			asset = archive.getAsset(id);
		}
		

		if( Boolean.parseBoolean( inReq.getContentProperty("assetfolderid") ) ) 
		{
			String id = PathUtilities.extractDirectoryName(inReq.getPath());
			asset = archive.getAsset(id);
		}
		if( asset == null)
		{
			String sourcePath = inReq.getRequestParameter("sourcepath");
			
			if (sourcePath != null)
			{
				//asset = archive.getAssetArchive().getAssetBySourcePath(sourcePath, true);
				asset = archive.getAssetSearcher().getAssetBySourcePath(sourcePath, true);
			}
		}
		if (asset == null && archive != null)
		{
			asset = archive.getAssetBySourcePath(inReq.getContentPage());
			if (asset == null)
			{
				if (assetid != null)
				{
					asset = archive.getAsset(assetid);
				}
			}
		}
		if( inReq.getParent() != null)
		{
			inReq.getParent().putPageValue("asset", asset);
		}
		else
		{
			inReq.putPageValue("asset", asset);
		}
		return asset;
	}
	
	public Searcher loadSearcher(WebPageRequest inReq) throws Exception
	{
		// Load by url
		// catalogid/type.html
		inReq.putPageValue("searcherManager", getSearcherManager());
		String fieldname = resolveSearchType(inReq);
		if (fieldname == null)
		{
			return null;
		}
		String catalogId = resolveCatalogId(inReq);

		org.openedit.data.Searcher searcher = getSearcherManager().getSearcher(catalogId, fieldname);
		inReq.putPageValue("searcher", searcher);
		inReq.putPageValue("detailsarchive", searcher.getPropertyDetailsArchive());
		return searcher;
	}

	protected String resolveCatalogId(WebPageRequest inReq)
	{
		String catalogId = null;//inReq.getRequestParameter("catalogid");
		if (catalogId == null)
		{
			catalogId = inReq.findValue("catalogid");
		}
		if( catalogId == null)
		{
			catalogId = inReq.findValue("applicationid");
		}
		inReq.putPageValue("catalogid", catalogId);
		
		return catalogId;
	}

	protected String resolveSearchType(WebPageRequest inReq)
	{
		String searchtype = inReq.findValue("searchtype");

		inReq.putPageValue("searchtype", searchtype);
		return searchtype;
	}
	
	/**
	 * are these used?
	 * @param inReq
	 * @return
	 */
	public UserSearcher getUserSearcher(WebPageRequest inReq){
		MediaArchive archive = getMediaArchive(inReq);
		return archive.getUserManager().getUserSearcher();
		
	}
	public GroupSearcher getGroupSearcher(WebPageRequest inReq){
		MediaArchive archive = getMediaArchive(inReq);
		return archive.getUserManager().getGroupSearcher();
		
	}
	public UserManager getUserManager(WebPageRequest inReq){
		MediaArchive archive = getMediaArchive(inReq);
		return archive.getUserManager();
		
	}
	
}
