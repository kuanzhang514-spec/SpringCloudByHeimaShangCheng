package com.hmall.research.controller;

import cn.hutool.core.util.StrUtil;
import com.hmall.common.domain.ItemDTO;
import com.hmall.common.domain.ItemPageQuery;
import com.hmall.common.domain.PageDTO;
import com.hmall.research.util.parseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Api(tags = "搜索相关接口")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {


    private RestHighLevelClient client = new RestHighLevelClient(
            RestClient.builder(HttpHost.create("http://192.168.100.130:9200"))
    );


    @ApiOperation("搜索商品")
    @GetMapping("/list")
    public PageDTO<ItemDTO> search(ItemPageQuery query) throws IOException {
      /*  // 分页查询
        Page<Item> result = itemService.lambdaQuery()
                .like(StrUtil.isNotBlank(query.getKey()), Item::getName, query.getKey())
                .eq(StrUtil.isNotBlank(query.getBrand()), Item::getBrand, query.getBrand())
                .eq(StrUtil.isNotBlank(query.getCategory()), Item::getCategory, query.getCategory())
                .eq(Item::getStatus, 1)
                .between(query.getMaxPrice() != null, Item::getPrice, query.getMinPrice(), query.getMaxPrice())
                .page(query.toMpPage("update_time", false));
        // 封装并返回
        return PageDTO.of(result, ItemDTO.class);*/
        String indexName = "items";

        //准备请求
        SearchRequest request = new SearchRequest(indexName);
        BoolQueryBuilder bool = QueryBuilders.boolQuery();  //准备bool查询

        if (StrUtil.isNotBlank(query.getKey())) {
            //关键字搜索
            bool.must(QueryBuilders.matchQuery("name", query.getKey()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            //分类过滤
            bool.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }
        if (StrUtil.isNotBlank(query.getBrand())) {
            //品牌过滤
            bool.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }
        if (query.getMinPrice() != null) {
            //价格最小值过滤
            bool.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()));
        }
        if (query.getMaxPrice() != null) {
            //价格最大值过滤
            bool.filter(QueryBuilders.rangeQuery("price").lte(query.getMaxPrice()));
        }

        request.source().query(bool);

        //分页
        request.source().from((query.getPageNo() - 1) * query.getPageSize()).size(query.getPageSize());

        //排序
        if (StrUtil.isNotBlank(query.getSortBy())) {
            request.source().sort(query.getSortBy(), query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC);
        } else {
            request.source().sort("updateTime", query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC);
        }

        //发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //解析结果
        PageDTO<ItemDTO> itemDTOPageDTO = parseResult.parseResponseResult(response, query);
        System.out.println("--查询条件:" + query);
        System.out.print("数据总条数:" + itemDTOPageDTO.getTotal());
        System.out.println(", 数据总页数:" + itemDTOPageDTO.getPages());
        System.out.println("查询到的数据:" + itemDTOPageDTO);
        return itemDTOPageDTO;
    }


}
