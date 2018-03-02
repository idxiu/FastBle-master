package com.clj.blesample;

import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleRssiCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.clj.fastble.utils.HexUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;


/**
 * site:istarvip.cn
 *
 * @author：istarvip on 2018/1/15 16:50
 * 邮箱：917017530@qq.com
 * FIXME MyBleUtils
 */

public  class MyBleUtils {
    private String TAG = getClass().getSimpleName();
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private static final int SEND_PACKET_SIZE = 20;
    private static final int FREE = 0;
    private static final int SENDING = 1;
    private static final int RECEIVING = 2;

    private Context context;
    private Intent intent;
    private ArrayList<BleDevice> bleDeviceList = new ArrayList<>();
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic gattCharacteristic;
    private BroadcastReceiver BLEStatusChangeReceiver;

    private BluetoothGattService RxService;
    private String uuid;
    private String uuid_service = "";
    private String uuid_notify = "";

    private int mConnectionState = STATE_DISCONNECTED;

    private int ble_status = FREE;
    private BleDevice mBleDevice;

    private BleScanCallback bleScanCallback = new BleScanCallback() {

        @Override
        public void onScanStarted(boolean success) {
           // EventBusUtils.INSTANCE.post(new MyBlueEvent(MyConstants.onStartScan));

        }

        @Override
        public void onScanning(BleDevice result) {
           // EventBusUtils.INSTANCE.post(new MyBlueEvent(MyConstants.onScanning, result));

        }

        @Override
        public void onScanFinished(List<BleDevice> scanResultList) {
          //  EventBusUtils.INSTANCE.post(new MyBlueEvent(MyConstants.onScanFinished));
        }
    };

    private BleGattCallback bleGattCallback = new BleGattCallback() {
        @Override
        public void onStartConnect() {
            Log.i(TAG, "onStartConnect");
            mConnectionState = STATE_CONNECTING;
        }

        @Override
        public void onConnectFail(BleException exception) {
            Log.i(TAG, "连接失败"+exception.toString());
          //  EventBusUtils.INSTANCE.post(new MyBlueEvent(MyConstants.onConnFail));
            removeSPALL();
            MyApplication.isConnecting=false;
            mConnectionState = STATE_DISCONNECTED;
            close();
        }

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            Log.i(TAG, "onConnectSuccess");
            setMtu(bleDevice, 23);
            MyApplication.isConnecting=true;
            removeSP();
            mBleDevice = bleDevice;

            mBluetoothGatt = BleManager.getInstance().getBluetoothGatt(mBleDevice);
            mConnectionState = STATE_CONNECTED;
          //  EventBusUtils.INSTANCE.post(new MyBlueEvent(MyConstants.onConnected, bleDevice));
            readRssi(bleDevice);
            enableTXNotification();//允许接收蓝;牙设备发送过来的数据
            notifyMsg();
        }

        @Override
        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
            Log.e(TAG, "onDisConnected:"+ isActiveDisConnected);
            MyApplication.isConnecting=false;
            removeSPALL();
            close();
         //   EventBusUtils.INSTANCE.post(new MyBlueEvent(MyConstants.onDisConnected, device));
            if (isActiveDisConnected) {
                //是否是主动调用了断开连接方法
                Log.i(TAG, "断开了");
            } else {
                Log.i(TAG, "连接断开");
                connect(device);
            }

        }
    };


    private void removeSP() {
    }

    //del before data
    private void removeBeforeSP() {
    }

    private void removeSPALL() {
        removeSP();
    }

    private SendDataToBleReceiver sendDataToBleReceiver;

    private Timer mTimer;
    private int time_out_counter = 0;
    private int TIMER_INTERVAL = 100;
    private int TIME_OUT_LIMIT = 100;

    private byte[] send_data = null;
    private int packet_counter = 0;
    private int send_data_pointer = 0;
    private boolean sendingStoredData = false;
    private boolean first_packet = false;
    private boolean final_packet = false;
    private boolean packet_send = false;

    public ArrayList<byte[]> data_queue = new ArrayList<>();

    public void init(Application app, BroadcastReceiver BLEStatusChangeReceiver) {
        bleDeviceList = new ArrayList();
        context = app.getApplicationContext();
        sendDataToBleReceiver = new SendDataToBleReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyConstants.ACTION_SEND_DATA_TO_BLE);
        LocalBroadcastManager.getInstance(context).registerReceiver(sendDataToBleReceiver, intentFilter);
        this.BLEStatusChangeReceiver = BLEStatusChangeReceiver;
        //注册广播
        context.getApplicationContext().registerReceiver(
                this.BLEStatusChangeReceiver, makeGattUpdateIntentFilter());
        BleManager.getInstance().init(app);
        setScanRule(true);
        BleManager.getInstance()
                .enableLog(true)
                .setMaxConnectCount(7)
                .setOperateTimeout(5000);
    }

    public void destroy() {
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
        close();
        if (context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(sendDataToBleReceiver);
            context.unregisterReceiver(BLEStatusChangeReceiver);
        }
    }

    public Boolean isBlueEnable() {
        return BleManager.getInstance().isBlueEnable();
    }

    public void openBle() {
        BleManager.getInstance().enableBluetooth();
    }
    public void closeBle() {
        BleManager.getInstance().disableBluetooth();
    }

    public void stopScan() {
        BleManager.getInstance().cancelScan();
    }


    public Boolean isConnected(BleDevice bleDevice) {
        return BleManager.getInstance().isConnected(bleDevice);
    }

    public void disconnect(BleDevice bleDevice) {
        BleManager.getInstance().disconnect(bleDevice);
    }

    public void disconnect() {
        BleManager.getInstance().disconnect(mBleDevice);
    }

    public void connect(BleDevice bleDevice) {
        mConnectionState = STATE_CONNECTING;
        mBluetoothGatt = BleManager.getInstance().connect(bleDevice, bleGattCallback);
    }

    public void scan() {
        BleManager.getInstance().scan(bleScanCallback);
    }

    /**
     * 在没有扩大MTU及扩大MTU无效的情况下，当遇到超过20字节的长数据需要发送的时候，需要进行分包。
     * 参数boolean split表示是否使用分包发送；无 boolean split参数的write方法默认对超过20字节的数据进行分包发送。
     */
    public void write(BleDevice bleDevice, String uuid_service, String uuid_write, byte[] data, Boolean split, BleWriteCallback callback) {
        BleManager.getInstance().write(bleDevice, uuid_service, uuid_write, data, callback);
    }

    public void notifyMsg() {
        RxService = mBluetoothGatt.getService(MyConstants.RX_SERVICE_UUID);
        if (RxService == null) {
            Log.e(TAG, "RxService null");
            return;
        }
        RxService = mBluetoothGatt.getService(MyConstants.RX_SERVICE_UUID);
        gattCharacteristic = RxService.getCharacteristic(MyConstants.TX_CHAR_UUID);
        if (gattCharacteristic == null) {
            Log.e(TAG, "gattCharacteristic null");
            return;
        }
        uuid = RxService.getUuid().toString();
        uuid_service = gattCharacteristic.getService().getUuid().toString();
        uuid_notify = gattCharacteristic.getUuid().toString();
        //   Log.e(TAG, "uuid_service:" + uuid_service + "---uuid_notify--" + uuid_notify + "---uuid--" + uuid);

        BleManager.getInstance().notify(mBleDevice, uuid_service, uuid_notify, new BleNotifyCallback() {
            @Override
            public void onNotifySuccess() {
                // 打开通知操作成功
            }

            @Override
            public void onNotifyFailure(BleException exception) {
                // 打开通知操作失败
            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
                // 打开通知后，设备发过来的数据将在这里出现
                //  byte[] aa=gattCharacteristic.getValue();
                // byte[]转String，参数addSpace表示每一位之间是否增加空格，常用于打印日志。
                // version:ab 00 06 ff 92 c0 03 06 b3 power:ab 00 05 ff 91 80 00 64
                Log.e(TAG, "broadcastUpdate: received from ble:" + HexUtil.formatHexString(data, true));
                if (ble_status == FREE || ble_status == RECEIVING) {
                    ble_status = RECEIVING;
                    if (data != null) {
                        intent = new Intent(MyConstants.ACTION_DATA_AVAILABLE);
                        intent.putExtra(MyConstants.EXTRA_DATA, data);
                        context.sendBroadcast(intent);
                    }
                    ble_status = FREE;

                } else if (ble_status == SENDING) {
                    if (final_packet) {
                        final_packet = false;
                    }
                    ble_status = FREE;
                }
            }
        });
    }

    //停止通知
    public void stopNotify() {
        if (TextUtils.isEmpty(uuid_service) || TextUtils.isEmpty(uuid_notify)) {
            return;
        }
        BleManager.getInstance().stopNotify(mBleDevice, uuid_service, uuid_notify);
    }



    public void setScanRule(Boolean isAutoConnect) {

        UUID[] serviceUuids = new UUID[]{
                MyConstants.RX_SERVICE_UUID, MyConstants.TX_CHAR_UUID,
                MyConstants.CCCD, MyConstants.RX_CHAR_UUID
        };

        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
                //   .setServiceUuids(serviceUuids)      // 只扫描指定的服务的设备，可选
                //  .setDeviceName(true, names)   // 只扫描指定广播名的设备，可选
                //  .setDeviceMac(mac)                  // 只扫描指定mac的设备，可选
                .setAutoConnect(isAutoConnect)      // 连接时的autoConnect参数，可选，默认false
                .setScanTimeOut(10000)              // 扫描超时时间，可选，默认10秒
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);
    }


    public void clear() {
    }

    public void readRssi(BleDevice bleDevice) {
        BleManager.getInstance().readRssi(bleDevice, new BleRssiCallback() {
            @Override
            public void onRssiFailure(BleException exception) {
                Log.i(TAG, "onRssiFailure" + exception.toString());
            }

            @Override
            public void onRssiSuccess(int rssi) {
                Log.i(TAG, "onRssiSuccess: " + rssi);
            }
        });
    }

    //设置最大传输单元MTU
    private void setMtu(BleDevice bleDevice, int mtu) {
        BleManager.getInstance().setMtu(bleDevice, mtu, new BleMtuChangedCallback() {
            @Override
            public void onSetMTUFailure(BleException exception) {
                // // 设置MTU失败
                Log.i(TAG, "onsetMTUFailure" + exception.toString());
            }

            @Override
            public void onMtuChanged(int mtu) {
                // 设置MTU成功，并获得当前设备传输支持的MTU值
                Log.i(TAG, "onMtuChanged: " + mtu);
            }
        });
    }

    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    public void enableTXNotification() {


        if (mBluetoothGatt == null) {
            return;
        }
        BluetoothGattService RxService = mBluetoothGatt
                .getService(MyConstants.RX_SERVICE_UUID);
        if (RxService == null) {
            return;
        }

        BluetoothGattCharacteristic TxChar = RxService
                .getCharacteristic(MyConstants.TX_CHAR_UUID);
        if (TxChar == null) {
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(TxChar, true);
        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(MyConstants.CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }


    class SendDataToBleReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e( TAG, "SendDataToBleReceiver onReceive");
            if (action.equals(MyConstants.ACTION_SEND_DATA_TO_BLE)) {
                Log.e(TAG, "ACTION_SEND_DATA_TO_BLE");
                byte[] send_data = intent.getByteArrayExtra(MyConstants.EXTRA_SEND_DATA_TO_BLE);
                if (send_data != null) {
                    BLE_send_data_set(send_data, false);
                }
            }
        }
    }

    private IntentFilter makeGattUpdateIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyConstants.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    /**
     * 设置数据到内部缓冲区对BLE发送数据
     */
    private void BLE_send_data_set(byte[] data, boolean retry_status) {
        if (ble_status != FREE || mConnectionState != STATE_CONNECTED) {
            //蓝牙没有连接或是正在接受或发送数据，此时将要发送的指令加入集合
            Log.e(TAG, "蓝牙没有连接或是正在接受或发送数据:$ble_status  $mConnectionState");
            if (sendingStoredData) {
                if (!retry_status) {
                    data_queue.add(data);
                }
                return;
            } else {
                data_queue.add(data);
                start_timer();
            }

        } else {
            Log.e(TAG, "ble_status = SENDING");
            ble_status = SENDING;

            if (data_queue.size() != 0) {
                send_data = data_queue.get(0);
                sendingStoredData = false;
            } else {
                send_data = data;
            }
            packet_counter = 0;
            send_data_pointer = 0;
            //第一个包
            first_packet = true;
            BLE_data_send();

            if (data_queue.size() != 0) {
                data_queue.remove(0);
            }

            if (data_queue.size() == 0) {
                if (mTimer != null) {
                    mTimer.cancel();
                }
            }
        }
    }

    /**
     * 定时器
     */
    private void start_timer() {
        sendingStoredData = true;
        if (mTimer != null) {
            mTimer.cancel();
        }
        mTimer = new Timer(true);
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                timer_Tick();
            }
        }, 100, TIMER_INTERVAL);
    }

    /**
     * @brief Interval timer  public voidction.
     */
    private void timer_Tick() {
        if (data_queue.size() != 0) {
            sendingStoredData = true;
            BLE_send_data_set(data_queue.get(0), true);
        }

        if (time_out_counter < TIME_OUT_LIMIT) {
            time_out_counter++;
        } else {
            ble_status = FREE;
            time_out_counter = 0;
        }
        return;
    }


    /**
     * @brief Send data using BLE. 发送数据到蓝牙
     */
    private void BLE_data_send() {
        err_count = 0;
        send_data_pointer_save = 0;
        first_packet_save = false;
        int wait_counter = 0;
        while (!final_packet) {
            //不是最后一个包
            byte[] temp_buffer;
            send_data_pointer_save = send_data_pointer;
            first_packet_save = first_packet;
            if (first_packet) {
                //第一个包

                if ((send_data.length - send_data_pointer) > (SEND_PACKET_SIZE)) {
                    temp_buffer = new byte[SEND_PACKET_SIZE];//20
                    for (int i = 0; i < SEND_PACKET_SIZE; i++) {
                        //将原数组加入新创建的数组
                        temp_buffer[i] = send_data[send_data_pointer];
                        send_data_pointer++;
                    }
                } else {
                    //发送的数据包不大于20
                    temp_buffer = new byte[send_data.length - send_data_pointer];
                    for (int i = 0; i < temp_buffer.length; i++) {
                        //将原数组未发送的部分加入新创建的数组
                        temp_buffer[i] = send_data[send_data_pointer];
                        send_data_pointer++;
                    }
                    final_packet = true;
                }
                first_packet = false;
            } else {
                //不是第一个包
                if (send_data.length - send_data_pointer >= SEND_PACKET_SIZE) {
                    temp_buffer = new byte[SEND_PACKET_SIZE];
                    temp_buffer[0] = (byte) packet_counter;
                    for (int i = 1; i < SEND_PACKET_SIZE; i++) {
                        temp_buffer[i] = send_data[send_data_pointer];
                        send_data_pointer++;
                    }
                } else {
                    final_packet = true;
                    temp_buffer = new byte[send_data.length - send_data_pointer + 1];
                    temp_buffer[0] = (byte) packet_counter;
                    for (int i = 1; i < temp_buffer.length; i++) {
                        temp_buffer[i] = send_data[send_data_pointer];
                        send_data_pointer++;
                    }
                }
                packet_counter++;
            }
            packet_send = false;
            writeRXCharacteristic(temp_buffer);
            // Send Wait
            Log.e("aaaaaaaaa", "Send Wait");
            for (wait_counter = 0; wait_counter < 5; wait_counter++) {
                if (packet_send == true) {
                    break;
                }
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        final_packet = false;
        ble_status = FREE;
    }

    int err_count = 0;
    int send_data_pointer_save = 0;
    Boolean first_packet_save = false;


    /**
     * @brief writeRXCharacteristic
     */
    public void writeRXCharacteristic(byte[] value) {


        if (mBluetoothGatt == null) {
            Log.e("aaaaaaaaa", "mBluetoothGatt == null");
            return;
        }
        BluetoothGattService RxService = mBluetoothGatt
                .getService(MyConstants.RX_SERVICE_UUID);
        if (RxService == null) {
            Log.e("aaaaaaaaa", "RxService == null");
            return;
        }

        BluetoothGattCharacteristic RxChar = RxService
                .getCharacteristic(MyConstants.RX_CHAR_UUID);
        if (RxChar == null) {
            Log.e("aaaaaaaaa", "RxChar == null");
            return;
        }

        String service = RxChar.getService().getUuid().toString();
        String uuid = RxChar.getUuid().toString();

       Log.e(TAG , " uuid service " + service
                + " uuid   " + uuid
        );

        //6e400001-b5a3-f393-e0a9-e50e24dcca9e
        //6e400002-b5a3-f393-e0a9-e50e24dcca9e


        write(mBleDevice, service, uuid, value, true, new BleWriteCallback() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
               Log.e(TAG , " write success -->  current:" + current
                        + "total" + total + "justWrite" + HexUtil.formatHexString(justWrite, true));

            }
            @Override
            public void onWriteFailure(BleException exception) {
               Log.e(TAG , " write exception -->" + exception.toString());
                if (err_count < 3) {
                    err_count++;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    send_data_pointer = send_data_pointer_save;
                    first_packet = first_packet_save;
                    packet_counter--;
                }
            }

        });
    }


    public static MyBleUtils bUtils;

    public static MyBleUtils getInstance() {
        if (bUtils == null) {
            bUtils = new MyBleUtils();
        }
        return bUtils;
    }

}
