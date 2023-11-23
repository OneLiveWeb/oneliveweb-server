package org.entermediadb.opensearch.searchers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.entermediadb.opensearch.ElasticHitTracker;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.data.PropertyDetail;
import org.openedit.hittracker.HitTracker;
import org.openedit.hittracker.SearchQuery;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchType;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;

public class ElasticModuleSearchKeywordSearcher extends BaseOpenSearcher
{
	private static final Log log = LogFactory.getLog(ElasticModuleSearchKeywordSearcher.class);

	//search only modules as specified on the search terms in the query
	@Override
	public HitTracker search(SearchQuery inQuery)
	{
		
		Collection details = getSearcherManager().getSearcher(getCatalogId(),"modulesearch").getPropertyDetails().getDetailsByType("datatype", "list");
		List tables = new ArrayList();
		for (Iterator iterator = details.iterator(); iterator.hasNext();)
		{
			PropertyDetail detail = (PropertyDetail) iterator.next();
			String listd = detail.getListId();
			tables.add(listd);
		}
				
			
		String[] searchmodules = (String[])tables.toArray(new String[tables.size()]);//listSearchModules();
		
		SearchRequestBuilder search = getClient().prepareSearch(toIndexId());
		search.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
		
		//search.setTypes(searchmodules);

		search.setRequestCache(false);  //What does this do?

		BoolQueryBuilder terms = buildTerms(inQuery);

		//addFacets(inQuery,search);

		TermQueryBuilder deleted = QueryBuilders.termQuery("emrecordstatus.recorddeleted", true);
		terms.mustNot(deleted);
		
		search.setQuery(terms);
		// search.
		addSorts(inQuery, search);
		//addFacets(inQuery, search);

		addSearcherTerms(inQuery, search);
		//addHighlights(inQuery, search);
		//search.setRequestCache(true);

		search.setFetchSource(null, "description");
		long start = System.currentTimeMillis();
		
		//search.toString()
		ElasticHitTracker hits = new ElasticHitTracker(getClient(), search, terms, 1000);
		hits.setSearcherManager(getSearcherManager());
		hits.setIndexId(getIndexId());
		hits.setSearcher(this);
		hits.setSearchQuery(inQuery);
		hits.size(); //load it up
		long end = System.currentTimeMillis();
		
		log.info("Found " + hits.size() + " in " + ((end-start)) + " milli-seconds for " + search) ;
		
		return hits;
	}
	
	@Override
	public void reindexInternal() throws OpenEditException
	{
		//super.reindexInternal();
	}
	
	
	protected String[] listSearchModules()
	{
		String[] allmodules = (String[])getCacheManager().get("modulesearch","all");
		if( allmodules == null)
		{
			Collection<Data> modules = getSearcherManager().getList(getCatalogId(), "module");
			Collection searchmodules = new ArrayList();
			for (Iterator iterator = modules.iterator(); iterator.hasNext();)
			{
				Data data = (Data) iterator.next();
				String show = data.get("showonsearch");
				if( !"modulesearch".equals(data.getId() ) && Boolean.parseBoolean(show)) //Permission check?
				{
					searchmodules.add(data.getId());
				}
			}
			allmodules = (String[])searchmodules.toArray(new String[searchmodules.size()]);
		}
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
}
