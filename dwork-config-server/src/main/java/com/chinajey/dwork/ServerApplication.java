package com.chinajey.dwork;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@SpringBootApplication
@MapperScan({"com.chinajey.dwork.**.mapper", "com.tengnat.dwork.**.mapper"})
@ComponentScan(basePackages = {"com.chinajey.**", "com.tengnat.dwork.**"})
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
        log.info("==============DWORK应用端启动成功==============");
    }
}
