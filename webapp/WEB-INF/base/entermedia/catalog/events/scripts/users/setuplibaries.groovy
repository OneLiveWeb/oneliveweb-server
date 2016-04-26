package users

import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.scanner.HotFolderManager
import org.openedit.Data
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.page.Page

public void init()
{
		MediaArchive mediaArchive = context.getPageValue("mediaarchive");//Search for all files looking for videos
		
		
		
		HotFolderManager manager =mediaArchive.getModuleManager().getBean(mediaArchive.getCatalogId(), "hotFolderManager");
		
		String catalogId = mediaArchive.getCatalogId();
		
		
		
		Searcher searcher = mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(), "user");
		Searcher libraries = mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(), "library");
		Searcher collectionsearcher = mediaArchive.getSearcherManager().getSearcher(mediaArchive.getCatalogId(), "librarycollection");
		
		
		
		//Loop over every user profile and move the userid colum into the id column
		 
		HitTracker profiles = searcher.getAllHits();
		profiles.enableBulkOperations();
		int ok = 0;
		profiles.each
		{
		
			Data hit =  it;
			//Create a hotfolder
			
			String path = "/WEB-INF/data/" + catalogId + "/originals/hotfolders/${it.id}/";
			Data existing = manager.getFolderByPathEnding(catalogId, "test");
			if( existing != null)
			{
				manager.deleteFolder(catalogId,existing);
			}
			Searcher hotfolders = mediaArchive.getSearcher("hotfolder").createNewData();
			
			Data newrow = hotfolders.searchById("user-${it.id}");
			if(newrow == null){
				newrow = hotfolders.createNewData();
				newrow.setName("Hot Folder for ${it} (${it.id})");
				newrow.setProperty("subfolder", "hotfolders/${hit.id}");
				newrow.setProperty("externalpath", path);
				newrow.setProperty("includes", path);
				newrow.setProperty("excludes", path);
				if(hit.get("syncthing") != null){
					newrow.setProperty("hotfoldertype", "syncthing");
					newrow.setProperty("deviceid", hit.get("syncthing"));
					
					
				}
					
				manager.saveFolder(catalogId,newrow);
			}
					
			
			
			//Create a library
			
			Data userlibrary = libraries.searchById("${hit.id}-library");
			if(userlibrary == null){
				userlibrary = libraries.createNewData();
				userlibrary.setId("${hit.id}-library");
				userlibrary.setName("${it}'s Library");
			}
			userlibrary.setProperty("folder", "hotfolders/${hit.id}");
			
			libraries.saveData(userlibrary);
			
			// create some collections
			HashMap collections = new HashMap();
			collections.put("document", "Document");
			collections.put("audio", "Audio");
			collections.put("video", "Video");
			collections.put("photo", "Photo");
			
			collections.keySet().each {
				
				String collectionpath = "/WEB-INF/data/" + catalogId + "/originals/hotfolders/${hit.id}/${it}/";
				Page colfolder = mediaArchive.getPageManager().getPage(collectionpath);
				if(!colfolder.exists()){
					
					mediaArchive.getPageManager().putPage(colfolder);
					
				}
				
				Data collection = collectionsearcher.searchById("${hit.id}-${it}-collection");
				if(collection == null){
					collection = collectionsearcher.createNewData();
					collection.setId("${hit.id}-${it}-collection");
					collection.setName(collections.get(it));
					collection.setProperty("library", userlibrary.getId());
					collection.setProperty("owner", hit.getId());
					collectionsearcher.saveData(collection);	
					
				}
				
				
			}
			
			
		}
		
		log.info("verified  ${ok}");
		
}

init();