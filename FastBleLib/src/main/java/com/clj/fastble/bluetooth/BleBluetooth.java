package com.clj.fastble.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleIndicateCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleReadCallback;
import com.clj.fastble.callback.BleRssiCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleConnectState;
import com.clj.fastble.data.BleConnectStateParameter;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.data.BleMsg;
import com.clj.fastble.exception.ConnectException;
import com.clj.fastble.utils.BleLog;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleBluetooth {

    private BleGattCallback bleGattCallback;
    private BleRssiCallback bleRssiCallback;
    private BleMtuChangedCallback bleMtuChangedCallback;
    private HashMap<String, BleNotifyCallback> bleNotifyCallbackHashMap = new HashMap<>();
    private HashMap<String, BleIndicateCallback> bleIndicateCallbackHashMap = new HashMap<>();
    private HashMap<String, BleWriteCallback> bleWriteCallbackHashMap = new HashMap<>();
    private HashMap<String, BleReadCallback> bleReadCallbackHashMap = new HashMap<>();

    private BleConnectState connectState = BleConnectState.CONNECT_IDLE;
    private boolean isActiveDisconnect = false;
    private BleDevice bleDevice;
    private BluetoothGatt bluetoothGatt;
    private boolean isMainThread = false;
    private MainHandler handler = new MainHandler(Looper.getMainLooper());

    public BleBluetooth(BleDevice bleDevice) {
        this.bleDevice = bleDevice;
    }

    public BleConnector newBleConnector() {
        return new BleConnector(this);
    }

    public synchronized void addConnectGattCallback(BleGattCallback callback) {
        bleGattCallback = callback;
    }

    public synchronized void removeConnectGattCallback() {
        bleGattCallback = null;
    }

    public synchronized void addNotifyCallback(String uuid, BleNotifyCallback bleNotifyCallback) {
        bleNotifyCallbackHashMap.put(uuid, bleNotifyCallback);
    }

    public synchronized void addIndicateCallback(String uuid, BleIndicateCallback bleIndicateCallback) {
        bleIndicateCallbackHashMap.put(uuid, bleIndicateCallback);
    }

    public synchronized void addWriteCallback(String uuid, BleWriteCallback bleWriteCallback) {
        bleWriteCallbackHashMap.put(uuid, bleWriteCallback);
    }

    public synchronized void addReadCallback(String uuid, BleReadCallback bleReadCallback) {
        bleReadCallbackHashMap.put(uuid, bleReadCallback);
    }

    public synchronized void removeNotifyCallback(String uuid) {
        if (bleNotifyCallbackHashMap.containsKey(uuid))
            bleNotifyCallbackHashMap.remove(uuid);
    }

    public synchronized void removeIndicateCallback(String uuid) {
        if (bleIndicateCallbackHashMap.containsKey(uuid))
            bleIndicateCallbackHashMap.remove(uuid);
    }

    public synchronized void removeWriteCallback(String uuid) {
        if (bleWriteCallbackHashMap.containsKey(uuid))
            bleWriteCallbackHashMap.remove(uuid);
    }

    public synchronized void removeReadCallback(String uuid) {
        if (bleReadCallbackHashMap.containsKey(uuid))
            bleReadCallbackHashMap.remove(uuid);
    }

    public synchronized void clearCharacterCallback() {
        if (bleNotifyCallbackHashMap != null)
            bleNotifyCallbackHashMap.clear();
        if (bleIndicateCallbackHashMap != null)
            bleIndicateCallbackHashMap.clear();
        if (bleWriteCallbackHashMap != null)
            bleWriteCallbackHashMap.clear();
        if (bleReadCallbackHashMap != null)
            bleReadCallbackHashMap.clear();
    }

    public synchronized void addRssiCallback(BleRssiCallback callback) {
        bleRssiCallback = callback;
    }

    public synchronized void removeRssiCallback() {
        bleRssiCallback = null;
    }

    public synchronized void addMtuChangedCallback(BleMtuChangedCallback callback) {
        bleMtuChangedCallback = callback;
    }

    public synchronized void removeMtuChangedCallback() {
        bleMtuChangedCallback = null;
    }


    public String getDeviceKey() {
        return bleDevice.getKey();
    }

    public BleConnectState getConnectState() {
        return connectState;
    }

    public BleDevice getDevice() {
        return bleDevice;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }


    public synchronized BluetoothGatt connect(BleDevice bleDevice,
                                              boolean autoConnect,
                                              BleGattCallback callback) {
        BleLog.i("connect device: " + bleDevice.getName()
                + "\nmac: " + bleDevice.getMac()
                + "\nautoConnect: " + autoConnect
                + "\ncurrentThread: " + Thread.currentThread().getId());

        addConnectGattCallback(callback);
        isMainThread = Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper();
        BluetoothGatt gatt;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = bleDevice.getDevice().connectGatt(BleManager.getInstance().getContext(),
                    autoConnect, coreGattCallback, TRANSPORT_LE);
        } else {
            gatt = bleDevice.getDevice().connectGatt(BleManager.getInstance().getContext(),
                    autoConnect, coreGattCallback);
        }
        if (gatt != null) {
            if (bleGattCallback != null)
                bleGattCallback.onStartConnect();
            connectState = BleConnectState.CONNECT_CONNECTING;
        }
        return gatt;
    }

    private synchronized boolean refreshDeviceCache() {
        try {
            final Method refresh = BluetoothGatt.class.getMethod("refresh");
            if (refresh != null) {
                boolean success = (Boolean) refresh.invoke(getBluetoothGatt());
                BleLog.i("refreshDeviceCache, is success:  " + success);
                return success;
            }
        } catch (Exception e) {
            BleLog.i("exception occur while refreshing device: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public synchronized void disconnect() {
        if (bluetoothGatt != null) {
            isActiveDisconnect = true;
            bluetoothGatt.disconnect();
        }
    }

    private synchronized void closeBluetoothGatt() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
    }

    public void destroy() {
        connectState = BleConnectState.CONNECT_IDLE;
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
        if (bluetoothGatt != null) {
            refreshDeviceCache();
        }
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        removeConnectGattCallback();
        removeRssiCallback();
        removeMtuChangedCallback();
        clearCharacterCallback();
        handler.removeCallbacksAndMessages(this);
    }

    private static final class MainHandler extends Handler {

        MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case BleMsg.MSG_CONNECT_FAIL: {
                    BleConnectStateParameter para = (BleConnectStateParameter) msg.obj;
                    BleGattCallback callback = para.getCallback();
                    BluetoothGatt gatt = para.getGatt();
                    int status = para.getStatus();
                    if (callback != null)
                        callback.onConnectFail(new ConnectException(gatt, status));
                    break;
                }

                case BleMsg.MSG_DISCONNECTED: {
                    BleConnectStateParameter para = (BleConnectStateParameter) msg.obj;
                    BleGattCallback callback = para.getCallback();
                    BluetoothGatt gatt = para.getGatt();
                    boolean isActive = para.isAcitive();
                    BleDevice bleDevice = para.getBleDevice();
                    int status = para.getStatus();
                    if (callback != null)
                        callback.onDisConnected(isActive, bleDevice, gatt, status);
                    break;
                }

                case BleMsg.MSG_CONNECT_SUCCESS: {
                    BleConnectStateParameter para = (BleConnectStateParameter) msg.obj;
                    BleGattCallback callback = para.getCallback();
                    BluetoothGatt gatt = para.getGatt();
                    BleDevice bleDevice = para.getBleDevice();
                    int status = para.getStatus();
                    if (callback != null)
                        callback.onConnectSuccess(bleDevice, gatt, status);
                    break;
                }

                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }


    private BluetoothGattCallback coreGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            BleLog.i("BluetoothGattCallback：onConnectionStateChange "
                    + '\n' + "status: " + status
                    + '\n' + "newState: " + newState
                    + '\n' + "currentThread: " + Thread.currentThread().getId());

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices();

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                closeBluetoothGatt();
                BleManager.getInstance().getMultipleBluetoothController().removeBleBluetooth(BleBluetooth.this);

                if (connectState == BleConnectState.CONNECT_CONNECTING) {
                    connectState = BleConnectState.CONNECT_FAILURE;

                    if (isMainThread) {
                        Message message = handler.obtainMessage();
                        message.what = BleMsg.MSG_CONNECT_FAIL;
                        message.obj = new BleConnectStateParameter(bleGattCallback, gatt, status);
                        handler.sendMessage(message);
                    } else {
                        if (bleGattCallback != null)
                            bleGattCallback.onConnectFail(new ConnectException(gatt, status));
                    }

                } else if (connectState == BleConnectState.CONNECT_CONNECTED) {
                    connectState = BleConnectState.CONNECT_DISCONNECT;

                    if (isMainThread) {
                        Message message = handler.obtainMessage();
                        message.what = BleMsg.MSG_DISCONNECTED;
                        BleConnectStateParameter para = new BleConnectStateParameter(bleGattCallback, gatt, status);
                        para.setAcitive(isActiveDisconnect);
                        para.setBleDevice(getDevice());
                        message.obj = para;
                        handler.sendMessage(message);
                    } else {
                        if (bleGattCallback != null)
                            bleGattCallback.onDisConnected(isActiveDisconnect, bleDevice, gatt, status);
                    }
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            BleLog.i("BluetoothGattCallback：onServicesDiscovered "
                    + '\n' + "status: " + status
                    + '\n' + "currentThread: " + Thread.currentThread().getId());

            if (status == BluetoothGatt.GATT_SUCCESS) {
                bluetoothGatt = gatt;
                connectState = BleConnectState.CONNECT_CONNECTED;
                isActiveDisconnect = false;
                BleManager.getInstance().getMultipleBluetoothController().addBleBluetooth(BleBluetooth.this);

                if (isMainThread) {
                    Message message = handler.obtainMessage();
                    message.what = BleMsg.MSG_CONNECT_SUCCESS;
                    BleConnectStateParameter para = new BleConnectStateParameter(bleGattCallback, gatt, status);
                    para.setBleDevice(getDevice());
                    message.obj = para;
                    handler.sendMessage(message);
                } else {
                    if (bleGattCallback != null)
                        bleGattCallback.onConnectSuccess(getDevice(), gatt, status);
                }
            } else {
                closeBluetoothGatt();
                connectState = BleConnectState.CONNECT_FAILURE;

                if (isMainThread) {
                    Message message = handler.obtainMessage();
                    message.what = BleMsg.MSG_CONNECT_FAIL;
                    message.obj = new BleConnectStateParameter(bleGattCallback, gatt, status);
                    handler.sendMessage(message);
                } else {
                    if (bleGattCallback != null)
                        bleGattCallback.onConnectFail(new ConnectException(gatt, status));
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            Iterator iterator = bleNotifyCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleNotifyCallback) {
                    BleNotifyCallback bleNotifyCallback = (BleNotifyCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleNotifyCallback.getKey())) {
                        Handler handler = bleNotifyCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_NOTIFY_DATA_CHANGE;
                            message.obj = bleNotifyCallback;
                            Bundle bundle = new Bundle();
                            bundle.putByteArray(BleMsg.KEY_NOTIFY_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                    }
                }
            }

            iterator = bleIndicateCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleIndicateCallback) {
                    BleIndicateCallback bleIndicateCallback = (BleIndicateCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleIndicateCallback.getKey())) {
                        Handler handler = bleIndicateCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_INDICATE_DATA_CHANGE;
                            message.obj = bleIndicateCallback;
                            Bundle bundle = new Bundle();
                            bundle.putByteArray(BleMsg.KEY_INDICATE_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                    }
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);

            Iterator iterator = bleNotifyCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleNotifyCallback) {
                    BleNotifyCallback bleNotifyCallback = (BleNotifyCallback) callback;
                    if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(bleNotifyCallback.getKey())) {
                        Handler handler = bleNotifyCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_NOTIFY_RESULT;
                            message.obj = bleNotifyCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_NOTIFY_BUNDLE_STATUS, status);
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                    }
                }
            }

            iterator = bleIndicateCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleIndicateCallback) {
                    BleIndicateCallback bleIndicateCallback = (BleIndicateCallback) callback;
                    if (descriptor.getCharacteristic().getUuid().toString().equalsIgnoreCase(bleIndicateCallback.getKey())) {
                        Handler handler = bleIndicateCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_INDICATE_RESULT;
                            message.obj = bleIndicateCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_INDICATE_BUNDLE_STATUS, status);
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            Iterator iterator = bleWriteCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleWriteCallback) {
                    BleWriteCallback bleWriteCallback = (BleWriteCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleWriteCallback.getKey())) {
                        Handler handler = bleWriteCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_WRITE_RESULT;
                            message.obj = bleWriteCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_WRITE_BUNDLE_STATUS, status);
                            bundle.putByteArray(BleMsg.KEY_WRITE_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            Iterator iterator = bleReadCallbackHashMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object callback = entry.getValue();
                if (callback instanceof BleReadCallback) {
                    BleReadCallback bleReadCallback = (BleReadCallback) callback;
                    if (characteristic.getUuid().toString().equalsIgnoreCase(bleReadCallback.getKey())) {
                        Handler handler = bleReadCallback.getHandler();
                        if (handler != null) {
                            Message message = handler.obtainMessage();
                            message.what = BleMsg.MSG_CHA_READ_RESULT;
                            message.obj = bleReadCallback;
                            Bundle bundle = new Bundle();
                            bundle.putInt(BleMsg.KEY_READ_BUNDLE_STATUS, status);
                            bundle.putByteArray(BleMsg.KEY_READ_BUNDLE_VALUE, characteristic.getValue());
                            message.setData(bundle);
                            handler.sendMessage(message);
                        }
                    }
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);

            if (bleRssiCallback != null) {
                Handler handler = bleRssiCallback.getHandler();
                if (handler != null) {
                    Message message = handler.obtainMessage();
                    message.what = BleMsg.MSG_READ_RSSI_RESULT;
                    message.obj = bleRssiCallback;
                    Bundle bundle = new Bundle();
                    bundle.putInt(BleMsg.KEY_READ_RSSI_BUNDLE_STATUS, status);
                    bundle.putInt(BleMsg.KEY_READ_RSSI_BUNDLE_VALUE, rssi);
                    message.setData(bundle);
                    handler.sendMessage(message);
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);

            if (bleMtuChangedCallback != null) {
                Handler handler = bleMtuChangedCallback.getHandler();
                if (handler != null) {
                    Message message = handler.obtainMessage();
                    message.what = BleMsg.MSG_SET_MTU_RESULT;
                    message.obj = bleMtuChangedCallback;
                    Bundle bundle = new Bundle();
                    bundle.putInt(BleMsg.KEY_SET_MTU_BUNDLE_STATUS, status);
                    bundle.putInt(BleMsg.KEY_SET_MTU_BUNDLE_VALUE, mtu);
                    message.setData(bundle);
                    handler.sendMessage(message);
                }
            }
        }
    };

}
