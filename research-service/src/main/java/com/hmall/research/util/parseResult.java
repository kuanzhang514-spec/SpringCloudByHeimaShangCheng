package com.hmall.research.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmall.common.domain.ItemDTO;
import com.hmall.common.domain.ItemDoc;
import com.hmall.common.domain.ItemPageQuery;
import com.hmall.common.domain.PageDTO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.util.ArrayList;
import java.util.List;

/**
 * 这个工具类用来解析elasticsearch的复合查询结果，封装成分页数据返回
 */

public class parseResult {
    public static PageDTO<ItemDTO> parseResponseResult(SearchResponse response, ItemPageQuery query) {
        //集合用来封装解析的数据
        ArrayList<ItemDoc> itemDocList = new ArrayList<>();
        //获取全部数据
        SearchHits searchHits = response.getHits();

        long total = searchHits.getTotalHits().value;
        //获取源数据
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
            itemDocList.add(doc);
        }
        List<ItemDTO> itemDTOS = BeanUtil.copyToList(itemDocList, ItemDTO.class);

        long pageSize = query.getPageSize();
        long pages = total % pageSize == 0 ? total / pageSize : total / pageSize + 1;

        // 使用 PageDTO.of 的重载方法：传入 total、pages 和 list
        return PageDTO.of(total, pages, itemDTOS);
    }

}
