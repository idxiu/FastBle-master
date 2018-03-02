package com.clj.blesample;

import java.util.UUID;

/**
 * site:istarvip.cn
 *
 * @author：istarvip on 2018/3/2 14:45
 * 邮箱：917017530@qq.com
 * FIXME
 */

public class MyConstants {

    public final static String ACTION_DATA_AVAILABLE = "ACTION_DATA_AVAILABLE";
    public final static String ACTION_SEND_DATA_TO_BLE = "ACTION_SEND_DATA_TO_BLE";
    public final static String EXTRA_SEND_DATA_TO_BLE = "EXTRA_SEND_DATA_TO_BLE";

    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    public static final UUID CCCD = UUID
            .fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID
            .fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID
            .fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID
            .fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
}
