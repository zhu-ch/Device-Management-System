package com.management.admin.modules.tool.service;

import com.management.admin.common.utils.ExcelUtils;
import com.management.admin.common.utils.IdGen;
import com.management.admin.common.utils.SystemPath;
import com.management.admin.modules.sys.service.DictService;
import com.management.admin.modules.tool.dao.ColumnMapFieldDao;
import com.management.admin.modules.tool.dao.ExcelTemplateDao;
import com.management.admin.modules.tool.dao.ImportDataDao;
import com.management.admin.modules.tool.entity.*;
import com.management.admin.modules.tool.entity.tiny.*;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    @Autowired
    ExcelTemplateDao excelTemplateDao;

    @Autowired
    ColumnMapFieldDao columnMapFieldDao;

    @Autowired
    ImportDataDao importDataDao;

    @Autowired
    DictService dictService;

    private List<PartInfo> partInfos;
    private boolean isScrapped=false;
    /**
     * @param excelTemplate include all info
     * @return is successful
     * @apiNote 1st: copy the file in temp dir to excelTemplate dir
     * 2nd: insertOrUpdate ExcelTemplate
     * 3rd: insertOrUpdate ColumnMapFieldList
     */
    @Transactional
    public boolean insert(ExcelTemplate excelTemplate) {
        // 1st step: copy the file in temp dir to excelTemplate dir
        File srcFile = new File(SystemPath.getRootPath() + SystemPath.getTemporaryPath() + excelTemplate.getFilePath());
        if (!srcFile.exists()) {
            srcFile.mkdirs();
        }
        File targetDir = new File(SystemPath.getRootPath() + SystemPath.getExcelTemplatePath());
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        try {
            FileUtils.copyFileToDirectory(srcFile, targetDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 2nd step: insertOrUpdate ExcelTemplate
        excelTemplate.preInsert();
        excelTemplateDao.insertOrUpdate(excelTemplate);
        // 3rd step: insertOrUpdate ColumnMapFieldList
        for (ColumnMapField columnMapField : excelTemplate.getColumnMapFieldList()) {
            columnMapField.preInsert();
            columnMapField.setTemplateId(excelTemplate.getId());
        }
        if (excelTemplate.getColumnMapFieldList().size() > 0)
            columnMapFieldDao.insertList(excelTemplate.getColumnMapFieldList());
        return true;
    }

    public void deleteByIds(List<String> ids){
         excelTemplateDao.deleteByIds(ids);
        excelTemplateDao.deleteByIds2(ids);
    }

    public  List<String> getTableList(){
        return excelTemplateDao.getTableList();
    }

    public List<String>  getTableColumnList(String tableName){
        List<String>data = excelTemplateDao.getTableColumnList(tableName);
        //去掉不必要的字段
        List<String> result=new ArrayList<>();
        for(String i :data){
            if(i.equals("id")||i.equals("create_time")||i.equals("modify_time")||i.equals("del_flag")){
                continue;
            }else{
                result.add(i);
            }
        }
        return result;
    }

    public List<ExcelColumn> getExcelColumnList(String ExcelName){

        String fileName=SystemPath.getRootPath()+SystemPath.getTemporaryPath()+ExcelName;
        Sheet sheet = null;
        try {
            File file = new File(fileName);
            sheet = ExcelUtils.getSheet(file,0);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Row firstRow = sheet.getRow(2);
        List<ExcelColumn> excelColumnList = new ArrayList<>();
        for (int colIndex = 0; colIndex <= firstRow.getLastCellNum(); colIndex++) {
            Cell cell = firstRow.getCell(colIndex);
            if (cell == null) continue; // 略过列名为空的项
            ExcelColumn excelColumn = new ExcelColumn();
            excelColumn.setColumnIndex(colIndex);
            excelColumn.setColumnName(cell.getStringCellValue());
            excelColumnList.add(excelColumn);
        }
        return excelColumnList;
    }

    public List<ExcelTemplate> selectAllTemplate(ExcelTemplate conditions) {
        return excelTemplateDao.selectAllTemplate(conditions);
    }

    public HashMap<String, Object> importExcelToTable(ImportExcel importExcel) throws IOException {
        isScrapped=false;
        //数据excel名字
        String dataExcelPath=SystemPath.getRootPath()+SystemPath.getTemporaryPath()+ importExcel.getExcelDataName();
        System.out.println("这是系统读取的相对路径"+dataExcelPath);
        //获得模版信息
        ExcelTemplate excelTemplate = excelTemplateDao.selectById(importExcel.getId());

        //获得字段对应列名map信息
        List<ColumnMapField> columnMapFieldList = columnMapFieldDao.selectByTemplateId(importExcel.getId());
        partInfos =  importDataDao.getPartList();

        if(excelTemplate.getTypeName().equals("报废涉密计算机")||excelTemplate.getTypeName().equals("报废涉密存储介质")||excelTemplate.getTypeName().equals("报废信息设备")
        ||excelTemplate.getTypeName().equals("报废安全保密产品")||excelTemplate.getTypeName().equals("报废USB Key")
        ){
            isScrapped=true;
        }
//        1.准备数据，来自excel表格之外
        List<List<Object>> data = new ArrayList<>();

        File file = new File(dataExcelPath);

        Sheet sheet = ExcelUtils.getSheet(file,0);//获取excel表格
        Date now = new Date();//当前时间

//            2.获取数据库字段信息
        // add info from table field and reorder
        List<TableField> tableFieldList = getTableFieldList(importExcel.getTableName(), false);
        List<ColumnMapField> columnMapFieldList2 = new ArrayList<>();
        for (TableField tableField : tableFieldList) {
            for (ColumnMapField columnMapField : columnMapFieldList) {
                if (tableField.getFieldName().equals(columnMapField.getTableColumnName())) {
                    columnMapField.setFieldType(tableField.getFieldType());
                    columnMapFieldList2.add(columnMapField);
                    break;
                }
            }
        }
        Map<String, Integer> hashMap = new LinkedHashMap<>();
        //数据库字段头序列
        List<String> fieldList = new ArrayList<>();
        fieldList.add("id");
        fieldList.add("create_time");
        fieldList.add("modify_time");
        fieldList.add("del_flag");
        fieldList.add("scrapped_flag");
        for (ColumnMapField columnMapField : columnMapFieldList2) {
            fieldList.add("`"+columnMapField.getTableColumnName()+"`");
        }



        DynamicInsertParam dynamicInsertParam = new DynamicInsertParam();
        DynamicUpdateParam dynamicUpdateParam = new DynamicUpdateParam();
        //设置插入的数据库表名
        dynamicInsertParam.setTableName(importExcel.getTableName());
        dynamicUpdateParam.setTableName(importExcel.getTableName());
        //设置插入字段
        dynamicInsertParam.setFieldList(fieldList);
        dynamicUpdateParam.setFieldList(fieldList);

//        3.处理表格数据
        List<DictInfo>dictInfos =importDataDao.selectAllDictInfo();
        //获取主键列表
        List<String> primaryList;
        if(fieldList.indexOf("`number`") != -1){
            primaryList = importDataDao.getNumber(importExcel.getTableName());
        } else {
            primaryList = importDataDao.getSecretNumber(importExcel.getTableName());
        }

        int successInsert = 0;
        int successUpdate = 0;
        int failInsert = 0;
        int failUpdate = 0;
        List<String> failMessage = new ArrayList<>();

        for(int rowIndex = 3; rowIndex <=sheet.getLastRowNum();rowIndex++){ //忽略第一行
            Row dataRow = sheet.getRow(rowIndex);   //获取一行的数据
            //判断是不是空行 是的话就停止
            if("".equals(dataRow.getCell(1).getStringCellValue())) {
                break;
            }

            Boolean isWrong = false;
            Boolean hasExist = false;
            String errorColumn = "";
            //对应前面四个固定值
            List<Object> row = new ArrayList<>();
            row.add(IdGen.uuid());
            row.add(now);
            row.add(now);
            row.add(0);
            if(isScrapped) row.add(1);
            else row.add(0);

                //处理每个map对应关系

            for(int i=0;i<columnMapFieldList2.size();i++){
                ColumnMapField columnMapField = columnMapFieldList2.get(i);  //取得单个映射
                Object cellValue = null;
                if(columnMapField.getColumnIndex()!= null){   //如果存在映射关系
                        int tmp=columnMapField.getColumnIndex();
                        Cell cell =dataRow.getCell(tmp);
                    cellValue = ExcelUtils.getCellValueByFieldType(cell, columnMapField.getFieldType());
                    //如果是部门或课题组
                    if(columnMapField.getColumnName().equals("单位")||columnMapField.getColumnName().equals("科室/课题组")){
//                        String deName=cellValue.toString().split(" ")[1];
//                        String deCode=cellValue.toString().split(" ")[0];
                        String deName=cellValue.toString();
                        String uu =getUuidByNameAndCode(deName,"000");
                        if(uu.equals("wrong")){
                            isWrong = true;
                            errorColumn = columnMapField.getColumnName();
                            break;
                        }else{
                            cellValue  = uu;
                        }
                    }else if(columnMapField.getIsDict()){
                        //使用了字典
                        Boolean findDict=false;
                        for(DictInfo dictInfo:dictInfos){
                            if(dictInfo.getDicProperty().equals(columnMapField.getDict()) &&cellValue.equals(dictInfo.getDicValue()))
                            {
                                cellValue=dictInfo.getId();
                                findDict=true;
                                break;
                             }
                        }
                        if(!findDict){
                            System.out.println("字典项匹配失败！！！！！！！！！！！！！");
                            isWrong=true;
                            errorColumn = columnMapField.getColumnName();
                            break;
                        }
                    }
                }
                if(columnMapField.getTableColumnName().equals("number") || columnMapField.getTableColumnName().equals("secret_number")){
                    if(primaryList.indexOf(cellValue.toString()) != -1){
                        dynamicUpdateParam.setPrimaryKey(cellValue.toString());
                        hasExist = true;
                    }
                }
                if(isWrong){
                    errorColumn = columnMapField.getColumnName();
                    break;
                }
                row.add(cellValue);
            }
            if(isWrong){
                if(hasExist){
                    failUpdate ++;
                    failMessage.add("第" + (rowIndex + 1) + "行更新失败，" + errorColumn + "字典映射不匹配");
                } else {
                    failInsert ++;
                    failMessage.add("第" + (rowIndex + 1) + "行插入失败，" + errorColumn + "字典映射不匹配");
                }
            }else{
                //判断主键是否重复
                if(hasExist){
                    dynamicUpdateParam.setData(row);
                    dynamicUpdateParam.makeValue();
                    dynamicUpdateParam.setDate(now);
                    if(fieldList.indexOf("`number`") != -1){
                        dynamicUpdateParam.setPrimaryName("`number`");
                    } else {
                        dynamicUpdateParam.setPrimaryName("secret_number");
                    }
                    if(importDataDao.dynamicUpdate(dynamicUpdateParam) == 1){
                        successUpdate++;
                    } else {
                        failUpdate++;
                        failMessage.add("第" + (rowIndex + 1) + "插入失败");
                    }
                }
                else {
                    data.add(row);
                    successInsert++;
                }
            }

        }//for
        dynamicInsertParam.setData(data);

        HashMap<String,Object> status =new HashMap<>();
        status.put("successInsert",successInsert);
        status.put("successUpdate",successUpdate);
        status.put("failInsert",failInsert);
        status.put("failUpdate",failUpdate);
        status.put("failMessage", failMessage);

        if(data.size() == 0) {
            return status;
        }
        // 4.进行插入，并返回是否成功
        try {
            importDataDao.dynamicInsert(dynamicInsertParam);
            return status;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }

    }

    /**
     * @param tableName table's name in database
     * @return all fields' info except 6 automatic field
     */
    public List<TableField> getTableFieldList(String tableName, boolean isAll) {
        List<TableField> tableFieldList = importDataDao.selectFieldListByTableName(tableName);
        if (isAll)
            return tableFieldList;
        return tableFieldList.stream()
                .filter(columnMapField -> isFieldRetained(columnMapField.getFieldName()))
                .collect(Collectors.toList());
    }

    /**
     * @return is field retained
     */
    private boolean isFieldRetained(String fieldName) {
        String[] excludeColumns = new String[]{"id",  "create_date",
                 "modify_date", "del_flag","scrapped_flag"};
        for (String s : excludeColumns) {
            if (s.equals(fieldName))
                return false;
        }
        return true;
    }

    public List<TemplateType> getTemplateTypeList(){
        return excelTemplateDao.getTypeList();
    }


    public String getUuidByNameAndCode(String Name,String Code){
        for(int i=0;i<partInfos.size();i++){
            PartInfo partInfo = partInfos.get(i);
            if(partInfo.getName().equals(Name)){
                return partInfo.getId();
            }
        }
        return "wrong";
    }
}
