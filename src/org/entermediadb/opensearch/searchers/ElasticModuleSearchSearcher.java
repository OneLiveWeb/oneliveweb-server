package org.entermediadb.opensearch.searchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.opensearch.ElasticHitTracker;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.data.Searcher;
import org.openedit.hittracker.HitTracker;
import org.openedit.hittracker.SearchQuery;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;

public class ElasticModuleSearchSearcher extends BaseOpenSearcher
{
	private static final Log log = LogFactory.getLog(ElasticModuleSearchSearcher.class);

	//search only modules as specified on the search terms in the query
	@Override
	public HitTracker search(SearchQuery inQuery)
	{
		Collection searchmodules = inQuery.getValues("searchtypes");
		if( searchmodules == null)
		{
			throw new OpenEditException("DataEditModule.loadOrSearchByTypes needs to be called on this search " + inQuery);
		}
		if( searchmodules.contains("asset"))
		{
			//We always skip assets
			searchmodules = new ArrayList(searchmodules);
			searchmodules.remove("asset");
		}
		SearchRequestBuilder search = getClient().prepareSearch(toIndexId());
		search.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
		
		
		/*
		//TODO: Auto added from advancedfilter
		AggregationBuilder b = AggregationBuilders.terms("keywords").field("keywords" + ".exact").size(100);
		search.addAggregation(b);

		b = AggregationBuilders.terms("ibmsdl_source_type").field("ibmsdl_source_type").size(20); 
		search.addAggregation(b);

		b = AggregationBuilders.terms("ibmentitycompany").field("ibmentitycompany").size(20); 
		search.addAggregation(b);

		b = AggregationBuilders.terms("ibmentitypeople").field("ibmentitypeople").size(20); 
		search.addAggregation(b);

		
		b = AggregationBuilders.terms("ibmfundingSource").field("ibmfundingSource").size(500); 
		search.addAggregation(b);

		b = AggregationBuilders.terms("ibmfilename").field("ibmfilename" + ".exact").size(100); //Used for type aheads
		search.addAggregation(b);
		
		b = AggregationBuilders.terms("trackedtopics").field("trackedtopics").size(10); 
		search.addAggregation(b);
	*/
		
		//AggregationBuilder b = AggregationBuilders.terms("keywords").field("keywords");
//
//		AggregationBuilder b = AggregationBuilders.terms("tags_count").field("keywords");
//		SumBuilder sum = new SumBuilder("assettype_sum");
//		sum.field("filesize");
//		b.subAggregation(sum);
//		search.addAggregation(b);

		search.setRequestCache(false);  //What does this do?

		BoolQueryBuilder terms = buildTerms(inQuery);
		
		//search.setTypes((String[])searchmodules.toArray(new String[searchmodules.size()]));
		
		

		addFacets(inQuery,search);

		TermQueryBuilder deleted = QueryBuilders.termQuery("emrecordstatus.recorddeleted", true);
		terms.mustNot(deleted);
		
		search.setQuery(terms);
		// search.
		addSorts(inQuery, search);
		//addFacets(inQuery, search);

		addSearcherTerms(inQuery, search);
		//addHighlights(inQuery, search);
		//search.setRequestCache(true);

		if( inQuery.getMainInput() != null)
		{
			//TODO: If we are doing a simple search then add in the fav results first?
		}
		
		if (!inQuery.isIncludeDescription())
		{
			search.setFetchSource(null, "description");
		}
		long start = System.currentTimeMillis();
		
		//search.toString()
		ElasticHitTracker hits = new ElasticHitTracker(getClient(), search, terms, 1000);
		hits.setSearcherManager(getSearcherManager());
		hits.setIndexId(getIndexId());
		hits.setSearcher(this);
		hits.setSearchQuery(inQuery);
		
		if (getSearcherManager().getShowSearchLogs(getCatalogId()))
		{
			long size = hits.size(); // order is important
			String json = search.toString();
			long end = System.currentTimeMillis() - start;
			log.info(toIndexId() + "/" + getSearchType() + "/_search' -d '" + json + "' \n" + size + " hits in: " + (double) end / 1000D + " seconds]");
		}
		
//		hits.size(); //load it up
//		long end = System.currentTimeMillis();
//		
		//log.info("Found " + hits.size() + searchmodules +  " onsf: " + search) ;
		
		return hits;
	}
	
	@Override
	public void reindexInternal() throws OpenEditException
	{
		//super.reindexInternal();
	}
	
	protected String[] listSearchModules()
	{
		Collection<Data> modules = getSearcherManager().getList(getCatalogId(), "module");
		Collection searchmodules = new ArrayList();
		for (Iterator iterator = modules.iterator(); iterator.hasNext();)
		{
			Data data = (Data) iterator.next();
			if( data.getId().equals("asset"))
			{
				continue; //Too big
			}
			String show = data.get("showonsearch");
			if( !"modulesearch".equals(data.getId() ) && Boolean.parseBoolean(show)) //Permission check?
			{
				searchmodules.add(data.getId());
			}
		}
		String[] allmodules = (String[])searchmodules.toArray(new String[searchmodules.size()]);
		return allmodules;
	}
	@Override
	public void reIndexAll() throws OpenEditException
	{
		//super.reIndexAll();
	}
	
	@Override
	public boolean initialize()
	{
		//return super.initialize();
		return true;
	}
	
	@Override
	public String getIndexId()
	{
		String[] searchmodules = listSearchModules();
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < searchmodules.length; i++)
		{
			Searcher searcher = getSearcherManager().getSearcher(getCatalogId(), searchmodules[i]);
			buffer.append(searcher.getIndexId());
		}
		return buffer.toString();
	}
	
}
