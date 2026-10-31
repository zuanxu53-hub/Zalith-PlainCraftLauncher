/*
 * Zalith Launcher 2
 *
 * 启动器设置向 LayerController 传递的运行时开关。
 *
 * LayerController 是独立模块，不能反向依赖 app 的 AllSettings；
 * 由游戏界面在组合时把用户设置写进来，摇杆逻辑读取。
 */

package com.movtery.layer_controller

object LayerControllerConfig {
    /** 摇杆推到上方松手后是否锁定持续前进。默认关闭，避免误触后一直走。 */
    @Volatile
    var joystickForwardLockEnabled: Boolean = false
}
