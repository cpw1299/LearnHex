package com.cpw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cpw.entity.VehicleBeidouRelationEntity;

public interface IVehicleBeidouRelationService extends IService<VehicleBeidouRelationEntity> {

    VehicleBeidouRelationEntity getByVehicleIdAndVersionId(Long vehicleId, Long versionId);
}
