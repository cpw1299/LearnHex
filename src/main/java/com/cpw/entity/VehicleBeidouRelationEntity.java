package com.cpw.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("vehicle_beidou_relation")
@ApiModel(value = "VehicleBeidouRelationEntity", description = "车辆北斗关系")
public class VehicleBeidouRelationEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "车辆id")
    private Long vehicleId;

    @ApiModelProperty(value = "北斗参数版本id")
    private Long beidouParamVersionId;

    @ApiModelProperty(value = "北斗设备id")
    private Long beidouDeviceId;

    @ApiModelProperty(value = "设备id(冗余)")
    private String deviceId;

    @ApiModelProperty(value = "编号(冗余)")
    private String deviceNo;

    @ApiModelProperty(value = "名称(冗余)")
    private String deviceName;

    @ApiModelProperty(value = "序列号(冗余)")
    private String serialNo;

    @ApiModelProperty(value = "北斗参数版本号(冗余)")
    private String versionNo;

    @ApiModelProperty(value = "状态(1:启用,0:禁用)")
    private Integer status;

    @ApiModelProperty(value = "绑定时的报文配置)")
    private String bdConfig;

    @ApiModelProperty(value = "操作人")
    private String operator;

    @ApiModelProperty(value = "操作时间")
    private Date operateTime;

    private String deleteFlg;

    private String createUser;

    private Date createTime;

    private String updateUser;

    private Date updateTime;
}
