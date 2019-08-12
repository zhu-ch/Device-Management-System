package com.management.admin.modules.sys.controller;

import com.management.admin.common.persistence.Page;
import com.management.admin.common.web.BaseApi;
import com.management.admin.common.web.MsgType;
import com.management.admin.modules.sys.entity.Dict;
import com.management.admin.modules.sys.entity.DictType;
import com.management.admin.modules.sys.service.DictService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.*;

/**
      * @Description 字典管理api
      * @author wh
      * @date 2019/8/12 13:08
      */
@RequestMapping("api/sys/dict")
@Controller
public class DictController  extends BaseApi {

    @Autowired
     DictService dictService;

    //后端插入字典类型用，不做前端实现
    @RequestMapping(value = "insertDictType",method = RequestMethod.POST)
    @ResponseBody
    public Object insertDictType(@RequestBody DictType dictType)throws Exception{
        if(dictService.insertDictType(dictType)){
            return retMsg.Set(MsgType.SUCCESS);
        }else
            return retMsg.Set(MsgType.ERROR);
    }


    @RequestMapping(value ="selectDictListByPage",method = RequestMethod.GET)
    @ResponseBody
    public Object selectDictListByPage(@RequestBody Dict dict)throws  Exception{
            try{
                Page<Dict> page = new Page<>();
                page.setResultList(dictService.selectDictListByPage(dict));
                page.setTotal(dictService.selectSearchCount(dict));
                return retMsg.Set(MsgType.SUCCESS,page);
            }catch (Exception e){
                return retMsg.Set(MsgType.ERROR);
            }
    }

}