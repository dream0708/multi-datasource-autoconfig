package com.github.datasource.demo.dynamic;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.datasource.annotation.DDS;
import com.github.datasource.demo.entity.User;
import org.apache.ibatis.annotations.Select;

/**
 * @author yaomengke
 * @create 2021- 09 - 06 - 9:14
 */
public interface UserDao extends BaseMapper<User> {

    @DDS
    @Select("select * from tb_user where id = #{id}")
    public User  getMasterUserById(String id ) ;

    @DDS("slave")
    @Select("select * from tb_user where id = #{id}")
    public User  getSlaveUserById(String id ) ;
}
