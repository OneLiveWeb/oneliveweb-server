package org.entermediadb.asset.sources;


import java.io.File;
import java.io.InputStream;
import java.util.List;

import org.entermediadb.asset.Asset;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.pull.PullManager;
import org.openedit.ModuleManager;
import org.openedit.OpenEditException;
import org.openedit.repository.ContentItem;
import org.openedit.repository.filesystem.FileItem;
import org.openedit.users.User;

public class EntermediaAssetSource extends BaseAssetSource
{

	protected ModuleManager fieldModuleManager;
	protected PullManager fieldPullManager;
	

	public PullManager getPullManager()
	{
		if (fieldPullManager == null)
		{
			fieldPullManager = (PullManager) getMediaArchive().getBean("pullManager");			
		}

		return fieldPullManager;
	}

	public void setPullManager(PullManager inPullManager)
	{
		fieldPullManager = inPullManager;
	}


	
	@Override
	public InputStream getOriginalDocumentStream(Asset inAsset) throws OpenEditException
	{
		return getPullManager().getOriginalDocumentStream(getMediaArchive(),inAsset);
	}

	public ContentItem getOriginalContent(Asset inAsset, boolean downloadifNeeded)
	{
		

		return getPullManager().downloadOriginal(getMediaArchive(), inAsset, getFile(inAsset), downloadifNeeded);
	}



	@Override
	public boolean handles(Asset inAsset) {
		
		String localid = getMediaArchive().getNodeManager().getLocalClusterId();
		String clusterid = inAsset.get("mastereditclusterid");
		if(clusterid != null && !clusterid.equals(localid) ) {
			return true;
		}
		return false;
	}

	@Override
	public boolean removeOriginal(Asset inAsset)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Asset addNewAsset(Asset inAsset, List<ContentItem> inTemppages)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Asset replaceOriginal(Asset inAsset, List<ContentItem> inTemppages)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Asset assetOrginalSaved(Asset inAsset)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void detach()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void saveConfig()
	{
		// TODO Auto-generated method stub

	}

	@Override
	public int importAssets(String inBasepath)
	{
		return (int)getPullManager().processPullQueue(getMediaArchive());
	}

	@Override
	public void checkForDeleted()
	{
		// TODO Auto-generated method stub

	}

	@Override
	protected ContentItem checkLocation(Asset inAsset, ContentItem inUploaded, User inUser)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ContentItem getOriginalContent(Asset inAsset)
	{
		return getOriginalContent(inAsset, true);

	}
	
	
	protected File getFile(Asset inAsset)
	{
		String path = "/WEB-INF/data" + getMediaArchive().getCatalogHome() + "/originals/";
		path = path + inAsset.getSourcePath(); //Check archived?

		String primaryname = inAsset.getPrimaryFile();
		if (primaryname != null && inAsset.isFolder())
		{
			path = path + "/" + primaryname;
		}
		return new File(getMediaArchive().getPageManager().getPage(path).getContentItem().getAbsolutePath());
		
		
	}
	
	
	

}
