/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 各页面左栏的条目标题清单。
 *
 * **存在的唯一理由**：PCL 的左栏没有固定宽度 —— `FormMain.xaml` 里 `PanMain` 的第一列是
 * `Auto`，`MyPageLeft` 自身不设宽，所以左栏宽度 = **最宽条目的固有宽度**。
 * 外壳（[com.zalith.pcl.ui.pcl2.PclBody]）需要这份清单才能把宽度算出来，
 * 于是把它单独放一处，左右两边引用同一批常量，避免两处各写一份。
 *
 * 各左栏的正文请继续使用这些常量（不要另写字符串），改条目时只改这里。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import com.zalith.pcl.ui.pcl2.PclStrings

/**
 * 左栏条目文案（与 PCL 的语言文件一一对应）。
 *
 * 注意：分组标签（「社区资源」这种小灰字）不参与宽度计算 —— 它没有图标列，
 * 且永远比同栏的条目短（见 `MyListItem`：条目至少多出 4+34+4+35 的固定宽度）。
 */
object PclLeftLabels {

    // ------------------------------------------------------------ 「版本管理」（PageInstanceLeft）

    val instanceOverview = PclStrings.LangPageVersionLeftOverview
    val instanceSetup = PclStrings.LangPageVersionLeftSetting
    val instanceMod = PclStrings.LangPageVersionLeftModManagement
    val instanceExport = PclStrings.LangPageVersionLeftInstanceExport

    // ---- 「游戏资源」分组（PCL CE 的 PageInstanceLeft 在这一组里塞了七个条目）
    val instanceSaves = "存档"
    val instanceScreenshot = "截图"
    val instanceResourcepack = PclStrings.LangDownloadPageLeftResourcepack
    val instanceShader = PclStrings.LangDownloadPageLeftShader
    val instanceSchematic = "投影原理图"
    val instanceServer = "服务器"

    /** 分组标签文案（`PageInstanceLeft` 里「游戏资源」那行 12 号小灰字）。 */
    val instanceResourceGroup = "游戏资源"

    /**
     * 顺序与 PCL CE 的 `PageInstanceLeft` 一致：
     * 概览 / 设置 →「游戏资源」分组（存档、截图、模组、资源包、光影包、投影原理图、服务器）→ 导出。
     */
    val instance = listOf(
        instanceOverview, instanceSetup,
        instanceSaves, instanceScreenshot, instanceMod,
        instanceResourcepack, instanceShader, instanceSchematic, instanceServer,
        instanceExport,
    )

    // ------------------------------------------------------------ 「下载」（PageDownloadLeft）

    val downloadGame = PclStrings.LangDownloadPageLeftAutoInstall
    val downloadMod = PclStrings.LangDownloadPageLeftMods
    val downloadModpack = PclStrings.LangDownloadPageLeftModpack
    val downloadSave = "存档" // PCL 是「数据包」，Zalith 的资源平台只有存档
    val downloadResourcepack = PclStrings.LangDownloadPageLeftResourcepack
    val downloadShader = PclStrings.LangDownloadPageLeftShader

    val download = listOf(
        downloadGame,
        downloadMod,
        downloadModpack,
        downloadSave,
        downloadResourcepack,
        downloadShader,
    )

    // ------------------------------------------------------------ 「设置」（PageSetupLeft）

    val setupGame = PclStrings.LangPageSetupLeftGame
    val setupLink = PclStrings.LangPageSetupLeftLink
    val setupPersonalize = PclStrings.LangPageSetupLeftPersonalize
    val setupLaunch = PclStrings.LangPageSetupLeftLaunch

    // Zalith 原生独有的四张设置页（PCL2 只有上面四页）。
    // 标签文案直接沿用原生 `settings_tab_*` 的中文写法，不再另起名字。
    val setupControl = "控制设置"
    val setupGamepad = "手柄设置"
    val setupJava = "Java 管理"
    val setupControlManage = "控制管理"
    val setupPlugin = "插件管理"

    val setup = listOf(
        setupGame,
        setupLink,
        setupPersonalize,
        setupLaunch,
        setupControl,
        setupGamepad,
        setupJava,
        setupControlManage,
        setupPlugin,
    )

    // ------------------------------------------------------------ 「更多」（PageOtherLeft）

    val otherHelp = PclStrings.LangPageOtherLeftHelp
    val otherAbout = PclStrings.LangPageOtherLeftAbout
    val otherTools = PclStrings.LangPageOtherLeftTools
    val otherFeedback = PclStrings.LangPageOtherLeftFeedback
    val otherVoteNew = PclStrings.LangPageOtherLeftVoteNew

    val other = listOf(otherHelp, otherAbout, otherTools, otherFeedback, otherVoteNew)

    // ------------------------------------------------------------ 「版本选择」（PageSelectLeft）

    /**
     * `PageSelectLeft.xaml:12` 把左栏写成了
     * `MyScrollViewer MinWidth="250" MaxWidth="300"`，
     * 所以这一页**不按内容算**，直接在 `pclVersionSelectPage()` 里钉 250。
     */
    const val selectPanelWidth = 250
}
