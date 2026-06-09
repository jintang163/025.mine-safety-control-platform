package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mine.safety.domain.PlcDevice;
import com.mine.safety.netty.client.ModbusTcpClient;
import com.mine.safety.repository.PlcDeviceRepository;
import com.mine.safety.service.LinkageActionEngineService.ActionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlcControlService {

    private final PlcDeviceRepository plcDeviceRepository;
    private final Map<String, ModbusTcpClient> clientCache = new ConcurrentHashMap<>();

    public ActionResult triggerSoundLightAlarm(String zoneCode, Map<String, Object> params) {
        log.info("触发声光报警 - 区域: {}, 参数: {}", zoneCode, params);

        try {
            List<PlcDevice> devices = plcDeviceRepository.selectList(
                    new LambdaQueryWrapper<PlcDevice>()
                            .eq(PlcDevice::getDeviceType, "PLC_SOUND_LIGHT")
                            .eq(PlcDevice::getZoneCode, zoneCode)
                            .eq(PlcDevice::getEnabled, true));

            if (devices.isEmpty()) {
                return ActionResult.failure("No sound light devices found for zone: " + zoneCode);
            }

            String pattern = params != null && params.containsKey("pattern")
                    ? (String) params.get("pattern") : "ALARM_WARNING";
            int duration = params != null && params.containsKey("duration")
                    ? (int) params.get("duration") : 120;

            for (PlcDevice device : devices) {
                boolean success = writeCoil(device, true);
                if (success) {
                    log.info("声光报警启动成功 - 设备: {}, 模式: {}, 时长: {}s", device.getDeviceCode(), pattern, duration);
                } else {
                    log.warn("声光报警启动失败 - 设备: {}", device.getDeviceCode());
                }
            }

            return ActionResult.success(JSON.toJSONString(Map.of(
                    "zone", zoneCode,
                    "pattern", pattern,
                    "duration", duration,
                    "deviceCount", devices.size()
            )));

        } catch (Exception e) {
            log.error("触发声光报警异常 - 区域: {}", zoneCode, e);
            return ActionResult.failure(e.getMessage());
        }
    }

    public ActionResult triggerVoiceBroadcast(String zoneCode, Map<String, Object> params) {
        log.info("触发语音广播 - 区域: {}, 参数: {}", zoneCode, params);

        try {
            List<PlcDevice> devices = plcDeviceRepository.selectList(
                    new LambdaQueryWrapper<PlcDevice>()
                            .eq(PlcDevice::getDeviceType, "PLC_BROADCAST")
                            .eq(PlcDevice::getZoneCode, zoneCode)
                            .eq(PlcDevice::getEnabled, true));

            if (devices.isEmpty()) {
                return ActionResult.failure("No broadcast devices found for zone: " + zoneCode);
            }

            String audioFile = params != null && params.containsKey("audioFile")
                    ? (String) params.get("audioFile") : "warning_notice.mp3";
            int loop = params != null && params.containsKey("loop")
                    ? (int) params.get("loop") : 2;
            int volume = params != null && params.containsKey("volume")
                    ? (int) params.get("volume") : 80;

            for (PlcDevice device : devices) {
                boolean success = writeRegister(device, 1);
                if (success) {
                    log.info("语音广播启动成功 - 设备: {}, 音频: {}, 循环: {}次, 音量: {}%",
                            device.getDeviceCode(), audioFile, loop, volume);
                } else {
                    log.warn("语音广播启动失败 - 设备: {}", device.getDeviceCode());
                }
            }

            return ActionResult.success(JSON.toJSONString(Map.of(
                    "zone", zoneCode,
                    "audioFile", audioFile,
                    "loop", loop,
                    "volume", volume,
                    "deviceCount", devices.size()
            )));

        } catch (Exception e) {
            log.error("触发语音广播异常 - 区域: {}", zoneCode, e);
            return ActionResult.failure(e.getMessage());
        }
    }

    public ActionResult triggerRemotePowerOff(String zoneCode, Map<String, Object> params) {
        log.info("触发远程断电 - 区域: {}, 参数: {}", zoneCode, params);

        try {
            List<PlcDevice> devices = plcDeviceRepository.selectList(
                    new LambdaQueryWrapper<PlcDevice>()
                            .eq(PlcDevice::getDeviceType, "PLC_POWER_CONTROL")
                            .eq(PlcDevice::getZoneCode, zoneCode)
                            .eq(PlcDevice::getEnabled, true));

            if (devices.isEmpty()) {
                return ActionResult.failure("No power control devices found for zone: " + zoneCode);
            }

            boolean confirm = params != null && params.containsKey("confirm")
                    ? (boolean) params.get("confirm") : false;
            String reason = params != null && params.containsKey("reason")
                    ? (String) params.get("reason") : "报警联动断电";

            if (!confirm) {
                log.warn("断电操作需要确认 - 区域: {}", zoneCode);
                return ActionResult.failure("Power off requires confirmation");
            }

            for (PlcDevice device : devices) {
                boolean success = writeCoil(device, false);
                if (success) {
                    log.info("远程断电成功 - 设备: {}, 原因: {}", device.getDeviceCode(), reason);
                } else {
                    log.error("远程断电失败 - 设备: {}", device.getDeviceCode());
                }
            }

            return ActionResult.success(JSON.toJSONString(Map.of(
                    "zone", zoneCode,
                    "reason", reason,
                    "confirmed", confirm,
                    "deviceCount", devices.size()
            )));

        } catch (Exception e) {
            log.error("触发远程断电异常 - 区域: {}", zoneCode, e);
            return ActionResult.failure(e.getMessage());
        }
    }

    public boolean writeCoil(PlcDevice device, boolean value) {
        try {
            ModbusTcpClient client = getOrCreateClient(device);
            int address = Integer.parseInt(device.getRegisterAddress().replaceAll("[^0-9]", ""));
            return client.writeCoil(address, value);
        } catch (Exception e) {
            log.error("写入线圈失败 - 设备: {}", device.getDeviceCode(), e);
            return false;
        }
    }

    public boolean writeRegister(PlcDevice device, int value) {
        try {
            ModbusTcpClient client = getOrCreateClient(device);
            int address = Integer.parseInt(device.getRegisterAddress().replaceAll("[^0-9]", ""));
            return client.writeRegister(address, value);
        } catch (Exception e) {
            log.error("写入寄存器失败 - 设备: {}", device.getDeviceCode(), e);
            return false;
        }
    }

    public boolean readCoil(PlcDevice device) {
        try {
            ModbusTcpClient client = getOrCreateClient(device);
            int address = Integer.parseInt(device.getRegisterAddress().replaceAll("[^0-9]", ""));
            return client.readCoil(address);
        } catch (Exception e) {
            log.error("读取线圈失败 - 设备: {}", device.getDeviceCode(), e);
            return false;
        }
    }

    public int readRegister(PlcDevice device) {
        try {
            ModbusTcpClient client = getOrCreateClient(device);
            int address = Integer.parseInt(device.getRegisterAddress().replaceAll("[^0-9]", ""));
            return client.readRegister(address);
        } catch (Exception e) {
            log.error("读取寄存器失败 - 设备: {}", device.getDeviceCode(), e);
            return -1;
        }
    }

    private ModbusTcpClient getOrCreateClient(PlcDevice device) {
        String key = device.getIpAddress() + ":" + device.getPort();
        return clientCache.computeIfAbsent(key, k -> {
            ModbusTcpClient client = new ModbusTcpClient(
                    device.getIpAddress(),
                    device.getPort(),
                    device.getSlaveId()
            );
            client.connect();
            return client;
        });
    }

    public void disconnectAll() {
        clientCache.values().forEach(ModbusTcpClient::disconnect);
        clientCache.clear();
        log.info("所有PLC连接已断开");
    }

    public boolean testDeviceConnection(String deviceCode) {
        PlcDevice device = plcDeviceRepository.selectOne(
                new LambdaQueryWrapper<PlcDevice>().eq(PlcDevice::getDeviceCode, deviceCode));
        if (device == null) {
            return false;
        }
        ModbusTcpClient client = getOrCreateClient(device);
        return client.isConnected();
    }
}
