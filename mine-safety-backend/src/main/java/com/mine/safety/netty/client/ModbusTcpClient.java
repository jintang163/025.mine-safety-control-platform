package com.mine.safety.netty.client;

import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.io.ModbusTCPTransaction;
import com.ghgande.j2mod.modbus.msg.*;
import com.ghgande.j2mod.modbus.net.TCPMasterConnection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModbusTcpClient {

    private String host;
    private int port;
    private int slaveId;
    private int timeout = 5000;
    private int retries = 3;

    private TCPMasterConnection connection;
    private volatile boolean connected = false;

    public ModbusTcpClient(String host, int port, int slaveId) {
        this.host = host;
        this.port = port;
        this.slaveId = slaveId;
    }

    public boolean connect() {
        try {
            if (connection != null && connected) {
                return true;
            }

            InetAddress address = InetAddress.getByName(host);
            connection = new TCPMasterConnection(address);
            connection.setPort(port);
            connection.setTimeout(timeout);
            connection.connect();

            connected = true;
            log.info("Modbus TCP 连接成功 - {}:{}, 从站: {}", host, port, slaveId);
            return true;

        } catch (Exception e) {
            log.error("Modbus TCP 连接失败 - {}:{}", host, port, e);
            connected = false;
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null) {
                connection.close();
                connected = false;
                log.info("Modbus TCP 连接已断开 - {}:{}", host, port);
            }
        } catch (Exception e) {
            log.error("Modbus TCP 断开连接异常 - {}:{}", host, port, e);
        }
    }

    public boolean readCoil(int address) {
        return executeWithRetry(() -> {
            ReadCoilsRequest request = new ReadCoilsRequest(address, 1);
            request.setUnitID(slaveId);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            ReadCoilsResponse response = (ReadCoilsResponse) transaction.getResponse();
            return response.getCoils().getBit(0);
        });
    }

    public boolean[] readCoils(int address, int count) {
        return executeWithRetry(() -> {
            ReadCoilsRequest request = new ReadCoilsRequest(address, count);
            request.setUnitID(slaveId);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            ReadCoilsResponse response = (ReadCoilsResponse) transaction.getResponse();
            boolean[] result = new boolean[count];
            for (int i = 0; i < count; i++) {
                result[i] = response.getCoils().getBit(i);
            }
            return result;
        });
    }

    public boolean writeCoil(int address, boolean value) {
        return executeWithRetry(() -> {
            WriteCoilRequest request = new WriteCoilRequest(address, value);
            request.setUnitID(slaveId);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            WriteCoilResponse response = (WriteCoilResponse) transaction.getResponse();
            return response.getCoil();
        });
    }

    public int readRegister(int address) {
        return executeWithRetry(() -> {
            ReadMultipleRegistersRequest request = new ReadMultipleRegistersRequest(address, 1);
            request.setUnitID(slaveId);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) transaction.getResponse();
            return response.getRegisterValue(0);
        });
    }

    public int[] readRegisters(int address, int count) {
        return executeWithRetry(() -> {
            ReadMultipleRegistersRequest request = new ReadMultipleRegistersRequest(address, count);
            request.setUnitID(slaveId);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            ReadMultipleRegistersResponse response = (ReadMultipleRegistersResponse) transaction.getResponse();
            int[] result = new int[count];
            for (int i = 0; i < count; i++) {
                result[i] = response.getRegisterValue(i);
            }
            return result;
        });
    }

    public boolean writeRegister(int address, int value) {
        return executeWithRetry(() -> {
            WriteSingleRegisterRequest request = new WriteSingleRegisterRequest(address, value);
            request.setUnitID(slaveId);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            WriteSingleRegisterResponse response = (WriteSingleRegisterResponse) transaction.getResponse();
            return response.getRegisterValue() == value;
        });
    }

    public boolean writeRegisters(int address, int[] values) {
        return executeWithRetry(() -> {
            WriteMultipleRegistersRequest request = new WriteMultipleRegistersRequest(address, values);
            request.setUnitID(slaveId);

            ModbusTCPTransaction transaction = new ModbusTCPTransaction(connection);
            transaction.setRequest(request);
            transaction.execute();

            WriteMultipleRegistersResponse response = (WriteMultipleRegistersResponse) transaction.getResponse();
            return response.getWordCount() == values.length;
        });
    }

    private <T> T executeWithRetry(ModbusOperation<T> operation) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                if (!connected) {
                    connect();
                }
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                log.warn("Modbus操作失败 - 尝试: {}/{}, 错误: {}", attempt, retries, e.getMessage());

                if (attempt < retries) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (attempt == retries) {
                    connected = false;
                }
            }
        }

        throw new ModbusClientException("Modbus操作失败", lastException);
    }

    @FunctionalInterface
    private interface ModbusOperation<T> {
        T execute() throws Exception;
    }

    public static class ModbusClientException extends RuntimeException {
        public ModbusClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    protected void finalize() {
        disconnect();
    }
}
