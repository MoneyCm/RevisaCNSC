package co.meritoradar.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Requires the local real-data API; this is deliberately not a mock integration. */
class RealCatalogTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun searchDetailAndPersistentFollowing() {
        rule.waitUntil(60000) {
            rule.onAllNodes(hasText("procesos guardados", substring = true)
                and !hasText("Catálogo CNSC · 0 procesos guardados")).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Concursos").performClick()
        rule.onNodeWithText("Buscar concurso").performTextInput("Territorial 12")
        rule.onNodeWithText("Territorial 12").performClick()
        rule.onNodeWithText("Etapa exacta: por confirmar").assertIsDisplayed()
        rule.onNodeWithText("Ver fuente oficial").assertIsDisplayed()
        val followed = rule.onAllNodesWithText("★ Siguiendo").fetchSemanticsNodes().isNotEmpty()
        if (followed) rule.onNodeWithText("★ Siguiendo").performClick()
        rule.onNodeWithText("☆ Seguir").performClick()
        rule.onNodeWithText("★ Siguiendo").assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.onNodeWithText("★ Siguiendo").assertIsDisplayed()
    }
}
