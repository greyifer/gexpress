package dev.mapselect.client.game;

import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.text.Text;

/** Victory titles describe the winning side, independently of the viewer's role. */
public final class ClientRoundWinnerText {
    private ClientRoundWinnerText() {}
    public static Text side(GameFunctions.WinStatus status) {
        return switch(status) {
            case KILLERS -> RoleAnnouncementTexts.KILLER.winText;
            case PASSENGERS, TIME -> RoleAnnouncementTexts.CIVILIAN.winText;
            default -> null;
        };
    }
}
