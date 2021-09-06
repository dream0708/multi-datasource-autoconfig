package com.github.datasource.aspect;


import com.github.datasource.annotation.DDS;
import com.github.datasource.routing.DynamicDataSourceContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import java.util.Objects;

/**
 * 多数据源处理
 *
 */
@Aspect
@Order(1)
//@Component
public class DataSourceAspect {
    protected Logger logger = LoggerFactory.getLogger(getClass());

    @Pointcut("@annotation(com.github.datasource.annotation.DDS)"
            + "|| @within(com.github.datasource.annotation.DDS)")
    public void dsPointCut() {

    }

    @Around("dsPointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        DDS DDS = getDataSource(point);

        if (!Objects.isNull(DDS)) {
            DynamicDataSourceContextHolder.setDataSourceType(DDS.value());
        }

        try {
            return point.proceed();
        } finally {
            // 销毁数据源 在执行方法之后
            DynamicDataSourceContextHolder.clearDataSourceType();
        }
    }

    /**
     * 获取需要切换的数据源
     */
    public DDS getDataSource(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        DDS DDS = AnnotationUtils.findAnnotation(signature.getMethod(),
                DDS.class);
        if (Objects.nonNull(DDS)) {
            return DDS;
        }

        return AnnotationUtils.findAnnotation(signature.getDeclaringType(), DDS.class);
    }
}
