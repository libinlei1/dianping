package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogDoc;
import com.hmdp.query.BlogPageQuery;
import com.hmdp.service.ISearchService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SearchServiceImpl implements ISearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Override
    public Result search(BlogPageQuery query) throws IOException {
        SearchRequest searchRequest = new SearchRequest("blogs");
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        if (StrUtil.isNotBlank(query.getTitle())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("title", query.getTitle()));
        }
        if (StrUtil.isNotBlank(query.getName())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("name", query.getName()));
        }

        searchRequest.source().query(boolQueryBuilder);
        searchRequest.source().from((query.getPageNo()-1)*query.getPageSize()).size(query.getPageSize());
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        SearchHits searchHits = response.getHits();
        long totalHits = searchHits.getTotalHits().value;

        List<BlogDoc> blogDocs = new ArrayList<>();
        for(SearchHit hit : searchHits.getHits()) {
            BlogDoc blogDoc = JSONUtil.toBean(hit.getSourceAsString(), BlogDoc.class);
            blogDocs.add(blogDoc);
        }

        return Result.ok(blogDocs);
    }
}
