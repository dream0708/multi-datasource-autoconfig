package com.github.datasource.demo.service.impl;

import com.github.datasource.demo.master.dao.mapper.MasterDao;
import com.github.datasource.demo.service.MasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MasterServiceImpl implements MasterService {
    @Autowired
    private MasterDao masterDao;

}
