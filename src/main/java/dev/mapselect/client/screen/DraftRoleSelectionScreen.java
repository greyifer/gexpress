package dev.mapselect.client.screen;

import dev.mapselect.network.game.DraftPickPayload;
import dev.mapselect.network.game.DraftStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public final class DraftRoleSelectionScreen extends Screen {
	private static final int BACKGROUND = 0xFF080711;
	private static final int BACKGROUND_STRIPE = 0xFF0D0A17;
	private static final int PANEL = 0xF2161222;
	private static final int PANEL_INNER = 0xFF1C172A;
	private static final int PANEL_HOVER = 0xFF2B2340;
	private static final int PANEL_SELECTED = 0xFF44345F;
	private static final int BORDER = 0xFF665479;
	private static final int BORDER_BRIGHT = 0xFFAA8ACB;
	private static final int GOLD = 0xFFFFC857;
	private static final int TEXT = 0xFFF7F2FF;
	private static final int MUTED = 0xFFA99EB8;
	private static final int SUCCESS = 0xFF63D69A;
	private static final int DANGER = 0xFFFF6666;
	private static final int MAX_VISIBLE_PLAYERS = 10;
	private static final int LEDGER_ROW_HEIGHT = 32;
	private static final float LEDGER_TEXT_SCALE = 0.90F;
	private static final int PICK_TICKS = 20 * 10;
	private static final int ORDER_RAFFLE_TICKS = 20 * 8;
	private static final int ORDER_RESULT_TICKS = 20 * 2;
	private static final int ORDER_TOTAL_TICKS = ORDER_RAFFLE_TICKS + ORDER_RESULT_TICKS;

	private final Card[] roleCards = new Card[4];
	private DraftStatePayload state;
	private DraftStatePayload.PickResult[] pickByPosition = new DraftStatePayload.PickResult[0];
	private int localRemainingTicks;
	private int animationTicks;
	private boolean submitted;
	private int submittedChoice = -1;
	private float ledgerScroll;
	private float ledgerScrollTarget;
	private boolean raffleResultFocused;
	private boolean openedSoundPlayed;
	private boolean raffleResultSoundPlayed;
	private int lastWarningSecond = -1;

	public DraftRoleSelectionScreen(DraftStatePayload state) {
		super(Text.literal("Role Draft"));
		this.state = state;
		this.localRemainingTicks = state == null ? 0 : state.remainingTicks();
		rebuildPickIndex();
	}

	@Override
	protected void init() {
		super.init();
		layoutCards();
		if (!openedSoundPlayed) {
			openedSoundPlayed = true;
			playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.65F, 0.72F);
		}
		int focus = state == null ? -1 : state.phase() == DraftStatePayload.Phase.ORDER
			? 0 : state.activePosition() >= 0 ? state.activePosition() : state.selfPosition();
		focusLedger(focus, true);
	}

	public void update(DraftStatePayload next) {
		if (next == null) return;
		boolean newTurn = state == null || next.phase() != state.phase()
			|| next.activePosition() != state.activePosition();
		state = next;
		rebuildPickIndex();
		localRemainingTicks = next.remainingTicks();
		if (newTurn) {
			animationTicks = 0;
			submitted = false;
			submittedChoice = -1;
			raffleResultFocused = false;
			raffleResultSoundPlayed = false;
			lastWarningSecond = -1;
			int focus = next.phase() == DraftStatePayload.Phase.ORDER
				? 0 : next.activePosition() >= 0 ? next.activePosition() : next.selfPosition();
			focusLedger(focus, false);
			if (next.phase() == DraftStatePayload.Phase.FINISHING) {
				playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
			} else if (next.phase() == DraftStatePayload.Phase.PICK
					&& next.selfPosition() == next.activePosition()) {
				playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.85F, 1.15F);
			} else if (next.phase() == DraftStatePayload.Phase.PICK) {
				playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 0.85F);
			}
		}
	}

	@Override
	public void tick() {
		animationTicks++;
		if (localRemainingTicks > 0) localRemainingTicks--;
		if (state != null && state.phase() == DraftStatePayload.Phase.ORDER
				&& raffleResolved() && !raffleResultFocused) {
			raffleResultFocused = true;
			focusLedger(state.selfPosition(), false);
		}
		if (state != null && state.phase() == DraftStatePayload.Phase.ORDER
				&& raffleResolved() && !raffleResultSoundPlayed) {
			raffleResultSoundPlayed = true;
			playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9F, 0.92F);
		}
		if (state != null && state.phase() == DraftStatePayload.Phase.PICK
				&& state.selfPosition() == state.activePosition() && !submitted) {
			int seconds = Math.max(0, (localRemainingTicks + 19) / 20);
			if (seconds > 0 && seconds <= 5 && seconds != lastWarningSecond) {
				lastWarningSecond = seconds;
				playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.55F,
					seconds <= 2 ? 1.45F : 1.15F);
			}
		}
		ledgerScroll += (ledgerScrollTarget - ledgerScroll) * 0.22F;
		if (Math.abs(ledgerScrollTarget - ledgerScroll) < 0.08F) ledgerScroll = ledgerScrollTarget;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, BACKGROUND);
		for (int y = 0; y < height; y += 48) {
			context.fill(0, y, width, Math.min(height, y + 1), BACKGROUND_STRIPE);
		}
		context.fill(0, 0, 4, height, 0xFF332546);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		if (state == null) return;
		drawHeader(context);
		drawLedger(context, mouseX, mouseY);
		switch (state.phase()) {
			case ORDER -> drawOrderPhase(context);
			case PICK -> drawPickPhase(context, mouseX, mouseY);
			case FINISHING -> drawFinishing(context);
			case CLOSED -> {
			}
		}
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawHeader(DrawContext context) {
		context.fill(0, 0, width, 45, 0xD90C0914);
		context.fill(0, 44, width, 45, 0xFF30243F);
		Text title = Text.literal("ROLE DRAFT").styled(style -> style.withBold(true).withColor(GOLD));
		context.drawTextWithShadow(textRenderer, title,
			width / 2 - textRenderer.getWidth(title) / 2, 17, TEXT);
	}

	private void drawLedger(DrawContext context, int mouseX, int mouseY) {
		int x = ledgerX();
		int top = ledgerTop();
		int panelWidth = ledgerWidth();
		int listTop = ledgerListTop();
		int listHeight = ledgerListHeight();
		int panelHeight = 40 + listHeight + 12;
		drawPanel(context, x, top, panelWidth, panelHeight, GOLD);

		Text heading = Text.literal("DRAFT ORDER").styled(style -> style.withBold(true).withColor(TEXT));
		context.drawTextWithShadow(textRenderer, heading, x + 12, top + 11, TEXT);
		context.fill(x + 10, top + 30, x + panelWidth - 10, top + 31, 0x664F3E61);

		ledgerScroll = MathHelper.clamp(ledgerScroll, 0.0F, maxLedgerScroll());
		ledgerScrollTarget = MathHelper.clamp(ledgerScrollTarget, 0.0F, maxLedgerScroll());
		context.enableScissor(x + 7, listTop, x + panelWidth - 7, listTop + listHeight);
		for (int position = 0; position < state.playerCount(); position++) {
			int rowY = Math.round(listTop + position * LEDGER_ROW_HEIGHT - ledgerScroll);
			if (rowY + LEDGER_ROW_HEIGHT <= listTop || rowY >= listTop + listHeight) continue;
			drawLedgerRow(context, x + 8, rowY, panelWidth - 16, position,
				inside(mouseX, mouseY, x + 8, rowY, panelWidth - 16, LEDGER_ROW_HEIGHT - 2));
		}
		context.disableScissor();

		if (state.playerCount() > visibleLedgerRows()) drawScrollbar(context, x, panelWidth, listTop, listHeight);
	}

	private void drawLedgerRow(DrawContext context, int x, int y, int rowWidth, int position, boolean hovered) {
		DraftStatePayload.PickResult pick = pickAt(position);
		boolean active = position == state.activePosition() && state.phase() == DraftStatePayload.Phase.PICK;
		boolean self = position == state.selfPosition()
			&& (state.phase() != DraftStatePayload.Phase.ORDER || raffleResolved());
		int fill = active ? PANEL_SELECTED : pick != null ? 0xE0142A23 : hovered ? 0xF0231D31 : PANEL_INNER;
		int accent = active ? GOLD : pick != null ? SUCCESS : self ? GOLD : 0xFF514361;
		context.fill(x, y, x + rowWidth, y + LEDGER_ROW_HEIGHT - 2, fill);
		context.fill(x, y, x + 3, y + LEDGER_ROW_HEIGHT - 2, accent);
		if (active || self || hovered) context.drawBorder(x, y, rowWidth, LEDGER_ROW_HEIGHT - 2, accent);

		String player = "Player " + (position + 1);
		String playerLabel = self ? player + "  YOU" : player;
		drawScaledText(context, playerLabel, x + 8, y + 5, self ? GOLD : TEXT, LEDGER_TEXT_SCALE);

		String status;
		int statusColor;
		if (pick != null && pick.randomSelection()) {
			status = "Random";
			statusColor = SUCCESS;
		} else if (pick != null) {
			status = pick.displayName();
			statusColor = 0xFF000000 | pick.color();
		} else if (active) {
			status = "CHOOSING...";
			statusColor = GOLD;
		} else {
			status = "WAITING";
			statusColor = MUTED;
		}
		drawScaledTextToFit(context, status, x + 13, y + 18, statusColor,
			LEDGER_TEXT_SCALE, rowWidth - 22);
	}

	private void drawScrollbar(DrawContext context, int panelX, int panelWidth, int listTop, int listHeight) {
		int trackX = panelX + panelWidth - 6;
		context.fill(trackX, listTop, trackX + 2, listTop + listHeight, 0x88443650);
		float visibleFraction = visibleLedgerRows() / (float) state.playerCount();
		int thumbHeight = Math.max(18, Math.round(listHeight * visibleFraction));
		float progress = maxLedgerScroll() <= 0 ? 0 : ledgerScroll / maxLedgerScroll();
		int thumbY = listTop + Math.round((listHeight - thumbHeight) * progress);
		context.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbHeight, GOLD);
	}

	private void drawOrderPhase(DrawContext context) {
		int x = contentX();
		int contentWidth = contentWidth();
		int panelWidth = Math.min(540, contentWidth);
		int panelX = x + (contentWidth - panelWidth) / 2;
		int panelHeight = Math.min(330, Math.max(250, height - 116));
		int panelY = Math.max(62, height / 2 - panelHeight / 2 + 12);
		boolean resolved = raffleResolved();
		drawPanel(context, panelX, panelY, panelWidth, panelHeight, resolved ? SUCCESS : GOLD);
		drawCentered(context, resolved ? "YOUR DRAFT NUMBER" : "DRAWING YOUR NUMBER",
			panelX, panelWidth, panelY + 27, resolved ? SUCCESS : TEXT, true);
		drawCentered(context, resolved ? "The raffle has landed" : "Every player is being placed at random",
			panelX, panelWidth, panelY + 53, MUTED, false);

		int displayedNumber = resolved && state.selfPosition() >= 0
			? state.selfPosition() + 1 : rollingRaffleNumber();
		int numberCenterY = panelY + panelHeight / 2 + 3;
		context.fill(panelX + panelWidth / 2 - 76, numberCenterY - 62,
			panelX + panelWidth / 2 + 76, numberCenterY + 62, 0xFF100D18);
		context.drawBorder(panelX + panelWidth / 2 - 76, numberCenterY - 62,
			152, 124, resolved ? SUCCESS : GOLD);
		drawLargeNumber(context, displayedNumber, panelX + panelWidth / 2, numberCenterY,
			resolved ? SUCCESS : GOLD, resolved ? 6.0F : 5.4F);

		if (resolved) {
			String result = state.selfPosition() < 0
				? "You are observing this draft" : "You are Player " + (state.selfPosition() + 1);
			drawCentered(context, result, panelX, panelWidth, panelY + panelHeight - 49,
				state.selfPosition() < 0 ? MUTED : GOLD, true);
			drawCentered(context, "Role selection starts next", panelX, panelWidth,
				panelY + panelHeight - 27, MUTED, false);
		} else {
			int rollTicksLeft = Math.max(0, localRemainingTicks - ORDER_RESULT_TICKS);
			int seconds = Math.max(1, (rollTicksLeft + 19) / 20);
			drawCentered(context, "ROLLING  •  " + seconds + "s", panelX, panelWidth,
				panelY + panelHeight - 43, GOLD, true);
			int barWidth = Math.min(320, panelWidth - 70);
			int barX = panelX + (panelWidth - barWidth) / 2;
			float progress = MathHelper.clamp(
				(ORDER_RAFFLE_TICKS - rollTicksLeft) / (float) ORDER_RAFFLE_TICKS, 0.0F, 1.0F);
			context.fill(barX, panelY + panelHeight - 22, barX + barWidth,
				panelY + panelHeight - 17, 0xFF211A2D);
			context.fill(barX, panelY + panelHeight - 22,
				barX + Math.round(barWidth * progress), panelY + panelHeight - 17, GOLD);
		}
	}

	private boolean raffleResolved() {
		return localRemainingTicks <= ORDER_RESULT_TICKS;
	}

	private int rollingRaffleNumber() {
		int count = Math.max(1, state.playerCount());
		int elapsed = MathHelper.clamp(ORDER_TOTAL_TICKS - localRemainingTicks, 0, ORDER_RAFFLE_TICKS - 1);
		int step;
		if (elapsed < 80) {
			step = elapsed / 2;
		} else if (elapsed < 120) {
			step = 40 + (elapsed - 80) / 3;
		} else if (elapsed < 145) {
			step = 53 + (elapsed - 120) / 4;
		} else {
			step = 59 + (elapsed - 145) / 5;
		}
		int value = Math.floorMod(mixRaffleStep(step), count);
		if (count > 1 && value == Math.floorMod(mixRaffleStep(step - 1), count)) value = (value + 1) % count;
		return value + 1;
	}

	private static int mixRaffleStep(int step) {
		int value = step * 0x45D9F3B + 0x27100001;
		value = (value ^ value >>> 16) * 0x45D9F3B;
		return value ^ value >>> 16;
	}

	private void drawLargeNumber(DrawContext context, int number, int centerX, int centerY,
			int color, float scale) {
		String value = Integer.toString(Math.max(1, number));
		if (value.length() >= 3) scale = Math.min(scale, 4.4F);
		context.getMatrices().push();
		context.getMatrices().translate(centerX, centerY, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.getMatrices().translate(-centerX, -centerY, 0.0F);
		context.drawTextWithShadow(textRenderer, Text.literal(value),
			centerX - textRenderer.getWidth(value) / 2,
			centerY - textRenderer.fontHeight / 2, color);
		context.getMatrices().pop();
	}

	private void drawPickPhase(DrawContext context, int mouseX, int mouseY) {
		boolean myTurn = state.selfPosition() >= 0 && state.selfPosition() == state.activePosition();
		int x = contentX();
		int contentWidth = contentWidth();
		String heading = myTurn ? "CHOOSE YOUR ROLE" : "PLAYER " + (state.activePosition() + 1) + " IS CHOOSING";
		drawCentered(context, heading, x, contentWidth, 66, myTurn ? GOLD : TEXT, true);
		drawTimer(context, x, contentWidth, 88);

		if (myTurn && !state.choices().isEmpty()) {
			drawChoices(context, mouseX, mouseY);
			drawCentered(context, submitted ? "Choice submitted" : "Click a role to select it",
				x, contentWidth, Math.min(height - 18, choiceBottom() + 16), submitted ? SUCCESS : MUTED, false);
		} else {
			drawWaitingPanel(context, x, contentWidth);
		}
	}

	private void drawWaitingPanel(DrawContext context, int x, int contentWidth) {
		int panelWidth = Math.min(520, contentWidth);
		int panelX = x + (contentWidth - panelWidth) / 2;
		int panelY = Math.max(128, height / 2 - 76);
		drawPanel(context, panelX, panelY, panelWidth, 152, 0xFF8D6DB2);
		drawCentered(context, "PLAYER " + (state.activePosition() + 1), panelX, panelWidth,
			panelY + 31, GOLD, true);
		drawCentered(context, "is deciding between their role options",
			panelX, panelWidth, panelY + 61, TEXT, false);
		String turn = state.selfPosition() < 0 ? "Spectating draft"
			: state.selfPosition() < state.activePosition() ? "Your pick is complete"
			: "Your turn: Player " + (state.selfPosition() + 1);
		drawCentered(context, turn, panelX, panelWidth, panelY + 102,
			state.selfPosition() < state.activePosition() ? SUCCESS : MUTED, false);
	}

	private void drawFinishing(DrawContext context) {
		int x = contentX();
		int contentWidth = contentWidth();
		int panelWidth = Math.min(520, contentWidth);
		int panelX = x + (contentWidth - panelWidth) / 2;
		int panelY = Math.max(112, height / 2 - 70);
		drawPanel(context, panelX, panelY, panelWidth, 140, SUCCESS);
		drawCentered(context, "DRAFT COMPLETE", panelX, panelWidth, panelY + 38, SUCCESS, true);
		drawCentered(context, "Preparing the map...", panelX, panelWidth, panelY + 79, MUTED, false);
		int elapsed = Math.max(0, 20 - localRemainingTicks);
		int alpha = MathHelper.clamp(elapsed * 255 / 20, 0, 255);
		context.fill(0, 0, width, height, alpha << 24);
	}

	private void drawTimer(DrawContext context, int contentX, int contentWidth, int y) {
		int seconds = Math.max(0, (localRemainingTicks + 19) / 20);
		int timerWidth = Math.min(360, Math.max(180, contentWidth - 80));
		int timerX = contentX + (contentWidth - timerWidth) / 2;
		int timerColor = seconds <= 5 ? DANGER : GOLD;
		String label = seconds + "s";
		context.drawTextWithShadow(textRenderer, Text.literal(label),
			timerX + timerWidth / 2 - textRenderer.getWidth(label) / 2, y, timerColor);
		context.fill(timerX, y + 16, timerX + timerWidth, y + 21, 0xFF211A2D);
		float progress = MathHelper.clamp(localRemainingTicks / (float) PICK_TICKS, 0.0F, 1.0F);
		context.fill(timerX, y + 16, timerX + Math.round(timerWidth * progress), y + 21, timerColor);
	}

	private void drawChoices(DrawContext context, int mouseX, int mouseY) {
		for (int i = 0; i < roleCards.length; i++) {
			Card card = roleCards[i];
			if (card == null) continue;
			boolean hovered = inside(mouseX, mouseY, card.x, card.y, card.width, card.height);
			boolean selected = submitted && submittedChoice == i;
			int cardX = card.x;
			int cardY = card.y;
			int cardWidth = card.width;
			int cardHeight = card.height;

			int fill = selected ? PANEL_SELECTED : hovered && !submitted ? PANEL_HOVER : PANEL_INNER;
			int border = selected ? GOLD : hovered && !submitted ? BORDER_BRIGHT : BORDER;
			context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, fill);
			context.drawBorder(cardX, cardY, cardWidth, cardHeight, border);

			int accent;
			String name;
			String sub;
			if (i < state.choices().size()) {
				DraftStatePayload.RoleChoice role = state.choices().get(i);
				name = role.displayName();
				accent = 0xFF000000 | role.color();
				sub = role.alignment().label();
			} else {
				name = "RANDOM ROLE";
				accent = GOLD;
				sub = "";
			}
			context.fill(cardX, cardY, cardX + cardWidth, cardY + 4, accent);

			int nameColor = hovered && !submitted ? brighten(accent, 0.48F) : accent;
			int nameY = cardY + cardHeight / 2 - (sub.isEmpty() ? 5 : 13);
			drawCenteredScaledTextToFit(context, name, cardX + cardWidth / 2, nameY,
				nameColor, true, cardWidth - 20, 1.0F);
			if (!sub.isEmpty()) {
				int subColor = hovered && !submitted ? TEXT : MUTED;
				drawCenteredScaledTextToFit(context, sub, cardX + cardWidth / 2,
					cardY + cardHeight / 2 + 14, subColor, false, cardWidth - 20, 1.0F);
			}
			if (selected) {
				drawCentered(context, "SELECTED", cardX, cardWidth, cardY + 18, SUCCESS, true);
			}
		}
	}

	private void layoutCards() {
		int gap = 14;
		int ledgerClearance = ledgerX() + ledgerWidth() + 12;
		int centeredWidth = Math.max(286, width - ledgerClearance * 2);
		int cardWidth = MathHelper.clamp((centeredWidth - gap * 2) / 3, 86, 170);
		int cardHeight = MathHelper.clamp(height / 7, 96, 132);
		int totalWidth = cardWidth * 3 + gap * 2;
		int startX = width / 2 - totalWidth / 2;
		int startY = Math.max(126, height / 2 - cardHeight - 20);
		for (int i = 0; i < 3; i++) {
			roleCards[i] = new Card(startX + i * (cardWidth + gap), startY, cardWidth, cardHeight);
		}
		roleCards[3] = new Card(width / 2 - cardWidth / 2,
			startY + cardHeight + 18, cardWidth, cardHeight);
	}

	private int choiceBottom() {
		Card random = roleCards[3];
		return random == null ? height - 32 : random.y + random.height;
	}

	private void drawPanel(DrawContext context, int x, int y, int panelWidth, int panelHeight, int accent) {
		context.fill(x + 4, y + 5, x + panelWidth + 4, y + panelHeight + 5, 0x66000000);
		context.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
		context.drawBorder(x, y, panelWidth, panelHeight, BORDER);
		context.fill(x, y, x + panelWidth, y + 3, accent);
	}

	private void drawCentered(DrawContext context, String value, int x, int areaWidth, int y,
			int color, boolean bold) {
		Text text = Text.literal(value).styled(style -> style.withColor(color).withBold(bold));
		context.drawTextWithShadow(textRenderer, text, x + areaWidth / 2 - textRenderer.getWidth(text) / 2, y, color);
	}

	private void drawScaledText(DrawContext context, String value, int x, int y, int color, float scale) {
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawTextWithShadow(textRenderer, Text.literal(value), 0, 0, color);
		context.getMatrices().pop();
	}

	private void drawScaledTextToFit(DrawContext context, String value, int x, int y,
			int color, float maximumScale, int maximumWidth) {
		int textWidth = Math.max(1, textRenderer.getWidth(value));
		float scale = Math.min(maximumScale, Math.max(0.35F, maximumWidth / (float) textWidth));
		drawScaledText(context, value, x, y, color, scale);
	}

	private void drawCenteredScaledTextToFit(DrawContext context, String value, int centerX, int y,
			int color, boolean bold, int maximumWidth, float maximumScale) {
		Text text = Text.literal(value).styled(style -> style.withColor(color).withBold(bold));
		int textWidth = Math.max(1, textRenderer.getWidth(text));
		float scale = Math.min(maximumScale, Math.max(0.35F, maximumWidth / (float) textWidth));
		context.getMatrices().push();
		context.getMatrices().translate(centerX, y, 0.0F);
		context.getMatrices().scale(scale, scale, 1.0F);
		context.drawTextWithShadow(textRenderer, text, -textWidth / 2, 0, color);
		context.getMatrices().pop();
	}

	private static int brighten(int color, float amount) {
		amount = MathHelper.clamp(amount, 0.0F, 1.0F);
		int red = color >> 16 & 0xFF;
		int green = color >> 8 & 0xFF;
		int blue = color & 0xFF;
		red += Math.round((255 - red) * amount);
		green += Math.round((255 - green) * amount);
		blue += Math.round((255 - blue) * amount);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private void rebuildPickIndex() {
		int playerCount = state == null ? 0 : state.playerCount();
		pickByPosition = new DraftStatePayload.PickResult[playerCount];
		if (state == null) return;
		for (DraftStatePayload.PickResult pick : state.picks()) {
			if (pick.position() >= 0 && pick.position() < pickByPosition.length) {
				pickByPosition[pick.position()] = pick;
			}
		}
	}

	private DraftStatePayload.PickResult pickAt(int position) {
		return position < 0 || position >= pickByPosition.length ? null : pickByPosition[position];
	}

	private int ledgerX() {
		return 10;
	}

	private int ledgerTop() {
		return 58;
	}

	private int ledgerWidth() {
		return MathHelper.clamp(width / 6, 190, 230);
	}

	private int ledgerListTop() {
		return ledgerTop() + 40;
	}

	private int visibleLedgerRows() {
		int rowsThatFit = Math.max(3, (height - ledgerListTop() - 20) / LEDGER_ROW_HEIGHT);
		return Math.min(MAX_VISIBLE_PLAYERS, Math.min(Math.max(1, state.playerCount()), rowsThatFit));
	}

	private int ledgerListHeight() {
		return visibleLedgerRows() * LEDGER_ROW_HEIGHT;
	}

	private float maxLedgerScroll() {
		return Math.max(0.0F, (state.playerCount() - visibleLedgerRows()) * LEDGER_ROW_HEIGHT);
	}

	private int contentX() {
		return 0;
	}

	private int contentWidth() {
		return width;
	}

	private void focusLedger(int position, boolean immediate) {
		if (state == null || position < 0 || position >= state.playerCount()) return;
		float centered = position * LEDGER_ROW_HEIGHT
			- (visibleLedgerRows() * LEDGER_ROW_HEIGHT - LEDGER_ROW_HEIGHT) / 2.0F;
		ledgerScrollTarget = MathHelper.clamp(centered, 0.0F, maxLedgerScroll());
		if (immediate) ledgerScroll = ledgerScrollTarget;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (state != null && inside(mouseX, mouseY, ledgerX(), ledgerListTop(), ledgerWidth(), ledgerListHeight())) {
			float nextScroll = MathHelper.clamp(
				ledgerScroll - (float) verticalAmount * LEDGER_ROW_HEIGHT * 1.6F,
				0.0F, maxLedgerScroll());
			ledgerScroll = nextScroll;
			ledgerScrollTarget = nextScroll;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && canChoose()) {
			for (int i = 0; i < roleCards.length; i++) {
				Card card = roleCards[i];
				if (card == null) continue;
				if (inside(mouseX, mouseY, card.x, card.y, card.width, card.height)) {
					submit(i);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (canChoose()) {
			int choice = switch (keyCode) {
				case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> 0;
				case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> 1;
				case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> 2;
				case GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_KP_4 -> 3;
				default -> -1;
			};
			if (choice >= 0) {
				submit(choice);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void close() {
		// The server closes the draft screen once the final fade has completed.
	}

	private boolean canChoose() {
		return !submitted && localRemainingTicks > 0 && state != null
			&& state.phase() == DraftStatePayload.Phase.PICK
			&& state.selfPosition() == state.activePosition()
			&& state.choices().size() == 3;
	}

	private void submit(int choice) {
		if (!canChoose() || choice < 0 || choice > 3 || !ClientPlayNetworking.canSend(DraftPickPayload.ID)) return;
		net.minecraft.util.Identifier roleId = choice < state.choices().size()
			? state.choices().get(choice).roleId() : null;
		submitted = true;
		submittedChoice = choice;
		playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F, choice == 3 ? 0.82F : 1.08F);
		ClientPlayNetworking.send(new DraftPickPayload(state.activePosition(), choice, roleId));
	}

	private static void playSound(SoundEvent sound, float volume, float pitch) {
		if (sound == null) return;
		var client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(sound, volume, pitch));
		}
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int areaWidth, int areaHeight) {
		return mouseX >= x && mouseX < x + areaWidth && mouseY >= y && mouseY < y + areaHeight;
	}

	private record Card(int x, int y, int width, int height) {}
}
