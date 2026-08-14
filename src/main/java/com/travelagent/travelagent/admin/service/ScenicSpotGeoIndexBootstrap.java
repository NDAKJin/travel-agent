package com.travelagent.travelagent.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScenicSpotGeoIndexBootstrap implements ApplicationRunner {

    private final ScenicSpotGeoService scenicSpotGeoService;

    @Override
    public void run(ApplicationArguments args) {
        long startedAt = System.nanoTime();
        log.info("Preparing scenic geo index at application startup");
        scenicSpotGeoService.ensureIndexReady();
        log.info("Scenic geo index is ready: durationMs={}", elapsedMillis(startedAt));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
