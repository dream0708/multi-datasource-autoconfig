package com.github.datasource.annotation;

import com.github.datasource.enums.ORM;
import com.github.datasource.register.DataSourceAutoRegister;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author yaomengke
 * @create 2021- 08 - 25 - 9:33
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(DataSourceAutoRegister.class)
public @interface EnableAutoDataSource {

    String[] dataSourcePrefix() default  { "datasource" } ;

    ORM orm() default  ORM.MYBATIS_DEFAULT ;

    String  defaultMybatisPrefix() default "mybatis" ;

    String  dynamicDataSource() default "" ;

    String  dynamicDefaultTarget() default "default" ;

}

