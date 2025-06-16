package com.hmdp.service;


import com.hmdp.dto.Result;
import com.hmdp.query.BlogPageQuery;

import java.io.IOException;

public interface ISearchService {

    Result search(BlogPageQuery query) throws IOException;

}
