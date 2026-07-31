"use client";

import { AppDialog, MaterialIcon } from "./primitives";
import {
  ApprovalInboxScreen,
  CallCenterWorkspaceScreen,
  CommandWorkbenchScreen,
  DepositNavigationScreen,
  FeeInquiryScreen,
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
import { useTerminalNavigation } from "./use-terminal-navigation";
import type { MenuTarget, ScreenControlMetadata, ScreenKey } from "./types";

export function IntegratedTerminalApp() {
  const terminal = useTerminalNavigation();

  return (
    <main className="iworks-root">
      <section className="iworks-window" aria-label="통합단말 프로토타입">
        <TerminalHeader
          activeScreen={terminal.activeScreen}
          activeModuleLabel={terminal.activeModuleLabel}
          searchText={terminal.searchText}
          onSearchText={terminal.setSearchText}
          onModuleChange={terminal.selectModule}
        />
        <div className="iworks-workspace-tabs">
          {terminal.openTabs.map((tab) => (
            <button className={tab.key === terminal.activeScreen ? "is-active" : ""} type="button" key={tab.key} onClick={() => terminal.selectScreen(tab.key)}>
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
