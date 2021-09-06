package com.github.datasource.properties;

import lombok.Data;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;

/**
 * @author yaomengke
 * @create 2021- 08 - 25 - 19:29
 */
@Data
public class ExtendMybatisProperties extends MybatisProperties {

    private String[] basePackages = new String[0];
    private String   basePackage ;
}
