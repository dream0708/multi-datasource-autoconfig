package com.github.datasource.demo.master.dao.mapper;

import com.github.datasource.demo.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MasterDao {
    @Select("SELECT * FROM tb_user WHERE userName = #{userName}")
    User findByName(@Param("userName") String userName);
    
    @Insert("INSERT INTO tb_user(id,userName,password) VALUES(#{id},#{userName},#{password})")
    int insertHappiness(@Param("id") Long id, @Param("userName") String userName,@Param("password") String password);
}
