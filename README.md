# multi-datasource-autoconfig
#### 背景
通常项目中通常有很多数据源，这样就需要配置多个XML bean或者configure

为了减少重复工作量，并且配置标准化，开发了一个自动注入数据源的springboot组件

主要特点：简单方便、支持多种类数据源、多种ORM配置、方便扩展

关键技术：springboot datasoure dynamic-datasource mybatis tkmybatis mybatis-plus jta autoconfigure  druid

#### 使用说明

1. 添加依赖

~~~xml
<dependency>
    <groupId>com.github</groupId>
    <artifactId>multi-datasource-core</artifactId>
    <version>1.0.0</version>
</dependency>

~~~
2. 打开开关
~~~java

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
~~~

3. 配置propertis
~~~properties
#master
#是否需要配置AtomikosDataSource 默认false
datasource.master.xa: true
#实例化完是否需要调用init方法 DruidDataSource.init 默认false
datasource.master.init: true
#配置transactionManager实例 默认不配置
datasource.master.transactionManager: transactionManager
datasource.master.driverClassName: net.sourceforge.jtds.jdbc.Driver
datasource.master.url: jdbc:jtds:sqlserver://127.0.0.1/master;sendStringParametersAsUnicode=false
datasource.master.username: sa
datasource.master.password: 123456
#master数据源 Mybatis配置 basePackages不配置 就不配置master
datasource.master.mybatis.basePackages[0]:com.github.datasource.demo.**.dao
datasource.master.mybatis.mapper-locations[0]: classpath*:com/github/datasource/**/mapper/*.xml
datasource.master.mybatis.type-aliases-package=com.github

#slave 数据源
datasource.slave.driverClassName: net.sourceforge.jtds.jdbc.Driver
datasource.slave.url: jdbc:jtds:sqlserver://127.0.0.1/slave;sendStringParametersAsUnicode=false
datasource.slave.username: sa
datasource.slave.password: 123456
datasource.slave.mybatis.basePackages[0]:com.github.datasource.demo.slave.**.dao
datasource.slave.mybatis.mapper-locations[0]: classpath*:com/github/datasource/**/mapper/*.xml
datasource.slave.mybatis.type-aliases-package=com.github
#动态数据源 配置 支持DDS注解 动态选择
ds.dynamic.mybatis.basePackages[0]:com.github.datasource.demo.dynamic.**.dao
ds.dynamic.mybatis.mapper-locations[0]: classpath*:com/github/datasource/**/mapper/*.xml
ds.dynamic.mybatis.type-aliases-package=com.github

#Druid数据源默认配置 
spring.druid.datasource.initialSize: 5
spring.druid.datasource.maxActive: 10
spring.druid.datasource.minIdle: 3
spring.druid.datasource.maxWait: 600000
spring.druid.datasource.removeAbandoned: true
spring.druid.datasource.removeAbandonedTimeout: 1800
spring.druid.datasource.timeBetweenEvictionRunsMillis: 600000
spring.druid.datasource.minEvictableIdleTimeMillis: 300000
spring.druid.datasource.testWhileIdle: true
spring.druid.datasource.testOnBorrow: false
spring.druid.datasource.testOnReturn: false
spring.druid.datasource.poolPreparedStatements: true
spring.druid.datasource.maxPoolPreparedStatementPerConnectionSize: 50
spring.druid.datasource.filters: stat
~~~




#### 更多
1、源码  multi-datasource-core

2、实例  multi-datasource-demo

