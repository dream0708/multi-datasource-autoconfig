package com.github.datasource.demo;

import com.github.datasource.annotation.EnableAutoDataSource;
import com.github.datasource.enums.ORM;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAutoDataSource(dataSourcePrefix = {"datasource"} , //自动配置数据源前缀
		orm = ORM.MYBATIS_PLUS , // orm类型 支持 mybatis tkmybatis mybatis-plus
		dynamicDataSource = "ds" , // 动态数据源配置 前缀
		dynamicDefaultTarget = "master" // 动态数据源默认数据源
)
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
