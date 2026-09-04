package com.yeowool.core.mailbox;

import com.yeowool.core.api.gui.GuiButton;
import com.yeowool.core.api.gui.YeowoolGui;
import com.yeowool.core.api.model.MailboxEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Right-click preview of one {@link MailboxEntry}'s actual contents — display only, never claims it. */
public final class MailboxPreviewGui extends YeowoolGui {

    private static final int SLOT_ITEM = 4;

    public MailboxPreviewGui(MailboxEntry entry) {
        super(9, Component.text("우편 미리보기", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        setButton(SLOT_ITEM, GuiButton.display(entry.item().clone()));
    }
}
