package com.lepu.blepro.ble

import android.content.Context
import com.lepu.blepro.base.BaseBleManager
import com.lepu.blepro.ble.cmd.bpm.BpmBleCmd
import com.lepu.blepro.utils.LepuBleLog

/**
 * author: wujuan
 * created on: 2021/2/5 15:14
 * description:
 */
class BpmBleManager(context: Context): BaseBleManager(context) {
    override fun init() {
        getInfo()
        LepuBleLog.d("BpmBleManager inited")
    }
    private fun getInfo() {
        sendCmd(BpmBleCmd.getCmd(BpmBleCmd.BPMCmd.MSG_TYPE_GET_INFO));
    }


}