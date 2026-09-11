package app.dswriter.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.dswriter.domain.model.Provider
import app.dswriter.domain.settings.DiscoveredModel
import app.dswriter.ui.theme.DSWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun provider(
        id: String = "p1",
        name: String = "本地 Qwen",
        baseUrl: String = "http://192.168.1.10:8080/v1",
        modelId: String = "novel-a",
    ) = Provider(id = id, name = name, baseUrl = baseUrl, modelId = modelId)

    @Test
    fun startsEmptyAndTeachesHowToAddAService() {
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(state = SettingsUiState(), onBack = {})
            }
        }

        composeRule.onNodeWithText("模型服务").assertExists()
        composeRule.onNodeWithText("还没有配置服务。点“添加服务”填一个接口地址即可开始。").assertExists()
        composeRule.onNodeWithText("添加服务").assertExists()
    }

    @Test
    fun listsServicesAndMarksTheActiveOne() {
        var activated: String? = null
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        providers = listOf(
                            provider(),
                            provider(
                                id = "p2",
                                name = "官方",
                                baseUrl = "https://api.example.com",
                                modelId = "m",
                            ),
                        ),
                        activeProviderId = "p1",
                    ),
                    onBack = {},
                    onActivateProvider = { activated = it },
                )
            }
        }

        composeRule.onNodeWithText("本地 Qwen").assertExists()
        composeRule.onNodeWithText("官方").assertExists()
        composeRule.onNodeWithText("当前使用").assertExists()
        composeRule.onNodeWithText("设为当前").performScrollTo().performClick()
        assertEquals("p2", activated)
    }

    @Test
    fun reportsAServiceThatHasNoModelYet() {
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        providers = listOf(provider(modelId = "")),
                        activeProviderId = null,
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("未填写模型名").assertExists()
    }

    @Test
    fun theFormExplainsBothAddressShapesAndCollectsTheModelName() {
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(draft = ProviderDraftState()),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("添加服务").assertExists()
        composeRule.onNodeWithText("接口地址").assertExists()
        composeRule.onNodeWithText("模型名").assertExists()
        composeRule.onNodeWithText(
            "公网服务填接口根地址(https)，例如 https://api.deepseek.com。\n" +
                "局域网服务填 http://内网IP:端口/v1，服务端需监听 0.0.0.0 并放行端口。",
        ).assertExists()
        composeRule.onNodeWithText("获取模型列表").assertExists()
        composeRule.onNodeWithText("支持图片").assertDoesNotExist()
    }

    /**
     * Regression: the save button used to live at the end of a form taller than the screen, so it
     * sat past the bottom edge and could not be tapped at all. It is in the pinned footer now, and
     * it must be assertable without any scrolling.
     */
    @Test
    fun saveIsReachableWithoutScrollingTheForm() {
        var saved = 0
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(draft = ProviderDraftState()),
                    onBack = {},
                    onSaveDraft = { saved++ },
                )
            }
        }

        composeRule.onNodeWithText("保存").performClick()
        composeRule.onNodeWithText("取消").assertExists()
        assertEquals(1, saved)
        // A brand-new service has no stored key, so there is nothing to test yet.
        composeRule.onNodeWithText("测试连接").assertDoesNotExist()
    }

    @Test
    fun tuningFieldsStayHiddenUntilAdvancedIsOpened() {
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(draft = ProviderDraftState()),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("高级设置").assertExists()
        composeRule.onNodeWithText("上下文长度").assertDoesNotExist()
        composeRule.onNodeWithText("单次输出上限").assertDoesNotExist()
        composeRule.onNodeWithText("temperature").assertDoesNotExist()

        composeRule.onNodeWithText("高级设置").performClick()

        composeRule.onNodeWithText("上下文长度").assertExists()
        composeRule.onNodeWithText("单次输出上限").assertExists()
        composeRule.onNodeWithText("temperature").assertExists()
        composeRule.onNodeWithText("支持图片").assertExists()
    }

    @Test
    fun aDiscoveredModelCanBePickedWithoutTypingIt() {
        var picked: String? = null
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        draft = ProviderDraftState(),
                        discoveryStatus = DiscoveryStatus.FOUND,
                        discoveredModels = listOf(DiscoveredModel("novel-a", "novel-a")),
                    ),
                    onBack = {},
                    onSelectDiscoveredModel = { picked = it },
                )
            }
        }

        composeRule.onNodeWithText("选一个填入模型名：").assertExists()
        composeRule.onNodeWithText("novel-a").performScrollTo().performClick()
        assertEquals("novel-a", picked)
    }

    @Test
    fun reportsBadInputInOneShortLine() {
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        draft = ProviderDraftState(),
                        status = SettingsStatus.NUMERIC_FIELD_INVALID,
                    ),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(
            "数值填错了：输出上限要大于 0，temperature 在 0 到 2 之间",
        ).assertExists()
    }

    @Test
    fun writingAssistanceIsOffByDefaultAndSaysSoInOneLine() {
        var toggled: Boolean? = null
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onBack = {},
                    onSuggestRecapChanged = { toggled = it },
                )
            }
        }

        composeRule.onNodeWithText("写作辅助").assertExists()
        composeRule.onNodeWithText("已关闭").assertExists()
        composeRule.onNodeWithText("这些开关默认关闭。关闭时请求里只有你发送的消息。").assertExists()
        composeRule.onNodeWithText("不会主动提醒。仍可在对话菜单里手动压缩。").assertExists()

        composeRule.onNodeWithText("上下文快满时提醒我压缩").performScrollTo().performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun thereIsNoAppUpdateSectionAnyMore() {
        composeRule.setContent {
            DSWriterTheme {
                SettingsScreen(
                    state = SettingsUiState(providers = listOf(provider())),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("应用更新").assertDoesNotExist()
        composeRule.onNodeWithText("检查更新").assertDoesNotExist()
    }
}