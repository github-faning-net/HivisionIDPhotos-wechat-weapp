package org.zjzWx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class App {
    public static void main(String[] args) {
        System.out.println("欢迎使用证件照伴侣服务");
        SpringApplication.run(App.class, args);
        System.out.println("服务启动成功-当前版本：v2024.12.16");
    }
}
