package org.entermediadb.opensearch.searchers;


import org.openedit.hittracker.SearchQuery;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;

public class ElasticLogSearcher extends BaseElasticSearcher  {

	
	/**
	 * @override
	 */
	protected boolean isTrackEdits()
	{
		return false;
	}
	
	
	
	@Override
	protected void addSearcherTerms(SearchQuery inQuery, SearchRequestBuilder inSearch)
	{
		// TODO Auto-generated method stub
		DateHistogramAggregationBuilder builder = new DateHistogramAggregationBuilder("event_breakdown_day");
		builder.field("date");
		builder.dateHistogramInterval(DateHistogramInterval.DAY);
		
//		DateHistogramBuilder builder = new DateHistogramBuilder("event_breakdown");
//		builder.interval(DateHistogramInterval.DAY);
		
		inSearch.addAggregation(builder);
		
		 builder = new DateHistogramAggregationBuilder("event_breakdown_week");
		builder.field("date");
		builder.dateHistogramInterval(DateHistogramInterval.WEEK);
		
//		DateHistogramBuilder builder = new DateHistogramBuilder("event_breakdown");
//		builder.interval(DateHistogramInterval.DAY);
		
		inSearch.addAggregation(builder);
		
		
	}
	
	
	

}
