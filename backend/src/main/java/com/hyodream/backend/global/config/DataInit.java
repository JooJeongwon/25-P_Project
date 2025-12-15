package com.hyodream.backend.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInit implements CommandLineRunner {

    private final DbSeeder dbSeeder;

    @Override
    public void run(String... args) throws Exception {
        dbSeeder.seedAll();
    }
}