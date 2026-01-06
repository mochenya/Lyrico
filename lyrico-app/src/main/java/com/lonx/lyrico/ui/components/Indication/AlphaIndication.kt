package com.lonx.lyrico.ui.components.Indication


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

object AlphaIndication : IndicationNodeFactory {
    override fun create(
        interactionSource: InteractionSource
    ): DelegatableNode = AlphaIndicationInstance(interactionSource)

    override fun hashCode(): Int = -1

    override fun equals(other: Any?) = other === this

    private class AlphaIndicationInstance(
        private val interactionSource: InteractionSource
    ) : Modifier.Node(), DrawModifierNode {

        private val alphaAnim = Animatable(0f)

        override fun onAttach() {
            coroutineScope.launch {
                var pressCount = 0
                var hoverCount = 0
                var focusCount = 0

                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressCount++
                        is PressInteraction.Release -> pressCount--
                        is PressInteraction.Cancel -> pressCount--
                        is HoverInteraction.Enter -> hoverCount++
                        is HoverInteraction.Exit -> hoverCount--
                        is FocusInteraction.Focus -> focusCount++
                        is FocusInteraction.Unfocus -> focusCount--
                    }

                    // 1. 根据当前状态计算“目标透明度”
                    val targetAlpha = when {
                        pressCount > 0 -> 0.2f // 按下时最深 (逐渐加深到 0.2)
                        hoverCount > 0 || focusCount > 0 -> 0.1f // 悬停/聚焦时稍浅
                        else -> 0f // 无状态时完全透明
                    }

                    // 2. 启动协程执行渐变动画
                    // Animatable 内部有互斥锁，新的 animateTo 会自动取消旧的，所以这里可以直接 launch
                    launch {
                        // 如果是按下，使用较快的动画；如果是松开，稍微慢一点，更有呼吸感
                        val duration = if (pressCount > 0) 150 else 250

                        alphaAnim.animateTo(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = duration)
                        )
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()

            // 3. 只要透明度大于0，就绘制矩形
            // 读取 alphaAnim.value 会自动在值变化时触发重绘
            val currentAlpha = alphaAnim.value
            if (currentAlpha > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = currentAlpha),
                    size = size
                )
            }
        }
    }
}