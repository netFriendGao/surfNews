package com.imooc.api.controller.admin;


import com.imooc.api.config.MyServiceList;
import com.imooc.grace.result.GraceJSONResult;
import io.swagger.annotations.Api;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author 高昂
 */
@Api(value = "文件上传的Controller", tags = {"文件上传的Controller"})
@RequestMapping("fs")
@FeignClient(value = MyServiceList.SERVICE_FILES)
public interface ReadFace64InGridFsApi {
    /**
     * 从gridfs中读取图片内容，并且返回base64
     * @param faceId
     * @return
     * @throws Exception
     */
    @GetMapping("/readFace64InGridFS")
    public GraceJSONResult readFace64InGridFS(@RequestParam String faceId)
            throws Exception;
}
