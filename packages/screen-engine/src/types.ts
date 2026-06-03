export type ScreenType = "INQUIRY" | "COMMAND" | "CASE" | "PARAMETER" | "DASHBOARD";

export type ChannelAppId =
  | "admin-console"
  | "audit-console"
  | "complaint-portal"
  | "customer-web"
  | "fds-aml-console"
  | "ops-console"
  | "staff-terminal";

export type ManifestField = {
  name: string;
  label?: string;
  type?: string;
  required?: boolean;
  mask?: string;
};

export type ScreenManifest = {
  screenId: string;
  transactionCode?: string;
  title: string;
  app: ChannelAppId;
  type: ScreenType;
  domain: string;
  highRisk?: boolean;
  requiredRoles: string[];
  layout: {
    template: string;
    customerContext?: boolean;
    tabbed?: boolean;
  };
  approval?: {
    required?: boolean;
    makerChecker?: boolean;
    approverRole?: string;
    businessTypes?: string[];
  };
  audit: {
    enabled: boolean;
    reasonRequired: boolean;
    eventTypes?: string[];
    piiAccess?: boolean;
    maskingPolicy: string;
    selfService?: boolean;
  };
  query?: {
    endpoint?: string;
    fields?: ManifestField[];
  };
  resultTable?: {
    columns?: string[];
  };
  fields?: ManifestField[];
  parameter?: {
    namespace: string;
    keys?: string[];
    currentValueEndpoint?: string;
    historyEndpoint?: string;
  };
  api?: {
    command?: string;
  };
  workflow?: {
    name: string;
    states: string[];
  };
  sla?: {
    enabled?: boolean;
    targetHours?: number;
  };
  sections?: string[];
  widgets?: string[];
  actions?: Array<{
    id: string;
    label: string;
    type?: string;
    target?: string;
  }>;
};
