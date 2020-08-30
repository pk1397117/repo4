package cn.itcast.jd.task;

import cn.itcast.jd.pojo.Item;
import cn.itcast.jd.service.ItemService;
import cn.itcast.jd.util.HttpUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class ItemTask {

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private ItemService itemService;


    private static final ObjectMapper MAPPER = new ObjectMapper();

    //private static JSONObject jsonObject = new JSONObject();


    //当下载任务完成后，间隔多长时间进行下一次的任务。
    @Scheduled(fixedDelay = 100*1000)
    public void itemTask() {

        //声明需要解析的初始地址
        String url = "https://search.jd.com/Search?keyword=%E6%89%8B%E6%9C%BA&s=1&click=1&page=";

        //按照页码对搜索结果进行遍历解析
        for(int i = 1; i < 10; i = i+2){
            String html = httpUtils.doGetHtml(url + i);

            //解析页面获取商品数据并存储
            this.parse(html);
        }
        System.out.println("手机数据抓取完成！");
    }

    //解析页面获取商品数据并存储
    private void parse(String html) {
        //解析html获取Document对象
        Document doc = Jsoup.parse(html);

        //获取所有spu
        Elements spuEles = doc.select("div#J_goodsList > ul > li");

        for(Element spuEle : spuEles){
            //获取每个spu
            long spu;
            if (spuEle.attr("data-spu") == ""){
                spu = Long.parseLong(spuEle.attr("data-sku"));
            }else {
                spu = Long.parseLong(spuEle.attr("data-spu"));
            }

            //获取每个spu下所有sku
            Elements skuEles = spuEle.select("li.ps-item");

            for(Element skuEle : skuEles){
                //获取每个spu下每个sku
                long sku = Long.parseLong(skuEle.select("[data-sku]").attr("data-sku"));

                //根据sku查询商品数据
                Item item = new Item();
                item.setSku(sku);
                List<Item> list = this.itemService.findAll(item);

                if(list.size()>0){
                    //如果商品存在，就进行下一个循环，该商品不保存，因为已存在
                    continue;
                }
                //设置商品的spu
                item.setSpu(spu);

                //获取商品的详情的url
                String itemUrl = "https://item.jd.com/" + sku + ".html";
                item.setUrl(itemUrl);


                //获取商品的价格
                String priceJson = httpUtils.doGetHtml("https://p.3.cn/prices/mgets?skuIds=J_" + sku);


                System.out.println("json:::" + priceJson);

                double price = 0;
                try {
                    price = MAPPER.readTree(priceJson).get(0).get("p").asDouble();
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                    continue;
                }

                item.setPrice(price);

                //获取商品的标题
                String itemInfo = this.httpUtils.doGetHtml(itemUrl);
                String title = Jsoup.parse(itemInfo).select("div.sku-name").text();
                item.setTitle(title);

                //获取商品的创建时间
                item.setCreated(new Date());

                //获取商品的更新时间
                item.setUpdated(item.getCreated());

                //获取商品的图片
                String picUrl = "https:"+skuEle.select("img[data-sku]").first().attr("data-lazy-img");
                picUrl = picUrl.replace("/n7/","/n1/");
                String picName = this.httpUtils.doGetImage(picUrl);
                item.setPic(picName);

                //保存商品数据到数据库中
                this.itemService.save(item);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


            }
        }
    }
}
