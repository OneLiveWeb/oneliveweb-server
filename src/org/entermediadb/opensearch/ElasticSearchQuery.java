/*
 * Created on Jul 19, 2006
 */
package org.entermediadb.opensearch;

import org.openedit.hittracker.SearchQuery;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;

public class ElasticSearchQuery extends SearchQuery
{	
	
	public AbstractAggregationBuilder fieldAggregationBuilder;
	protected String fieldAggregationJson;
	
	public String getAggregationJson()
	{
		return fieldAggregationJson;
	}

	public void setAggregationJson(String inAggregationJson)
	{
		fieldAggregationJson = inAggregationJson;
	}

	public AbstractAggregationBuilder getAggregation()
	{
		return fieldAggregationBuilder;
	}

	public void setAggregation(Object inAggregationBuilder)
	{
		fieldAggregationBuilder = (AbstractAggregationBuilder) inAggregationBuilder;
		setEndUserSearch(true);
	}

	public ElasticSearchQuery()
	{
		// TODO Auto-generated constructor stub
	}

}
