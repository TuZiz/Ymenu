package cc.neurons.ymenu.menu

import cc.neurons.ymenu.model.ButtonDefinition
import cc.neurons.ymenu.model.ButtonType

class PageRenderer {
    fun pageButtons(session: MenuSession, isVisible: (ButtonDefinition) -> Boolean): List<ButtonDefinition> {
        val pageSpec = session.menu.pageSpec ?: return emptyList()
        if (pageSpec.slots.isEmpty() || pageSpec.elementKeys.isEmpty()) {
            return emptyList()
        }

        return pageSpec.elementKeys.mapNotNull(session.menu.buttons::get)
            .filter { it.buttonType == ButtonType.PAGE_ITEM || it.buttonType == ButtonType.NORMAL }
            .filter(isVisible)
    }

    fun maxPage(session: MenuSession, isVisible: (ButtonDefinition) -> Boolean): Int {
        val layoutPages = session.menu.totalLayoutPages
        val pageSpec = session.menu.pageSpec ?: return layoutPages
        val pageSize = pageSpec.slots.size
        if (pageSize <= 0) {
            return layoutPages
        }
        val total = pageButtons(session, isVisible).size
        val pagedButtons = maxOf(1, (total + pageSize - 1) / pageSize)
        return maxOf(layoutPages, pagedButtons)
    }

    fun pageSlice(session: MenuSession, isVisible: (ButtonDefinition) -> Boolean): List<Pair<Int, ButtonDefinition>> {
        val pageSpec = session.menu.pageSpec ?: return emptyList()
        val buttons = pageButtons(session, isVisible)
        val pageSize = pageSpec.slots.size
        if (pageSize <= 0) {
            return emptyList()
        }

        val safePage = session.currentPage.coerceIn(1, maxPage(session, isVisible))
        val start = (safePage - 1) * pageSize
        return pageSpec.slots.zip(buttons.drop(start).take(pageSize))
    }
}
