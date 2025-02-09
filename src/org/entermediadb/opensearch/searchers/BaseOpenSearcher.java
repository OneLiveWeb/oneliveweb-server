package org.entermediadb.opensearch.searchers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.join.ScoreMode;
import org.entermediadb.asset.MediaArchive;
import org.entermediadb.asset.cluster.IdManager;
import org.entermediadb.data.FullTextLoader;
import org.entermediadb.location.Position;
import org.entermediadb.opensearch.ElasticHitTracker;
import org.entermediadb.opensearch.ElasticSearchQuery;
import org.entermediadb.opensearch.OpenNodeManager;
import org.entermediadb.opensearch.SearchHitData;
import org.json.simple.JSONObject;
import org.openedit.Data;
import org.openedit.MultiValued;
import org.openedit.OpenEditException;
import org.openedit.WebPageRequest;
import org.openedit.cache.CacheManager;
import org.openedit.data.BaseSearcher;
import org.openedit.data.PropertyDetail;
import org.openedit.data.PropertyDetails;
//import org.openedit.data.QueryBuilder;
import org.openedit.data.SearchData;
import org.openedit.data.Searcher;
import org.openedit.hittracker.GeoFilter;
import org.openedit.hittracker.HitTracker;
import org.openedit.hittracker.SearchQuery;
import org.openedit.hittracker.Term;
import org.openedit.modules.translations.LanguageMap;
import org.openedit.page.Page;
import org.openedit.page.manage.PageManager;
import org.openedit.repository.ContentItem;
import org.openedit.users.User;
import org.openedit.util.DateStorageUtil;
import org.openedit.util.IntCounter;
import org.openedit.util.OutputFiller;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.opensearch.action.admin.indices.flush.FlushRequest;
import org.opensearch.action.admin.indices.flush.FlushResponse;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.opensearch.action.admin.indices.refresh.RefreshResponse;
import org.opensearch.action.bulk.BackoffPolicy;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkProcessor;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteRequestBuilder;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchType;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.AdminClient;
import org.opensearch.client.Client;
import org.opensearch.client.Requests;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.collect.ImmutableOpenMap;
import org.opensearch.common.geo.GeoDistance;
import org.opensearch.common.geo.GeoPoint;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.Settings.Builder;
import org.opensearch.common.unit.ByteSizeUnit;
import org.opensearch.common.unit.ByteSizeValue;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.GeoDistanceQueryBuilder;
import org.opensearch.index.query.MatchPhrasePrefixQueryBuilder;
import org.opensearch.index.query.MatchPhraseQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.Operator;
import org.opensearch.index.query.PrefixQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.QueryStringQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.WildcardQueryBuilder;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.AggregatorFactories;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.opensearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.opensearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.opensearch.search.aggregations.metrics.SumAggregationBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.opensearch.transport.RemoteTransportException;

import groovy.json.JsonOutput;




public class BaseOpenSearcher extends BaseSearcher implements FullTextLoader
{

	private static final Log log = LogFactory.getLog(BaseOpenSearcher.class);
	public static final Pattern VALUEDELMITER = Pattern.compile("\\s*\\|\\s*");
	protected static final Pattern operators = Pattern.compile("(\\sAND\\s|\\sOR\\s|\\sNOT\\s)");
	protected static final Pattern andoperators = Pattern.compile("(\\sAND\\s)");
	protected OpenNodeManager fieldOpensearchNodeManager;
	public OpenNodeManager getOpensearchNodeManager()
	{
		return fieldOpensearchNodeManager;
	}

	public void setOpensearchNodeManager(OpenNodeManager inOpensearchNodeManager)
	{
		fieldOpensearchNodeManager = inOpensearchNodeManager;
	}
	// protected IntCounter fieldIntCounter;
	// protected PageManager fieldPageManager;
	// protected LockManager fieldLockManager;
	protected boolean fieldAutoIncrementId;

	protected boolean fieldReIndexing;
	protected boolean fieldCheckVersions;
	protected boolean fieldRefreshSaves = true;
	protected long fieldIndexId = System.currentTimeMillis();
	protected CacheManager fieldCacheManager;
	protected ArrayList<String> fieldSearchTypes;
	protected boolean fieldIncludeFullText = true;
	protected OutputFiller fieldFiller;
	protected PageManager fieldPageManager;
	

	protected boolean fieldOptimizeReindex = true;
	public boolean isOptimizeReindex()
	{
		return fieldOptimizeReindex;
	}

	public void setOptimizeReindex(boolean inOptimizeReindex)
	{
		fieldOptimizeReindex = inOptimizeReindex;
	}
	public PageManager getPageManager()
	{
		if (fieldPageManager == null)
		{
			fieldPageManager = (PageManager)getModuleManager().getBean("pageManager");
		}
		return fieldPageManager;
	}

	public void setPageManager(PageManager pageManager)
	{
		fieldPageManager = pageManager;
	}
	
	
	protected OutputFiller getFiller()
	{
		if (fieldFiller == null)
		{
			fieldFiller = new OutputFiller();
		}
		return fieldFiller;
	}
	protected int fieldFullTextCap = 25000;
	
	public int getFullTextCap()
	{
		return fieldFullTextCap;
	}

	public void setFullTextCap(int inFullTextCap)
	{
		fieldFullTextCap = inFullTextCap;
	}

	public boolean isIncludeFullText()
	{
		return fieldIncludeFullText;
	}

	public void setIncludeFullText(boolean inIncludeFullText)
	{
		fieldIncludeFullText = inIncludeFullText;
	}


	public ArrayList<String> getSearchTypes()
	{
		if (fieldSearchTypes == null)
		{
			fieldSearchTypes = new ArrayList();

		}

		return fieldSearchTypes;
	}

	public void setSearchTypes(ArrayList<String> inSearchTypes)
	{
		fieldSearchTypes = inSearchTypes;
	}

	public CacheManager getCacheManager()
	{
		return fieldCacheManager;
	}

	public void setCacheManager(CacheManager inCacheManager)
	{
		fieldCacheManager = inCacheManager;
	}

	public boolean isRefreshSaves()
	{
		return fieldRefreshSaves;
	}

	public void setRefreshSaves(boolean inRefreshSaves)
	{
		fieldRefreshSaves = inRefreshSaves;
	}



	public boolean isCheckVersions()
	{
		return fieldCheckVersions;
	}

	public void setCheckVersions(boolean inCheckVersions)
	{
		fieldCheckVersions = inCheckVersions;
	}

	public boolean isReIndexing()
	{
		return fieldReIndexing;
	}

	public void setReIndexing(boolean inReIndexing)
	{
		fieldReIndexing = inReIndexing;
	}

	/**
	 * @deprecated not used
	 * @return
	 */
	public boolean isAutoIncrementId()
	{
		return fieldAutoIncrementId;
	}

	public void setAutoIncrementId(boolean inAutoIncrementId)
	{
		fieldAutoIncrementId = inAutoIncrementId;
	}

	public SearchQuery createSearchQuery()
	{
		SearchQuery query = new ElasticSearchQuery();
		query.setPropertyDetails(getPropertyDetails());
		query.setCatalogId(getCatalogId());
		query.setResultType(getSearchType()); // a default
		query.setSearcherManager(getSearcherManager());
		return query;
	}

	protected Client getClient()
	{
		return getElasticNodeManager().getClient();
	}

	
	
	
	protected String toIndexId()
	{
		return getElasticIndexId();
	}

	public HitTracker search(SearchQuery inQuery)
	{
		// if (isReIndexing())
		// {
		// int timeout = 0;
		// while (isReIndexing())
		// {
		// try
		// {
		// Thread.sleep(250);
		// }
		// catch (InterruptedException ex)
		// {
		// log.error(ex);
		// }
		// timeout++;
		// if (timeout > 100)
		// {
		// throw new OpenEditException("timeout on search while reindexing" +
		// getSearchType());
		// }
		// }
		// }
		// Think this is lucene junk
		String json = null;
		try
		{
			if (!(inQuery instanceof ElasticSearchQuery))
			{
				throw new OpenEditException("Elastic search requires elastic query");
			}
			long start = System.currentTimeMillis();
			SearchRequestBuilder search = getClient().prepareSearch(toIndexId());
			search.setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

			

			if (isCheckVersions())
			{
				search.setVersion(true);
			}
			
			BoolQueryBuilder terms = buildTerms(inQuery);
			
			//Replace type with a custom field:
			
			
//			if(!inQuery.isIncludeDeleted()) 
//			{
//				TermQueryBuilder deleted = QueryBuilders.termQuery("emrecordstatus.recorddeleted", true);
//				terms.mustNot(deleted);
//			}
			
			search.setQuery(terms);
			// search.
			//addSorts(inQuery, search);
			addFacets(inQuery, search);

			addSearcherTerms(inQuery, search);
			addHighlights(inQuery, search);
			search.setRequestCache(true);

			if (!inQuery.isIncludeDescription())
			{
				search.setFetchSource(null, "description");
			}
			ElasticHitTracker hits = new ElasticHitTracker(getClient(), search, terms, inQuery.getHitsPerPage());
			hits.setSearcherManager(getSearcherManager());
			hits.setIndexId(getIndexId());
			hits.setSearcher(this);
			hits.setSearchQuery(inQuery);
			if (getSearcherManager().getShowSearchLogs(getCatalogId()) || true)
			{
				log.info(toIndexId() + "/" + getSearchType() + "/_search' -d '" + json );

				long size = hits.size(); // order is important
				json = search.toString();
				long end = System.currentTimeMillis() - start;
				log.info(toIndexId() + "/" + getSearchType() + "/_search' -d '" + json + "' \n" + size + " hits in: " + (double) end / 1000D + " seconds]");
			}
			return hits;
		}
		catch (Exception ex)
		{
			if (json != null)
			{
				log.error("Could not query: " + toIndexId() + "/" + getSearchType() + "/_search' -d '" + json + "' sort by " + inQuery.getSorts(), ex);
			}

			if (ex instanceof OpenEditException)
			{
				throw (OpenEditException) ex;
			}
			throw new OpenEditException(ex);
		}
	}

	public void addHighlights(SearchQuery inQuery, SearchRequestBuilder search)
	{
		for (Iterator iterator = getPropertyDetails().iterator(); iterator.hasNext();)
		{
			PropertyDetail detail = (PropertyDetail) iterator.next();
			if (detail.isHighlight())
			{
				HighlightBuilder highlightBuilder = new HighlightBuilder();

				
				highlightBuilder.field(detail.getId(), 180);      
				search.highlighter(highlightBuilder);
			}
		}

	}

	public boolean addFacets(SearchQuery inQuery, SearchRequestBuilder inSearch)
	{
		Collection facets = inQuery.getFacets();
		if( facets == null || facets.isEmpty()) //We might want the real facets just in case
		{
			boolean added = false;
			if (inQuery.getAggregation() != null)
			{
				inSearch.addAggregation((AbstractAggregationBuilder) inQuery.getAggregation());
				added = true;
			}
			ElasticSearchQuery q = (ElasticSearchQuery) inQuery;
			if(q.getAggregationJson() != null) {
				 try {
	                    XContentParser parser = XContentFactory.xContent(XContentType.JSON)
	                            .createParser(new NamedXContentRegistry(Collections.emptyList()),
	                                    null, q.getAggregationJson());
	                    AggregatorFactories.Builder aggregationBuilder = AggregatorFactories.parseAggregators(parser);
	                    inSearch.addAggregation(aggregationBuilder.getAggregatorFactories().iterator().next());
	                    added = true;
	                } catch (IOException e) {
	                    e.printStackTrace();
	                }				
				added = true;
			}			
			return added;
		}
		List added = new ArrayList();
		
		for (Iterator iterator = facets.iterator(); iterator.hasNext();)
		{
			PropertyDetail detail = (PropertyDetail) iterator.next();
			if(added.contains(detail.getId())) {
				continue;
			}
			if (detail.isDate())
			{
				DateHistogramAggregationBuilder builder = new DateHistogramAggregationBuilder(detail.getId() + "_breakdown_day");
				builder.field(detail.getId());
				builder.calendarInterval(DateHistogramInterval.DAY);
				//builder.order(Order.KEY_DESC);
				//	String timezone = TimeZone.getDefault().getID();
				//		builder.timeZone(timezone);
				inSearch.addAggregation(builder);

				builder = new DateHistogramAggregationBuilder(detail.getId() + "_breakdown_week");
				builder.field(detail.getId());
				//	builder.timeZone(timezone);

				builder.calendarInterval(DateHistogramInterval.WEEK);
			//	builder.order(Order.COUNT_DESC);

				inSearch.addAggregation(builder);

			}

			else if (detail.isNumber())
			{
				SumAggregationBuilder b = new SumAggregationBuilder(detail.getId() + "_sum");
				b.field(detail.getId());
				inSearch.addAggregation(b);

				AvgAggregationBuilder avg = new AvgAggregationBuilder(detail.getId() + "_avg");
				avg.field(detail.getId());

			}
			else if (detail.isList() || detail.isBoolean() || detail.isMultiValue())
			{
				if (detail.isViewType("tageditor"))
				{
					AggregationBuilder b = AggregationBuilders.terms(detail.getId()).field(detail.getId() + ".exact").size(100);
					inSearch.addAggregation(b);
				}
				else
				{

					AggregationBuilder b = AggregationBuilders.terms(detail.getId()).field(detail.getId()).size(50);
					inSearch.addAggregation(b);

				}
			}
			else
			{
				AggregationBuilder b = AggregationBuilders.terms(detail.getId()).field(detail.getId() + ".exact").size(50);
				inSearch.addAggregation(b);
			}
			added.add(detail.getId());

		}

		// For reports, we can pass in a custom aggregation from a script or
		// somewhere

		if (inQuery.getAggregation() != null)
		{
			inSearch.addAggregation((AbstractAggregationBuilder) inQuery.getAggregation());
		}
		ElasticSearchQuery q = (ElasticSearchQuery) inQuery;
		if(q.getAggregationJson() != null) {
			try {
				XContentParser parser = XContentFactory.xContent(XContentType.JSON)
				        .createParser(new NamedXContentRegistry(Collections.emptyList()),
				                null, q.getAggregationJson());
				AggregatorFactories.Builder aggregationBuilder = AggregatorFactories.parseAggregators(parser);
				inSearch.addAggregation(aggregationBuilder.getAggregatorFactories().iterator().next());
			} catch (IOException e) {
				throw new OpenEditException(e);
			}
	
		}
		return true;
	}

	protected void addSearcherTerms(SearchQuery inQuery, SearchRequestBuilder inSearch)
	{
		// TODO Auto-generated method stub

	}

	// protected void addQueryFilters(SearchQuery inQuery, QueryBuilder inTerms)
	// {
	//
	// BoolQueryBuilder andFilter = inTerms.bo
	//
	// for (Iterator iterator = inQuery.getFilters().iterator();
	// iterator.hasNext();)
	// {
	// FilterNode node = (FilterNode) iterator.next();
	//
	// QueryBuilder filter = QueryBuilders.termQuery(node.getId(),
	// node.get("value"));
	// andFilter.must(filter);
	// }
	// .
	// //return andFilter;
	//
	//
	// }
	//
	//

	@SuppressWarnings("rawtypes")

	@Override
	public boolean initialize()
	{
		
		//TODO:  Sort out new type mappins

//		try
//		{
//			boolean alreadyin = getClient().admin().indices().typesExists(new TypesExistsRequest(new String[] { getElasticIndexId() }, getSearchType())).actionGet().isExists();
//			if (!alreadyin)
//			{
//				log.info("initi mapping " + getCatalogId() + "/" + getSearchType());
//				putMappings();
//			}
//		}
//		catch (Exception ex)
//		{
//			log.error("index could not be created ", ex);
//			return false;
//		}
		return true;
	}

	protected String getElasticIndexId()
	{
		String indexid = getAlternativeIndex();

		if (indexid == null)
		{
			indexid = getCatalogId() + "-" + getSearchType();
			indexid = indexid.replaceAll("/", "-").toLowerCase();
		}
		return indexid;
	}

	//
	// protected void deleteOldMapping()
	// {
	// log.info("Does not work");
	// }

	// AdminClient admin = getElasticNodeManager().getClient().admin();
	// String indexid = toId(getCatalogId());
	// //XContentBuilder source = buildMapping();
	//
	// //DeleteMappingRequest dreq =
	// Requests.deleteMappingRequest(indexid).types(getSearchType());
	// try
	// {
	// DeleteMappingResponse dpres =
	// admin.indices().deleteMapping(dreq).actionGet();
	// if (dpres.isAcknowledged())
	// {
	// log.info("Cleared out the mapping " + getSearchType() );
	// getClient().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
	// }
	// }
	// catch (Throwable ex)
	// {
	// log.error(ex);
	// }
	// }
	public boolean putMappings()
	{
		AdminClient admin = getElasticNodeManager().getClient().admin();

		String indexid = getElasticIndexId();

		

	    // Step 1: Check if the index exists
	    boolean indexExists = admin.indices().exists(new IndicesExistsRequest(indexid)).actionGet().isExists();

	    // Step 2: Create index if it does not exist
	    if (!indexExists) {
	        try {
	            CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexid);

	            // Load YAML configuration
	            Page yaml = getPageManager().getPage("/system/configuration/opensearch-index.yaml");
	            InputStream in = yaml.getInputStream();
	            Builder settingsBuilder = Settings.builder().loadFromStream(yaml.getName(), in, true);

	            // Apply additional settings from local node properties
//	            for (Iterator iterator = getLocalNode().getProperties().keySet().iterator(); iterator.hasNext();) {
//	                String key = (String) iterator.next();
//	                if (key.startsWith("index.")) {
//	                    String val = getLocalNode().getSetting(key);
//	                    settingsBuilder.put(key, val);
//	                }
//	            }

	            // Apply the settings to the CreateIndexRequest
	            createIndexRequest.settings(settingsBuilder.build());

	            // Define mappings for the new index

	            // Create the index
	            admin.indices().create(createIndexRequest).actionGet();
	        } catch (Exception e) {
	            log.error("Failed to create index: " + indexid, e);
	            return false;
	        }
	    }

		
		
		
		List<PropertyDetails> dependson = getPropertyDetailsArchive().findChildTables();
		for (Iterator iterator = dependson.iterator(); iterator.hasNext();)
		{
			PropertyDetails details = (PropertyDetails) iterator.next();
			PropertyDetail parent = details.getDetail("_parent");
			if (parent.getListId().equals(getSearchType()))
			{
				Searcher child = getSearcherManager().getSearcher(getCatalogId(), details.getId());
				child.setAlternativeIndex(getAlternativeIndex());
				child.reloadSettings();
				child.setAlternativeIndex(null);
			}
		}

		XContentBuilder source = buildMapping();
		
			log.info(indexid + "/" + getSearchType() + "/_mapping' -d '" + source.prettyPrint() + "'");
		
		// GetMappingsRequest find = new
		// GetMappingsRequest().types(getSearchType());
		// GetMappingsResponse found =
		// admin.indices().getMappings(find).actionGet();
		// if( !found.isContextEmpty())
		try
		{
			putMapping(admin, indexid, source);
			admin.cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();

			// Remove error warning
			getElasticNodeManager().removeMappingError(getSearchType());
		}
		catch (Exception ex)
		{
			// https://www.elastic.co/guide/en/elasticsearch/guide/current/scan-scroll.html
			// https://github.com/jprante/elasticsearch-knapsack
			log.info("Could not put mapping over existing mapping." + getSearchType(), ex);
			getElasticNodeManager().addMappingError(getSearchType(), ex.getMessage());
			//throw new OpenEditException("Mapping was not saved " + getSearchType(),ex);
			return false;
			// you will need to export data");
		}
		return true;
		// try
		// {
		// //Save existing index values
		// HitTracker all = getAllHits();
		// //Export to csv file?
		//
		// DeleteMappingRequest dreq =
		// Requests.deleteMappingRequest(indexid).types(getSearchType());
		// DeleteMappingResponse dpres =
		// admin.indices().deleteMapping(dreq).actionGet();
		// if (dpres.isAcknowledged())
		// {
		// log.info("Cleared out the mapping " + getSearchType() );
		// getClient().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		// putMapping(admin, indexid, source);
		// }
		// //save it all back
		// }
		// catch( Throwable ex)
		// {
		// log.info("failed to clear mapping before reloading ",ex);
		// }
	}

	public void putMapping(AdminClient admin, String indexid, XContentBuilder source)
	{
		PutMappingRequest req = Requests.putMappingRequest(indexid);
		
		req = req.source(source);

		req.validate();
		AcknowledgedResponse pres = admin.indices().putMapping(req).actionGet();

		if (pres.isAcknowledged())
		{
			// log.info("mapping applied " + getSearchType());
			//	admin.cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		}

	}

	public XContentBuilder buildMapping()
	{
		try
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			XContentBuilder jsonBuilder = XContentFactory.jsonBuilder(out);
			
			
		

			
			XContentBuilder jsonproperties = jsonBuilder.startObject().field("date_detection", "false").startObject("properties");
			

			//"_all" : {"enabled" : false},
		//	jsonproperties.startObject("_all").field("enabled", "false").endObject();

			//jsonproperties = jsonproperties.startObject("properties");

			List props = getPropertyDetails().findIndexProperties();
			//List objectarrays = new ArrayList();
			if (props.size() == 0)
			{
				log.error("No fields defined for " + getSearchType());
			}
			// https://github.com/elasticsearch/elasticsearch/pull/606
			// https://gist.github.com/870714
			/*
			 * index.analysis.analyzer.lowercase_keyword.type=custom
			 * index.analysis.analyzer.lowercase_keyword.filter.0=lowercase
			 * index.analysis.analyzer.lowercase_keyword.tokenizer=keyword
			 */

			// Add in namesorted
			// if (getPropertyDetails().contains("name") &&
			// !getPropertyDetails().contains("namesorted"))
			// {
			// props = new ArrayList(props);
			// PropertyDetail detail = new PropertyDetail();
			// detail.setId("namesorted");
			// props.add(detail);
			// }

//			jsonproperties = jsonproperties.startObject("mastereditclusterid");
//			jsonproperties = jsonproperties.field("type", "text");
//			jsonproperties = jsonproperties.field("index", "not_analyzed");
//			jsonproperties = jsonproperties.field("include_in_all", "false");
//			jsonproperties = jsonproperties.field("store", "false");
//
//			jsonproperties = jsonproperties.endObject();
//
//			jsonproperties = jsonproperties.startObject("recordmodificationdate");
//			jsonproperties = jsonproperties.field("include_in_all", "false");
//			jsonproperties = jsonproperties.field("type", "date");
//			jsonproperties = jsonproperties.field("store", "true");
//			jsonproperties = jsonproperties.endObject();

			//jsonproperties = buildClusterSyncMappings(jsonproperties);

			for (Iterator i = props.iterator(); i.hasNext();)
			{
				PropertyDetail detail = (PropertyDetail) i.next();
				if (detail.isDeleted())
				{
					continue;
				}
				if (detail.getId() == null || "_id".equals(detail.getId()) || "id".equals(detail.getId()))
				{
					// jsonproperties = jsonproperties.startObject("_id");
					// jsonproperties = jsonproperties.field("index",
					// "not_analyzed");
					// jsonproperties = jsonproperties.field("type", "_id");
					// jsonproperties = jsonproperties.endObject();
					continue;
				}
				if ("_parent".equals(detail.getId()) || detail.getId().contains(".") || "emrecordstatus".equals(detail.getId()) || "recordmodificationdate".equals(detail.getId()) || "mastereditclusterid".equals(detail.getId()) || detail.getId().startsWith("_")) //TODO: Check search type instead?
				{
					continue;
				}

				if (detail.isMultiLanguage() || false)
				{
					jsonproperties.startObject(detail.getId() + "_int");
					jsonproperties.field("type", "object");

					jsonproperties.startObject("properties");
					HitTracker languages = getSearcherManager().getList(getCatalogId(), "locale");
					for (Iterator iterator = languages.iterator(); iterator.hasNext();)
					{
						Data locale = (Data) iterator.next();
						String id = locale.getId();

						jsonproperties.startObject(id);
						String analyzer = locale.get("analyzer");
						jsonproperties.field("type", "text");
						if (detail.isAnalyzed())
						{
							jsonproperties = createExactEnabledField(detail,jsonproperties);

						}

						if (analyzer != null)
						{
							jsonproperties.field("analyzer", analyzer);
						}
						else
						{
							jsonproperties.field("analyzer", "lowersnowball");

						}
						jsonproperties = jsonproperties.field("index", "analyzed");
						jsonproperties.endObject();
					}
					jsonproperties.endObject();
					jsonproperties.endObject();

					 jsonproperties.startObject(detail.getId());
					 
					 if(detail.isAnalyzed()) {
						 jsonproperties.field("type", "text");
					 }
					 else {
						 jsonproperties.field("type", "keyword");
					 }
					 createExactEnabledField(detail,jsonproperties);
					 //jsonproperties.field("include_in_all", "false");
					 jsonproperties.endObject();

					continue;
				}

			jsonproperties.startObject(detail.getId());
				configureDetail(detail, jsonproperties);
				 jsonproperties.endObject();
			}
			jsonproperties.endObject();
//			PropertyDetail _parent = getPropertyDetails().getDetail("_parent");
//			if (_parent != null)
//			{
//				jsonproperties = jsonproperties.startObject("_parent");
//				jsonproperties = jsonproperties.field("type", _parent.getListId());
//				jsonproperties = jsonproperties.endObject();
//			}
			jsonBuilder = jsonproperties.endObject();
			


			// ... your content building logic ...
			jsonBuilder.close();
			// After building, get the content as string
			//String content = out.toString("UTF-8");
			String content = out.toString("UTF-8");

			log.info(content);
			
			return jsonproperties;
		}
		catch (Throwable ex)
		{
			ex.printStackTrace();
			throw new OpenEditException(ex);
		}

	}

	private XContentBuilder buildClusterSyncMappings(XContentBuilder jsonproperties) throws Exception
	{

		//		What about an object called asset.recordstatus.deleted = true
		//				asset.recordstatus.lastmodified and asset.recordstatus.lastmodifiedclusterid and asset.recordstatus.masternodeid and asset.recordstatus.masterlastmodified (edited) 

		jsonproperties.startObject("emrecordstatus").field("type", "object");
		jsonproperties.startObject("properties");

		jsonproperties.startObject("mastereditclusterid").field("type", "text").field("store", "false").endObject();
		jsonproperties.startObject("masterrecordmodificationdate").field("type", "date").field("store", "true").endObject();

		jsonproperties.startObject("lastmodifiedclusterid").field("type", "text").field("store", "false").endObject();
		jsonproperties.startObject("recordmodificationdate").field("type", "date").field("store", "true").endObject();
		jsonproperties.startObject("recorddeleted").field("type", "boolean").field("store", "false").endObject();

		
		
		jsonproperties.endObject();
		jsonproperties.endObject();

		return jsonproperties;
	}

	protected void configureDetail(PropertyDetail detail, XContentBuilder jsonproperties) throws Exception
	{

		if ("description".equals(detail.getId()))
		{
			String analyzer = "lowersnowball";
			jsonproperties = jsonproperties.field("analyzer", analyzer);
			jsonproperties = jsonproperties.field("type", "text");

		//	jsonproperties = jsonproperties.field("include_in_all", "false");
			return;
		}

		//CHECK TIMECODE
		if (detail.isDataType("objectarray"))
		{
			jsonproperties = jsonproperties.field("type", "object");
			//"type": "nested",
			
			jsonproperties.startObject("properties");
			for (Iterator iterator = detail.getObjectDetails().iterator(); iterator.hasNext();)
			{
				PropertyDetail child = (PropertyDetail) iterator.next();
				jsonproperties = jsonproperties.startObject(child.getId());
				configureDetail(child, jsonproperties);
				jsonproperties = jsonproperties.endObject();
			}
			jsonproperties.endObject();

			return;

		}
		else if (detail.isDataType("nested"))
		{
			jsonproperties = jsonproperties.field("type", "nested");
			jsonproperties.startObject("properties");
			for (Iterator iterator = detail.getObjectDetails().iterator(); iterator.hasNext();)
			{
				PropertyDetail child = (PropertyDetail) iterator.next();
				jsonproperties = jsonproperties.startObject(child.getId());
				configureDetail(child, jsonproperties);
				jsonproperties = jsonproperties.endObject();
			}
			jsonproperties.endObject();

			return;

		}

		// First determine type
		if (detail.isDate())
		{
			jsonproperties = jsonproperties.field("type", "date");
			jsonproperties = jsonproperties.field("store", "true");

			// "date_detection" : 0
			// jsonproperties = jsonproperties.field("format",
			// "yyyy-MM-dd HH:mm:ss Z");
		}
		else if (detail.isBoolean())
		{
			jsonproperties = jsonproperties.field("type", "boolean");
		}
		else if (detail.isDataType("number") || detail.isDataType("long"))
		{
			jsonproperties = jsonproperties.field("type", "long");
		}
		else if (detail.isDataType("double"))
		{
			jsonproperties = jsonproperties.field("type", "double");
		}
		else if (detail.isDataType("geo_point"))
		{
			jsonproperties = jsonproperties.field("type", "geo_point");
		}

		else if (detail.isList()) // Or multi valued?
		{
			if (Boolean.parseBoolean(detail.get("nested")))
			{
				jsonproperties = jsonproperties.field("type", "nested");
			}
			else
			{
				jsonproperties = jsonproperties.field("type", "text");
			}
			//TODO: enable sort on list fields. if exact field is sortable add sort subfield with the actual lookedup name value?
		}
		else
		{
			jsonproperties = jsonproperties.field("type", "text");
			if (detail.isAnalyzed())
			{
				jsonproperties = createExactEnabledField(detail, jsonproperties);
			}

		}

		// Now determine index
//		String indextype = detail.get("indextype");
//
//		if (indextype == null)
//		{
//			if (!detail.isAnalyzed())
//			{
//				indextype = "not_analyzed";
//			}
//		}
//		if (indextype != null)
//		{
//			jsonproperties = jsonproperties.field("index", indextype);
//		}

	

		String analyzer = detail.get("analyzer");
		if (analyzer != null)
		{
			jsonproperties.field("analyzer", analyzer);
		}
		else
		{
//			if (detail.isAnalyzed()) //&& !("name".equals(detail.getId()))) 
//			{
//				jsonproperties.field("analyzer", "lowersnowball");
//			}
		}
	}


	protected XContentBuilder createExactEnabledField(PropertyDetail detail, XContentBuilder jsonproperties) throws IOException
	{
		jsonproperties.startObject("fields");
		jsonproperties.startObject("exact");
		jsonproperties = jsonproperties.field("type", "keyword");
		
		if (!detail.getId().contains("path"))
		{
			jsonproperties = jsonproperties.field("ignore_above", 256);
		}
		jsonproperties.endObject();

		jsonproperties.startObject("sort");
		jsonproperties = jsonproperties.field("type", "keyword");
		//jsonproperties = jsonproperties.field("analyzer", "tags");
		jsonproperties.endObject();

		jsonproperties.endObject();
		return jsonproperties;
	}

	public BoolQueryBuilder buildTerms(SearchQuery inQuery)
	{

		// if (inQuery.getTerms().size() == 1 && inQuery.getChildren().size() ==
		// 0 ) //Shortcut for common cases
		// {
		// Term term = (Term) inQuery.getTerms().iterator().next();
		//
		// if ("orgroup".equals(term.getOperation()) ||
		// "orsGroup".equals(term.getOperation())) //orsGroup?
		// {
		// return addOrsGroup(term);
		// }
		//
		// String value = term.getValue();
		//
		// if (value != null && value.equals("*"))
		// {
		// return QueryBuilders.matchAllQuery();
		// }
		// QueryBuilder find = buildTerm(term.getDetail(), term, value);
		// return find;
		// }

		BoolQueryBuilder bool = QueryBuilders.boolQuery();

		buildBoolTerm(inQuery, bool, inQuery.isAndTogether());

		// if( inQuery.isEndUserSearch() )
		// {
		// Collection properties =
		// getPropertyDetails().findAutoIncludeProperties();
		// for (Iterator iterator = properties.iterator(); iterator.hasNext();)
		// {
		// PropertyDetail detail = (PropertyDetail) iterator.next();
		// if( inQuery.getDetail(detail.getId()) == null ) //Not already
		// included
		// {
		// //QueryBuilder find = buildTerm(detail, term, value);
		//
		// bool.must(find);
		// }
		// }
		// }

		if (inQuery.getChildren().size() > 0)
		{
			for (Iterator iterator = inQuery.getChildren().iterator(); iterator.hasNext();)
			{
				SearchQuery query = (SearchQuery) iterator.next();
				BoolQueryBuilder builder = buildTerms(query);
				if (inQuery.isAndTogether())
				{
					bool.must(builder);
				}
				else
				{
					bool.should(builder);
				}
			}
		}
		return bool;

	}

	protected void buildBoolTerm(SearchQuery inQuery, BoolQueryBuilder bool, boolean inAnd)
	{
		for (Iterator iterator = inQuery.getTerms().iterator(); iterator.hasNext();)
		{
			Term term = (Term) iterator.next();
			PropertyDetail detail = term.getDetail();
			//We handle joins with SearchQueryFilter.java
			String ignoretypes  =  inQuery.get("ignoresearchttype");
			if( ignoretypes == null || !Boolean.parseBoolean(ignoretypes))
			{
				if (detail.getSearchType() != null && !getSearchType().equals(detail.getSearchType()))
				{
					continue;
				}
			}
			Object value = term.getValue();
			if (value == null)
			{
				value = term.getValues();
			}
			QueryBuilder find = buildTerm(detail, term, value);
			if (find != null)
			{
				if (inAnd)
				{
					bool.must();
				}
				else
				{
					bool.should();
				}
			}
		}
		// }
	}

	// protected BoolQueryBuilder addOrsGroup(Term term)
	// {
	// if (term.getValues() != null)
	// {
	// BoolQueryBuilder or = QueryBuilders.boolQuery();
	// for (int i = 0; i < term.getValues().length; i++)
	// {
	// Object val = term.getValues()[i];
	// if (val != null && !val.equals(""))
	// {
	// QueryBuilder aterm = buildTerm(term.getDetail(), term, val);
	// if (aterm != null)
	// {
	// or.should(aterm);
	// }
	// }
	// }
	// return or;
	// }
	// return null;
	// }

	protected QueryBuilder buildTerm(PropertyDetail inDetail, Term inTerm, Object inValue)
	{
		
		QueryBuilder find = buildNewTerm(inDetail, inTerm, inValue);

		if ("not".equals(inTerm.getOperation()) || "notgroup".equals(inTerm.getOperation()))
		{
			BoolQueryBuilder or = QueryBuilders.boolQuery();
			or.mustNot();
			return or;
		}
		else if( inDetail.getId().contains("."))
		{
			String[] ids = inDetail.getId().split("\\.");
			PropertyDetail parent = getDetail(ids[0]);
			if( parent != null && "nested".equals(parent.getDataType()))
			{
				//TODO:  Figure out what this scoremode is
				find = QueryBuilders.nestedQuery(ids[0], find, ScoreMode.Avg);
			}
			/*
			  "nested": {
	            "path": "faceprofiles",
	             "query": {
                     "bool": {
                        "must": [
                           {
                              "term": {
                                 "faceprofiles.faceprofilegroup":"AXM5Gn6zvm9C1jY32Xy5"
                              }
                           }
                        ]
                     }
                  }
          		}
			 */
		}

		return find;
	}

	protected QueryBuilder buildNewTerm(PropertyDetail inDetail, Term inTerm, Object inValue)
	{
		// Check for quick date object
		org.opensearch.index.query.QueryBuilder find = null;
		String valueof = null;

		if (inValue instanceof Date)
		{
			valueof = DateStorageUtil.getStorageUtil().formatForStorage((Date) inValue);
		}
		else
		{
			valueof = String.valueOf(inValue);
		}

		String fieldid = inDetail.getId();
		if (inDetail.isMultiLanguage())
		{
			if (!fieldid.endsWith("_int"))
			{
				fieldid = fieldid + "_int.en";//default to search the english
			}
		}

		if ("searchjoin".equals(inDetail.getDataType()))
		{
			// contact.state
			String fieldname = fieldid.substring(0, fieldid.indexOf(".")); // contact
			String path = fieldid.substring(fieldid.indexOf(".") + 1); // state
			// TermsLookupQueryBuilder joinquery =
			// QueryBuilders.termsLookupQuery(fieldname);
			// joinquery.lookupId(inTerm.getValue());
			// joinquery.lookupType(inDetail.getListId());
			// joinquery.lookupIndex(toId( inDetail.getListCatalogId()));
			// joinquery.lookupPath(path);
			// return joinquery;
			org.openedit.data.QueryBuilder builder = getSearcherManager().getSearcher(inDetail.getListCatalogId(), inDetail.getListId()).query();
			HitTracker hits = builder.match(path, valueof).search();

			hits.setHitsPerPage(1000);
			Collection ids = new ArrayList(hits.size());
			for (Iterator iterator = hits.iterator(); iterator.hasNext();)
			{
				Data data = (Data) iterator.next();
				ids.add(data.getId());
			}
			if (ids.size() > 0)
			{
				find = QueryBuilders.termsQuery(fieldname, ids);
				return find;
			}
			else
			{
				return null;
			}
		}
		//TODO:  Migrate this to Join queries
//		else if ("childfilter".equals(inTerm.getOperation()))
//		{
//			ChildFilter filter = (ChildFilter) inTerm;
//			TermQueryBuilder parent = QueryBuilders.termQuery(filter.getChildColumn(), filter.getValue());
//			QueryBuilder haschild = QueryBuilders.hasChildQuery(filter.getChildTable(), parent);
//			return haschild;
//		}

		// if( fieldid.equals("description"))
		// {
		// //fieldid = "_all";
		// //valueof = valueof.toLowerCase();
		// find = QueryBuilders.textQuery(fieldid, valueof);
		// return find;
		// }
		if (fieldid!= null && fieldid.equals("id"))
		{
			// valueof = valueof.toLowerCase();
			if (valueof.equals("*"))
			{
				find = QueryBuilders.matchAllQuery();
			}
			else if (!"orgroup".equals(inTerm.getOperation()))
			{
				find = QueryBuilders.termQuery("_id", valueof);
			}
			else if (inTerm.getValues() != null)
			{
				find = QueryBuilders.termsQuery("_id", inTerm.getValues());
			}
			if (find != null)
			{
				return find;
			}
		}

		if (valueof.equals("*"))
		{
			//find = QueryBuilders.wildcardQuery(fieldid, "*");
			find = QueryBuilders.matchAllQuery();
			// ExistsFilterBuilder filter =
			// FilterBuilders.existsFilter(fieldid);
			// find = QueryBuilders.filteredQuery(all, filter);

		}
		
		
		else if ("contains".equals(inTerm.getOperation()))
		{
			// MatchQueryBuilder text = QueryBuilders.matchPhraseQuery(fieldid,
			// valueof);
			// QueryBuilder text = QueryBuilders.queryString("*" + valueof +
			// "*").field(fieldid);
			String wildcard = valueof;

			if (!wildcard.startsWith("*"))
			{
				wildcard = "*" + wildcard;
			}
			if (!wildcard.endsWith("*"))
			{
				wildcard = wildcard + "*";
			}
			wildcard = wildcard.toLowerCase(); // Some reason wildcard searches
												// are not run by the analyser
												// MatchQueryBuilder text = QueryBuilders.matchPhraseQuery(fieldid,
												// valueof);
			String altid = null;
			if( inDetail.isAnalyzed() && !inDetail.getId().equals("description") )
			{
				altid = fieldid+".sort";
			}
			else
			{
				altid = fieldid;
			}
			
			WildcardQueryBuilder text = QueryBuilders.wildcardQuery(altid, wildcard);

			BoolQueryBuilder or = QueryBuilders.boolQuery();
			or.should(text);

			valueof = valueof.replace("*", "");
			MatchPhrasePrefixQueryBuilder phrase = QueryBuilders.matchPhrasePrefixQuery(altid, valueof);
			phrase.maxExpansions(75);
			or.should(phrase);
			find = or;
		}
		else if("missing".equals(inTerm.getOperation())) {
			find =  QueryBuilders.boolQuery()
				    .mustNot(QueryBuilders.existsQuery(inTerm.getId()));
		}
		else if("exists".equals(inTerm.getOperation())) {
			find= QueryBuilders.existsQuery(inTerm.getId());
		}
		
		else if ("startswith".equals(inTerm.getOperation()))
		{
			//TODO: Should startswith be exact or analysed phrases? 
			//find = QueryBuilders.prefixQuery(fieldid, valueof);
			//Left this in for now...

			if (inDetail.isAnalyzed())
			{

				MatchPhrasePrefixQueryBuilder text = QueryBuilders.matchPhrasePrefixQuery(fieldid, valueof);
				text.maxExpansions(10);
				find = text;
			}
			else
			{
				PrefixQueryBuilder text = QueryBuilders.prefixQuery(fieldid, valueof);
				find = text;
			}
		}
		else if ("freeform".equals(inTerm.getOperation()))
		{
			if ((valueof.startsWith("\"") && valueof.endsWith("\"")))
			{
				valueof = valueof.replace("\"", "");
				valueof = QueryParser.escape(valueof);
				String query = "+(" + valueof + ")";
				MatchPhraseQueryBuilder text = QueryBuilders.matchPhraseQuery(inTerm.getId(), query);
				text.analyzer("lowersnowball");
				find = text;
			}
			else
			{
				String uppercase = valueof.replace(" and ", " AND ").replace(" And ", " AND ").replace(" Or ", " OR ").replace(" or ", " OR ").replace(" not ", " NOT ").replace(" to ", " TO ").replace(", ", " AND "); //Babson uses lots of commas
				//We no longer allow + or - notation
				// Parse by Operator
				// Add wildcards
				// Look for Quotes

				Matcher customlogic = operators.matcher(uppercase);
				if (!customlogic.find()) //This somehow ignores things in " " .. ie. "Some things" Cool
				{
					uppercase = uppercase.replaceAll(" ", " AND "); //All spaces
				}
				// tom and nancy == *tom* AND *nancy*
				// tom or nancy == *tom* OR *nancy*
				// tom nancy => *tom* AND *nancy*
				// tom*nancy => tom*nancy
				// tom AND "Nancy Druew" => *tom* AND "Nancy Druew"
				// "Big Deal" => "Big Deal"
				//valueof = valueof.replace(" and ", " AND ").replace(" or ", " OR ").replace(" not ", " NOT ").replace(" to ", " TO "); // Why do this again?

				Matcher m = operators.matcher(uppercase);

				int start = 0;
				int ending = uppercase.length();
				String operator = null;
				boolean keepgoing = true;
				String nextoperator = null;

				BoolQueryBuilder or = QueryBuilders.boolQuery();
				while (keepgoing)
				{
					if (m.find())
					{
						ending = m.start();
						nextoperator = m.group().trim();
					}
					else
					{
						keepgoing = false;
						ending = uppercase.length();
					}

					String word = uppercase.substring(start, ending);
					if (keepgoing)
					{
						start = m.end();
					}

					StringBuffer out = new StringBuffer();
					out.append("+(");
					//Check for quotes..
					if ((word.startsWith("\"") && word.endsWith("\"")))
					{
						String sub = word.substring(1, word.length() - 1);
						out.append("\"" + QueryParser.escape(sub) + "\"");
					}
					else
					{
						wildcard(out, word);
					}
					out.append(")");

					//Make a *xxx* OR xxx* search to deal with bugs
					BoolQueryBuilder pair = QueryBuilders.boolQuery();
					QueryStringQueryBuilder text = QueryBuilders.queryStringQuery(out.toString());
					text.defaultOperator(Operator.AND);
					text.analyzeWildcard(true); //This is important
					text.allowLeadingWildcard(true);
					text.analyzer("lowersnowball");
					text.defaultField("description");
					pair.should(text);

					String startswith = "+(" + QueryParser.escape(word) + "*)"; //THis is needed because HL_06_19_42_DRY.WAV cant be found when searching for just HL_06_19_42_DRY
					QueryStringQueryBuilder startw = QueryBuilders.queryStringQuery(startswith);
					startw.defaultOperator(Operator.AND);
					startw.analyzer("lowersnowball");
					startw.defaultField("description");
					pair.should(startw);

					if (operator == null && (nextoperator != null && nextoperator.equals("OR")))
					{
						operator = "OR";
					}
					else if (operator == null)
					{
						operator = "AND";
					}
					if (operator.equals("NOT"))
					{
						or.mustNot(pair);
					}
					if (operator.equals("OR"))
					{
						or.should(pair);
					}
					else
					{
						or.must(pair);
					}

					operator = nextoperator;
				}

				find = or;
			}
		}
		else if (valueof.endsWith("*"))
		{
			valueof = valueof.substring(0, valueof.length() - 1);

			MatchPhrasePrefixQueryBuilder text = QueryBuilders.matchPhrasePrefixQuery(fieldid, valueof);
			text.maxExpansions(10);
			find = text;
		}
		else if (valueof.contains("*"))
		{
			if( inDetail.isAnalyzed())
			{
				find = QueryBuilders.wildcardQuery(fieldid + ".sort", valueof);
			}
			else
			{
				find = QueryBuilders.wildcardQuery(fieldid, valueof);
			}
		}
		else if (inDetail.isBoolean())
		{
			find = QueryBuilders.termQuery(fieldid, Boolean.parseBoolean(valueof));
		}
		
		else if (inDetail.isDate())
		{
			if ("beforedate".equals(inTerm.getOperation()))
			{
				// Date after = new Date(0);
				Date before = DateStorageUtil.getStorageUtil().parseFromStorage(valueof);
				Calendar c = new GregorianCalendar();

				c.setTime(before);
				c.set(Calendar.HOUR_OF_DAY, 23);
				c.set(Calendar.MINUTE, 59);
				c.set(Calendar.SECOND, 59);
				c.set(Calendar.MILLISECOND, 999);
				before = c.getTime();
				find = QueryBuilders.rangeQuery(inDetail.getId()).to(before);
			}
			else if ("afterdate".equals(inTerm.getOperation()))
			{
				Date after = DateStorageUtil.getStorageUtil().parseFromStorage(valueof);
				find = QueryBuilders.rangeQuery(fieldid).from(after);// .to(before);
			}
			else if ("betweendates".equals(inTerm.getOperation()))
			{
				// String end =
				// DateStorageUtil.getStorageUtil().formatForStorage(new
				// Date(Long.MAX_VALUE));
				Date before = (Date) inTerm.getValue("beforeDate");
				Date after = (Date) inTerm.getValue("afterDate");

				// inTerm.getParameter("beforeDate");

				// String before
				//TODO: Use gte ?
				find = QueryBuilders.rangeQuery(fieldid).from(after).to(before).includeUpper(true).includeLower(true);
			}
			else if ("ondate".equals(inTerm.getOperation()))
			{
				Date target = DateStorageUtil.getStorageUtil().parseFromStorage(valueof);

				Calendar c = new GregorianCalendar();
				c.setTime(target);
				c.set(Calendar.HOUR_OF_DAY, 0);
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				c.set(Calendar.MILLISECOND, 0);
				Date fromtime = c.getTime();

				c.set(Calendar.HOUR_OF_DAY, 23);
				c.set(Calendar.MINUTE, 59);
				c.set(Calendar.SECOND, 59);
				c.set(Calendar.MILLISECOND, 999);

				// inTerm.getParameter("beforeDate");

				// String before
				find = QueryBuilders.rangeQuery(fieldid).includeLower(true).includeLower(true).from(fromtime).to(c.getTime()).includeUpper(true).includeLower(true);
			}
			else
			{
				// Think this doesn't ever run. I think we use betweendates.
				Date target = DateStorageUtil.getStorageUtil().parseFromStorage(valueof);
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(target);
				int year = calendar.get(Calendar.YEAR);
				int month = calendar.get(Calendar.MONTH);
				int day = calendar.get(Calendar.DATE);
				calendar.set(Calendar.MILLISECOND, 0);
				calendar.set(year, month, day, 0, 0, 0);

				Date after = calendar.getTime();
				calendar.set(year, month, day, 23, 59, 59);
				calendar.set(Calendar.MILLISECOND, 999);

				Date before = calendar.getTime();

				find = QueryBuilders.rangeQuery(fieldid).from(after).to(before);

				// find = QueryBuilders.termQuery(fieldid, valueof); //TODO make
				// it a range query? from 0-24 hours
			}
		}
		else if (inDetail.isNumber())
		{
			if ("betweennumbers".equals(inTerm.getOperation()))
			{

				if (inDetail.isDataType("double"))
				{
					Double lowval = (Double) inTerm.getValue("lowval");
					Double highval = (Double) inTerm.getValue("highval");
					find = QueryBuilders.rangeQuery(fieldid).from(lowval).to(highval);
				}
				if (inDetail.isDataType("long") || inDetail.isDataType("number"))
				{
					Long lowval = (Long) inTerm.getValue("lowval");
					Long highval = (Long) inTerm.getValue("highval");
					find = QueryBuilders.rangeQuery(fieldid).from(lowval).to(highval);
				}
			}

			else if ("lessthannumber".equals(inTerm.getOperation()))
			{
				if (inDetail.isDataType("double"))
				{
					Double val = Double.valueOf(inTerm.getValue());
					find = QueryBuilders.rangeQuery(fieldid).lt(val);
				}
				if (inDetail.isDataType("long") || inDetail.isDataType("number"))
				{
					Long val = Long.valueOf(inTerm.getValue());
					find = QueryBuilders.rangeQuery(fieldid).lt(val);
				}

			}

			else if ("greaterthannumber".equals(inTerm.getOperation()))
			{
				if (inDetail.isDataType("double"))
				{
					Double val = Double.valueOf(inTerm.getValue());
					find = QueryBuilders.rangeQuery(fieldid).gt(val);
				}
				if (inDetail.isDataType("long") || inDetail.isDataType("number"))
				{
					Long val = Long.valueOf(inTerm.getValue());
					find = QueryBuilders.rangeQuery(fieldid).gt(val);
				}

			}

			else
			{
				if (inDetail.isDataType("double"))
				{
					find = QueryBuilders.termQuery(fieldid, Double.parseDouble(valueof));

				}
				else
				{
					find = QueryBuilders.termQuery(fieldid, Long.parseLong(valueof));
				}
			}

		}
		else if (inDetail.isGeoPoint())
		{
			GeoFilter filter = (GeoFilter) inTerm;
			if (filter.getLatitude() == 0)
			{
				find = QueryBuilders.termQuery("id", "-" + System.currentTimeMillis());
			}
			else
			{
				GeoDistanceQueryBuilder geoDistanceFilterBuilder = new GeoDistanceQueryBuilder(inDetail.getId());
				geoDistanceFilterBuilder.point(filter.getLatitude(), filter.getLongitude());
				geoDistanceFilterBuilder.distance(String.valueOf(filter.getDistance()));
					geoDistanceFilterBuilder.geoDistance(GeoDistance.ARC); // Or GeoDistance.PLANE
				find = geoDistanceFilterBuilder;
			}
		}
		// DO not use _all use _description
		// else if (fieldid.equals("description"))
		// {
		// // valueof = valueof.substring(0,valueof.length()-1);
		//
		// MatchQueryBuilder text = QueryBuilders.matchPhrasePrefixQuery("_all",
		// valueof);
		// text.analyzer("lowersnowball");
		// text.maxExpansions(10);
		// find = text;
		// }
		else
		{
			if ("exact".equals(inTerm.getOperation()))
			{
				if (inDetail.isAnalyzed())
				{
					find = QueryBuilders.termQuery(fieldid + ".exact", valueof);
				}
				else
				{
					find = QueryBuilders.termQuery(fieldid, valueof);
				}
			}
			else if ("orgroup".equals(inTerm.getOperation()) || "notgroup".equals(inTerm.getOperation()))
			{
				find = QueryBuilders.termsQuery(fieldid, inTerm.getValues()); //This is an OR
				//				BoolQueryBuilder or  = QueryBuilders.boolQuery();
				//				Object[] values = inTerm.getValues();
				//				for (int i = 0; i < values.length; i++)
				//				{
				//					Object val = values[i];
				//
				//					TermQueryBuilder item = QueryBuilders.termQuery(fieldid, val);
				//					if("notgroup".equals(inTerm.getOperation()))
				//					{
				//						or.mustNot(item);
				//					}
				//					else
				//					{
				//						or.should(item);						
				//					}
				//				}
				//				find = or;
			}
			else if ("andgroup".equals(inTerm.getOperation()))
			{
				Object[] values = inTerm.getValues();
				BoolQueryBuilder or = QueryBuilders.boolQuery();

				for (int i = 0; i < values.length; i++)
				{
					Object val = values[i];
					if (inDetail.isAnalyzed() || "keywords".equals(fieldid))
					{
						MatchQueryBuilder item = QueryBuilders.matchQuery(fieldid, val);
						or.must(item);
					}
					else
					{
						TermQueryBuilder item = QueryBuilders.termQuery(fieldid, val);
						or.must(item);

					}
				}
				find = or;
			}
			else if ("matches".equals(inTerm.getOperation()))
			{
				find = createMatchQuery(inDetail, fieldid, valueof);
			}
			else if ("contains".equals(inTerm.getOperation()))
			{
				find = createMatchQuery(inDetail, fieldid, valueof);
			}
			else if (inDetail.isList())
			{
				find = QueryBuilders.termQuery(fieldid, valueof);
			}
			else
			{
				find = createMatchQuery(inDetail, fieldid, valueof);
			}
		}
		return find;
	}

	protected QueryBuilder createMatchQuery(PropertyDetail inDetail, String fieldid, String valueof)
	{
		QueryBuilder find;
		if( inDetail.isAnalyzed() && !inDetail.getId().equals("description"))
		{
			find = QueryBuilders.matchQuery(fieldid + ".sort", valueof);
		}
		else
		{
			find = QueryBuilders.matchQuery(fieldid, valueof);
		}
		return find;
	}

	private void wildcard(StringBuffer output, String word)
	{
		output.append("*");
		output.append(QueryParser.escape(word));
		output.append("*");
	}

	protected void addSorts(SearchQuery inQuery, SearchRequestBuilder search)
	{
		if (inQuery.getSorts() == null)
		{
			return;
		}
		for (Iterator iterator = inQuery.getSorts().iterator(); iterator.hasNext();)
		{
			String field = (String) iterator.next();
			boolean direction = false;
			if (field.endsWith("Down"))
			{
				direction = true;
				field = field.substring(0, field.length() - 4);
			}
			else if (field.endsWith("Up"))
			{
				direction = false;
				field = field.substring(0, field.length() - 2);
			}
			PropertyDetail detail = getDetail(field);
			FieldSortBuilder sort = null;

			if (detail != null)
			{
				if (detail.isMultiLanguage())
				{
					if (detail.isAnalyzed())
					{
						sort = SortBuilders.fieldSort(field + "_int." + inQuery.getSortLanguage() + ".sort");
					}
					else
					{
						sort = SortBuilders.fieldSort(field + "_int." + inQuery.getSortLanguage());
					}
				}
				else if (detail.isDataType("objectarray") && detail.getObjectDetails() != null && !detail.getObjectDetails().isEmpty())
				{
					PropertyDetail first = (PropertyDetail) detail.getObjectDetails().iterator().next();
					if (first.isAnalyzed())
					{
						sort = SortBuilders.fieldSort(field + "." + first.getId() + ".sort");
					}
					else
					{
						sort = SortBuilders.fieldSort(field + "." + first.getId());
					}
				}
				else if (detail.isAnalyzed())
				{
					sort = SortBuilders.fieldSort(field + ".sort");
				}
				else
				{
					sort = SortBuilders.fieldSort(field);
				}
			}

			if (sort == null)
			{
				sort = SortBuilders.fieldSort(field);

			}

			//sort.ignoreUnmapped(true);
			if (direction)
			{
				sort.order(SortOrder.DESC);
			}
			else
			{
				sort.order(SortOrder.ASC);
			}
			search.addSort(sort);

		}
	}

	public String getIndexId()
	{
		if (fieldIndexId == -1)
		{
			fieldIndexId = System.currentTimeMillis();
		}
		return String.valueOf(fieldIndexId);
	}

	public void clearIndex()
	{
		fieldIndexId = -1;
	}

	public void saveData(Data inData, User inUser)
	{
		// update the index
		// List<Data> list = new ArrayList(1);
		// list.add((Data) inData);
		// saveAllData(list, inUser);
		PropertyDetails details = getPropertyDetailsArchive().getPropertyDetailsCached(getSearchType());
		saveToElasticSearch(details, inData, false, inUser);
		clearIndex();
	}

	/*
	 * protected void bulkUpdateIndex(Collection<Data> inBuffer, User inUser) {
	 * try { String catid = toId(getCatalogId());
	 * 
	 * // BulkRequestBuilder brb = getClient().prepareBulk(); //
	 * brb.add(Requests.indexRequest(indexName).type(getIndexType()).id(id).
	 * source(source)); // } // if (brb.numberOfActions() > 0)
	 * brb.execute().actionGet(); PropertyDetails details =
	 * getPropertyDetailsArchive().getPropertyDetailsCached(getSearchType());
	 * 
	 * BulkRequestBuilder bulkRequest = getClient().prepareBulk();
	 * 
	 * for (Data data : inBuffer) { XContentBuilder content =
	 * XContentFactory.jsonBuilder().startObject();
	 * 
	 * updateIndex(content, data, details);
	 * 
	 * content.endObject(); if (data.getId() == null) { IndexRequestBuilder
	 * builder = getClient().prepareIndex(catid,
	 * getSearchType()).setSource(content); updateVersion(data, builder);
	 * bulkRequest.add(builder); } else { IndexRequestBuilder builder =
	 * getClient().prepareIndex(catid, getSearchType(),
	 * data.getId()).setSource(content); updateVersion(data, builder);
	 * 
	 * bulkRequest.add(builder); } }
	 * 
	 * BulkResponse bulkResponse =
	 * bulkRequest.setRefresh(true).execute().actionGet(); if
	 * (bulkResponse.hasFailures()) { log.info("Failures detected!"); throw new
	 * OpenEditException("failure during batch update");
	 * 
	 * } log.info("Saved " + inBuffer.size() + " records into " + catid + "/" +
	 * getSearchType()); } catch (Exception e) { throw new OpenEditException(e);
	 * } }
	 */
	public void updateIndex(Collection<Data> inBuffer, User inUser)
	{
		if (inBuffer.size() > 99 || fieldForceBulk) // 100 was too low - caused shard exceptions
		// due to thread pool size on large
		// ingests..
		{
			updateInBatch(inBuffer, inUser); // This is asynchronous
		}
		else
		{
			PropertyDetails details = getPropertyDetailsArchive().getPropertyDetailsCached(getSearchType());
			for (Data data : inBuffer)
			{
				if (data == null)
				{
					throw new OpenEditException("Data was null!");
				}
				saveToElasticSearch(details, data, false, inUser);
			}
		}
		clearIndex();

		// inBuffer.clear();
	}

	public void updateInBatch(Collection<Data> inBuffer, User inUser)
	{
		String catid = getElasticIndexId();
		long start = new Date().getTime();
		// We cant use this for normal updates since we do not get back the id
		// or the version for new data object

		// final Map<String, Data> toversion = new HashMap(inBuffer.size());
		final List<Data> toprocess = new ArrayList(inBuffer);
		final List errors = new ArrayList();
		// Make this not return till it is finished?
		BulkProcessor bulkProcessor = BulkProcessor.builder(getClient(), new BulkProcessor.Listener()
		{
			@Override
			public void beforeBulk(long executionId, BulkRequest request)
			{

			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, BulkResponse response)
			{
				for (int i = 0; i < response.getItems().length; i++)
				{
					// request.getFromContext(key)
					BulkItemResponse res = response.getItems()[i];
					if (res.isFailed())
					{
						log.info(res.getFailureMessage());
						errors.add(res.getFailureMessage());

					}
					// Data toupdate = toversion.get(res.getId());
					Data toupdate = toprocess.get(res.getItemId());
					if (toupdate == null)
					{
						errors.add("Data ["+i+"] was null: "+res.getItemId());
					}
					else {
						if (isCheckVersions())
						{
							toupdate.setProperty(".version", String.valueOf(res.getVersion()));
						}
						toupdate.setId(res.getId());
					}
				}
				//	request.refresh(true);
			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, Throwable failure)
			{
				log.info(failure);
				errors.add(failure);
			}
		}).setBulkActions(-1).setBulkSize(new ByteSizeValue(10, ByteSizeUnit.MB)).setFlushInterval(TimeValue.timeValueMinutes(4)).setConcurrentRequests(1).setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueMillis(100), 10)).build();

		//setConcurrentRequests = 1 sets concurrentRequests to 1, which means an asynchronous execution of the flush operation.

		PropertyDetails details = getPropertyDetailsArchive().getPropertyDetailsCached(getSearchType());

		for (Iterator iterator = inBuffer.iterator(); iterator.hasNext();)
		{
			try
			{
				Data data2 = (Data) iterator.next();
//				if( data2.getId() == null || data2.getId().trim().isEmpty())
//				{
					//continue;
//				}
				XContentBuilder content = XContentFactory.jsonBuilder().startObject();
				updateMasterClusterId(details, data2, content, false);
				updateIndex(content, data2, details, inUser);
				content.endObject();
				IndexRequest req = Requests.indexRequest(catid);
				PropertyDetail parent = details.getDetail("_parent");
				if (parent != null)
				{
					// String _parent = data.get(parent.getListId());
					String _parent = data2.get(parent.getId());
					if (_parent != null)
					{
						//TODO:  Figure out joins/nested etc
					//	req.parent(_parent);
					}
				}
				if (data2.getId() != null)
				{
					req = req.id(data2.getId());

				}
				req = req.source(content);
				// if( isRefreshSaves() )
				// {
				// req = req.refresh(true);
				// }
				//				try
				//				{
				bulkProcessor.add(req);
				//				}
				//				catch( RemoteTransportException ex)
				//				{
				//					if( ex.getCause() instanceof EsRejectedExecutionException)
				//					{
				//						
				//					}
				//				}
			}
			catch (Throwable ex)
			{
				if( ex instanceof OpenEditException)
				{
					throw (OpenEditException)ex;
				}
				throw new OpenEditException(ex);

			}
		}

		//		bulkProcessor.close();
		try
		{
			bulkProcessor.flush();
			bulkProcessor.awaitClose(5, TimeUnit.MINUTES);

			//This is in memory only flush
			RefreshResponse actionGet = getClient().admin().indices().prepareRefresh(catid).execute().actionGet();

		}
		catch (InterruptedException e)
		{
			throw new OpenEditException(e);
		}

		if (errors.size() > 0)
		{
			throw new OpenEditException((String) errors.get(0).toString());

		}
		long end = new Date().getTime();
		double total = (end - start) / 1000.0;
		log.info("processed bulk save  " + inBuffer.size() + " records in " + total + " seconds (" + getSearchType() + ")");
		// ConcurrentModificationException
		// builder = builder.setSource(content).setRefresh(true);
		// BulkRequestBuilder brb = getClient().prepareBulk();
		//
		// brb.add(Requests.indexRequest(indexName).type(getIndexType()).id(id).source(source));
		// }
		// if (brb.numberOfActions() > 0) brb.execute().actionGet();

		//getClient().admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();

	}

	protected void updateMasterClusterId(PropertyDetails details, Data inData, XContentBuilder content, boolean delete)
	{
		try
		{
			if (!isTrackEdits())
			{
				return;
			}
			
			Map status = (Map) inData.getValue("emrecordstatus");
			/*
			if(status == null) {
				status = new HashMap();
			}
			if (isReIndexing())
			{
				content.field("emrecordstatus", status);
				return;
			}
			*/
			if (isReIndexing())
			{
				if( status != null ) 
				{
					content.field("emrecordstatus", status);
					return;
				}
				if( isOptimizeReindex() )
				{
					return; //Dont worry if its not created already
				}
			}

			if(status == null) 
			{
				status = new HashMap();
			}

			String localClusterId = getElasticNodeManager().getLocalClusterId();
			String currentid = null;
			if(status != null) 
			{
				currentid = (String) status.get("mastereditclusterid");
			}
			if (currentid == null)
			{
				currentid = localClusterId;
			}
			
			status.put("recorddeleted", delete);
			status.put("mastereditclusterid", currentid);
			status.put("lastmodifiedclusterid", localClusterId);

			Object currentmod = status.get("recordmodificationdate");
			if (currentmod instanceof String)
			{
				currentmod = DateStorageUtil.getStorageUtil().parseFromStorage((String) currentmod);
			}

			currentmod = new Date();

			status.put("recordmodificationdate", currentmod);

			Object currentmastermod= null;
			if(status != null ) {
					
				currentmastermod = status.get("masterrecordmodificationdate");
			}
			if (currentmastermod instanceof String)
			{
				currentmastermod = DateStorageUtil.getStorageUtil().parseFromStorage((String) currentmastermod);
			}
			if (currentid.equals(localClusterId))
			{
				currentmastermod = currentmod;
			}
			status.put("masterrecordmodificationdate", currentmastermod);

			content.field("emrecordstatus", status);
			
		}
		catch (Exception ex)
		{
			throw new OpenEditException(ex);
		}
	}

	protected boolean isTrackEdits()
	{
		return true;
	}

	public void deleteAll(Collection inBuffer, User inUser)
	{
		if (inBuffer instanceof HitTracker)
		{
			HitTracker htracker = (HitTracker) inBuffer;
			htracker.enableBulkOperations();

		}
		String catid = getElasticIndexId();

		if (inBuffer.size() < 99) // 100 was too low - caused shard exceptions
		// due to thread pool size on large
		// ingests..
		{
			for (Iterator iterator = inBuffer.iterator(); iterator.hasNext();)
			{
				Data object = (Data) iterator.next();
				try
				{
					delete(object, inUser);
				}
				catch( Exception ex)
				{
					log.error("Could not delete " + object,ex);
				}
			}
			return;
		}

		final List errors = new ArrayList();
		// Make this not return till it is finished?
		BulkProcessor bulkProcessor = BulkProcessor.builder(getClient(), new BulkProcessor.Listener()
		{
			@Override
			public void beforeBulk(long executionId, BulkRequest request)
			{

			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, BulkResponse response)
			{
				for (int i = 0; i < response.getItems().length; i++)
				{
					// request.getFromContext(key)
					BulkItemResponse res = response.getItems()[i];
					if (res.isFailed())
					{
						log.info(res.getFailureMessage());
						errors.add(res.getFailureMessage());

					}
				}
			}

			@Override
			public void afterBulk(long executionId, BulkRequest request, Throwable failure)
			{
				log.info(failure);
				errors.add(failure);
			}
		}).setBulkActions(inBuffer.size()).setBulkSize(new ByteSizeValue(1, ByteSizeUnit.GB)).setFlushInterval(TimeValue.timeValueSeconds(5)).setConcurrentRequests(2).build();

		for (Iterator iterator = inBuffer.iterator(); iterator.hasNext();)
		{
			try
			{
				Data data2 = (Data) iterator.next();

				DeleteRequest req = Requests.deleteRequest(catid);

				if (data2.getId() != null)
				{
					req = req.id(data2.getId());

				}
				bulkProcessor.add(req);
			}
			catch (Exception ex)
			{
				log.error(ex);
			}
		}
		try
		{
			bulkProcessor.flush();
			bulkProcessor.awaitClose(5, TimeUnit.MINUTES);
			clearIndex();
			//This is in memory only flush
			RefreshResponse actionGet = getClient().admin().indices().prepareRefresh(catid).execute().actionGet();

		}
		catch (InterruptedException e)
		{
			throw new OpenEditException(e);
		}

		if (errors.size() > 0)
		{
			log.error("Bulk delete errors" + errors);
			//TODO: Throw exception?
		}
		clearIndex();
	}

	protected void saveToElasticSearch(PropertyDetails details, Data data, boolean delete, User inUser)
	{
		try
		{
			String catid = getElasticIndexId();
			XContentBuilder content = XContentFactory.jsonBuilder().startObject();

			IndexRequestBuilder builder = null;
			if (data.getId() == null)
			{
				builder = getClient().prepareIndex(catid); //Should we preface the id?
			}
			else
			{
				builder = getClient().prepareIndex(catid);
			}
			if(data.getId() != null) {
				builder.setId(data.getId());
			}
			
			
//			PropertyDetail parent = details.getDetail("_parent");
//			if (parent != null)
//			{
//				// String _parent = data.get(parent.getListId());
//				String _parent = data.get(parent.getId());
//				if (_parent != null)
//				{
//					builder = builder.setParent(_parent);
//				}
//				else
//				{
//					return; // Can't save data that doesn't have a parent!
//				}
//			}
			updateMasterClusterId(details, data, content, delete);
			updateIndex(content, data, details, inUser);
			content.endObject();
			if (log.isDebugEnabled())
			{
				log.info("Saving " + getSearchType() + " " + data.getId() + " = " + content.toString());
			}

			builder = builder.setSource(content);
			//log.info("Saving " + getSearchType() + " " + data.getId() + " = " + content.string());

			if (isRefreshSaves())
			{
				builder = builder.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
			}
			if (isCheckVersions())
			{
				updateVersion(data, builder);
			}
			IndexResponse response = null;

			response = builder.execute().actionGet();
			if (response.getId() != null)
			{
				data.setId(response.getId());
			}
			data.setValue(".version", response.getVersion());
		}
		catch (RemoteTransportException ex)
		{
			if (ex.getCause() instanceof VersionConflictEngineException)
			{
				throw new ConcurrentModificationException(ex.getMessage());
			}
		}
		catch (VersionConflictEngineException ex)
		{
			throw new ConcurrentModificationException(ex.getMessage());
		}
		catch (Exception ex)
		{
			log.error("Problem saving data in " + getCatalogId() + " " + getSearchType() + " " + data.getId());
			if (ex instanceof OpenEditException)
			{
				throw (OpenEditException) ex;
			}
			throw new OpenEditException(ex);
		}
	}

	public void setIndexId(long inIndexId)
	{
		fieldIndexId = inIndexId;
	}

	private void updateVersion(Data data, IndexRequestBuilder builder)
	{
		if (isCheckVersions())
		{
			Long version = (Long) data.getValue(".version");
			if (version != null)
			{
				if (version.longValue() > -1)
				{
					builder.setVersion(version);
				}
			}
		}
	}

	protected void updateIndex(XContentBuilder inContent, Data inData, PropertyDetails inDetails)
	{
		updateIndex(inContent, inData, inDetails, null);
	}

	protected void updateIndex(XContentBuilder inContent, Data inData, PropertyDetails inDetails, User inUser)
	{
		if (inData == null)
		{
			log.error("Null Data");
			return;
		}
		try
		{
			Map props = inData.getProperties();
			HashSet allprops = new HashSet();
			allprops.addAll(props.keySet());
			for (Iterator iterator = inDetails.iterator(); iterator.hasNext();)
			{
				PropertyDetail detail = (PropertyDetail) iterator.next();
				if (!detail.isDeleted())  //TODO: Dont pass in deleted to begin with
				{
					allprops.add(detail.getId());  //We need to make a copy anyways
				}
			}
			if (!allprops.contains("description"))
			{
				allprops.add("description");
			}
			if (!allprops.contains("id"))
			{
				allprops.add("id");
			}
			List badges = new ArrayList();

			for (Iterator iterator = allprops.iterator(); iterator.hasNext();)
			{
				String propid = (String) iterator.next();
				if (propid == null)
				{
					continue;
				}
				if (propid.contains("."))
				{
					continue;
				}
				if (propid.equals("entitysourcetype") ) 
				{
					inContent.field(propid, getSearchType()); //Cheap workaround for emfinder
					continue;
				}
				if (propid.equals("recordmodificationdate") || propid.equals("mastereditclusterid") || propid.equals("masterrecordmodificationdate") || propid.equals("emrecordstatus"))
				{
					continue;
				}
				if (propid.contains("recorddeleted"))
				{
					continue;
				}

				PropertyDetail detail = (PropertyDetail) inDetails.getDetail(propid);
				if (detail == null)
				{
					detail = inDetails.getLegacyDetail(propid);
				}

				if (detail == null && !propid.equals("description") && !propid.contains("_int") && !propid.equals("emrecordstatus") && !propid.equals("recordmodificationdate") && !propid.equals("mastereditclusterid"))
				{
					if(getPageManager().getPage("/prevent.html").exists()) {
						//make this a page prop or something.  Local testing.
						
						throw new OpenEditException(getSearchType() + " could not put mapping on data " + propid + " rowid=" + inData.getId());
					}
					detail = getPropertyDetailsArchive().createDetail(propid, propid);
					detail.setDeleted(false);
					//setType(detail);
					getPropertyDetailsArchive().savePropertyDetail(detail, getSearchType(), null);
					inDetails.addDetail(detail);

					if (!putMappings())
					{
						throw new OpenEditException(getSearchType() + " could not put mapping on data " + propid + " rowid=" + inData.getId());
					}
					else
					{
						log.info("Added new detail " + propid + " to " + getSearchType() + " as " + detail.getDataType());
					}
				}
				if (detail == null || !detail.isIndex()) //&& !propid.contains("sourcepath")
				{
					continue;
				}
				String key = detail.getId();
				if (key == null)
				{
					continue;
				}
				if (shoudSkipField(key))
				{
					continue;
				}
				if (propid.equals("description")) //This field must be defined by user first. or it will continue above
				{
					Object value = inData.getValue(propid);
					if (value == null || !isReIndexing())
					{
						StringBuffer desc = new StringBuffer();
						populateKeywords(desc, inData, inDetails);
						//populateFullText(inData, desc);

						if (desc.length() > 0)
						{
							value = desc.toString();
						}
					}
					//?inData.setValue("description",value);
					inContent.field(propid, value);
					continue;
				}
				Object value = inData.getValue(key);
				if (value != null)
				{
					if (value instanceof String && ((String) value).isEmpty())
					{
						value = null;
					}
				}
				//				if( isReIndexing() ) //When reindexing dont mess with this data
				//				{
				//					if (key.equals("recordmodificationdate"))
				//					{
				//						inContent.field(key, value);
				//						continue;
				//					}
				//					if (key.equals("mastereditclusterid"))
				//					{
				//						inContent.field(key, value); //Copy over existing values
				//						continue;
				//					}
				//				}	

				if (detail.isBadge() && value != null)
				{
					badges.add(getSearchType() + "_" + detail.getId() + "_" + value);
				}

				if (value != null && (detail.isDataType("objectarray") || detail.isDataType("nested") ))
				{
					if (!(value instanceof Collection))
					{
						if (value instanceof String)
						{
							String[] values = MultiValued.VALUEDELMITER.split((String) value);
							Collection objects = new ArrayList(values.length);
							//JsonSlurper slurper = new JsonSlurper();
							for (int i = 0; i < values.length; i++)
							{
								//{cliplabel=New Clip, timecodelength=114863, timecodestart=108276}
								String text = values[i];
								text = text.substring(1, text.length() - 1);
								String[] parts = text.split(",");
								Map chunk = new HashMap();
								for (int j = 0; j < parts.length; j++)
								{
									String ptext = parts[j];
									int eq = ptext.indexOf("=");
									if (eq > 0)
									{
										String id = ptext.substring(0, eq);
										String valtext = ptext.substring(eq + 1, ptext.length());
										chunk.put(id.trim(), valtext.trim());
									}
								}
								objects.add(chunk);
							}
							value = objects;
						}
						else
						{
							throw new OpenEditException(inData.getId() + " / " + detail.getId() + " Data was not a collection or a string " + value.getClass());
						}
					}
					Collection vals = (Collection) value;
					inContent.field(key, value); //This seems to map Long data types to Integer when they are read again	
					
					
				}
				else if (detail.isDate())
				{
					if (value != null)
					{
						Date date = null;
						if (value instanceof Date)
						{
							date = (Date) value;
						}
						else if (value instanceof String)
						{
							date = DateStorageUtil.getStorageUtil().parseFromStorage((String) value);
						}
						if (date != null)
						{
							inContent.field(key, date);
						}
					}
				}
				else if (detail.isBoolean())
				{
					boolean val = false;
					if (value instanceof Boolean)
					{
						val = (Boolean) value;
					}
					else if (value != null)
					{
						val = Boolean.valueOf((String) value);
					}
					inContent.field(key, val);
				}
				else if (detail.isDataType("double"))
				{
					Double val = null;

					if (value instanceof Double)
					{
						val = (Double) value;
					}
					else if (value instanceof Integer)
					{
						val = Double.valueOf((int) value);
					}
					else if (value != null)
					{
						val = Double.valueOf((String) value);
					}
					inContent.field(key, val);
				}
				else if (detail.isDataType("long"))
				{
					Long val = null;
					if (value instanceof Double && detail.getId().contains("timecode"))
					{
						Double d = (Double) value;
						val = Math.round(d * 1000d);
					}
					else if (value instanceof Double)
					{
						val = Math.round((Double) value); //Throw exception?
					}
					else if (value instanceof Integer)
					{
						val = ((Integer) value).longValue();
					}
					else if (value != null)
					{
						val = Long.valueOf(value.toString());
					}
					inContent.field(key, val);
				}
				else if (detail.isDataType("number"))
				{
					Number val = 0;

					if (value instanceof Number)
					{
						val = (Number) value;
					}
					else if (value != null)
					{
						try
						{
							val = Long.valueOf((String) value);
						}
						catch (Exception e)
						{
							//						throw new OpenEditException("Bad Value for Number:  " + val + " trying to set: " + key);
							log.info("Bad Value for Number:  " + val + " trying to set: " + key);

						}
					}
					if( value == null && detail.isViewType("autoincrement"))
					{
						IdManager manager = (IdManager)getModuleManager().getBean(getCatalogId(),"idManager");

						val = manager.nextNumber(getSearchType() + "_" + detail.getId());
					}
					
					inContent.field(key, val);
				}
				else if (detail.isMultiValue() || detail.isList())
				{
					if (value != null)
					{
						if (value instanceof Data)
						{
							String id = ((Data) value).getId();
							inContent.field(key, id);
						}
						else if (value instanceof Collection)
						{
							Collection values = (Collection) value;
							Collection ids = new ArrayList(values.size());
							for (Iterator iterator2 = values.iterator(); iterator2.hasNext();)
							{
								Object object = (Object) iterator2.next();
								if (object instanceof Data)
								{
									ids.add(((Data) object).getId());
								}
								else
								{
									ids.add(String.valueOf(object));
								}
							}
							inContent.field(key, ids);
						}
						else if (value instanceof String)
						{
							String vs = (String) value;
							if (vs.contains("|"))
							{
								String[] vals = VALUEDELMITER.split(vs);
								Collection values = Arrays.asList(vals);
								inContent.field(key, values);
							}
							else
							{
								inContent.field(key, value);
							}
						}
						else
						{
							inContent.field(key, value);
						}
					}
				}
				else if (value != null && detail.isGeoPoint())
				{
					//Saved it as two fields?
					if (value instanceof Position)
					{
						Position pos = (Position) value;
						GeoPoint point = new GeoPoint(pos.getLatitude(), pos.getLongitude());
						inContent.field(key, point);
					}
					else if (value instanceof String)
					{
						GeoPoint point = new GeoPoint((String) value);
						inContent.field(key, point);
						Position position = new Position(point.getLat(), point.getLon());
						inData.setValue(key, position); //For next time?
					}
					else if (value instanceof GeoPoint)
					{
						GeoPoint point = (GeoPoint) value;
						inContent.field(key, point);
						Position position = new Position(point.getLat(), point.getLon());
						inData.setValue(key, position); //For next time?
					}
				}
				else if (detail.isMultiLanguage())
				{
					// This is a nested document
					if (value == null)
					{
						continue;
					}
					key = key + "_int";
					XContentBuilder lanobj = inContent.startObject(key); // start
																			// first
																			// detail
																			// object

					HitTracker locales = getSearcherManager().getList(getCatalogId(), "locale");

					if (value instanceof String)
					{
						String target = (String) value;
						LanguageMap map = new LanguageMap();
						map.setText("en", target);
						value = map;
					}
					if (!(value instanceof LanguageMap))
					{
						throw new OpenEditException("Unexpexted value for MultiLanguage enabled field : " + value + " detail: " + detail.getId() + "Data Was: " + inData.getId() + " searchtype " + getSearchType());
					}
					LanguageMap map = (LanguageMap) value;
					for (Iterator iterator2 = locales.iterator(); iterator2.hasNext();)
					{
						Data locale = (Data) iterator2.next();
						String id = locale.getId();
						String localeval = map.getText(id); // get value
						if (localeval != null)
						{
							lanobj.field(id, localeval);
						}
					}
					lanobj.endObject();
				}
				else
				{
					if (value == null)
					{
						// log.info( getSearchType() + "Had null value " + key);
					}
					else
					{
						if (value instanceof LanguageMap)
						{
							value = ((LanguageMap) value).toString();
						}
						else if (!(value instanceof String))
						{
							String svalue = String.valueOf(value);
							if (svalue.isEmpty())
							{
								value = null;
							}
						}
						if (value != null)
						{
							inContent.field(key, value);
						}
					}
				}
				// log.info("Saved" + key + "=" + value );
			}
			if (!badges.isEmpty())
			{
				inContent.field("badge", badges);
			}
			
		
			addCustomFields(inContent, inData);
		}

		catch (Exception ex)
		{
			if (ex instanceof OpenEditException)
			{
				throw (OpenEditException) ex;
			}
			throw new OpenEditException(ex);
		}

	}

	public void addCustomFields(XContentBuilder inContent, Data inData)
	{
		// TODO Auto-generated method stub  Override this for custom searchers

	}

	//	private void setType(PropertyDetail detail) {
	//	
	//		
	//		
	//		String catid = getElasticIndexId();
	//		GetFieldMappingsRequest	 req = new GetFieldMappingsRequest().indices(catid).fields(detail.getId());
	//		GetFieldMappingsResponse resp = getClient().admin().indices().getFieldMappings(req).actionGet();
	//		Map data = resp.mappings();
	//		Object mappings = data.get(catid);
	//		if(mappings != null){
	//			Map types = (Map) data.get("blah");
	//		}
	//	}

	private void checkMapping(String inKey) throws Exception
	{
		String catid = getElasticIndexId();

		GetMappingsRequest req = new GetMappingsRequest().indices(catid);
		GetMappingsResponse resp = getClient().admin().indices().getMappings(req).actionGet();
		String indexname = getElasticNodeManager().getIndexNameFromAliasName(catid);
		if (indexname != null)
		{
			MappingMetadata typeMappings = resp.getMappings().get(indexname);

			LinkedHashMap data = (LinkedHashMap) typeMappings.getSourceAsMap();
			Map properties = (Map) data.get("properties");
			Object prop = properties.get(inKey);

			if (prop == null)
			{

				XContentBuilder jsonBuilder = XContentFactory.jsonBuilder();
				jsonBuilder.startObject("properties");
				jsonBuilder.startObject(inKey);
				jsonBuilder.endObject();
				jsonBuilder.endObject();
				PutMappingRequest putreq = new PutMappingRequest().indices(new String[] { catid }).source(jsonBuilder);
				getClient().admin().indices().putMapping(putreq);
			}
		}
	}

	public boolean shoudSkipField(String inKey)
	{
		//skip description?
		if ("_id".equals(inKey) || "_parent".equals(inKey) || "_all".equals(inKey) || inKey.contains("."))
		{
			return true;
		}
		return false;
	}

	public void deleteAll(User inUser)
	{

		// https://github.com/elastic/elasticsearch/blob/master/plugins/delete-by-query/src/main/java/org/elasticsearch/action/deletebyquery/TransportDeleteByQueryAction.java#L104

		if(inUser != null) {
		log.info("Deleted all records database " + getSearchType() + " by user:" + inUser.getId());
	}
//		 DeleteByQueryRequestBuilder delete =
//		 getClient().prepareDeleteByQuery(toId(getCatalogId()));
//		 delete.setTypes(getSearchType());
//		 delete.setQuery(new MatchAllQueryBuilder()).execute().actionGet();
		
		org.openedit.data.QueryBuilder q = query().all();
		
		q.getQuery().setIncludeDeleted(true);
		
		HitTracker all = q.search();
		all.enableBulkOperations();
		
		deleteAll(all, null);

	}

	public void delete(Data inData, User inUser)
	{
		PropertyDetails details = getPropertyDetailsArchive().getPropertyDetailsCached(getSearchType());

		Map recordstatus = (Map) inData.getValue("emrecordstatus");

		if( recordstatus != null)
		{
			if( inUser != null)
			{
				saveToElasticSearch(details, inData, true, inUser);
				clearIndex();
				return;
			}
		}
		//We should not do this as much for some tables
		String id = inData.getId();
		//log.info(id.length());
		DeleteRequestBuilder delete = getClient().prepareDelete(toIndexId(), id);
//		if (inData.get("_parent") != null)
//		{
//			delete.setParent(inData.get("_parent"));
//		}
		delete.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE).execute().actionGet();
		clearIndex();
		
	}

	// Base class only updated the index in bulk
	public void saveAllData(Collection<Data> inAll, User inUser)
	{
		updateIndex(inAll, inUser);
	}

	public synchronized String nextId()
	{
		// Lock lock = getLockManager().lock(getCatalogId(), loadCounterPath(),
		// "admin");
		// try
		// {
		// return String.valueOf(getIntCounter().incrementCount());
		// }
		// finally
		// {
		// getLockManager().release(getCatalogId(), lock);
		// }
		throw new OpenEditException("Should not call next ID");
	}

	protected IntCounter getIntCounter()
	{
		// if (fieldIntCounter == null)
		// {
		// fieldIntCounter = new IntCounter();
		// // fieldIntCounter.setLabelName(getSearchType() + "IdCount");
		// Page prop = getPageManager().getPage(loadCounterPath());
		// File file = new File(prop.getContentItem().getAbsolutePath());
		// file.getParentFile().mkdirs();
		// fieldIntCounter.setCounterFile(file);
		// }
		// return fieldIntCounter;
		throw new OpenEditException("Cant load int counters from elasticsearch");
	}

	/** TODO: Update this location to match the new standard location */
	protected String loadCounterPath()
	{
		return "/WEB-INF/data/" + getCatalogId() + "/" + getSearchType() + "s/idcounter.properties";
	}

	// public boolean hasChanged(HitTracker inTracker)
	// {
	// //We will add a refresh() to the tracker and call it with cachedSearch
	// //We will scroll forward using the scroll
	// //We will scroll backwards using a previous chunck or new search
	// return false;
	// }

	// public HitTracker checkCurrent(WebPageRequest inReq, HitTracker
	// inTracker) throws OpenEditException
	// {
	// return inTracker;
	// }

	protected boolean flushChanges()
	{
		FlushRequest req = Requests.flushRequest(toIndexId()); //To The disk drive
		FlushResponse res = getClient().admin().indices().flush(req).actionGet();
		if (res.getSuccessfulShards() > 0)
		{
			return true;
		}
		return false;
	}

	
	protected boolean isDeleted(Map source)
	{
		Map  status  = (Map)source.get("emrecordstatus");
		if( status != null)
		{
			Object deleted = status.get("recorddeleted");
			if( deleted != null && Boolean.parseBoolean(String.valueOf( deleted) ) )
			{
				return true;
			}
		}
		return false;
	}

	protected void copyData(Data data, Data typed)
	{
		typed.setId(data.getId());
		typed.setName(data.getName());
		typed.setSourcePath(data.getSourcePath());
		Map<String, Object> props = data.getProperties();
		updateData(props, typed);
		// for (Iterator iterator = props.keySet().iterator();
		// iterator.hasNext();)
		// {
		// String key = (String) iterator.next();
		// Object obj = props.get(key);
		// typed.setProperty(key, String.valueOf(obj)); //TODO: use setValue ?
		// }
	}

	protected void populateKeywords(StringBuffer inFullDesc, Data inData, PropertyDetails inDetails)
	{
		for (Iterator iter = inDetails.findKeywordProperties().iterator(); iter.hasNext();)
		{
			PropertyDetail det = (PropertyDetail) iter.next();
			if (det.isList())
			{
				Object prop = inData.getValue(det.getId());
				if (prop != null)
				{
					if (prop instanceof Collection)
					{
						Collection values = (Collection) prop;
						for (Iterator iterator = values.iterator(); iterator.hasNext();)
						{
							Object object = (Object) iterator.next();
							if (object instanceof String)
							{
								Data data = (Data) getSearcherManager().getCachedData(det.getListCatalogId(), det.getListId(), (String) object);
								if (data != null && data.getName() != null)
								{
									inFullDesc.append(data.getName());
								}
							}
							else
							{
								inFullDesc.append(String.valueOf(object));
							}
							inFullDesc.append(' ');
						}
					}
					else if(prop instanceof String)
					{
						Data data = (Data) getSearcherManager().getCachedData(det.getListCatalogId(), det.getListId(), (String) prop);
						if (data != null && data.getName() != null)
						{
							inFullDesc.append(data.getName());
							inFullDesc.append(' ');
						}
					}
				}
			}
			else
			{
				if (det.isMultiLanguage())
				{

					Object value = inData.getValue(det.getId());
					if (value instanceof String)
					{
						String target = (String) value;
						LanguageMap map = new LanguageMap();
						map.setText("en", target);
						value = map;

					}
					LanguageMap map = (LanguageMap) value;

					if (map != null)
					{
						HitTracker locales = getSearcherManager().getList(getCatalogId(), "locale");

						for (Iterator iterator2 = locales.iterator(); iterator2.hasNext();)
						{
							Data locale = (Data) iterator2.next();
							String id = locale.getId();
							String localeval = map.getText(id); // get a
																// location
																// specific
																// value
							if (localeval != null)
							{

								inFullDesc.append(localeval);
								inFullDesc.append(' ');
							}

						}
					}
				}
				else if (det.isDataType("objectarray") || det.isDataType("nested"))
				{
					Object values = inData.getValue(det.getId());
					if (values != null && values instanceof String)
					{
						//Spreadsheet import
						inFullDesc.append(values);
						inFullDesc.append(' ');
						return;
					}

					if (values != null && det.getObjectDetails() != null)
					{
						Collection maps = (Collection) values;
						for (Iterator iterator = maps.iterator(); iterator.hasNext();)
						{
							Map map = (Map) iterator.next();
							for (Iterator miterator = det.getObjectDetails().iterator(); miterator.hasNext();)
							{
								PropertyDetail detal = (PropertyDetail) miterator.next();
								if (detal.isKeyword())
								{
									Object val = map.get(detal.getId());
									if (val != null)
									{
										if( detal.isMultiValue() )
										{
											Collection colvalues = null;
											if (val instanceof Collection)
											{
												colvalues = (Collection) val;
											}
											else
											{
												colvalues = new ArrayList();
												colvalues.add(val);
											}
											//Could  be an collection
											for (Iterator iterator2 = colvalues.iterator(); iterator2.hasNext();)
											{
												String string = (String) String.valueOf(iterator2.next());

												if( detal.isList() )
												{
													Data data = (Data) getSearcherManager().getCachedData(detal.getListCatalogId(), detal.getListId(), (String) string);
													if (data != null && data.getName() != null)
													{
														inFullDesc.append(data.getName());
														inFullDesc.append(' ');
													}
												}
												else
												{
													inFullDesc.append(string);
													inFullDesc.append(' ');
												}
											}
										}
										else
										{
											inFullDesc.append(String.valueOf(val));
											inFullDesc.append(' ');
										}
									}
								}

							}
						}
					}
				}
				else if( det.isMultiValue() ) //But not a list
				{
					Collection values  = inData.getValues(det.getId());
					if( values != null && !values.isEmpty() )
					{
						for (Iterator iterator = values.iterator(); iterator.hasNext();)
						{
							String oneval = (String) iterator.next();
							inFullDesc.append(oneval);
							inFullDesc.append(' ');
						}
					}
				}
				else
				{
					String val = inData.get(det.getId());
					if (val != null)
					{
						inFullDesc.append(val);
						inFullDesc.append(' ');
					}
				}
			}
		}
	}

	public void reIndexAll() throws OpenEditException
	{
		// there is not reindex step since it is only in memory
		if (isReIndexing())
		{
			return;
		}
		synchronized (this)
		{
			try
			{
				setReIndexing(true);
				setOptimizeReindex(false);

				// putMappings(); //We can only try to put mapping. If this
				// failes then they will
				ElasticHitTracker allhits = (ElasticHitTracker) getAllHits();
				allhits.enableBulkOperations();
				ArrayList tosave = new ArrayList();
				for (Iterator iterator2 = allhits.iterator(); iterator2.hasNext();)
				{
					Data hit = (Data) iterator2.next();
					if( hit.getId() == null || hit.getId().trim().isEmpty())
					{
						continue;
					}
					Data real = (Data) loadData(hit);

					tosave.add(real);
					if (tosave.size() > 1000)
					{
						updateIndex(tosave, null);
						tosave.clear();
					}
				}
				updateIndex(tosave, null);
				ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
				clearScrollRequest.addScrollId(allhits.getLastScrollId());
				getClient().clearScroll(clearScrollRequest).actionGet();
				//System.gc();

			}
			finally
			{
				setReIndexing(false);
				setOptimizeReindex(true);
			}
		}
	}

	@Override
	public void restoreSettings()
	{
		getPropertyDetailsArchive().clearCustomSettings(getSearchType());
		// deleteOldMapping(); //you will lose your data!
		// reIndexAll();
		putMappings();
	}

	@Override
	public void reloadSettings()
	{
		// deleteOldMapping(); //you will lose your data!
		// reIndexAll();
		putMappings();

	}

	public void updateData(Map inSource, Data inData)
	{
		if (inData instanceof SearchData)
		{
			SearchData data = (SearchData) inData;
			data.setSearchData(inSource);
		}
		else
		{

			for (Iterator iterator = inSource.keySet().iterator(); iterator.hasNext();)
			{
				String key = (String) iterator.next();
				Object object = inSource.get(key);

				inData.setValue(key, object);

			}
		}
	}

	public boolean tableExists()
	{
		boolean used = getClient().admin().indices().exists(new IndicesExistsRequest(toIndexId())).actionGet().isExists();
		return used;

	}
	//TODO:  This is a node level task now
//	@Override
//	public void reindexInternal() throws OpenEditException
//	{
//		HitTracker allhits = getAllIndexed();
//		setReIndexing(true);
//		try
//		{
//			int SIZE = 3000;
//
//			allhits.enableBulkOperations();
//			allhits.setHitsPerPage(SIZE);
//			ArrayList tosave = new ArrayList();
//			for (Iterator iterator2 = allhits.iterator(); iterator2.hasNext();)
//			{
//				Data hit = (Data) iterator2.next();
//				if( hit.getId() == null || hit.getId().trim().isEmpty())
//				{
//					continue;
//				}
//				tosave.add(hit);
//				if (tosave.size() > SIZE)
//				{
//					updateInBatch(tosave, null);
//
//					tosave.clear();
//				}
//			}
//			updateInBatch(tosave, null);
//		}
//		finally
//		{
//			setReIndexing(false);
//		}
//
//	}

	/**
	 * @override
	 */
	public String getConfigValue(String inKey)
	{
		MediaArchive archive = (MediaArchive) getModuleManager().getBean(getCatalogId(), "mediaArchive");
		return archive.getCatalogSettingValue(inKey);
	}

	public String getExistingMapping()
	{
		String cat = getCatalogId().replace("/", "_");
		String indexid = getElasticNodeManager().getIndexNameFromAliasName(cat);

		GetMappingsResponse getMappingsResponse = getElasticNodeManager().getClient().admin().indices().getMappings(new GetMappingsRequest().indices(indexid)).actionGet();
		ImmutableOpenMap<String, MappingMetadata> indexToMappings = getMappingsResponse.getMappings();

		MappingMetadata actualMapping = indexToMappings.get(indexid);
		if (actualMapping != null)
		{
			String jsonString;
			
				jsonString = actualMapping.source().string();
				//				JSONObject config = (JSONObject) new JSONParser().parse(returned);
				jsonString = JsonOutput.prettyPrint(jsonString);
				//				JSONObject json = new JSONObject(jsonString); // Convert text to object
				//				SjsonString = json.toString(4);
				return jsonString;
			

		}
		return null;
	}

	protected Map checkTypes(Map inData)
	{
		for (Iterator iterator = inData.keySet().iterator(); iterator.hasNext();)
		{
			String type = (String) iterator.next();
			PropertyDetail detail = getDetail(type);
			if (detail != null)
			{
				if (detail.isDataType("objectarray") || detail.isDataType("nested"))
				{
					Object childdata = inData.get(type);
					if (childdata instanceof List)
					{
						Collection childdatalist = (List) childdata;
						for (Iterator iterator2 = childdatalist.iterator(); iterator2.hasNext();)
						{
							Map map = (Map) iterator2.next();
							for (Iterator iterator3 = detail.getObjectDetails().iterator(); iterator3.hasNext();)
							{
								PropertyDetail childdetail = (PropertyDetail) iterator3.next();
								fixTypes(map, childdetail);
							}
						}
					}
				}
				else
				{
					fixTypes(inData, detail);
				}
			}
		}
		return inData;
	}

	protected void fixTypes(Map inFields, PropertyDetail detail)
	{
		if (detail.isDataType("long"))
		{
			Object num = inFields.get(detail.getId());
			if (num != null)
			{
				if (num instanceof String)
				{
					num = Long.parseLong((String) num);
				}
				if (num instanceof Integer)
				{
					num = ((Integer) num).longValue();
				}
			}
			inFields.put(detail.getId(), num);
		}
	}
	
	public OpenNodeManager getElasticNodeManager() {
		return fieldOpensearchNodeManager;
	}
	

	public void saveJson(Collection inJsonArray)
	{
		BulkProcessor processor = getElasticNodeManager().getBulkProcessor();

		try
		{
			for (Iterator iterator = inJsonArray.iterator(); iterator.hasNext();)
			{
				JSONObject json = (JSONObject) iterator.next();
				json.put("olw.type", getSearchType());
				IndexRequest req = Requests.indexRequest(getElasticIndexId());
				req.source(json.toJSONString());
				//log.info("savinng " + json);
				//Parse the json and save it with id

				String id = (String) json.get("id");
				if (id != null)
				{
					req.id(id);
				}
				processor.add(req);
			}
			//processor.awaitClose(5, TimeUnit.MINUTES);  do in flushBulk
		}
		catch (Exception e)
		{
			throw new OpenEditException("Errors saving bulk data ", e);
		}
		finally
		{
			getElasticNodeManager().flushBulk();
		}
	}

	public void saveJson(String inID, JSONObject json)
	{

		BulkProcessor processor = getElasticNodeManager().getBulkProcessor();
		IndexRequest req = Requests.indexRequest(getElasticIndexId());
		req.source(json.toJSONString());
		req.id(inID);

		processor.add(req);

	}

	

	@Override
	public String getFulltext(Data inSearchHitData)
	{
		return getFulltext(inSearchHitData,getSearchType());
	}
	public String getFulltext(Data inSearchHitData, String type)
	{
		StringBuffer out = new StringBuffer();
		populateFullText(inSearchHitData,  type, out);
		return out.toString();
	}
	
	protected void populateFullText(Data data, String datatype, StringBuffer fullDesc)
	{
		if (isIncludeFullText() && Boolean.parseBoolean(data.get("hasfulltext")))
		{
			//Legacy support 
			if( datatype.equals("asset"))
			{
				datatype = "assets"; //TODO: Move everyone over
			}
			
			String path = "/WEB-INF/data/" + getCatalogId() + "/" + datatype + "/" + data.getSourcePath() + "/fulltext.txt";
			ContentItem item = getPageManager().getRepository().getStub(path);
			if (item.exists())
			{
				Reader input = null;
				try
				{
					input = new InputStreamReader(item.getInputStream(), "UTF-8");
					StringWriter output = new StringWriter();
					getFiller().setMaxSize(getFullTextCap());
					getFiller().fill(input, output);
					fullDesc.append(output.toString());
				}
				catch (IOException ex)
				{
					log.error(ex);
				}
				finally
				{
					getFiller().close(input);
				}
			}
		}
	}
	
	
	protected void addAggregations(WebPageRequest inPageRequest, SearchQuery inSearch) {

		String aggs = inPageRequest.findValue("aggs");
		if(aggs == null) {
			aggs = (String) inPageRequest.getPageValue("aggs");
		}
		if(aggs != null) {
			ElasticSearchQuery search = (ElasticSearchQuery)inSearch;
			search.setAggregationJson(aggs);
		}
	}

	
}
