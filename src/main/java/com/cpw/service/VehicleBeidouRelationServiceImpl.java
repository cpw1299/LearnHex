package com.cpw.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cpw.entity.VehicleBeidouRelationEntity;
import com.cpw.mapper.VehicleBeidouRelationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class VehicleBeidouRelationServiceImpl extends ServiceImpl<VehicleBeidouRelationMapper, VehicleBeidouRelationEntity> implements IVehicleBeidouRelationService {

    @Resource
    private VehicleBeidouRelationMapper vehicleBeidouRelationMapper;

    @Override
    public VehicleBeidouRelationEntity getByVehicleIdAndVersionId(Long vehicleId, Long versionId) {
        LambdaQueryWrapper<VehicleBeidouRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VehicleBeidouRelationEntity::getVehicleId, vehicleId);
        wrapper.eq(VehicleBeidouRelationEntity::getBeidouParamVersionId, versionId);
        wrapper.last("LIMIT 1");
        return vehicleBeidouRelationMapper.selectOne(wrapper);
    }
}
