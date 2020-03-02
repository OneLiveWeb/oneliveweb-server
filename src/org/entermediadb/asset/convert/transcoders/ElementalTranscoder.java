package org.entermediadb.asset.convert.transcoders;

import org.dom4j.Element;
import org.entermediadb.asset.convert.BaseTranscoder;
import org.entermediadb.asset.convert.ConvertInstructions;
import org.entermediadb.asset.convert.ConvertResult;
import org.entermediadb.elemental.ElementalManager;
import org.openedit.Data;

public class ElementalTranscoder extends BaseTranscoder
{

	@Override
	public ConvertResult convert(ConvertInstructions inStructions)
	{
		if( inStructions.getConversionTask() == null)
		{
			//Must be done with a queue
			ConvertResult result = new ConvertResult();
			result.setComplete(false);
			result.setOk(true);
			return result;
		}
		ElementalManager manager = (ElementalManager) inStructions.getMediaArchive().getBean("elementalManager");

		//Get the task
		//inStructions.getC
		Element job = manager.createJob(inStructions);
		
		//Wait for it to finish?
		String jobid = job.attributeValue("jobid");
		
		ConvertResult result = new ConvertResult();
		result.setProperty("externalid", jobid);
		result.setComplete(false);
		result.setOk(true);
		return result;
	}

	
	public ConvertResult updateStatus(Data inTask, ConvertInstructions inStructions)
	{
		ElementalManager manager = (ElementalManager) inStructions.getMediaArchive().getBean("elementalManager");
		return manager.updateJobStatus( inTask);
		
	}
	
	
	
}
