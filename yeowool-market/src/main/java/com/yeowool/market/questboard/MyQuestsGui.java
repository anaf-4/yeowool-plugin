package com.yeowool.market.questboard;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /의뢰} or the board's "내 의뢰" button: the viewer's latest requests, open ones first; click an open one to cancel it. */
public final class MyQuestsGui extends YeowoolGui {

    static final int SIZE = 27;

    public MyQuestsGui(QuestBoardService service, List<QuestRequest> requests) {
        super(SIZE, Component.text("내 의뢰", NamedTextColor.DARK_GREEN));
        for (int i = 0; i < requests.size() && i < SIZE; i++) {
            QuestRequest request = requests.get(i);
            if (request.isOpen()) {
                setButton(i, GuiButton.of(QuestBoardGui.icon(request, "클릭: 의뢰 취소 (남은 보상 환불, 수수료 제외)"),
                        event -> service.cancel((Player) event.getWhoClicked(), request.id())));
            } else {
                setButton(i, GuiButton.display(QuestBoardGui.icon(request, null)));
            }
        }
    }
}
