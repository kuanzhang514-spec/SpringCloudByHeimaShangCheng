package com.hmall.research.controller;

import com.hmall.common.domain.ItemDoc;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;


/**
 * 这个controller作用 :
 * 对elasticsearch 的 items 索引库的文档的<增><删><改>操作
 */

@RestController
@RequestMapping("/_doc")
@Slf4j
public class ItemDocController {

    private final String indexName = "items";

    private RestHighLevelClient client = new RestHighLevelClient(
            RestClient.builder(HttpHost.create("http://192.168.100.130:9200"))
    );

    /**
     * 1.全量修改的操作
     *
     * @param itemDoc
     * @return
     * @throws IOException
     */
    @PostMapping("/add")
    public boolean add(@RequestBody ItemDoc itemDoc) throws IOException {
        IndexRequest request = new IndexRequest(indexName);
        request.id(itemDoc.getId());
        request.source(itemDoc, XContentType.JSON);
        client.index(request, RequestOptions.DEFAULT);
        return true;
    }

    /**
     * 2.根据id删除文档
     *
     * @param id
     * @return
     * @throws IOException
     */
    @DeleteMapping("/delete/{id}")
    public void deleteDocById(@PathVariable("id") Long id) throws IOException {
        DeleteRequest request = new DeleteRequest(indexName, String.valueOf(id));
        client.delete(request, RequestOptions.DEFAULT);
    }
}
