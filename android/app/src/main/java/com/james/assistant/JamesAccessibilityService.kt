package com.james.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Automação pontual da janela visível; não coleta nem envia conteúdo da tela. */
class JamesAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var active: JamesAccessibilityService? = null
        fun execute(action: org.json.JSONObject): Pair<Boolean, String> {
            val service = active ?: return false to "Automação indisponível. Ative o Serviço de Acessibilidade do Jâmes nas Configurações."
            return service.runRequestedAction(action)
        }
    }
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* Eventos não são armazenados nem enviados. */ }
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (active === this) active = null; super.onDestroy() }

    private fun runRequestedAction(action: org.json.JSONObject): Pair<Boolean, String> = when (action.optString("action")) {
        "tap_text" -> tapText(action.optString("targetText"))
        "type_text" -> typeText(action.optString("text"))
        "scroll" -> scroll(action.optString("direction"))
        "back" -> if (performGlobalAction(GLOBAL_ACTION_BACK)) true to "Voltar executado." else false to "O Android recusou Voltar."
        "home" -> if (performGlobalAction(GLOBAL_ACTION_HOME)) true to "Tela inicial aberta." else false to "O Android recusou Início."
        else -> false to "Ação não reconhecida."
    }

    private fun tapText(target: String): Pair<Boolean, String> {
        if (target.isBlank() || target.length > 120) return false to "Texto-alvo inválido."
        val root = rootInActiveWindow ?: return false to "Nenhuma janela ativa acessível."
        for (match in root.findAccessibilityNodeInfosByText(target)) {
            if (!match.isVisibleToUser) continue
            var node: AccessibilityNodeInfo? = match
            var depth = 0
            while (node != null && depth < 8) {
                if (node.isVisibleToUser && node.isEnabled && node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true to "Elemento visível acionado: " + target
                node = node.parent
                depth++
            }
        }
        return false to "Não encontrei elemento visível acionável com o texto: " + target
    }

    private fun typeText(value: String): Pair<Boolean, String> {
        if (value.isEmpty() || value.length > 1500) return false to "Texto vazio ou acima de 1.500 caracteres."
        val root = rootInActiveWindow ?: return false to "Nenhuma janela ativa acessível."
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false to "Toque no campo desejado antes de pedir para digitar."
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return if (field.isEditable && field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) true to "Texto inserido no campo focado." else false to "O campo focado não permite inserir texto."
    }

    private fun scroll(direction: String): Pair<Boolean, String> {
        if (direction != "up" && direction != "down") return false to "Direção inválida."
        val root = rootInActiveWindow ?: return false to "Nenhuma janela ativa acessível."
        val node = findScrollable(root) ?: return false to "Nenhuma área rolável visível encontrada."
        val action = if (direction == "down") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (node.performAction(action)) true to "Rolagem executada." else false to "A área não aceitou a rolagem."
    }

    private fun findScrollable(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 20) return null
        if (node.isVisibleToUser && node.isEnabled && node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child, depth + 1)
            if (found != null) return found
        }
        return null
    }
}