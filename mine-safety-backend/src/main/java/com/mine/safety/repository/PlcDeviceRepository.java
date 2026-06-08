package com.mine.safety.repository;

import com.mine.safety.domain.PlcDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlcDeviceRepository extends JpaRepository<PlcDevice, Long> {

    Optional<PlcDevice> findByDeviceCode(String deviceCode);

    List<PlcDevice> findByEnabled(Boolean enabled);

    List<PlcDevice> findByDeviceTypeAndEnabled(String deviceType, Boolean enabled);

    List<PlcDevice> findByZoneCodeAndEnabled(String zoneCode, Boolean enabled);

    List<PlcDevice> findByDeviceTypeAndZoneCodeAndEnabled(String deviceType, String zoneCode, Boolean enabled);

    List<PlcDevice> findByProtocolAndEnabled(String protocol, Boolean enabled);

    boolean existsByDeviceCode(String deviceCode);
}
