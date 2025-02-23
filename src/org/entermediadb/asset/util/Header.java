/*
 * Created on Aug 15, 2005
 */
package org.entermediadb.asset.util;

import java.util.ArrayList;
import java.util.List;

public class Header
{
	protected List fieldHeaderNames;

	public List getHeaderNames()
	{
		if (fieldHeaderNames == null)
		{
			fieldHeaderNames = new ArrayList();
		}
		return fieldHeaderNames;
	}
	public void setHeaders(String[] inHeaders)
	{
		for (int i = 0; i < inHeaders.length; i++)
		{
			//Integer integer = new Integer(i);
			getHeaderNames().add(inHeaders[i]);
		}
	}

	public int getIndex(String inName)
	{
		return getHeaderNames().indexOf(inName);
	}
	public String getColumn(int inIndex)
	{
		String name = (String)getHeaderNames().get(inIndex);
		return name;
	}
	public int getSize()
	{
		return getHeaderNames().size();
	}

}
