/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「插件自定义页面」协议 —— 启动器侧的第二套扩展点。
 *
 * 第一套是 `ui.json`（皮肤：改颜色、改材质），它只能动**已有**界面的长相。
 * 这一套解决的是另一个问题：插件想往启动器里**加东西**。
 *
 * 插件在自己的 APK 里放 `assets/zplugin/pages.json`：
 * ```json
 * {
 *   "pages": [
 *     {
 *       "id": "hello",
 *       "title": "示例页面",
 *       "left": ["说明", "动作"],
 *       "blocks": [
 *         { "type": "title", "text": "你好" },
 *         { "type": "text",  "text": "这一页是插件加的。", "muted": true },
 *         { "type": "card",  "title": "卡片", "blocks": [
 *             { "type": "text", "text": "卡片里的内容" },
 *             { "type": "button", "text": "点我",
 *               "action": { "type": "hint", "text": "点到了" } }
 *         ]}
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * 为什么是「积木」而不是让插件写 Compose：
 * 插件是数据包，启动器从不加载它的 dex（见 `ZlPluginManager` 的说明）。
 * 所以插件能表达的只有**被启动器认识的积木**；加一种新积木要改启动器，
 * 但改色、改内容、拼页面都不用。
 *
 * 安全边界：积木只有「显示」和「跳转 / 提示」两类动作，没有文件读写、
 * 没有任意代码执行 —— 插件拿不到比它本来就能做的更多东西。
 */

package com.zalith.pcl.ui.pcl2.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/** 插件的一个页面。 */
data class PclPluginPageSpec(
    val id: String,
    val title: String,
    /** 左栏条目（可选）；为空时这一页没有左栏。 */
    val leftLabels: List<String> = emptyList(),
    val blocks: List<PclPluginBlock> = emptyList(),
)

/** 页面内容的基本单元。 */
sealed interface PclPluginBlock {
    /** 段落标题（比正文大一号、加粗）。 */
    data class Title(val text: String) : PclPluginBlock

    /** 正文。[muted] = 次要信息（灰一点）。 */
    data class Text(val text: String, val muted: Boolean = false) : PclPluginBlock

    /** 卡片容器，可以嵌套任意积木。 */
    data class Card(val title: String?, val blocks: List<PclPluginBlock>) : PclPluginBlock

    /** 横排。 */
    data class Row(val blocks: List<PclPluginBlock>) : PclPluginBlock

    /** 按钮。 */
    data class Button(val text: String, val action: PclPluginAction?) : PclPluginBlock

    /** 开关。状态由插件页面自己保存（运行期），不落盘。 */
    data class Switch(
        val text: String,
        val checked: Boolean,
        val action: PclPluginAction?,
    ) : PclPluginBlock

    /** 分割线。 */
    data object Divider : PclPluginBlock

    /** 竖向留白，[height] 单位 dp（0..200）。 */
    data class Space(val height: Int) : PclPluginBlock
}

/** 积木能触发的动作。 */
sealed interface PclPluginAction {
    /** 弹一条底部提示。 */
    data class Hint(val text: String) : PclPluginAction

    /** 打开另一个插件页面（同一次 pages.json 里的 id）。 */
    data class Page(val id: String) : PclPluginAction

    /** 切到启动器的内置版块（`home` / `download` / `link` / `setup` / `other`）。 */
    data class Section(val value: String) : PclPluginAction
}

/* ---------------------------------------------------------------- 解析 */

private data class ActionJson(
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("value") val value: String? = null,
)

private data class BlockJson(
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("muted") val muted: Boolean? = null,
    @SerializedName("checked") val checked: Boolean? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("blocks") val blocks: List<BlockJson>? = null,
    @SerializedName("action") val action: ActionJson? = null,
)

private data class PageJson(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("left") val left: List<String>? = null,
    @SerializedName("blocks") val blocks: List<BlockJson>? = null,
)

private data class PagesJson(
    @SerializedName("pages") val pages: List<PageJson>? = null,
)

private val pagesGson = Gson()

/** 单个页面允许的积木总数，防止插件写出一棵把界面撑爆的树。 */
private const val MAX_BLOCKS = 400

/** 解析 `pages.json`；解析失败返回空表（插件写错不该让启动器崩）。 */
fun parsePclPluginPages(json: String): List<PclPluginPageSpec> {
    val raw = runCatching { pagesGson.fromJson(json, PagesJson::class.java) }.getOrNull()
    val pages = raw?.pages ?: return emptyList()
    var budget = MAX_BLOCKS
    return pages.mapIndexedNotNull { index, page ->
        val title = page.title?.trim().takeUnless { it.isNullOrBlank() } ?: return@mapIndexedNotNull null
        val id = page.id?.trim().takeUnless { it.isNullOrBlank() } ?: "page-$index"
        PclPluginPageSpec(
            id = id,
            title = title,
            leftLabels = page.left?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
            blocks = page.blocks.orEmpty().mapNotNull { block ->
                if (budget <= 0) return@mapNotNull null
                budget--
                block.toBlock()
            },
        )
    }
}

private fun BlockJson.toBlock(): PclPluginBlock? = when (type?.trim()?.lowercase()) {
    "title", "heading" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginBlock.Title(it) }
    "text", "paragraph" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Text(it, muted = muted == true)
    }
    "card" -> PclPluginBlock.Card(
        title = title?.trim()?.takeIf { it.isNotEmpty() },
        blocks = blocks.orEmpty().mapNotNull { it.toBlock() },
    )
    "row" -> PclPluginBlock.Row(blocks.orEmpty().mapNotNull { it.toBlock() })
    "button" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Button(it, action?.toAction())
    }
    "switch", "toggle" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let {
        PclPluginBlock.Switch(it, checked == true, action?.toAction())
    }
    "divider", "line" -> PclPluginBlock.Divider
    "space", "gap" -> PclPluginBlock.Space((height ?: 12).coerceIn(0, 200))
    else -> null
}

private fun ActionJson.toAction(): PclPluginAction? = when (type?.trim()?.lowercase()) {
    "hint", "toast" -> text?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.Hint(it) }
    "page" -> id?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.Page(it) }
    "section" -> value?.trim()?.takeIf { it.isNotEmpty() }?.let { PclPluginAction.Section(it) }
    else -> null
}
