package org.entermediadb.asset.modules;

import java.util.Date;

import org.entermediadb.asset.Asset;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.convert.ConversionManager;
import org.entermediadb.asset.convert.ConvertInstructions;
import org.entermediadb.asset.upload.FileUpload;
import org.entermediadb.asset.upload.UploadRequest;
import org.openedit.Data;
import org.openedit.WebPageRequest;
import org.openedit.data.BaseData;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.event.EventManager;
import org.openedit.event.WebEvent;
import org.openedit.page.Page;
import org.openedit.repository.ContentItem;

public class ConvertStatusModule extends BaseMediaModule
{
	
	protected SearcherManager fieldSearcherManager;
	protected EventManager fieldEventManager;


	public SearcherManager getSearcherManager()
	{
		return fieldSearcherManager;
	}



	public void setSearcherManager(SearcherManager searcherManager)
	{
		fieldSearcherManager = searcherManager;
	}
	
	public EventManager getEventManager()
	{
		return fieldEventManager;
	}

	public void setEventManager(EventManager EventManager)
	{
		fieldEventManager = EventManager;
	}

	//this should kick off the groovy event by firing a path event?
	public void addConvertRequest(WebPageRequest inReq)
	{
		//sourcepath=" + asset.getSourcePath() + "preset=" + preset.getId());
		String sourcePath = inReq.getRequestParameter("sourcepath");
		if( sourcePath == null)
		{
			return;
		}
		String presetId = inReq.getRequestParameter("preset");
		
		if(presetId == null){
			presetId = inReq.getRequestParameter("presetid.value");
		}
		MediaArchive archive = getMediaArchive(inReq);

		Asset asset = archive.getAssetBySourcePath(sourcePath);
		if(presetId == null){
			return;
		}
		if(asset == null){
			return;
		}
		Searcher presetSearcher = getSearcherManager().getSearcher(archive.getCatalogId(), "convertpreset");
		
		Data preset = (Data) presetSearcher.searchById(presetId);
		
	 	BaseData settings = new BaseData();
			
		String []fields = inReq.getRequestParameters("field");
		
		for (int i = 0; i < fields.length; i++)
		{
			String field = fields[i];
			String val = inReq.getRequestParameter(field + ".value");
			if(field != null && val != null){
				settings.setValue(field, val);
			}		
		}
		
		
		settings.setProperty("presetdataid", preset.get("guid"));
		settings.setProperty("croplast", "true");
		settings.setProperty("force", "true");
		settings.setProperty("gravity", "NorthWest");
        //archive.getTranscodeTools().createOutputIfNeeded(settings, sourcePath, "jpg");
		ConversionManager manager = archive.getTranscodeTools().getManagerByFileFormat(asset.getFileFormat());
        
		ConvertInstructions instructions = manager.createInstructions(asset,preset,settings.getProperties() );
        
		instructions.setForce(true);
		
		ContentItem outputpage = archive.getContent("/WEB-INF/data/" + archive.getCatalogId() + "/generated/" + asset.getSourcePath() + "/" + preset.get("generatedoutputfile"));
		
		
//		//TODO: Re-enamble version control
//		if(outputpage.exists()){
//			getPageManager().putPage(outputpage); // this should create a new version
//		}archive
			
		
		instructions.setOutputFile(outputpage);
		//always use the 1024 - otherwise larger crops are incorrect
		
		//TODO: should do some scaling math based on the input file it selects and the numbers we got so there is no loss in quality
		
		
		Double originalheight = asset.getDouble("height");
		Double originalwidth = asset.getDouble("width");
		
		String hasheight = instructions.get("cropheight");
		//if(hasheight != null && (instructions.getMaxScaledSize().getHeight() > 768 || instructions.getMaxScaledSize().getWidth() > 1024)) {
		if(hasheight != null && originalheight != null && originalwidth != null)
		{
			//input will be the original
			boolean wide = true;
			instructions.setInputFile(archive.getOriginalContent(asset));
			if(originalheight > originalwidth) {
				wide = false;				
			}
			
			//{cropheight=165, assetid=AWEEgnrnvcTz0GAGVvnK, presetdataid=test, croplast=true, y1=101, x1=269, force=true, cropwidth=220, crop=true, outputextension=jpg, cachefilename=image.jpg}
			Double cropheight = Double.parseDouble(hasheight);
			Double cropwidth = Double.parseDouble(instructions.get("cropwidth"));
			Double x1 = Double.parseDouble(instructions.get("x1"));
			Double y1 = Double.parseDouble(instructions.get("y1"));

			Double scalefactor = 1d;
			
			Double croppreviewwidth = 1024d;
			if (instructions.get("cropprevieww") != null) {
				croppreviewwidth = Double.parseDouble(instructions.get("cropprevieww"));
			}
			Double croppreviewheight = 768d;
			if (instructions.get("croppreviewh") != null) {
				Double.parseDouble(instructions.get("croppreviewh"));
			}
			//Scale down to deal with big images
			if(originalwidth > croppreviewwidth && wide) 
			{
				//scalefactor = 1024d/originalwidth;
				scalefactor = croppreviewwidth/originalwidth;
			}
			else if( originalheight > croppreviewheight)
			{
				scalefactor = croppreviewheight/originalheight;
			}

			cropheight = cropheight/scalefactor;
			cropwidth = cropwidth/scalefactor;
			x1 = x1/scalefactor;
			y1 = y1/scalefactor;
			
			instructions.setProperty("cropheight", Integer.toString(cropheight.intValue()));
			instructions.setProperty("cropwidth", Integer.toString(cropwidth.intValue()));
			instructions.setProperty("x1", Integer.toString(x1.intValue()));
			instructions.setProperty("y1", Integer.toString(y1.intValue()));
			instructions.setOutputFile(outputpage);
			
		}
		
		
		
		
		if("image1024x768.jpg".equals(preset.get("generatedoutputfile"))){
			Page s1024 = getPageManager().getPage("/WEB-INF/data/" + archive.getCatalogId()	+ "/generated/" + asset.getPath() + "/image1024x768.jpg");
			instructions.setInputFile(s1024.getContentItem());//So it doesn't go back to the original when cropping 
		}
	
	
		manager.createOutput(instructions); //This will go back to the original if needed
	
		Searcher tasks = archive.getSearcher("conversiontask");
		Data task = tasks.query().exact("presetid", preset.getId()).exact("assetid", asset.getId()).searchOne();

		if( task == null)
		{
			task = tasks.createNewData();
			task.setProperty("presetid", preset.getId());
			task.setProperty("assetid", asset.getId());
		}
		task.setValue("submitteddate", new Date());
		task.setValue("completed", new Date());
		task.setValue("status", "complete");
		tasks.saveData(task);
		
		archive.fireMediaEvent("usercrop",inReq.getUser(),asset );
		
		processConversions(inReq);//non-block
		
		archive.saveAsset(asset); //Updates the lastmoddate for push
	}
	
	public void processConversions(WebPageRequest inReq)
	{
		
		WebEvent event = new WebEvent();
		event.setSource(this);
		MediaArchive archive = getMediaArchive(inReq);
		event.setCatalogId(archive.getCatalogId());
		event.setOperation("runconversions");
		event.setUser(inReq.getUser());
		//log.info(getEventManager());
		getEventManager().fireEvent(event);
	}
	
	
	
	public void uploadConversionDocument(WebPageRequest inReq){
		MediaArchive archive = getMediaArchive(inReq);
		FileUpload command = (FileUpload) archive.getSearcherManager().getModuleManager().getBean("fileUpload");
		UploadRequest properties = command.parseArguments(inReq);
		
		if (properties == null) {
			return;
		}
		if (properties.getFirstItem() == null) {
			return;
			
		}
		String assetid = inReq.getRequestParameter("assetid");
		
		String presetid = inReq.getRequestParameter("presetid");
		Data preset  = getSearcherManager().getData(archive.getCatalogId(), "convertpreset",presetid);
		Asset current = archive.getAsset(assetid);
	
		
		String generated = "/WEB-INF/data/" + archive.getCatalogId()	+ "/generated/" + current.getSourcePath() + "/" + preset.get("generatedoutputfile");
		properties.saveFileAs(properties.getFirstItem(), generated, inReq.getUser());

		boolean newdefault = Boolean.parseBoolean(inReq.getRequestParameter("replaceall"));
		if(newdefault){
			
		}
		archive.fireMediaEvent("saved", inReq.getUser(), current);

		inReq.putPageValue("asset", current);
		archive.saveAsset(current);
	}

	public void handleCustomThumb(WebPageRequest inReq){
		MediaArchive archive = getMediaArchive(inReq);
		FileUpload command = (FileUpload) archive.getSearcherManager().getModuleManager().getBean("fileUpload");
		UploadRequest properties = command.parseArguments(inReq);
		
		if (properties == null) {
			return;
		}
		if (properties.getFirstItem() == null) {
			return;
			
		}
		Asset current = getAsset(inReq);
		String input = "/WEB-INF/data/" + archive.getCatalogId()	+ "/generated/" + current.getSourcePath() + "/" + properties.getFirstItem().getName(); //TODO: Should run a conversion here first to ensure this is a large JPG
		properties.saveFileAs(properties.getFirstItem(), input, inReq.getUser());
		
		//String s1024 = "/WEB-INF/data/" + archive.getCatalogId()	+ "/generated/" + current.getSourcePath() + "/image1024x768.jpg"; //TODO: Should run a conversion here first to ensure this is a large JPG
		
		//archive.getPresetManager().getPresetByOutputName(archive, "image", "image1024x768");
		
        ConversionManager c = archive.getTranscodeTools().getManagerByRenderType("image");
		ConvertInstructions instructions = c.createInstructions(current,"image1024x768.jpg");
		instructions.setForce(true);
		instructions.setInputFile(archive.getContent( input ) );
	 	c.createOutput(instructions);

	 	String png1024 = "/WEB-INF/data/" + archive.getCatalogId()	+ "/generated/" + current.getSourcePath() + "/image1500x1500.png"; //TODO: Should run a conversion here first to ensure this is a large JPG
		instructions.setOutputFile(archive.getContent( png1024) );
	 	c.createOutput(instructions);
		
		 archive.removeGeneratedImages(current, false);
		 reloadThumbnails( inReq, archive, current);
		
	}
	public void rerunSmallerThumbnails(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		Asset asset = getAsset(inReq);
		if (asset != null) 
		{
			Page s1024 = getPageManager().getPage("/WEB-INF/data/" + archive.getCatalogId()	+ "/generated/" + asset.getSourcePath() + "/image1024x768.jpg"); 
			Page crop1024 = getPageManager().getPage("/WEB-INF/data/" + archive.getCatalogId()	+ "/generated/" + asset.getSourcePath() + "/customthumb.jpg");
			getPageManager().copyPage(s1024, crop1024);
			archive.removeGeneratedImages(asset, false);
			reloadThumbnails( inReq, archive, asset);
		}
	}
	public void rerunAllThumbnails(WebPageRequest inReq)
	{
		MediaArchive archive = getMediaArchive(inReq);
		Asset asset = getAsset(inReq);
		archive.removeGeneratedImages(asset, true);
		reloadThumbnails( inReq, archive, asset);

	}
	protected void reloadThumbnails(WebPageRequest inReq, MediaArchive archive, Asset current)
	{
		archive.getPresetManager().queueConversions(archive,archive.getSearcher("conversiontask"),current,true);
		//current.setProperty("importstatus", "imported");
		//archive.fireMediaEvent("importing/assetsimported", inReq.getUser());
		//archive.fireMediaEvent("conversions/thumbnailreplaced", inReq.getUser(), current);
		archive.fireMediaEvent("conversions","runconversion", inReq.getUser(), current);//block
		//archive.fireMediaEvent("asset/saved", inReq.getUser(), current);

		inReq.putPageValue("asset", current);
		archive.saveAsset(current);
	}
	public void reloadIndex(WebPageRequest inReq)
	{
		Asset asset = getAsset(inReq);
		String path = inReq.getContentPage().getDirectory();
		inReq.redirect(path + "/index.html?assetid=" + asset.getId());
	}

}
