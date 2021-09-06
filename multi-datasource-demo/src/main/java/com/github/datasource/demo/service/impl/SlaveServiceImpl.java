package com.github.datasource.demo.service.impl;

import com.github.datasource.demo.slave.dao.SlaveDao;
import com.github.datasource.demo.service.SlaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class SlaveServiceImpl implements SlaveService {
    @Autowired
    private SlaveDao slaveDao;

}
