import type { ReactNode } from "react";

export type IconName =
  | "account_balance"
  | "account_balance_wallet"
  | "add"
  | "arrow_drop_down"
  | "arrow_forward"
  | "article"
  | "badge"
  | "bar_chart"
  | "business_center"
  | "calendar_month"
  | "call"
  | "campaign"
  | "check"
  | "close"
  | "credit_card"
  | "currency_exchange"
  | "description"
  | "docs"
  | "domain"
  | "edit_square"
  | "folder"
  | "folder_open"
  | "grid_view"
  | "groups"
  | "help"
  | "history"
  | "home_work"
  | "keyboard_double_arrow_right"
  | "language"
  | "manage_search"
  | "menu_book"
  | "monitoring"
  | "more_horiz"
  | "payments"
  | "person_search"
  | "print"
  | "query_stats"
  | "receipt_long"
  | "search"
  | "settings"
  | "star"
  | "support_agent"
  | "sync_alt"
  | "task_alt"
  | "widgets"
  | "workspaces";

export type ScreenKey = "portal" | "deposit" | "fee" | "inheritance" | "fund";
export type SideMode = "menu" | "workflow" | "none";
export type RightMode = "manual" | "marketing";
export type ScreenTemplate = "portal" | "navigation" | "inquiry" | "command" | "case" | "parameter";
export type MaskingPolicy = "NONE" | "DEFAULT_MASKED" | "TIMEBOXED_UNMASK";

export type ScreenControlMetadata = {
  readonly template: ScreenTemplate;
  readonly reasonRequired: boolean;
  readonly piiAccess: boolean;
  readonly maskingPolicy: MaskingPolicy;
  readonly approvalRequired: boolean;
  readonly workflowVisible: boolean;
};

export type TopModule = {
  readonly label: string;
  readonly icon: IconName;
  readonly screen?: ScreenKey;
  readonly implemented?: boolean;
};

export type MenuTarget = {
  readonly code: string;
  readonly label: string;
  readonly screen?: ScreenKey;
  readonly moduleLabel?: string;
  readonly implemented?: boolean;
};

export type UtilityItem = {
  readonly label: string;
  readonly icon: IconName;
  readonly mode?: SideMode;
};

export type WorkspaceScreen = {
  readonly key: ScreenKey;
  readonly code: string;
  readonly title: string;
  readonly module: string;
};

export type ScreenDefinition = WorkspaceScreen & {
  readonly moduleLabel: string;
  readonly tabs: readonly string[];
  readonly controls: ScreenControlMetadata;
};

export type PanelProps = {
  readonly title?: string;
  readonly icon?: IconName;
  readonly tabs?: readonly string[];
  readonly actions?: ReactNode;
  readonly className?: string;
  readonly children: ReactNode;
};

export type TableColumn = {
  readonly key: string;
  readonly label: string;
  readonly width?: string;
};

export type TableRow = Record<string, ReactNode>;

export type MenuGroup = {
  readonly label: string;
  readonly open: boolean;
  readonly items: readonly MenuTarget[];
};

export type DialogState = {
  readonly title: string;
  readonly message: string;
  readonly code?: string;
};
