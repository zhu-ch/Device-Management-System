package com.management.admin.modules.tool.entity.tiny;

import lombok.Data;

@Data
public class TableField {
    private String fieldName;
    private String fieldType;



    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }
}
