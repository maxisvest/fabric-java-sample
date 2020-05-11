package com.maxisvest.fabric.controller;

import com.maxisvest.fabric.bean.vo.RequestModel;
import com.maxisvest.fabric.service.DataService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;

/**
 * Create by yuyang
 * 2020/5/8 18:32
 */
@Controller
@RequestMapping("/blockchain")
public class BlockChainController {

    @Resource
    private DataService dataService;

    @RequestMapping(value = "/get", method = RequestMethod.POST)
    @ResponseBody
    public Object get(@RequestBody RequestModel info) {
        return dataService.getContents(info.getUserID());
    }

    @RequestMapping(value = "/set", method = RequestMethod.POST)
    @ResponseBody
    public Object saveInstance(@RequestBody RequestModel info) {
        dataService.setContent(info.getUserID(), info.getContentType(), info.getContent());
        return "成功";
    }

}
