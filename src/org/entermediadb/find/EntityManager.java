package org.entermediadb.find;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.collections.map.HashedMap;
import org.entermediadb.asset.Category;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.projects.LibraryCollection;
import org.openedit.CatalogEnabled;
import org.openedit.Data;
import org.openedit.ModuleManager;
import org.openedit.cache.CacheManager;
import org.openedit.profile.UserProfile;
import org.openedit.users.User;
import org.openedit.util.DateStorageUtil;

public class EntityManager implements CatalogEnabled
{
	protected String fieldCatalogId;
	protected ModuleManager fieldModuleManager;
	protected CacheManager fieldCacheManager;
	
	public CacheManager getCacheManager()
	{
		return fieldCacheManager;
	}

	public void setCacheManager(CacheManager inCacheManager)
	{
		fieldCacheManager = inCacheManager;
	}

	public ModuleManager getModuleManager()
	{
		return fieldModuleManager;
	}

	public void setModuleManager(ModuleManager inModuleManager)
	{
		fieldModuleManager = inModuleManager;
	}

	public String getCatalogId()
	{
		return fieldCatalogId;
	}

	public void setCatalogId(String inCatalogId)
	{
		fieldCatalogId = inCatalogId;
	}

	public Collection loadCategories(String inEntityType, String inEntityId)
	{
		//TODO: Caching ability
		Collection categories = (Collection)getCacheManager().get("searchercategory" , inEntityType + "/ " + inEntityId);
		if( categories == null )
		{
			categories = getMediaArchive().query("category").exact(inEntityType, inEntityId).sort("categorypath").search();
			getCacheManager().put("searchercategory", inEntityType + "/" + inEntityId,categories);
			
		}
		return categories;
	}

	protected MediaArchive getMediaArchive()
	{
		return (MediaArchive)getModuleManager().getBean(getCatalogId(), "mediaArchive", true);
	}
	
	public String loadUploadSourcepath(Data module, Data entity, User inUser)
	{
		String mask = (String) module.getValue("uploadsourcepath");
		String sourcepath = "";
		if(mask != null)
		{
			Map values = new HashedMap();
			
			values.put("module", module);
			values.put(module.getId(), entity);
			
			sourcepath = getMediaArchive().getAssetImporter().getAssetUtilities().createSourcePathFromMask( getMediaArchive(), inUser, "", mask, values);
		}
		if( sourcepath.isEmpty() && entity != null)
		{
			
			if(module.getId().equals("librarycollection")) {
				LibraryCollection coll = (LibraryCollection) getMediaArchive().getData("librarycollection", entity.getId());
				if (coll != null)
				{
				
				
				Category uploadto  = null;
				uploadto = coll.getCategory();
				if(uploadto != null) 
				{
					sourcepath = uploadto.getCategoryPath(); 
					String year = getMediaArchive().getCatalogSettingValue("collectionuploadwithyear");
					if( year == null || Boolean.parseBoolean(year)) //Not reindexed yet
					{
						String thisyear = DateStorageUtil.getStorageUtil().formatDateObj(new Date(), "yyyy"); 
						sourcepath = sourcepath + "/" + thisyear;
					}
					sourcepath = sourcepath + "/";
				}
				}
			}
			if( sourcepath.isEmpty())
			{
				long year = Calendar.getInstance().get(Calendar.YEAR);
				sourcepath = module.getName("en") + "/" + year + "/" + entity.getName() + "/";
			}
		}
		return sourcepath;
	}	

	public Collection loadChildren(String inEntityParentType, String inParentEntityId, String inChildEntityType)
	{
		String cacheid = inEntityParentType + "/" + inParentEntityId + "/" + inChildEntityType;
		Collection entities = (Collection)getCacheManager().get("entitymanager", cacheid);
		if( entities == null)
		{
			entities = getMediaArchive().query(inChildEntityType).exact(inEntityParentType, inParentEntityId).sort("name").search();
			getCacheManager().put("entitymanager", cacheid,entities);
		}
		return entities;
	}
	
}
