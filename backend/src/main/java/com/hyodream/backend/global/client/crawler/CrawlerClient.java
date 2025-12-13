package com.hyodream.backend.global.client.crawler;

import com.hyodream.backend.global.client.crawler.dto.CrawlerResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "crawler-client", url = "${crawler.url}")
public interface CrawlerClient {

    @PostMapping("/crawl")
    CrawlerResponseDto crawlProduct(@RequestBody CrawlRequest request);

    record CrawlRequest(String url, int max_pages) {}
}
