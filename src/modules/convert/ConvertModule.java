package modules.convert;

import java.util.Date;

import org.entermediadb.asset.Asset;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.convert.QueueManager;
import org.entermediadb.asset.modules.BaseMediaModule;
import org.entermediadb.scripts.ScriptLogger;
import org.openedit.Data;
import org.openedit.WebPageRequest;
import org.openedit.data.Searcher;
import org.openedit.util.DateStorageUtil;

public class ConvertModule extends BaseMediaModule
{
	
	public void convertAsset(WebPageRequest inReq) throws Exception
	{
		//load up the preset id
		String presetid = inReq.findValue("presetid");
		String assetid = inReq.findValue("assetid");
		
		MediaArchive archive = getMediaArchive(inReq);
		Asset asset = archive.getAsset(assetid);
		if(asset == null){
			return;//nothing to do, missing asset
		}
		
		
		Searcher presetsearcher = archive.getSearcher("convertpreset");
		Searcher tasksearcher = archive.getSearcher("conversiontask");
		Data preset = (Data) archive.getSearcher("convertpreset").searchById( presetid );
		
		Data one = tasksearcher.query().match("assetid", asset.getId() ).match("presetid", presetid ).searchOne();
		
		if( one == null )
		{
			one = tasksearcher.createNewData();
			one.setProperty("status", "new");
		}
		else
		{
			one = (Data) tasksearcher.searchById(one.getId());
		}
		String status = one.get("status");
		if( status != "complete")
		{
			//TODO: Lock the asset?
			one.setSourcePath(asset.getSourcePath());
			one.setProperty("status", "new");
			one.setProperty("assetid", asset.getId() );
			one.setProperty("presetid", preset.getId() );
			one.setProperty("ordering", preset.get("ordering") );
			String nowdate = DateStorageUtil.getStorageUtil().formatForStorage(new Date() );
			one.setProperty("submitted", nowdate);
			tasksearcher.saveData(one, null);
		}
		
		inReq.putPageValue("asset", asset);
		inReq.putPageValue("preset", preset);
		inReq.putPageValue("conversiontask", one);
		
		String export = archive.asExportFileName(asset,preset);
		inReq.putPageValue("exportname", export);
		
		if( status.equals("complete") )
		{
			return;
		}
		archive.fireMediaEvent("conversions" , "runconversion", inReq.getUser(), asset); //this blocks does not use the queue	
		
	}
	public void checkQueue(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		QueueManager manager = (QueueManager)getModuleManager().getBean(archive.getCatalogId(),"queueManager");
		manager.checkQueue();
		ScriptLogger logger = (ScriptLogger)inReq.getPageValue("log");
		if( logger != null)
		{
			logger.info("Total Pending Tasks: " + manager.getTotalPending());
			logger.info("Threads running: " + manager.runningProcesses());
		}
	}	
}
