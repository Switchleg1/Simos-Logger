/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.app.simoslogger

import android.bluetooth.BluetoothGatt
import android.graphics.Color
import android.os.Environment
import java.util.*

// Message types sent from the BluetoothChatService Handler
enum class GUIMessage {
    STATE_CHANGE,
    TASK_CHANGE,
    TOAST,
    READ,
    ECU_INFO,
    CLEAR_DTC,
    READ_LOG,
    WRITE_LOG,
}

// Constants that indicate the current connection state
enum class BLEConnectionState {
    ERROR,
    NONE,
    CONNECTING,
    CONNECTED;

    var errorMessage: String = ""
    var deviceName: String = ""
}

//List of available tasks
enum class UDSTask {
    NONE,
    LOGGING,
    FLASHING,
    INFO,
    DTC
}

//BT functions
enum class BTServiceTask {
    STOP_SERVICE,
    START_SERVICE,
    DO_CONNECT,
    DO_DISCONNECT,
    DO_START_LOG,
    DO_START_FLASH,
    DO_GET_INFO,
    DO_CLEAR_DTC,
    DO_STOP_TASK
}

//Intent constants
enum class RequiredPermissions {
    LOCATION,
    READ_STORAGE,
    WRITE_STORAGE,
}

//ISOTP bridge command flags
enum class BLECommandFlags(val value: Int) {
    PER_ENABLE(1),
    PER_CLEAR(2),
    PER_ADD(4),
    SPLIT_PK(8),
    SET_GET(64),
    SETTINGS(128)
}

//ISOTP bridge internal settings
enum class BLESettings(val value: Int) {
    ISOTP_STMIN(1),
    LED_COLOR(2),
    PERSIST_DELAY(3),
    PERSIST_Q_DELAY(4),
    BLE_SEND_DELAY(5),
    BLE_MULTI_DELAY(6)
}

//Color List
enum class ColorList(var value: Int) {
    BG_NORMAL(Color.rgb(255, 255, 255)),
    BG_WARN(Color.rgb(127, 127, 255)),
    TEXT(Color.rgb(0,   0,   0)),
    BAR_NORMAL(Color.rgb(0,   255, 0)),
    BAR_WARN(Color.rgb(255, 0,   0)),
    ST_ERROR(Color.rgb(255, 0,   0)),
    ST_NONE(Color.rgb(100, 0,   255)),
    ST_CONNECTING(Color.rgb(100, 100, 255)),
    ST_CONNECTED(Color.rgb(0,   0,   255)),
    ST_LOGGING(Color.rgb(255, 255, 0)),
    ST_WRITING(Color.rgb(0,   255, 0))
}

//Logging modes
enum class UDSLoggingMode(val value: String) {
    MODE_22("22"),
    MODE_3E("3E")
}

//PID Index
enum class PIDIndex {
    A,
    B,
    C
}

// UDS return codes
enum class UDSReturn {
    OK,
    ERROR_RESPONSE,
    ERROR_NULL,
    ERROR_HEADER,
    ERROR_CMDSIZE,
    ERROR_UNKNOWN,
}

enum class GearRatios(val gear: String, var ratio: Float) {
    GEAR1("1", 2.92f),
    GEAR2("2",1.79f),
    GEAR3("3",1.14f),
    GEAR4("4",0.78f),
    GEAR5("5",0.58f),
    GEAR6("6",0.46f),
    GEAR7("7",0.0f),
    FINAL("Final",4.77f)
}

val TASK_END_DELAY              = 500
val TASK_END_TIMEOUT            = 3000

//Service info
val CHANNEL_ID                  = "BTService"
val CHANNEL_NAME                = "BTService"

//BLE settings
val BLE_DEVICE_NAME             = "BLE_TO_ISOTP"
val BLE_GATT_MTU_SIZE           = 512
val BLE_SCAN_PERIOD             = 5000L
val BLE_CONNECTION_PRIORITY     = BluetoothGatt.CONNECTION_PRIORITY_HIGH
val BLE_THREAD_PRIORITY         = 5 //Priority (max is 10)

//ISOTP bridge UUIDS
val BLE_CCCD_UUID               = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
val BLE_SERVICE_UUID            = UUID.fromString("0000abf0-0000-1000-8000-00805f9b34fb")
val BLE_DATA_TX_UUID            = UUID.fromString("0000abf1-0000-1000-8000-00805f9b34fb")
val BLE_DATA_RX_UUID            = UUID.fromString("0000abf2-0000-1000-8000-00805f9b34fb")
val BLE_CMD_TX_UUID             = UUID.fromString("0000abf3-0000-1000-8000-00805f9b34fb")
val BLE_CMD_RX_UUID             = UUID.fromString("0000abf4-0000-1000-8000-00805f9b34fb")

//ISOTP bridge BLE header defaults
val BLE_HEADER_ID               = 0xF1
val BLE_HEADER_PT               = 0xF2
val BLE_HEADER_RX               = 0x7E8
val BLE_HEADER_TX               = 0x7E0

//CSV PID Bitmask
val CSV_22_ADD_MIN              = 0x1000.toLong()
val CSV_22_ADD_MAX              = 0xFFFF.toLong()
val CSV_3E_ADD_MIN              = 0x10000000.toLong()
val CSV_3E_ADD_MAX              = 0xFFFFFFFF

val MAX_PIDS                    = 100
val CSV_CFG_LINE                = "Name,Unit,Equation,Format,Address,Length,Signed,ProgMin,ProgMax,WarnMin,WarnMax,Smoothing"
val CSV_VALUE_COUNT             = 12
val CFG_FILENAME                = "config.cfg"
val DEBUG_FILENAME              = "debug.log"

//Log files
val LOG_NONE                    = 0
val LOG_INFO                    = 1
val LOG_WARNING                 = 2
val LOG_DEBUG                   = 4
val LOG_EXCEPTION               = 8
val LOG_COMMUNICATIONS          = 16

//Default settings
val DEFAULT_KEEP_SCREEN_ON      = true
val DEFAULT_INVERT_CRUISE       = false
val DEFAULT_UPDATE_RATE         = 4
val DEFAULT_DIRECTORY           = Environment.DIRECTORY_DOWNLOADS
val DEFAULT_PERSIST_DELAY       = 20
val DEFAULT_PERSIST_Q_DELAY     = 10
val DEFAULT_CALCULATE_HP        = true
val DEFAULT_USE_MS2             = true
val DEFAULT_TIRE_DIAMETER       = 0.632f
val DEFAULT_CURB_WEIGHT         = 1500f
val DEFAULT_DRAG_COEFFICIENT    = 0.000003
val DEFAULT_ALWAYS_PORTRAIT     = false
val DEFAULT_DISPLAY_SIZE        = 1f
val DEFAULT_LOG_FLAGS           = LOG_INFO or LOG_WARNING or LOG_EXCEPTION

//TQ/HP Calculations
val KG_TO_N                     = 9.80665f
val TQ_CONSTANT                 = 16.3f

//Additional properties
infix fun Byte.shl(that: Int): Int = this.toInt().shl(that)
infix fun Short.shl(that: Int): Int = this.toInt().shl(that)
infix fun Byte.shr(that: Int): Int = this.toInt().shr(that)
infix fun Short.shr(that: Int): Int = this.toInt().shr(that)
infix fun Byte.and(that: Int): Int = this.toInt().and(that)
infix fun Short.and(that: Int): Int = this.toInt().and(that)
fun Byte.toHex(): String = "%02x".format(this)
fun Byte.toHexS(): String = " %02x".format(this)
fun Short.toHex(): String = "%04x".format(this)
fun Int.toHex(): String = "%08x".format(this)
fun Int.toColorInverse(): Int = Color.WHITE xor this or 0xFF000000.toInt()
fun Int.toColorHex(): String = "%06x".format(this and 0xFFFFFF)
fun Int.toTwo(): String = "%02d".format(this)
fun Int.toArray2(): ByteArray = byteArrayOf((this and 0xFF00 shr 8).toByte(), (this and 0xFF).toByte())
fun Long.toColorInt(): Int = (this.toInt() and 0xFFFFFF) or 0xFF000000.toInt()
fun Long.toHex2(): String = "%04x".format(this)
fun Long.toHex4(): String = "%08x".format(this)
fun Long.toHex(): String = "%16x".format(this)
fun Long.toArray4(): ByteArray = byteArrayOf((this and 0xFF000000 shr 24).toByte(), (this and 0xFF0000 shr 16).toByte(), (this and 0xFF00 shr 8).toByte(), (this and 0xFF).toByte())
fun ByteArray.toHex(): String = joinToString(separator = " ") { eachByte -> "%02x".format(eachByte) }