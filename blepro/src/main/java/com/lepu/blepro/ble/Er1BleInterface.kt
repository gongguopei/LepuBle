package com.lepu.blepro.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.jeremyliao.liveeventbus.LiveEventBus
import com.lepu.blepro.base.BleInterface
import com.lepu.blepro.ble.cmd.BleCRC
import com.lepu.blepro.ble.cmd.Er1BleResponse
import com.lepu.blepro.ble.cmd.UniversalBleCmd
import com.lepu.blepro.ble.data.Er1DataController
import com.lepu.blepro.ble.data.LepuDevice
import com.lepu.blepro.event.EventMsgConst
import com.lepu.blepro.event.InterfaceEvent
import com.lepu.blepro.objs.Bluetooth
import com.lepu.blepro.utils.LepuBleLog
import com.lepu.blepro.utils.toUInt
import kotlin.experimental.inv

/**
 *
 * 蓝牙操作
 */

class Er1BleInterface(model: Int): BleInterface(model) {
    private val tag: String = "Er1BleInterface"

    override fun initManager(context: Context, device: BluetoothDevice) {
        manager = Er1BleManager(context)
        manager.setConnectionObserver(this)
        manager.setNotifyListener(this)
        manager.connect(device)
            .useAutoConnect(false)
            .timeout(10000)
            .retry(3, 100)
            .done {
                LepuBleLog.d(tag, "Device Init")
            }
            .enqueue()
    }



    /**
     * download a file, name come from filelist
     */
    var curFileName: String? = null
    var curFile: Er1BleResponse.Er1File? = null
    var fileList: Er1BleResponse.Er1FileList? = null
    private var userId: String? = null

    override fun readFile(userId: String, fileName: String) {
        this.userId = userId
        this.curFileName =fileName
        sendCmd(UniversalBleCmd.readFileStart(fileName.toByteArray(), 0))
    }

    @ExperimentalUnsignedTypes
    private fun onResponseReceived(response: Er1BleResponse.Er1Response) {
//        LepuBleLog.d(TAG, "received: ${response.cmd}")
        when(response.cmd) {
            UniversalBleCmd.GET_INFO -> {
                val info = LepuDevice(response.content)

                LepuBleLog.d(tag, "model:$model,GET_INFO => success")
                LiveEventBus.get(InterfaceEvent.ER1.EventEr1Info).post(InterfaceEvent(model, info))

                if (runRtImmediately) {
                    runRtTask()
                    runRtImmediately = false
                }

            }

            UniversalBleCmd.RT_DATA -> {
                val rtData = Er1BleResponse.RtData(response.content)

                Er1DataController.receive(rtData.wave.wFs)
                LepuBleLog.d(tag, "model:$model,RT_DATA => success")
                LiveEventBus.get(InterfaceEvent.ER1.EventEr1RtData).post(InterfaceEvent(model, rtData))
            }

            UniversalBleCmd.READ_FILE_LIST -> {
                fileList = Er1BleResponse.Er1FileList(response.content)
                LepuBleLog.d(tag, "model:$model,READ_FILE_LIST => success, ${fileList.toString()}")
                fileList?.let {
                    LiveEventBus.get(InterfaceEvent.ER1.EventEr1FileList).post(InterfaceEvent(model, it))
                }

            }

            UniversalBleCmd.READ_FILE_START -> {
                if (response.pkgType == 0x01.toByte()) {
                    curFile = userId?.let { Er1BleResponse.Er1File(model, curFileName!!, toUInt(response.content), it) }
                    sendCmd(UniversalBleCmd.readFileData(0))
                } else {
                    LepuBleLog.d(tag, "read file failed：${response.pkgType}")
                    LiveEventBus.get(InterfaceEvent.ER1.EventEr1ReadFileError).post(InterfaceEvent(model, true))

                }
            }

            UniversalBleCmd.READ_FILE_DATA -> {
                curFile?.apply {
                    this.addContent(response.content)
                    LepuBleLog.d(tag, "read file：${curFile?.fileName}   => ${curFile?.index} / ${curFile?.fileSize}")
                    LiveEventBus.get(InterfaceEvent.ER1.EventEr1ReadingFileProgress).post(InterfaceEvent(model, (curFile!!.index * 1000).div(curFile!!.fileSize) ))

                    if (this.index < this.fileSize) {
                        sendCmd(UniversalBleCmd.readFileData(this.index))
                    } else {
                        sendCmd(UniversalBleCmd.readFileEnd())
                    }
                }
            }

            UniversalBleCmd.READ_FILE_END -> {
                LepuBleLog.d(tag, "read file finished: ${curFile?.fileName} ==> ${curFile?.fileSize}")

                curFileName = null
                curFile?.let {
                    LiveEventBus.get(InterfaceEvent.ER1.EventEr1ReadFileComplete).post(InterfaceEvent(model, it))
                }?: LepuBleLog.d(tag, "model:$model,  curFile error!!")

                curFile = null
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun hasResponse(bytes: ByteArray?): ByteArray? {
        val bytesLeft: ByteArray? = bytes

        if (bytes == null || bytes.size < 8) {
            return bytes
        }

        loop@ for (i in 0 until bytes.size-7) {
            if (bytes[i] != 0xA5.toByte() || bytes[i+1] != bytes[i+2].inv()) {
                continue@loop
            }

            // need content length
            val len = toUInt(bytes.copyOfRange(i+5, i+7))
//            Log.d(TAG, "want bytes length: $len")
            if (i+8+len > bytes.size) {
                continue@loop
            }

            val temp: ByteArray = bytes.copyOfRange(i, i+8+len)
            if (temp.last() == BleCRC.calCRC8(temp)) {
                val bleResponse = Er1BleResponse.Er1Response(temp)
//                LepuBleLog.d(TAG, "get response: ${temp.toHex()}" )
                onResponseReceived(bleResponse)

                val tempBytes: ByteArray? = if (i+8+len == bytes.size) null else bytes.copyOfRange(i+8+len, bytes.size)

                return hasResponse(tempBytes)
            }
        }

        return bytesLeft
    }

    /**
     * get device info
     */
    override fun getInfo() {
        sendCmd(UniversalBleCmd.getInfo())
    }

    override fun syncTime() {
    }

    override fun updateSetting(type: String, value: Any) {

    }



    override fun resetDeviceInfo() {
    }

    /**
     * get real-time data
     */
    override fun getRtData() {
        sendCmd(UniversalBleCmd.getRtData())
    }


    /**
     * get file list
     */
    override fun getFileList() {
        sendCmd(UniversalBleCmd.getFileList())
    }


}