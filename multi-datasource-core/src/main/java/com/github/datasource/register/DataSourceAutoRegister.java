package com.github.datasource.register;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.xa.DruidXADataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;

import com.github.datasource.annotation.EnableAutoDataSource;
import com.github.datasource.aspect.DataSourceAspect;
import com.github.datasource.enums.ORM;
import com.github.datasource.properties.ExtendMybatisProperties;
import com.github.datasource.routing.DynamicDataSource;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyNameAliases;
import org.springframework.boot.jta.atomikos.AtomikosDataSourceBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yaomengke
 * @create 2021- 08 - 25 - 9:00
 */

public class DataSourceAutoRegister implements EnvironmentAware, ImportBeanDefinitionRegistrar {

    public static final Logger logger = LoggerFactory.getLogger(DataSourceAutoRegister.class.getSimpleName());
    public final static List<String> XA_DATA_SOURCE = Arrays.asList("oracle", "mysql", "mariadb", "postgresql", "h2", "jtds");
    public final static Map<String, Object> registerBean = new ConcurrentHashMap<>();
    public final static ConfigurationPropertyNameAliases ALIASES = new ConfigurationPropertyNameAliases();
    public final static String MYBATIS_PREFIX = "mybatis";
    public final static String DATESOURCE_NAME = "DataSource";
    public final static String JDBCTEMPLATE_NAME = "JdbcTemplate";
    public final static String TSM_NAME = "transactionManager";
    public final static String SQL_SESSION_FACTORY_NAME = "SqlSessionFactory";
    public final static String SQL_SESSION_TEMPLATE_NAME = "SqlSessionTemplate";

    public final static Map<String , Boolean> primaryDataSource = new ConcurrentHashMap<String , Boolean>() ;

    static {
        ALIASES.addAliases("url", "jdbc-url");
        ALIASES.addAliases("username", "user");
    }

    private Environment env;
    private Binder binder;

    /**
     * ImportBeanDefinitionRegistrar
     *
     * @param annotationMetadata     annotationMetadata
     * @param beanDefinitionRegistry beanDefinitionRegistry
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
        Map<String, Object> annotationAttributes = annotationMetadata.getAnnotationAttributes(EnableAutoDataSource.class.getCanonicalName());
        String[] prefixNames = (String[]) annotationAttributes.get("dataSourcePrefix");
        ORM orm = (ORM) annotationAttributes.getOrDefault("orm", ORM.NONE);
        String mybatisPrefixName = (String) annotationAttributes.getOrDefault("defaultMybatisPrefix", MYBATIS_PREFIX);
        MybatisProperties mybatisDefault = binder.bind(mybatisPrefixName, MybatisProperties.class).orElse(null);
        for (String prefixName : prefixNames) {
            registerDataSourceProcess(prefixName, orm, mybatisPrefixName, mybatisDefault, annotationMetadata, beanDefinitionRegistry);
        }
        String dynamicDataSource = (String) annotationAttributes.get("dynamicDataSource");
        if (StringUtils.isNotBlank(dynamicDataSource)) {
            Map<Object, Object> targetDataSources = new HashMap<>();
            final DynamicDataSource multipleDataSource = new DynamicDataSource();
            String dynamicDefaultTarget =  (String) annotationAttributes.get("dynamicDefaultTarget");
            registerBean.forEach((k, v) -> {
                if (v instanceof DataSource) {
                    targetDataSources.put(k.replace(DATESOURCE_NAME, ""), v);
                    if(dynamicDefaultTarget.equalsIgnoreCase(k.replace(DATESOURCE_NAME, ""))){
                        multipleDataSource.setDefaultTargetDataSource(v);  //设置默认数据源
                        targetDataSources.put("default", v); //设置default
                    }
                    /*
                    if (primaryDataSource.getOrDefault(k.replace(DATESOURCE_NAME, "") , false)) {
                        multipleDataSource.setDefaultTargetDataSource(v);  //设置默认数据源
                        targetDataSources.put("default", v); //设置default
                    }*/
                }
            });
            //添加数据源
            multipleDataSource.setTargetDataSources(targetDataSources);
            registerDataSourceProcess(dynamicDataSource, orm, mybatisPrefixName,
                    multipleDataSource, "dynamic",
                    mybatisDefault,
                    annotationMetadata, beanDefinitionRegistry);
            BeanDefinitionBuilder dataSourceAspectBuilder = BeanDefinitionBuilder.genericBeanDefinition(DataSourceAspect.class, () ->  new DataSourceAspect() );
            BeanDefinition dataSourceAspectFactoryBean = dataSourceAspectBuilder.getRawBeanDefinition();
            beanDefinitionRegistry.registerBeanDefinition("dataSourceAspect", dataSourceAspectFactoryBean);
        }
        logger.info("Auto registration dataSource completed !");
    }

    public AtomikosDataSourceBean getAtomikosDataSourceBean(DruidDataSource dataSource, String key) {
        AtomikosDataSourceBean registerDataSource = new AtomikosDataSourceBean();
        registerDataSource.setXaDataSourceClassName("com.alibaba.druid.pool.xa.DruidXADataSource");
        registerDataSource.setXaDataSource((DruidXADataSource) dataSource);
        registerDataSource.setUniqueResourceName(key);
        registerDataSource.setMinPoolSize(dataSource.getMinIdle());
        registerDataSource.setMaxPoolSize(dataSource.getMaxActive());
        registerDataSource.setBorrowConnectionTimeout((int) dataSource.getTimeBetweenEvictionRunsMillis());
        registerDataSource.setMaxIdleTime((int) dataSource.getMaxEvictableIdleTimeMillis());
        registerDataSource.setTestQuery(dataSource.getValidationQuery());
        return registerDataSource;
    }

    public void registerDataSourceProcess(String prefixName,
                                          ORM mode,
                                          String mybatisPrefixName,
                                          DataSource dataSource,
                                          String key,
                                          MybatisProperties mybatisDefault,
                                          AnnotationMetadata annotationMetadata,
                                          BeanDefinitionRegistry beanDefinitionRegistry) {
        registerBean.put(key + DATESOURCE_NAME, dataSource);

        BeanDefinitionBuilder builderDataSoruce = BeanDefinitionBuilder.genericBeanDefinition(DataSource.class, () -> dataSource);
        AbstractBeanDefinition datasourceBean = builderDataSoruce.getRawBeanDefinition();
        //datasourceBean.setDependsOn("txManager");
        boolean primary = getEnvValue( prefixName + "." + key , "primary", false , Boolean.class ) ;
        if (primary) {
            if(primaryDataSource.getOrDefault(key , false)
                    && primaryDataSource.containsValue(Boolean.TRUE)){
                logger.error("Exists many primary Datasource , Ingore this({}) Primary " , key);
            }else{
                datasourceBean.setPrimary(primary);
                primaryDataSource.put(key + DATESOURCE_NAME  , true);
            }
        }
        beanDefinitionRegistry.registerBeanDefinition(key + DATESOURCE_NAME, datasourceBean);
        logger.info("Registration DataSource({}) end !", key);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        BeanDefinitionBuilder builderJdbcTemplate = BeanDefinitionBuilder.genericBeanDefinition(JdbcTemplate.class, () -> jdbcTemplate);
        AbstractBeanDefinition jdbcTemplateBean = builderJdbcTemplate.getRawBeanDefinition();
        beanDefinitionRegistry.registerBeanDefinition(key + JDBCTEMPLATE_NAME, jdbcTemplateBean);
        logger.info("Registration JdbcTemplate({}) end !", key);

        String transactionManagerName = getEnvValue( prefixName + "." + key , TSM_NAME , "" , String.class ) ;
        if (StringUtils.isNotBlank(transactionManagerName)) {
            PlatformTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
            BeanDefinitionBuilder builderTransactionManager = BeanDefinitionBuilder.genericBeanDefinition(PlatformTransactionManager.class, () -> transactionManager);
            AbstractBeanDefinition transactionManagerBeanDefinition = builderTransactionManager.getRawBeanDefinition();
            beanDefinitionRegistry.registerBeanDefinition(transactionManagerName, transactionManagerBeanDefinition);
            logger.info("Registration TransactionManagerName({}) end !", key);
        }
        if (mode == ORM.NONE) {
            logger.error("No load mybatis[{}] configure , continue ", key);
            return;
        }
        ExtendMybatisProperties mybatis = binder.bind(prefixName + "." + key + "." + mybatisPrefixName, ExtendMybatisProperties.class).orElse(null);
        if (mybatis == null ||
                (ArrayUtils.isEmpty(mybatis.getBasePackages()) && StringUtils.isBlank(mybatis.getBasePackage()))) {
            logger.error("No load mybatis[{}] configure , continue ", key);
            return;
        }
        SqlSessionFactory sqlSessionFactory = (SqlSessionFactory) registerBean.get(key + SQL_SESSION_FACTORY_NAME);
        if (sqlSessionFactory == null) {
            try {
                if (mode == ORM.MYBATIS_DEFAULT || mode == ORM.MYBATIS_TK) {
                    SqlSessionFactoryBean fb = new SqlSessionFactoryBean();
                    fb.setDataSource(dataSource);
                    if (mybatisDefault != null) {
                        fb.setTypeAliasesPackage(StringUtils.defaultIfBlank(mybatis.getTypeAliasesPackage(), mybatisDefault.getTypeAliasesPackage()));
                        fb.setTypeHandlersPackage(StringUtils.defaultIfBlank(mybatis.getTypeHandlersPackage(), mybatisDefault.getTypeHandlersPackage()));
                        fb.setMapperLocations((Resource[]) ArrayUtils.addAll(mybatis.resolveMapperLocations(), mybatisDefault.resolveMapperLocations()));
                        if (mode == ORM.MYBATIS_DEFAULT) {
                            fb.setVfs(SpringBootVFS.class);
                        }
                        if (mode == ORM.MYBATIS_TK) {
                            fb.setVfs(tk.mybatis.mapper.autoconfigure.SpringBootVFS.class);
                        }
                        Configuration configuration = ObjectUtils.defaultIfNull(mybatis.getConfiguration(), mybatisDefault.getConfiguration());
                        if (configuration == null) {
                            configuration = new Configuration();
                            configuration.setMapUnderscoreToCamelCase(true);
                            configuration.setUseColumnLabel(true);
                            configuration.setUseGeneratedKeys(true);
                            fb.setConfiguration(configuration);
                        } else {
                            configuration.setMapUnderscoreToCamelCase(true);
                            configuration.setUseColumnLabel(true);
                            configuration.setUseGeneratedKeys(true);
                            fb.setConfiguration(configuration);
                        }
                    } else {
                        fb.setTypeAliasesPackage(mybatis.getTypeAliasesPackage());
                        fb.setTypeHandlersPackage(mybatis.getTypeHandlersPackage());
                        fb.setMapperLocations(mybatis.resolveMapperLocations());
                        if (mode == ORM.MYBATIS_DEFAULT) {
                            fb.setVfs(SpringBootVFS.class);
                        }
                        if (mode == ORM.MYBATIS_TK) {
                            fb.setVfs(tk.mybatis.mapper.autoconfigure.SpringBootVFS.class);
                        }
                        if (mybatis.getConfiguration() != null) {
                            fb.setConfiguration(mybatis.getConfiguration());
                            mybatis.getConfiguration().setMapUnderscoreToCamelCase(true);
                            mybatis.getConfiguration().setUseColumnLabel(true);
                            mybatis.getConfiguration().setUseGeneratedKeys(true);
                        } else {
                            Configuration configuration = new Configuration();
                            configuration.setMapUnderscoreToCamelCase(true);
                            configuration.setUseColumnLabel(true);
                            configuration.setUseGeneratedKeys(true);
                            fb.setConfiguration(configuration);
                        }
                    }
                    sqlSessionFactory = fb.getObject();
                }

                if (mode == ORM.MYBATIS_PLUS) {
                    MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
                    bean.setVfs(com.baomidou.mybatisplus.autoconfigure.SpringBootVFS.class);
                    bean.setDataSource(dataSource);
                    //设置mapper位置
                    if (mybatisDefault != null) {
                        bean.setTypeAliasesPackage(StringUtils.defaultIfBlank(mybatis.getTypeAliasesPackage(), mybatisDefault.getTypeAliasesPackage()));
                        bean.setTypeHandlersPackage(StringUtils.defaultIfBlank(mybatis.getTypeHandlersPackage(), mybatisDefault.getTypeHandlersPackage()));
                        bean.setMapperLocations((Resource[]) ArrayUtils.addAll(mybatis.resolveMapperLocations(), mybatisDefault.resolveMapperLocations()));
                    } else {
                        bean.setTypeAliasesPackage(mybatis.getTypeAliasesPackage());
                        bean.setTypeHandlersPackage(mybatis.getTypeHandlersPackage());
                        bean.setMapperLocations(mybatis.resolveMapperLocations());
                    }
                    MybatisConfiguration configuration = new MybatisConfiguration();
                    //configuration.setLogImpl(StdOutImpl.class);
                    configuration.setMapUnderscoreToCamelCase(true);
                    configuration.setUseColumnLabel(true);
                    configuration.setUseGeneratedKeys(true);
                    bean.setConfiguration(configuration);
                    sqlSessionFactory = bean.getObject();
                }
                registerBean.put(key + SQL_SESSION_FACTORY_NAME, sqlSessionFactory);
            } catch (Exception e) {
                logger.error("Failed register mybatis[{}] configure.", key, e);
            }
        }
        SqlSessionFactory finalSqlSessionFactory = sqlSessionFactory;
        BeanDefinitionBuilder builderSqlSessionFactory = BeanDefinitionBuilder.genericBeanDefinition(SqlSessionFactory.class, () -> finalSqlSessionFactory);
        BeanDefinition sqlSessionFactoryBean = builderSqlSessionFactory.getRawBeanDefinition();
        beanDefinitionRegistry.registerBeanDefinition(key + SQL_SESSION_FACTORY_NAME, sqlSessionFactoryBean);
        // sqlSessionTemplate
        GenericBeanDefinition sqlSessionTemplate = new GenericBeanDefinition();
        sqlSessionTemplate.setBeanClass(SqlSessionTemplate.class);
        ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
        constructorArgumentValues.addIndexedArgumentValue(0, sqlSessionFactory);
        sqlSessionTemplate.setConstructorArgumentValues(constructorArgumentValues);
        beanDefinitionRegistry.registerBeanDefinition(key + SQL_SESSION_TEMPLATE_NAME, sqlSessionTemplate);
        //MapperScan

        if (mode == ORM.MYBATIS_DEFAULT || mode == ORM.MYBATIS_PLUS) {
            ClassPathMapperScanner scanner = new ClassPathMapperScanner(beanDefinitionRegistry);
            scanner.setSqlSessionFactoryBeanName(key + SQL_SESSION_FACTORY_NAME);
            scanner.registerFilters();
            if (StringUtils.isNotBlank(mybatis.getBasePackage())) {
                scanner.doScan(org.apache.commons.lang3.ArrayUtils.add(mybatis.getBasePackages(), mybatis.getBasePackage()));
            } else {
                scanner.doScan(mybatis.getBasePackages());
            }
        }
        if (mode == ORM.MYBATIS_TK) {
            tk.mybatis.spring.mapper.ClassPathMapperScanner scanner = new tk.mybatis.spring.mapper.ClassPathMapperScanner(beanDefinitionRegistry);
            scanner.setSqlSessionFactoryBeanName(key + SQL_SESSION_FACTORY_NAME);
            scanner.registerFilters();
            if (StringUtils.isNotBlank(mybatis.getBasePackage())) {
                scanner.doScan(org.apache.commons.lang3.ArrayUtils.add(mybatis.getBasePackages(), mybatis.getBasePackage()));
            } else {
                scanner.doScan(mybatis.getBasePackages());
            }
        }
        logger.info("Registration {} , Mybatis({}) end !", mode.name(), key);
    }

    public void registerDataSourceProcess(String prefixName,
                                          ORM mode,
                                          String mybatisPrefixName,
                                          MybatisProperties mybatisDefault,
                                          AnnotationMetadata annotationMetadata,
                                          BeanDefinitionRegistry beanDefinitionRegistry) {
        logger.info("###registerDataSourceProcess( {} , {} ) ####", prefixName, mode.name());
        Map<String, Map> multipleDataSources;
        try {
            multipleDataSources = binder.bind(prefixName, Map.class).get();
        } catch (NoSuchElementException e) {
            logger.error("Failed to configure druid DataSource[{}] ", prefixName);
            return;
        }
        for (String key : multipleDataSources.keySet()) {
            logger.info("Load Datasource[{}] ", prefixName + "." + key);
            //DataSource dataSource = binder.bind(prefixName + "." + key, DruidXADataSource.class).orElse(null);
            DataSource dataSource = createDataSource(prefixName + "." + key , "spring.druid.datasource" ) ;
            if (dataSource == null) {
                logger.info("Failed register Datasource[{}]", prefixName + "." + key);
                continue;
            }
            try {
                if(getEnvValue(prefixName + "." + key , ".init", false , Boolean.class)){
                    ((DruidDataSource) dataSource).init();
                }
            } catch (SQLException e) {
                logger.error("Init Datasource[" + key + "] error", e);
                //continue ;
            }
            if (getEnvValue(prefixName + "." + key , ".xa", false , Boolean.class)) {
                dataSource = getAtomikosDataSourceBean((DruidDataSource) dataSource, key);
            }
            registerDataSourceProcess(prefixName, mode, mybatisPrefixName, dataSource, key, mybatisDefault, annotationMetadata, beanDefinitionRegistry);
        }
    }

    /**
     * init environment
     *
     * @param environment environment
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
        binder = Binder.get(this.env);
    }



    public <T> T getEnvWithDefaultValue(String key , String defaultKey  , String property, T defaultValue , Class<T> clazz  ){
        return  env.getProperty( key + "." +  property , clazz ,
                env.getProperty(defaultKey +"." + property  , clazz , defaultValue )) ;
    }
    public <T> T getEnvValue(String key , String property, Class<T> clazz  ){
        return  env.getProperty( key + "." +  property , clazz ) ;
    }

    public <T> T getEnvValue(String key , String property, T defaultValue , Class<T> clazz  ){
        return  env.getProperty( key + "." +  property , clazz , defaultValue ) ;
    }


    public DruidXADataSource createDataSource(String key , String defaultKey )  {
        String url = getEnvValue(key , "url" , String.class) ;
        String username = getEnvValue(key , "username" , String.class) ;
        String password = getEnvValue(key , "password" , String.class) ;
        if(StringUtils.isBlank(url) ||
                StringUtils.isBlank(username) ||
                   StringUtils.isBlank(password)){
            return null ;
        }
        DruidXADataSource dataSource = new DruidXADataSource() ;
        dataSource.setDriverClassName( getEnvWithDefaultValue(key , defaultKey , "driverClassName" , null , String.class));
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        dataSource.setInitialSize(getEnvWithDefaultValue(key , defaultKey , "initialSize" , 50 , Integer.TYPE));
        dataSource.setMaxActive(getEnvWithDefaultValue(key , defaultKey , "maxActive" , 100 , Integer.TYPE));
        dataSource.setMinIdle(getEnvWithDefaultValue(key , defaultKey , "minIdle" , 1 , Integer.TYPE));
        dataSource.setMaxWait(getEnvWithDefaultValue(key , defaultKey , "maxWait" , 1000 , Integer.TYPE));
        dataSource.setMaxPoolPreparedStatementPerConnectionSize(getEnvWithDefaultValue(key , defaultKey , "maxPoolPreparedStatementPerConnectionSize" , 50 , Integer.TYPE));

        dataSource.setRemoveAbandoned(getEnvWithDefaultValue(key , defaultKey , "removeAbandoned" , true , Boolean.TYPE));
        dataSource.setRemoveAbandonedTimeout(getEnvWithDefaultValue(key , defaultKey , "removeAbandonedTimeout" , 180 , Integer.class));
        dataSource.setTimeBetweenEvictionRunsMillis(getEnvWithDefaultValue(key , defaultKey , "timeBetweenEvictionRunsMillis" , 600000 , Integer.class));
        dataSource.setMinEvictableIdleTimeMillis(getEnvWithDefaultValue(key , defaultKey , "minEvictableIdleTimeMillis" , 300000 , Integer.class));

        dataSource.setTestWhileIdle(getEnvWithDefaultValue(key , defaultKey , "testWhileIdle" , true , Boolean.TYPE));
        dataSource.setTestOnBorrow(getEnvWithDefaultValue(key , defaultKey , "testOnBorrow" , false , Boolean.TYPE));
        dataSource.setTestOnReturn(getEnvWithDefaultValue(key , defaultKey , "testOnReturn" , false , Boolean.TYPE));
        dataSource.setPoolPreparedStatements(getEnvWithDefaultValue(key , defaultKey , "poolPreparedStatements" , true , Boolean.TYPE));
        try {
            dataSource.setFilters(getEnvWithDefaultValue(key, defaultKey, "filters", "stat", String.class));
        }catch (Exception ex){
            logger.error(ex.getMessage() , ex);
        }


        return dataSource ;
    }
}
