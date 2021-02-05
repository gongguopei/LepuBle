package com.lepu.blepro

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.SparseArray
import com.lepu.blepro.base.BleInterface
import com.lepu.blepro.ble.BpmBleInterface
import com.lepu.blepro.ble.service.BleService
import com.lepu.blepro.constants.Ble
import com.lepu.blepro.objs.Bluetooth
import com.lepu.blepro.observer.BleChangeObserver
import com.lepu.blepro.observer.BleServiceObserver
import com.lepu.blepro.utils.LepuBleLog

/**
 * 单例的蓝牙服务帮助类，原则上只通过此类开放API
 *
 * 1. 在Application onCreate()中初始化，完成必须配置(modelConfig、runRtConfig)后通过initService()开启服务#BleService。
 *
 *
 */
class BleServiceHelper private constructor() {

    /**
     * 下载数据的保存路径，key为model
     */
    var rawFolder: SparseArray<String>? = null

    /**
     * 服务onServiceConnected()时，应该初始化的model配置。必须在initService()之前完成
     * key为model
     */
    var modelConfig: SparseArray<Int> = SparseArray()
    /**
     * 服务onServiceConnected()时，应该初始化的model配置。必须在initService()之前完成
     * key为model
     */
    var runRtConfig: SparseArray<Boolean> = SparseArray()

    /**
     * 多设备模式手动重连中
     */
    var isReconnectingMulti: Boolean = false

    companion object {
        const val tag: String = "BleServiceHelper"

        val BleServiceHelper: BleServiceHelper by lazy {
            BleServiceHelper()
        }

    }

    lateinit var bleService: BleService
    private val bleConn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            LepuBleLog.d("BleServiceHelper onServiceConnected")
            if (p1 is BleService.BleBinder) {
                BleServiceHelper.bleService = p1.getService()
                LepuBleLog.d("bleService inited")
                initCurrentFace()

            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            LepuBleLog.d("BleServiceHelper onServiceDisconnected")
        }
    }
    //===========================================================

    /**
     * 在Application onCreate中初始化本单列,
     *
     */
    fun initService(application: Application, observer: BleServiceObserver?): BleServiceHelper {

        LepuBleLog.d("BleServiceHelper initService  start")
        BleService.observer = observer
        BleService.startService(application)

        Intent(application, BleService::class.java).also { intent ->
            application.bindService(intent, bleConn, Context.BIND_AUTO_CREATE)
        }
        return this
    }

    fun initLog(log: Boolean): BleServiceHelper{
        LepuBleLog.setDebug(log)
        LepuBleLog.d(log.toString())
        return this
    }



    fun initRawFolder(folders: SparseArray<String>): BleServiceHelper{
        this.rawFolder = folders
        LepuBleLog.d(folders.toString())
        return this
    }
    fun initModelConfig(modelConfig: SparseArray<Int>): BleServiceHelper{
        this.modelConfig = modelConfig
        return this
    }
    fun initRtConfig(runRtConfig: SparseArray<Boolean>): BleServiceHelper{
        this.runRtConfig = runRtConfig
        return this
    }

    /**
     * 服务连接成功时初始化vailFace
     * @return BleServiceHelper
     */
    private fun initCurrentFace(): BleServiceHelper{
        if (this::bleService.isInitialized ) {

            LepuBleLog.d("initVailFace", "${modelConfig.size()}")
            for (i in 0 until modelConfig.size()) {

                val model = modelConfig.get(modelConfig.keyAt(i))
                runRtConfig.get(model)?.let {
                    LepuBleLog.d("setInterfaces ===== ", "$it")
                    setInterfaces(model, it)
                } ?: setInterfaces(model)

            }
        }else{
            LepuBleLog.d("initVailFace failed!!!")
        }
        return this
    }



    /**
     * 当前要设置的设备Model, 必须在initService 之后调用
     */
    fun setInterfaces(model: Int, runRtImmediately: Boolean = false) {
        if (!check()) return
        LepuBleLog.d(tag, "setInterfaces")
        if (getInterface(model) == null) bleService.initInterfaces(model, runRtImmediately)
    }



    /**
     * 重新初始化蓝牙
     * 场景：蓝牙关闭状态进入页面，开启系统蓝牙后，重新初始化
     */
    fun reInitBle(): BleServiceHelper {
        if (!check()) return this
        BleServiceHelper.bleService.reInitBle()
        return this
    }



    /**
     * 注册蓝牙状态改变的监听
     *
     */
    internal fun subscribeBI(model: Int, observer: BleChangeObserver) {
        if (!check()){
            LepuBleLog.d("bleService.isInitialized  = false")
            return
        }
        getInterface(model)?.onSubscribe(observer)
    }




    /**
     * 注销蓝牙状态改变的监听
     */
    internal fun detachBI(model: Int, observer: BleChangeObserver) {
        if (check()) getInterface(model)?.detach(observer)
    }


    /**
     * 开始扫描 单设备
     * @param scanModel Int
     * @param needPair Boolean
     */
    @JvmOverloads
    fun startScan(scanModel: Int, needPair: Boolean = false) {
        if (!check()) return
        bleService.startDiscover(intArrayOf(scanModel), needPair)
    }

    /**
     * 开始扫描 多设备
     */
    @JvmOverloads
    fun startScan(scanModel: IntArray, needPair: Boolean = false) {
        if (!check()) return
        bleService.startDiscover(scanModel, needPair)
    }


    /**
     * 停止扫描
     * 连接之前调用该方法，并会重置扫描条件为默认值
     * 组合套装时,targetModel 会被重置为末尾添加的model
     *
     */
    fun stopScan() {
        if (check()) bleService.stopDiscover()
    }

    /**
     * 获取model的interface
     */
    fun getInterface(model: Int): BleInterface? {
        if (!check()) return null

        val vailFace = bleService.vailFace
        LepuBleLog.d(tag, "getInterface: getInterface => currentModel：$model, vailFaceSize：${vailFace.size()}, curIsNUll = ${vailFace.get(model) == null}")
        return vailFace.get(model)
    }

    /**
     * 获取服务中所有的interface
     * @return SparseArray<BleInterface>?
     */
    fun getInterfaces(): SparseArray<BleInterface>? {
        if (!check())  return null
        return bleService.vailFace
    }

    /**
     * 发起连接，必须先停止扫描
     */
    fun connect(context: Context,model: Int, b: BluetoothDevice, isAutoReconnect: Boolean = true) {
        if (!check()) return

        LepuBleLog.d(tag, "connect")
        getInterface(model)?.let {
            stopScan()
            it.connect(context, b, isAutoReconnect)
        }
    }




    /**
     *  发起重连 允许扫描多个设备
     */
    fun reconnect(scanModel: IntArray, name: Array<String>) {
        LepuBleLog.d(tag, "into reconnect " )
        if (!check()) return
        bleService.reconnect(scanModel, name)

    }

    /**
     * 发起重连
     * @param scanModel Int
     * @param name String
     */
    fun reconnect(scanModel: Int, name: String) {
        LepuBleLog.d(tag, "into reconnect" )
        if (!check()) return
        bleService.reconnect(intArrayOf(scanModel), arrayOf(name))

    }

    /**
     * 全部断开连接
     */
    fun disconnect(autoReconnect: Boolean) {
        LepuBleLog.d(tag, "into disconnect" )

        if (!check()) return
        val vailFace = bleService.vailFace
        for (i in 0 until vailFace.size()) {
            getInterface(vailFace.keyAt(i))?.let { it ->
                stopScan()
                it.disconnect(autoReconnect)
            }
        }
    }

    /**
     * 断开指定model
     */
    fun disconnect(model: Int, autoReconnect: Boolean) {
        LepuBleLog.d(tag, "into disconnect" )
        if (!check()) return
        getInterface(model)?.let {
            stopScan()
            it.disconnect(autoReconnect)
        }

    }


    /**
     * 主动获取当前蓝牙连接状态
     */
    fun getConnectState(model: Int): Int {
        if (!check()) return Ble.State.UNKNOWN
        getInterface(model)?.let {
            return it.calBleState()
        }?: return  Ble.State.UNKNOWN

    }


    /**
     * 是否存在未连接状态的interface
     * @param model IntArray
     * @return Boolean
     */
    fun hasUnConnected(model: IntArray): Boolean{
        if (!check()) return false
        LepuBleLog.d(tag, "into hasUnConnected...")
        for (m in model){
            getConnectState(m).let {
                LepuBleLog.d(tag, "$it")
                if (it == Ble.State.DISCONNECTED) return true
            }
        }
        LepuBleLog.d(tag, "没有未连接设备")
        return false
    }


    /**
     * 获取主机信息
     */
    fun getInfo(model: Int) {
        if (!check()) return
        getInterface(model)?.getInfo()

    }


    fun getFileList(model: Int){
        when(model){
            Bluetooth.MODEL_ER1 and Bluetooth.MODEL_ER2 ->{
                getInterface(model)?.getFileList()
            }
            else -> LepuBleLog.d(tag, "getFileList, model$model,未被允许获取文件列表")
        }
    }

    /**
     * 读取主机文件
     */
    fun readFile(userId: String, fileName: String, model: Int) {
        if (!check()) return
        getInterface(model)?.readFile(userId, fileName)

    }

    /**
     * 重置主机
     */
    fun reset(model: Int) {
        if (!check()) return
        getInterface(model)?.resetDeviceInfo()

    }

    /**
     * 更新设备设置
     */
    fun updateSetting(model: Int, type: String, value: Any) {
        if (!check()) return
        getInterface(model)?.updateSetting(type, value)
    }

    /**
     * 同步时间
     */
    fun syncTime(model: Int) {
        if (!check()) return
        getInterface(model)?.syncTime()
    }

    /**
     * 设置实时任务的间隔时间
     */
    fun setRTDelayTime(model: Int, delayMillis: Long){
        if (!check()) return
        getInterface(model)?.delayMillis = delayMillis
    }



    /**
     * 开启实时任务
     */
    fun startRtTask(model: Int){
        if (!check()) return
        getInterface(model)?.runRtTask()
    }


    /**
     * 移除获取实时任务
     */
    fun stopRtTask(model: Int) {
        if (!check()) return
        getInterface(model)?.stopRtTask()
    }



    private fun check(): Boolean{
        if (!this::bleService.isInitialized){
            LepuBleLog.d("Error: bleService unInitialized")
            return false
        }
        return true
    }

    private fun checkState(inter: BleInterface): Boolean{
        if (!inter.state){
            LepuBleLog.d(tag, "Error:model:${inter.model}, ble state is ${inter.state}!!!")
            return false
        }
        return true
    }

    fun getBpmFileList(model: Int, map: HashMap<String, Any>){
        if (!check()) return
        if (model != Bluetooth.MODEL_BPM){
            LepuBleLog.d(tag,"getBpmFileList, 无效model：$model" )
            return
        }
        getInterface(model)?.let {
            if (checkState(it)) return
            (it as BpmBleInterface).getBpmFileList(model, map)

        }

    }



}