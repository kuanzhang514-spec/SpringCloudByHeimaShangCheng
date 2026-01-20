package com.hmall.item;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.common.domain.*;
import com.hmall.common.utils.CollUtils;
import com.hmall.item.service.impl.ItemServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 这个测试类的作用是利用elasticSearch的api操作文档（数据从mysql--->es）
 * <p>
 * 在elasticsearch提供的API中，与elasticsearch一切交互都封装在一个名为RestHighLevelClient的类中，
 * 必须先完成这个对象的初始化，建立与elasticsearch的连接
 * 利用properties激活local配置,用来操作mysql数据库
 */
@SpringBootTest(properties = "spring.profiles.active=local")
@Slf4j
public class DocumentTest {

    private RestHighLevelClient client;

    @Autowired
    private ItemServiceImpl itemService;

    /**
     * 初始化client连接
     */
    @BeforeEach
    void setUp() {
        client = new RestHighLevelClient(
                RestClient.builder(HttpHost.create("http://192.168.100.130:9200"))
        );
    }

    /**
     * 释放client连接
     *
     * @throws IOException
     */
    @AfterEach
    void tearDown() throws IOException {
        this.client.close();
    }

    /**
     * 1.往索引库里添加指定商品id的商品
     * 新增 && 全局修改
     */
    @Test
    public void addItemToDocById() throws IOException {
        //1.根据商品id查询商品，转换成itemDoc
        long id = 1L;
        Item item = itemService.getById(id);
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
        //2.往索引库里添加数据
        String itemName = "items";
        IndexRequest request = new IndexRequest(itemName);
        request.id(Long.toString(id));
        request.source(itemDoc, XContentType.JSON);
        client.index(request, RequestOptions.DEFAULT);
    }

    /**
     * 2.查询指定id的文档数据
     */
    @Test
    public void searchDocById() throws IOException {
        long id = 1L;
        String itemName = "items";
        GetRequest request = new GetRequest(itemName, String.valueOf(id));
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        String json = response.getSourceAsString();//获取源数据
        ItemDoc itemDoc = JSONUtil.toBean(json, ItemDoc.class);
        System.out.println(itemDoc);
    }

    /**
     * 3.删除指定id的文档数据
     *
     * @throws IOException
     */
    @Test
    public void deleteDocByid() throws IOException {
        long id = 1L;
        String itemName = "items";
        DeleteRequest request = new DeleteRequest(itemName, String.valueOf(id));
        client.delete(request, RequestOptions.DEFAULT);

    }

    /**
     * 4.根据指定id局部修改文档数据
     *
     * @throws IOException
     */
    @Test
    public void updateDocById() throws IOException {
        long id = 1L;
        String itemName = "items";
        UpdateRequest request = new UpdateRequest(itemName, String.valueOf(id));
        request.doc(
                "price", 58800,
                "commentCount", 1
        );
        client.update(request, RequestOptions.DEFAULT);
    }

    /**
     * 5.批量导入文档数据**************
     * 把item数据库表中的所有商品数据导入到es中
     * BulkRequest
     *
     * @throws IOException
     */
    @Test
    void loadItemDocs() throws IOException {
        String indexName = "items";
        int count = 0;
        // 分页查询商品数据
        int pageNo = 1;
        int size = 1000;
        while (true) {
            Page<Item> page = itemService.lambdaQuery().eq(Item::getStatus, 1).page(new Page<Item>(pageNo, size));
            // 非空校验
            List<Item> items = page.getRecords();
            if (CollUtils.isEmpty(items)) {
                log.info("成功添加数据{}条数据到--->{}", count, indexName);
                return;
            }
            count += items.size();
            log.info("加载第{}页数据，共{}条", pageNo, items.size());
            // 1.创建Request
            BulkRequest request = new BulkRequest(indexName);
            // 2.准备参数，添加多个新增的Request
            for (Item item : items) {
                // 2.1.转换为文档类型ItemDTO
                ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);
                // 2.2.创建新增文档的Request对象
                request.add(new IndexRequest()
                        .id(itemDoc.getId())
                        .source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON));
            }
            // 3.发送请求
            client.bulk(request, RequestOptions.DEFAULT);

            // 翻页
            pageNo++;
        }
    }

    /**
     * 基于es的搜索功能实现***
     * DSL语句 叶子查询+bool复合查询
     */

    /**
     * 1.查找出所有的数据
     *
     * @throws IOException
     */
    @Test
    public void queryAll() throws IOException {
        String indexName = "items";
        SearchRequest request = new SearchRequest(indexName);
        request.source().query(QueryBuilders.matchAllQuery());
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        //解析结果
        SearchHits searchHits = response.getHits();
        System.out.println("搜索到的商品数据量为:" + searchHits.getTotalHits() + " 条");
        SearchHit[] hits = searchHits.getHits();
        System.out.println("--------------");
        for (SearchHit hit : hits) {
            String json = hit.getSourceAsString();
            System.out.println(json);
        }
        System.out.println("--------------");
    }

    //    @Test
    public void searchItems() throws IOException {
        String key = "手机";
        Integer pageNo = 1;
        Integer pageSize = 20;
        String sortBy = "sold";
        Boolean isAsc = false;
        String category = "手机";
        String brand = "小米";
        Integer minPrice = 10000;
        Integer maxPrice = 29900;

        String indexName = "items";
        ItemPageQuery query = new ItemPageQuery();
        query.setKey(key);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setSortBy(sortBy);
        query.setIsAsc(isAsc);
        query.setBrand(brand);
        query.setMinPrice(minPrice);
        query.setMaxPrice(maxPrice);
        query.setCategory(category);

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
        PageDTO<ItemDTO> itemDTOPageDTO = parseResponseResult(response, query);
//        return itemDTOPageDTO;

        System.out.println(itemDTOPageDTO);
    }

    private PageDTO<ItemDTO> parseResponseResult(SearchResponse response, ItemPageQuery query) {
        //集合用来封装解析的数据
        ArrayList<ItemDoc> itemDocList = new ArrayList<>();
        //获取全部数据
        SearchHits searchHits = response.getHits();

        long total = searchHits.getTotalHits().value;
        System.out.println("总条数:" + total);

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
