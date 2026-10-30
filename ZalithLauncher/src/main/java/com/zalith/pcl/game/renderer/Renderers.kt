/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.zalith.pcl.game.renderer

import com.zalith.pcl.game.renderer.renderers.FreedrenoRenderer
import com.zalith.pcl.game.renderer.renderers.GL4ESRenderer
import com.zalith.pcl.game.renderer.renderers.KopperZinkRenderer
import com.zalith.pcl.game.renderer.renderers.NGGL4ESRenderer
import com.zalith.pcl.game.renderer.renderers.PanfrostRenderer
import com.zalith.pcl.game.renderer.renderers.VirGLRenderer
import com.zalith.pcl.utils.logging.Logger

private const val TAG = "Renderers"

/**
 * 启动器所有渲染器总管理者，启动器内置的渲染器与渲染器插件加载的渲染器，都会加载到这里
 */
object Renderers {
    private val renderers: MutableList<RendererInterface> = mutableListOf()
    private var currentRenderer: RendererInterface? = null
    private var isInitialized: Boolean = false

    fun init(
        reset: Boolean = false
    ) {
        if (isInitialized && !reset) return
        isInitialized = true

        if (reset) {
            renderers.clear()
            currentRenderer = null
        }

        addRenderers(
            NGGL4ESRenderer,
            GL4ESRenderer,
            KopperZinkRenderer,
            VirGLRenderer,
            FreedrenoRenderer,
            PanfrostRenderer
        )
    }

    /**
     * 获取当前的渲染器列表
     */
    fun getRenderers(): List<RendererInterface> = renderers

    /**
     * 加入一些渲染器
     */
    fun addRenderers(vararg renderers: RendererInterface) {
        renderers.forEach { renderer ->
            addRenderer(renderer)
        }
    }

    /**
     * 加入单个渲染器
     */
    fun addRenderer(renderer: RendererInterface): Boolean {
        return if (renderers.any { it.getUniqueIdentifier() == renderer.getUniqueIdentifier() }) {
            Logger.warning(TAG, "The unique identifier of this renderer (${renderer.getRendererName()} - ${renderer.getUniqueIdentifier()}) conflicts with an already loaded renderer. " +
                    "Normally, this shouldn't happen. You deliberately caused this conflict, didn't you, user?")
            false
        } else {
            renderers.add(renderer)
            Logger.info(TAG, "Renderer loaded: ${renderer.getRendererName()} (${renderer.getRendererId()} - ${renderer.getUniqueIdentifier()})")
            true
        }
    }

    /**
     * 设置当前的渲染器
     * @param uniqueIdentifier 渲染器的唯一标识符，用于找到当前想要设置的渲染器
     * @param retryToFirstOnFailure 如果未找到匹配的渲染器，是否跳回渲染器列表的首个渲染器
     */
    fun setCurrentRenderer(uniqueIdentifier: String, retryToFirstOnFailure: Boolean = true) {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        currentRenderer = renderers.find { it.getUniqueIdentifier() == uniqueIdentifier } ?: run {
            if (retryToFirstOnFailure) {
                val renderer = renderers[0]
                //空串就是设置里的「自动选择」，不是「写了但找不到」，别用 warning 吓人
                if (uniqueIdentifier.isEmpty()) {
                    Logger.info(TAG, "渲染器为「自动选择」，本次使用 ${renderer.getRendererName()}")
                } else {
                    Logger.warning(TAG, "Incompatible renderer $uniqueIdentifier will be replaced with ${renderer.getUniqueIdentifier()} (${renderer.getRendererName()})")
                }
                renderer
            } else null
        }
    }

    /**
     * 获取当前的渲染器
     */
    fun getCurrentRenderer(): RendererInterface {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        return currentRenderer ?: throw IllegalStateException("Current renderer not set")
    }

    /**
     * 当前是否设置了渲染器
     */
    fun isCurrentRendererValid(): Boolean = isInitialized && currentRenderer != null

    /**
     * 默认渲染器唯一标识。
     *
     * 就是注册顺序里的第一个（见 [init] 的 `addRenderers`），也正是
     * `setCurrentRenderer("")` 会回落到的那一个 —— 所以「没设置」和「默认」
     * 本来就是同一个东西，界面上不必再单列一项「自动选择」。
     *
     * 调用前会确保渲染器表已初始化；表真的空了才退回空串。
     */
    fun getDefaultRendererId(): String {
        if (!isInitialized) init()
        if (renderers.isEmpty()) init(reset = true)
        return renderers.firstOrNull()?.getUniqueIdentifier().orEmpty()
    }
}