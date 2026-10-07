package com.mojang.realmsclient.gui.screens.configuration;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.dto.Subscription;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsPopups;
import com.mojang.realmsclient.util.RealmsUtil;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.FormatStyle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonLinks;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
class RealmsSubscriptionTab extends GridLayoutTab implements RealmsConfigurationTab {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int DEFAULT_COMPONENT_WIDTH = 200;
	private static final int EXTRA_SPACING = 2;
	private static final int DEFAULT_SPACING = 6;
	static final Component TITLE = Component.translatable("mco.configure.world.subscription.tab");
	private static final Component SUBSCRIPTION_START_LABEL = Component.translatable("mco.configure.world.subscription.start");
	private static final Component TIME_LEFT_LABEL = Component.translatable("mco.configure.world.subscription.timeleft");
	private static final Component DAYS_LEFT_LABEL = Component.translatable("mco.configure.world.subscription.recurring.daysleft");
	private static final Component SUBSCRIPTION_EXPIRED_TEXT = Component.translatable("mco.configure.world.subscription.expired").withStyle(ChatFormatting.GRAY);
	private static final Component SUBSCRIPTION_LESS_THAN_A_DAY_TEXT = Component.translatable("mco.configure.world.subscription.less_than_a_day")
		.withStyle(ChatFormatting.GRAY);
	private static final Component UNKNOWN = Component.translatable("mco.configure.world.subscription.unknown");
	private static final Component RECURRING_INFO = Component.translatable("mco.configure.world.subscription.recurring.info");
	private final RealmsConfigureWorldScreen configurationScreen;
	private final Minecraft minecraft;
	private final Button deleteButton;
	private final FocusableTextWidget subscriptionInfo;
	private final StringWidget startDateWidget;
	private final StringWidget daysLeftLabelWidget;
	private final StringWidget daysLeftWidget;
	private RealmsServer serverData;
	private Component daysLeft = UNKNOWN;
	private Component startDate = UNKNOWN;
	private Subscription.@Nullable SubscriptionType type;

	RealmsSubscriptionTab(RealmsConfigureWorldScreen realmsConfigureWorldScreen, Minecraft minecraft, RealmsServer realmsServer) {
		super(TITLE);
		this.configurationScreen = realmsConfigureWorldScreen;
		this.minecraft = minecraft;
		this.serverData = realmsServer;
		GridLayout.RowHelper rowHelper = this.layout.rowSpacing(6).createRowHelper(1);
		Font font = realmsConfigureWorldScreen.getFont();
		rowHelper.addChild(new StringWidget(200, 9, SUBSCRIPTION_START_LABEL, font));
		this.startDateWidget = rowHelper.addChild(new StringWidget(200, 9, this.startDate, font));
		rowHelper.addChild(SpacerElement.height(2));
		this.daysLeftLabelWidget = rowHelper.addChild(new StringWidget(200, 9, TIME_LEFT_LABEL, font));
		this.daysLeftWidget = rowHelper.addChild(new StringWidget(200, 9, this.daysLeft, font));
		rowHelper.addChild(SpacerElement.height(2));
		rowHelper.addChild(
			Button.builder(
					Component.translatable("mco.configure.world.subscription.extend"),
					button -> ConfirmLinkScreen.confirmLinkNow(
						realmsConfigureWorldScreen, CommonLinks.extendRealms(realmsServer.remoteSubscriptionId, minecraft.getUser().getProfileId())
					)
				)
				.bounds(0, 0, 200, 20)
				.build()
		);
		rowHelper.addChild(SpacerElement.height(2));
		this.deleteButton = rowHelper.addChild(
			Button.builder(
					Component.translatable("mco.configure.world.delete.button"),
					button -> minecraft.setScreen(
						RealmsPopups.warningPopupScreen(
							realmsConfigureWorldScreen, Component.translatable("mco.configure.world.delete.question.line1"), popupScreen -> this.deleteRealm()
						)
					)
				)
				.bounds(0, 0, 200, 20)
				.build()
		);
		rowHelper.addChild(SpacerElement.height(2));
		this.subscriptionInfo = rowHelper.addChild(
			FocusableTextWidget.builder(Component.empty(), font).maxWidth(200).build(), LayoutSettings.defaults().alignHorizontallyCenter()
		);
		this.subscriptionInfo.setCentered(false);
		this.updateData(realmsServer);
	}

	private void deleteRealm() {
		RealmsUtil.runAsync(
				realmsClient -> realmsClient.deleteRealm(this.serverData.id),
				RealmsUtil.openScreenAndLogOnFailure(this.configurationScreen::createErrorScreen, "Couldn't delete world")
			)
			.thenRunAsync(() -> this.minecraft.setScreen(this.configurationScreen.getLastScreen()), this.minecraft);
		this.minecraft.setScreen(this.configurationScreen);
	}

	private void getSubscription(long l) {
		RealmsClient realmsClient = RealmsClient.getOrCreate();

		try {
			Subscription subscription = realmsClient.subscriptionFor(l);
			this.daysLeft = this.daysLeftPresentation(subscription.daysLeft());
			this.startDate = localPresentation(subscription.startDate());
			this.type = subscription.type();
		} catch (RealmsServiceException realmsServiceException) {
			LOGGER.error("Couldn't get subscription", realmsServiceException);
			this.minecraft.setScreen(this.configurationScreen.createErrorScreen(realmsServiceException));
		}
	}

	private static Component localPresentation(Instant instant) {
		String string = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).format(Util.localizedDateFormatter(FormatStyle.MEDIUM));
		return Component.literal(string).withStyle(ChatFormatting.GRAY);
	}

	private Component daysLeftPresentation(int i) {
		if (i < 0 && this.serverData.expired) {
			return SUBSCRIPTION_EXPIRED_TEXT;
		} else if (i <= 1) {
			return SUBSCRIPTION_LESS_THAN_A_DAY_TEXT;
		} else {
			int j = i / 30;
			int k = i % 30;
			boolean bl = j > 0;
			boolean bl2 = k > 0;
			if (bl && bl2) {
				return Component.translatable("mco.configure.world.subscription.remaining.months.days", j, k).withStyle(ChatFormatting.GRAY);
			} else if (bl) {
				return Component.translatable("mco.configure.world.subscription.remaining.months", j).withStyle(ChatFormatting.GRAY);
			} else {
				return bl2 ? Component.translatable("mco.configure.world.subscription.remaining.days", k).withStyle(ChatFormatting.GRAY) : Component.empty();
			}
		}
	}

	@Override
	public void updateData(RealmsServer realmsServer) {
		this.serverData = realmsServer;
		this.getSubscription(realmsServer.id);
		this.startDateWidget.setMessage(this.startDate);
		if (this.type == Subscription.SubscriptionType.NORMAL) {
			this.daysLeftLabelWidget.setMessage(TIME_LEFT_LABEL);
		} else if (this.type == Subscription.SubscriptionType.RECURRING) {
			this.daysLeftLabelWidget.setMessage(DAYS_LEFT_LABEL);
		}

		this.daysLeftWidget.setMessage(this.daysLeft);
		boolean bl = RealmsMainScreen.isSnapshot() && realmsServer.parentWorldName != null;
		this.deleteButton.active = realmsServer.expired;
		if (bl) {
			this.subscriptionInfo.setMessage(Component.translatable("mco.snapshot.subscription.info", realmsServer.parentWorldName));
		} else {
			this.subscriptionInfo.setMessage(RECURRING_INFO);
		}

		this.layout.arrangeElements();
	}

	@Override
	public Component getTabExtraNarration() {
		return CommonComponents.joinLines(TITLE, SUBSCRIPTION_START_LABEL, this.startDate, TIME_LEFT_LABEL, this.daysLeft);
	}
}
