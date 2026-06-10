import { useMemo, useReducer } from "react";
import { defaultModuleForScreen, defaultTargetForScreen, screenDefinitionFor, screenList } from "./registry";
import type { DialogState, MenuTarget, RightMode, ScreenKey, SideMode, TopModule, WorkspaceScreen } from "./types";

type TerminalNavigationState = {
  readonly activeScreen: ScreenKey;
  readonly activeModuleLabel: string;
  readonly activeMenu: MenuTarget;
  readonly dialog: DialogState | null;
  readonly sideMode: SideMode;
  readonly rightMode: RightMode;
  readonly searchText: string;
};

type TerminalNavigationAction =
  | { readonly type: "select-screen"; readonly screen: ScreenKey; readonly moduleLabel?: string; readonly target?: MenuTarget }
  | { readonly type: "select-module"; readonly module: TopModule }
  | { readonly type: "navigate-menu"; readonly target: MenuTarget }
  | { readonly type: "show-unavailable"; readonly target: Pick<MenuTarget, "code" | "label"> }
  | { readonly type: "close-dialog" }
  | { readonly type: "set-side-mode"; readonly mode: SideMode }
  | { readonly type: "set-right-mode"; readonly mode: RightMode }
  | { readonly type: "set-search-text"; readonly value: string };

const initialState: TerminalNavigationState = {
  activeScreen: "deposit",
  activeModuleLabel: "수신",
  activeMenu: defaultTargetForScreen("deposit"),
  dialog: null,
  sideMode: "none",
  rightMode: "manual",
  searchText: ""
};

function unavailableDialog(target: Pick<MenuTarget, "code" | "label">): DialogState {
  return {
    code: target.code,
    title: target.code ? `[${target.code}] ${target.label}` : target.label,
    message: "아직 구현되지 않은 업무입니다. 화면 정의가 추가되면 이 메뉴로 연결됩니다."
  };
}

function selectedScreenState(
  state: TerminalNavigationState,
  screen: ScreenKey,
  moduleLabel = defaultModuleForScreen(screen),
  target = defaultTargetForScreen(screen, moduleLabel)
): TerminalNavigationState {
  return {
    ...state,
    activeScreen: screen,
    activeModuleLabel: moduleLabel,
    activeMenu: target
  };
}

function reducer(state: TerminalNavigationState, action: TerminalNavigationAction): TerminalNavigationState {
  switch (action.type) {
    case "select-screen":
      return selectedScreenState(state, action.screen, action.moduleLabel, action.target);
    case "select-module": {
      const { module } = action;
      if (!module.screen || module.implemented === false) {
        return {
          ...state,
          dialog: unavailableDialog({ code: "", label: `${module.label} 업무 모듈` })
        };
      }
      return {
        ...selectedScreenState(state, module.screen, module.label),
        sideMode: module.screen === "portal" ? "none" : state.sideMode
      };
    }
    case "navigate-menu": {
      const { target } = action;
      if (!target.screen || target.implemented === false) {
        return {
          ...state,
          dialog: unavailableDialog(target)
        };
      }
      return selectedScreenState(state, target.screen, target.moduleLabel ?? defaultModuleForScreen(target.screen), target);
    }
    case "show-unavailable":
      return {
        ...state,
        dialog: unavailableDialog(action.target)
      };
    case "close-dialog":
      return {
        ...state,
        dialog: null
      };
    case "set-side-mode":
      return {
        ...state,
        sideMode: action.mode
      };
    case "set-right-mode":
      return {
        ...state,
        rightMode: action.mode
      };
    case "set-search-text":
      return {
        ...state,
        searchText: action.value
      };
    default:
      return state;
  }
}

function activeWorkspaceScreen(state: TerminalNavigationState): WorkspaceScreen {
  const activeBase = screenDefinitionFor(state.activeScreen);
  return {
    key: activeBase.key,
    code: state.activeMenu.screen === state.activeScreen ? state.activeMenu.code : activeBase.code,
    title: state.activeMenu.screen === state.activeScreen ? state.activeMenu.label : activeBase.title,
    module: state.activeMenu.screen === state.activeScreen ? state.activeMenu.code : activeBase.module
  };
}

export function useTerminalNavigation() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const activeDefinition = screenDefinitionFor(state.activeScreen);
  const active = useMemo(() => activeWorkspaceScreen(state), [state]);
  const openTabs = useMemo(() => screenList.filter((screen) => screen.key !== "portal" || state.activeScreen === "portal"), [state.activeScreen]);
  const showRightRail = state.activeScreen !== "portal" || state.rightMode === "marketing";

  return {
    active,
    activeDefinition,
    activeMenu: state.activeMenu,
    activeModuleLabel: state.activeModuleLabel,
    activeScreen: state.activeScreen,
    dialog: state.dialog,
    mainTabs: activeDefinition.tabs,
    openTabs,
    rightMode: state.rightMode,
    searchText: state.searchText,
    showRightRail,
    sideMode: state.sideMode,
    closeDialog: () => dispatch({ type: "close-dialog" }),
    navigateToMenu: (target: MenuTarget) => dispatch({ type: "navigate-menu", target }),
    selectModule: (module: TopModule) => dispatch({ type: "select-module", module }),
    selectScreen: (screen: ScreenKey) => dispatch({ type: "select-screen", screen }),
    setRightMode: (mode: RightMode) => dispatch({ type: "set-right-mode", mode }),
    setSearchText: (value: string) => dispatch({ type: "set-search-text", value }),
    setSideMode: (mode: SideMode) => dispatch({ type: "set-side-mode", mode })
  };
}
