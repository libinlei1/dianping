package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.query.BlogPageQuery;
import com.hmdp.service.IBlogService;
import com.hmdp.service.ISearchService;
import lombok.Generated;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.search.SearchService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@CrossOrigin
public class SearchController {

    private final ISearchService searchService;

    @GetMapping("/list")
    public Result search(BlogPageQuery query) throws IOException {
        return searchService.search(query);
    }

}
