package com.qinglong.feature.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * 给输入框注册 Autofill 节点，使 Bitwarden 等密码管理器能识别并填充字段。
 *
 * 项目使用 Compose 1.7.x（BOM 2024.12.01），官方推荐的 `ContentType` 语义 API 在
 * Compose 1.8.0 才公开；此处使用 1.7.x 可用的 `AutofillType` + `AutofillNode` 实验性 API。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.autofill(
    autofillTypes: List<AutofillType>,
    onFill: (String) -> Unit,
): Modifier {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val autofillNode = remember(autofillTypes, onFill) {
        AutofillNode(autofillTypes = autofillTypes, onFill = onFill)
    }
    LaunchedEffect(autofillNode) {
        autofillTree += autofillNode
    }
    return this
        .onGloballyPositioned { autofillNode.boundingBox = it.boundsInWindow() }
        .onFocusChanged { focusState ->
            autofill?.run {
                if (focusState.isFocused) requestAutofillForNode(autofillNode)
                else cancelAutofillForNode(autofillNode)
            }
        }
}
