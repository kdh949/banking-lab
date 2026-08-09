"use client";

import type { KeyboardEvent } from "react";
import { AppDialog, MaterialIcon } from "./primitives";
import {
  ApprovalInboxScreen,
  CallCenterWorkspaceScreen,
  CommandWorkbenchScreen,
  DepositNavigationScreen,
  FeeInquiryScreen,
  FdsReviewScreen,
  FundNavigationScreen,
  InheritanceScreen,
  OperationalRetryQueueScreen,
  PortalScreen,
  StaffAccountInquiryScreen,
  StaffCustomerInquiryScreen,
  StaffTransactionInquiryScreen,
  WorkflowTimelineScreen
} from "./screens";
import { BusinessTabs, RightRail, ScreenToolbar, SideDrawer, StatusBar, TerminalHeader, UtilityRail } from "./shell";
import { StaffSessionBoundary } from "./session-boundary";
import { useTerminalNavigation } from "./use-terminal-navigation";
import type { MenuTarget, ScreenControlMetadata, ScreenKey } from "./types";

export function IntegratedTerminalApp() {
  const terminal = useTerminalNavigation();

  return (
    <main className="iworks-root">
      <StaffSessionBoundary />
      <section className="iworks-window" aria-label="통합단말 프로토타입">
        <TerminalHeader
          activeScreen={terminal.activeScreen}
          activeModuleLabel={terminal.activeModuleLabel}
          searchText={terminal.searchText}
          onSearchText={terminal.setSearchText}
          onSearchSubmit={terminal.submitSearch}
          onModuleChange={terminal.selectModule}
        />
        <div className="iworks-workspace-tabs" role="tablist" aria-label="열린 업무 화면">
          {terminal.openTabs.map((tab, index) => (
            <button
              className={tab.key === terminal.activeScreen ? "is-active" : ""}
              type="button"
              role="tab"
              aria-selected={tab.key === terminal.activeScreen}
              tabIndex={tab.key === terminal.activeScreen ? 0 : -1}
              key={tab.key}
              onClick={() => terminal.selectScreen(tab.key)}
              onKeyDown={(event) => moveWorkspaceTab(event, index)}
            >
              <MaterialIcon name="folder_open" />
              <span>{tab.module}</span>
              <MaterialIcon name="close" />
            </button>
          ))}
        </div>
        <div className="iworks-body">
          <UtilityRail activeMode={terminal.sideMode} onModeChange={terminal.setSideMode} />
          <SideDrawer mode={terminal.sideMode} searchText={terminal.searchText} activeMenuCode={terminal.activeMenu.code} onMenuSelect={terminal.navigateToMenu} />
          <section className="iworks-main">
            <ScreenToolbar active={terminal.active} />
            {terminal.mainTabs.length > 0 ? <BusinessTabs tabs={terminal.mainTabs} activeIndex={terminal.activeScreen === "fund" ? 4 : 0} /> : null}
            <div className="iworks-content-row">
              <section className="iworks-content">
                <ActiveScreen screen={terminal.activeScreen} controls={terminal.activeDefinition.controls} onMenuSelect={terminal.navigateToMenu} />
              </section>
              {terminal.showRightRail ? <RightRail mode={terminal.rightMode} onModeChange={terminal.setRightMode} activeScreen={terminal.activeScreen} onMenuSelect={terminal.navigateToMenu} /> : null}
            </div>
          </section>
        </div>
        <StatusBar />
      </section>
      {terminal.dialog ? <AppDialog dialog={terminal.dialog} onClose={terminal.closeDialog} /> : null}
    </main>
  );

  function moveWorkspaceTab(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    const { key } = event;
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(key)) {
      return;
    }
    event.preventDefault();
    const last = terminal.openTabs.length - 1;
    const nextIndex = key === "Home"
      ? 0
      : key === "End"
        ? last
        : key === "ArrowLeft"
          ? (index - 1 + terminal.openTabs.length) % terminal.openTabs.length
          : (index + 1) % terminal.openTabs.length;
    terminal.selectScreen(terminal.openTabs[nextIndex].key);
    const tabs = event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>("[role='tab']");
    tabs?.item(nextIndex).focus();
  }
}

function ActiveScreen({
  screen,
  controls,
  onMenuSelect
}: {
  readonly screen: ScreenKey;
  readonly controls: ScreenControlMetadata;
  readonly onMenuSelect: (target: MenuTarget) => void;
}) {
  if (screen === "portal") {
    return <PortalScreen />;
  }
  if (screen === "deposit") {
    return <DepositNavigationScreen onMenuSelect={onMenuSelect} />;
  }
  if (screen === "fee") {
    return <FeeInquiryScreen controls={controls} />;
  }
  if (screen === "inheritance") {
    return <InheritanceScreen controls={controls} />;
  }
  if (screen === "fund") {
    return <FundNavigationScreen onMenuSelect={onMenuSelect} />;
  }
  if (screen === "staffCustomer") {
    return <StaffCustomerInquiryScreen />;
  }
  if (screen === "staffAccount") {
    return <StaffAccountInquiryScreen />;
  }
  if (screen === "staffTransaction") {
    return <StaffTransactionInquiryScreen />;
  }
  if (screen === "fdsReview") {
    return <FdsReviewScreen />;
  }
  if (screen === "approvalInbox") {
    return <ApprovalInboxScreen />;
  }
  if (screen === "opsRetry") {
    return <OperationalRetryQueueScreen />;
  }
  if (screen === "workflowTimeline") {
    return <WorkflowTimelineScreen />;
  }
  if (screen === "callCenter") {
    return <CallCenterWorkspaceScreen />;
  }
  return <CommandWorkbenchScreen />;
}
