package com.lkd.http.controller;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.lkd.entity.UserEntity;
import com.lkd.feign.VMService;
import com.lkd.http.vo.LoginReq;
import com.lkd.http.vo.LoginResp;
import com.lkd.http.vo.UserReq;
import com.lkd.service.RoleService;
import com.lkd.service.UserService;
import com.lkd.vo.Pager;
import com.lkd.vo.UserVO;
import com.lkd.vo.UserWorkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;
    private final RoleService roleService;
    private final DefaultKaptcha kaptcha;


    @Autowired
    private RedisTemplate<String,String> redisTemplate;

    /**
     * 根据id查询
     * @param id
     * @return 实体
     */
    @GetMapping("/{id}")
    public UserVO findById(@PathVariable Integer id){
        UserEntity userEntity = userService.getById(id);
        if(userEntity == null) {
            return null;
        }

        return convertToVM(userEntity);
    }

    /**
     * 新增
     * @param req
     * @return 是否成功
     */
    @PostMapping
    public boolean add(@RequestBody UserReq req){
        UserEntity user = new UserEntity();
        user.setUserName(req.getUserName());
        user.setRegionId(Long.valueOf(req.getRegionId()));
        user.setRegionName(req.getRegionName());
        user.setMobile(req.getMobile());
        user.setRoleId(req.getRoleId());
        user.setRoleCode(roleService.getById(req.getRoleId()).getRoleCode());
        user.setStatus(req.getStatus());
        user.setImage(req.getImage());
        String secret = System.currentTimeMillis()+"lkd";
        user.setSecret(secret + "");

        return userService.save(user);
    }

    /**
     * 修改
     * @param id
     * @param req
     * @return 是否成功
     */
    @PutMapping("/{id}")
    public boolean update(@PathVariable Integer id,@RequestBody UserReq req){
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUserName(req.getUserName());
        user.setRegionId(Long.valueOf(req.getRegionId()));
        user.setRegionName(req.getRegionName());
        user.setMobile(req.getMobile());
        user.setRoleId(req.getRoleId());
        user.setStatus(req.getStatus());

        return userService.updateById(user);
    }

    /**
     * 删除
     * @param id
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    public  boolean delete(@PathVariable Integer id){
        return userService.removeById(id);
    }

    /**
     * 分页查询
     * @param pageIndex 页码
     * @param pageSize 页大小
     * @param userName 用户名
     * @return 分页结果
     */
    @GetMapping("/search")
    public Pager<UserEntity> findPage(
            @RequestParam(value = "pageIndex",required = false,defaultValue = "1") long pageIndex,
            @RequestParam(value = "pageSize",required = false,defaultValue = "10") long pageSize,
            @RequestParam(value = "userName",required = false,defaultValue = "") String userName,
            @RequestParam(value = "roleId",required = false,defaultValue = "0") Integer roleId,
            @RequestParam(value = "isRepair",required = false,defaultValue = "") Boolean isRepair){
        return userService.findPage( pageIndex,pageSize,userName,roleId,isRepair);
    }


    /**
     * 登录
     * @param req
     * @return
     * @throws IOException
     */
    @PostMapping("/login")
    public LoginResp login(@RequestBody LoginReq req) throws IOException {
        return userService.login(req);
    }

    /**
     * 生成登录手机验证码
     * @param mobile
     */
    @GetMapping("/code/{mobile}")
    public void generateCode(@PathVariable String mobile){
        userService.sendCode(mobile);
    }

    /**
     * 获取图片验证码
     * @param httpServletRequest
     * @param httpServletResponse
     */
    @GetMapping("/imageCode/{clientToken}")
    public void getImageCode(HttpServletRequest httpServletRequest,
                             HttpServletResponse httpServletResponse,
                             @PathVariable String clientToken) throws IOException {
        //1.通过kaptcha获取验证码
        String createText = getImageCode(httpServletResponse);

        //2.将验证码存入redis  2分钟超时
        redisTemplate.boundValueOps(clientToken).set(createText,120, TimeUnit.SECONDS);
    }

    /**
     * 获取图片验证码
     * @param httpServletResponse mvc中的组件
     * @return 验证码
     * @throws IOException
     */
    private String getImageCode(HttpServletResponse httpServletResponse) throws IOException {
        ByteArrayOutputStream jpegOutputStream = new ByteArrayOutputStream();
        //生成随机字母+数字(4位)
        String createText = kaptcha.createText();
        //根据文本构建图片
        BufferedImage challenge = kaptcha.createImage(createText);

        ImageIO.write(challenge, "jpg", jpegOutputStream);
        byte[] captchaChallengeAsJpeg  = jpegOutputStream.toByteArray();
        httpServletResponse.setContentType("image/jpeg");
        ServletOutputStream responseOutputStream =
                httpServletResponse.getOutputStream();
        responseOutputStream.write(captchaChallengeAsJpeg);
        responseOutputStream.flush();
        responseOutputStream.close();
        return createText;
    }


    /**
     * 获取运营员数量
     * @return
     */
    @GetMapping("/operaterCount")
    public Integer getOperatorCount(){
        return userService.getOperatorCount();
    }

    /**
     * 获取维修员数量
     * @return
     */
    @GetMapping("/repairerCount")
    public Integer getRepairerCount(){
        return userService.getRepairerCount();
    }

    /**
     * 获取某区域下所有运营员
     * @param regionId
     * @return
     */
    @GetMapping("/operators/{regionId}")
    public List<UserVO> getOperatorList(@PathVariable String regionId){
        return userService.getOperatorList(Long.valueOf(regionId));
    }

    /**
     * 获取某区域下所有运维员
     * @param regionId
     * @return
     */
    @GetMapping("/repairers/{regionId}")
    public List<UserVO> getRepairerList(@PathVariable String regionId){
        return userService.getRepairerList(Long.valueOf(regionId));
    }

    @Autowired
    private VMService vmService;

    /**
     * 通过售货机编号获取同区域下所有运营员
     * @param innerCode
     * @return
     */
    @GetMapping("/operatorList/{innerCode}")
    public List<UserVO> getOperatorListByInnerCode(@PathVariable String innerCode){
        var vm = vmService.getVMInfo(innerCode);
        if(vm == null){
            return null;
        }

        return userService.getOperatorList(vm.getRegionId());
    }

    /**
     * 通过售货机编号获取同区域下所有维修员
     * @param innerCode
     * @return
     */
    @GetMapping("/repairerList/{innerCode}")
    public List<UserVO> getRepairerListByInnerCode(@PathVariable String innerCode){
        var vm = vmService.getVMInfo(innerCode);
        if(vm == null) {
            return null;
        }
        return userService.getRepairerList(vm.getRegionId());
    }



    /**
     * 获取某区域下维修员/运营员总数
     * @param isRepair
     * @return
     */
    @GetMapping("/countByRegion/{regionId}/{isRepair}")
    public Integer getCountByRegion(@PathVariable String  regionId,@PathVariable Boolean isRepair){
        return userService.getCountByRegion(Long.valueOf(regionId),isRepair);
    }



    private UserVO convertToVM(UserEntity userEntity){
        UserVO userVO = new UserVO();
        userVO.setMobile(userEntity.getMobile());
        userVO.setLoginName(userEntity.getLoginName());
        userVO.setRoleId(userEntity.getRoleId());
        userVO.setRoleCode(userEntity.getRoleCode());
        userVO.setUserId(userEntity.getId());
        userVO.setRoleName(userEntity.getRole().getRoleName());
        userVO.setUserName(userEntity.getUserName());
        userVO.setStatus(userEntity.getStatus());
        userVO.setRegionId(userEntity.getRegionId());
        userVO.setRoleName(userEntity.getRole().getRoleName());
        userVO.setRegionName(userEntity.getRegionName());
        userVO.setImage(userEntity.getImage());

        return userVO;
    }

    /**
     * 搜索用户工作量列表
     * @param pageIndex
     * @param pageSize
     * @param userName
     * @param roleId
     * @return
     */
    @GetMapping("/searchUserWork")
    public Pager<UserWorkVO> searchUserWork(
            @RequestParam(value = "pageIndex",required = false,defaultValue = "1") long pageIndex,
            @RequestParam(value = "pageSize",required = false,defaultValue = "10") long pageSize,
            @RequestParam(value = "userName",required = false,defaultValue = "") String userName,
            @RequestParam(value = "roleId",required = false,defaultValue = "0") Integer roleId,
            @RequestParam(value = "isRepair",required = false,defaultValue = "") Boolean isRepair){
        return userService.searchUserWork(pageIndex,pageSize,userName,roleId,isRepair) ;
    }
}
