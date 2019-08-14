package com.management.admin.modules.tool.dao;

import com.management.admin.modules.tool.entity.DynamicInsertParam;
import com.management.admin.modules.tool.entity.tiny.TableField;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ImportDataDao {

    //根据Excel数据插入数据库
    int dynamicInsert(DynamicInsertParam dynamicInsertParam);


    // 获取指定表中的所有列信息
    List<TableField> selectFieldListByTableName(@Param("tableName") String tableName);

}
