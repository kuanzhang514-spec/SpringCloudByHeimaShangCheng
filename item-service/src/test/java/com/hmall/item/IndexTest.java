package com.hmall.item;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

/**
 * 这个测试类的作用是利用elasticSearch的api操作索引库
 * <p>
 * 在elasticsearch提供的API中，与elasticsearch一切交互都封装在一个名为RestHighLevelClient的类中，
 * 必须先完成这个对象的初始化，建立与elasticsearch的连接
 */
@SpringBootTest
@Slf4j
public class IndexTest {

    private final String MAPPING_TEMPLATE = "{\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"id\": {\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"name\":{\n" +
            "        \"type\": \"text\",\n" +
            "        \"analyzer\": \"ik_max_word\"\n" +
            "      },\n" +
            "      \"price\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"stock\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"image\":{\n" +
            "        \"type\": \"keyword\",\n" +
            "        \"index\": false\n" +
            "      },\n" +
            "      \"category\":{\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"brand\":{\n" +
            "        \"type\": \"keyword\"\n" +
            "      },\n" +
            "      \"sold\":{\n" +
            "        \"type\": \"integer\"\n" +
            "      },\n" +
            "      \"commentCount\":{\n" +
            "        \"type\": \"integer\",\n" +
            "        \"index\": false\n" +
            "      },\n" +
            "      \"isAD\":{\n" +
            "        \"type\": \"boolean\"\n" +
            "      },\n" +
            "      \"updateTime\":{\n" +
            "        \"type\": \"date\"\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";


    private RestHighLevelClient client;

    /**
     * 初始化client连接
     */
    @BeforeEach
    void setUp() {
        client = new RestHighLevelClient(
                RestClient.builder(HttpHost.create("http://192.168.100.130:9200"))
        );
        log.info("es连接已创建");
    }

    /**
     * 释放client连接
     *
     * @throws IOException
     */
    @AfterEach
    void tearDown() throws IOException {
        this.client.close();
        log.info("es连接已断开");
    }

    /**
     * 1.创建搜索的索引库
     */
    @Test
    void createSearchIndex() throws IOException {
        String indexName = "items";
        //1.创建Request对象
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        //2.准备请求参数 MAPPING_TEMPLATE这是纯es语句
        request.source(MAPPING_TEMPLATE, XContentType.JSON);
        //3.发起请求
        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("成功创建{}索引库", indexName);
    }

    /**
     * 2.删除指定索引库
     */
    @Test
    void deleteIndex() throws IOException {
        String indexName = "items"; //在这里指定索引库的名字
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        client.indices().delete(request, RequestOptions.DEFAULT);
        log.info("成功删除{}索引库", indexName);
    }

    /**
     * 3.判断指定索引库是否存在
     */
    @Test
    void isIndexHave() throws IOException {
        String indexName = "items";
        GetIndexRequest request = new GetIndexRequest(indexName);
        boolean exists = client.indices().exists(request, RequestOptions.DEFAULT);
        if (exists) {
            System.out.println("索引库:" + indexName + "存在");
        } else {
            System.out.println("索引库:" + indexName + "不存在");
        }
    }
}


