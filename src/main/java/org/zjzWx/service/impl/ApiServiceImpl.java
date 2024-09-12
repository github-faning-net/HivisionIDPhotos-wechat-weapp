package org.zjzWx.service.impl;

import com.alibaba.fastjson.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.zjzWx.entity.*;
import org.zjzWx.model.dto.CreatePhotoDto;
import org.zjzWx.model.dto.HivisionDto;
import org.zjzWx.model.vo.PicVo;
import org.zjzWx.service.*;
import org.zjzWx.util.R;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

@SuppressWarnings("DataFlowIssue")
@Service
public class ApiServiceImpl implements ApiService {

    @Value("${webset.zjzDomain}")
    private String zjzDomain;

    @Autowired
    private CustomService customService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private PhotoService photoService;
    @Autowired
    private PhotoRecordService photoRecordService;
    @Autowired
    private WebSetService webSetService;
    @Autowired
    private UploadService uploadService;

    @Value("${webset.directory}")
    private String directory;

    @Value("${webset.picDomain}")
    private String picDomain;



    @Override
    public PicVo createIdPhoto(CreatePhotoDto createPhotoDto) {
        PicVo picVo = new PicVo();
        Item item = null;
        if(createPhotoDto.getType()==0){ //定制
            Custom custom = customService.getById(createPhotoDto.getItemId());
            if(null==custom){
                picVo.setMsg("规格不存在");
                return picVo;
            }
            if(!custom.getUserId().equals(createPhotoDto.getUserId())){
                picVo.setMsg("非法请求");
                return picVo;
            }
            createPhotoDto.setHeight(custom.getHeightPx());
            createPhotoDto.setWidth(custom.getWidthPx());

        }else { //列表
             item = itemService.getById(createPhotoDto.getItemId());
            if(null==item){
                picVo.setMsg("规格信息不存在");
                return picVo;
            }
            createPhotoDto.setHeight(item.getHeightPx());
            createPhotoDto.setWidth(item.getWidthPx());
        }



        try {
            RestTemplate restTemplate = new RestTemplate();

            // 构建 multipart 数据
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            MultipartFile multipartFile = base64ToMultipartFile(createPhotoDto.getImage());
            body.add("input_image", new MultipartInputStreamFileResource(multipartFile));
            body.add("height",createPhotoDto.getHeight());
            body.add("width", createPhotoDto.getWidth());

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    zjzDomain+"/idphoto",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);

            HivisionDto hivisionDto = JSON.parseObject(response.getBody(), HivisionDto.class);
            if(!hivisionDto.isStatus()){
                picVo.setMsg("未检测到人脸或多人脸");
                return picVo;
            }

            //因为小程序需要默认初始化一张蓝底。新版本HivisionIDPhotos无法通过修改py代码来实现，只能再java再调用一次换背景接口
            //可能会慢点，目前已经列入待优化名单
            HivisionDto hivisionDto2 = updateIdPhotoTwo(hivisionDto.getImageBase64Standard(), "#438edb");


            //保存生成记录
            Photo photo = new Photo();
            photo.setUserId(createPhotoDto.getUserId());
            if(null==item){
                photo.setName("用户自定义尺寸");
            }else {
                photo.setName(item.getName());
            }
            photo.setSize(createPhotoDto.getHeight()+"x"+createPhotoDto.getWidth());
            photo.setCreateTime(new Date());
            photoService.save(photo);

            //保存用户行为记录
            PhotoRecord record = new PhotoRecord();
            record.setName("生成证件照");
            record.setUserId(createPhotoDto.getUserId());
            record.setCreateTime(new Date());
            photoRecordService.save(record);

            //封装前端参数
            picVo.setId2(photo.getId());
            picVo.setOImg(hivisionDto.getImageBase64Hd());
            picVo.setKImg(hivisionDto.getImageBase64Standard());
            picVo.setCImg(hivisionDto2.getImageBase64());
            return picVo;
        } catch (Exception e) {
            e.printStackTrace();
            picVo.setMsg("系统繁忙，请稍后再试");
            return picVo;
        }


    }

    @Override
    public PicVo updateIdPhoto(CreatePhotoDto createPhotoDto) {
        PicVo picVo = new PicVo();
        try {
            HivisionDto hivisionDto = updateIdPhotoTwo(createPhotoDto.getImage(), createPhotoDto.getColors());
            if(!hivisionDto.isStatus()){
                picVo.setMsg("未检测到人脸或多人脸");
                return picVo;
            }

            //保存用户行为记录
            PhotoRecord record = new PhotoRecord();
            record.setName("换背景");
            record.setUserId(createPhotoDto.getUserId());
            record.setCreateTime(new Date());
            photoRecordService.save(record);

            picVo.setCImg(hivisionDto.getImageBase64());
            return picVo;
        } catch (Exception e) {
            e.printStackTrace();
            picVo.setMsg("系统繁忙，请稍后再试");
            return picVo;
        }


    }

private HivisionDto updateIdPhotoTwo(String img,String colors) throws Exception {
    RestTemplate restTemplate = new RestTemplate();

    // 构建 multipart 数据
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    MultipartFile multipartFile = base64ToMultipartFile(img);
    body.add("input_image", new MultipartInputStreamFileResource(multipartFile));
    body.add("color", colors);

    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

    ResponseEntity<String> response = restTemplate.exchange(
            zjzDomain+"/add_background",
            HttpMethod.POST,
            requestEntity,
            String.class);


    return JSON.parseObject(response.getBody(), HivisionDto.class);
}




    @Override
    public PicVo updateUserPhonto(Integer userid,String img,Integer photoId) {
        PicVo picVo = new PicVo();
        //防止被当图床
        Photo photo = photoService.getById(photoId);
        if(null==photo){
            picVo.setMsg("非法请求");
            return picVo;
        }
        if(!photo.getUserId().equals(userid)){
            picVo.setMsg("非法请求");
            return picVo;
        }



        //因为图片没刚开始存库，是为了防止性能浪费，所有由前端传入
        // 将图片转成MultipartFile，再次检查，防止数据伪造，如：被劫持数据包上传黄色，木马什么的
        MultipartFile file = base64ToMultipartFile(img);


        // 检查文件类型
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || (!originalFilename.toLowerCase().endsWith(".png")
                && !originalFilename.toLowerCase().endsWith(".jpg")) && !originalFilename.toLowerCase().endsWith(".jpeg")) {
            // 文件类型不合法
            picVo.setMsg("图片类型不合法，仅支持jpg/png/jpeg的图片");
            return picVo;
        }

        //不检查图片大小，因为生成的证件照可能很大
//        if (file.getSize() > 15 * 1024 * 1024) {
//            picVo.setMsg("图片大小不能超过20M");
//            return picVo;
//        }

        //不开启鉴黄，实际测试生成后的存在会被误判
//        WebSet webSet = webSetService.getById(1);
//        //如果开启鉴黄
//        if(webSet.getSafetyApi()==2){
//            String s = uploadService.checkNsfw(file);
//            if(s!=null){
//                picVo.setMsg(s);
//                return picVo;
//            }
//        }


    //检查通过，上传服务器，数据库保存url
    //之前试过保存base64，发现数据库加载很慢，性能很低
        try {
        // 创建文件存储目录
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String folderName = dateFormat.format(new Date());
        File uploadFolder = new File(directory, folderName);
        if (!uploadFolder.exists()) {
            uploadFolder.mkdirs();
        }

        // 生成新的文件名
        String filename = generateUniqueFilename(originalFilename, file);
        Path filePath = uploadFolder.toPath().resolve(filename);


            // 保存文件
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            //nginx帮助
            String imagePath = picDomain + folderName + "/" + filename;
            photo.setId(photoId);
            photo.setNImg(imagePath);
            photoService.updateById(photo);


            //保存用户行为记录
            PhotoRecord record = new PhotoRecord();
            record.setName("下载证件照");
            record.setUserId(userid);
            record.setCreateTime(new Date());
            photoRecordService.save(record);
            picVo.setPicUrl(imagePath);
            return picVo;

        } catch (IOException e) {
            e.printStackTrace();
            // 处理文件保存失败的情况
            picVo.setMsg("图片存入失败错误，请重试");
            return picVo;
        }

    }




    private static class MultipartInputStreamFileResource extends ByteArrayResource {
        private final String filename;

        MultipartInputStreamFileResource(MultipartFile multipartFile) throws Exception {
            super(multipartFile.getBytes());
            this.filename = multipartFile.getOriginalFilename();
        }

        @Override
        public String getFilename() {
            return this.filename;
        }
    }


    private static MultipartFile base64ToMultipartFile(String base64) {
        // 提取Base64内容
        String[] baseStrs = base64.split(",");

        // 获取 MIME 类型
        String mimeType = baseStrs[0].split(":")[1].split(";")[0];
        String extension = mimeType.split("/")[1]; // 提取文件扩展名

        // 解码Base64数据
        byte[] data = Base64.getDecoder().decode(baseStrs[1]);

        // 创建MultipartFile对象
        return new MockMultipartFile(
                "file", // 表单字段名
                "filename." + extension, // 原始文件名，带扩展名
                mimeType, // 文件类型
                data // 文件数据
        );
    }



    private String generateUniqueFilename(String originalFilename, MultipartFile file) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        String timestamp = dateFormat.format(new Date());
        String contentHash = getFileContentHash(file); // 获取文件内容哈希值
        return timestamp  + contentHash + getExtension(originalFilename);
    }

    private String getFileContentHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(file.getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex);
        }
        return "";
    }


}