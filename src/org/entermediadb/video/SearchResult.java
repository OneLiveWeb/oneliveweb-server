package org.entermediadb.video;

import org.entermediadb.asset.util.MathUtils;
import org.openedit.Data;

public class SearchResult
{

	public String getType()
	{
		return fieldType;
	}
	public void setType(String inType)
	{
		fieldType = inType;
	}
	Data fieldData;
	
	public Data getData()
	{
		return fieldData;
	}
	public void setData(Data inData)
	{
		fieldData = inData;
	}
	String fieldType;
	long fieldStartTime;
	public long getStartTime()
	{
		return fieldStartTime;
	}
	public void setStartTime(long inStartTime)
	{
		fieldStartTime = inStartTime;
	}

	public String getStartSecondsAndHours()
	{
		return MathUtils.toDuration(getStartTime());
	}
	
}
