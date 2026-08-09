import { expect, test } from "@playwright/test";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = process.env.BANKING_LAB_ROOT || path.resolve(specDir, "../../..");
const baseUrl = "http://localhost:3002";
const apiBaseUrl = process.env.BANKING_LAB_E2E_API_BASE_URL ?? "";
const simulatorTokensEnabled = process.env.NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED === "true";

test("iWorks integrated terminal renders the shell and terminal-status data", async ({ page, request }) => {
  const terminalStatus = await request.get(`${baseUrl}/api/terminal-status`);
  expect(terminalStatus.ok()).toBe(true);
  await expect(async () => {
    const payload = await terminalStatus.json();
    expect(payload.clientIp).toBeTruthy();
    expect(payload.serverTimeIso).toMatch(/^\d{4}-\d{2}-\d{2}T/u);
  }).toPass();

  await page.goto(baseUrl);

  await expect(page.locator(".iworks-root")).toBeVisible();
  await expect(page.getByLabel("통합단말 프로토타입")).toBeVisible();
  await expect(page.getByLabel("업무 모듈")).toBeVisible();
  await expect(page.getByPlaceholder("통합검색")).toBeVisible();
  await expect(page.getByText("INZENT").first()).toBeVisible();
  await expect(page.getByText("즐겨찾기").first()).toBeVisible();
  await expect(page.getByRole("button", { name: /IT 기기장애/u })).toBeVisible();
  await expect(page.getByText(/프린터\s+핀패드\s+즐겨찾기/u)).toBeVisible();
  await expect(page.locator(".iworks-statusbar time")).not.toHaveText("2019-08-01 13:37:45");

  await page.getByRole("button", { name: "여신", exact: true }).click();
  await expect(page.locator(".screen-title")).toContainText("[SY-Starts.scn] 통합 포털");
  await expect(page.getByTestId("staff-terminal-api-evidence")).toHaveCount(0);
});

test("iWorks integrated terminal executes Spring staff API evidence when configured", async ({ page }) => {
  test.skip(
    !apiBaseUrl || !simulatorTokensEnabled,
    "Set BANKING_LAB_E2E_API_BASE_URL and NEXT_PUBLIC_BANKING_SIMULATOR_TOKENS_ENABLED=true to run the staff-terminal Spring API evidence smoke."
  );

  await page.goto(baseUrl);
  await page.getByRole("button", { name: "업무메뉴" }).click();
  await page.getByRole("button", { name: /\[CUS101\].*고객 상세 조회/u }).click();
  const workbench = page.getByTestId("terminal-api-client-provider");
  await page.locator(".api-form-panel").getByRole("button", { name: "조회" }).click();

  await expect(workbench).toContainText("SYN-CUS-001", { timeout: 15_000 });
  await expect(workbench).toContainText(/010-\*\*\*\*/u);
  await expect(workbench).toContainText("MASKED");
});

test("iWorks integrated terminal handles implemented navigation and unavailable module dialogs", async ({ page }) => {
  await page.goto(baseUrl);

  await page.getByRole("button", { name: /펀드/u }).click();
  await expect(page.getByText("[F0000] 펀드_네비게이션")).toBeVisible();

  await page.getByRole("button", { name: /수신/u }).first().click();
  await expect(page.getByText("[20000] 수신_네비게이션")).toBeVisible();

  await page.getByRole("button", { name: /여신종합/u }).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toContainText("아직 구현되지 않은 업무입니다");
  await expect(dialog.locator(".dialog-x-mark")).toContainText("close");
  await dialog.getByRole("button", { name: "확인" }).click();
  await expect(dialog).toBeHidden();
});

test("iWorks integrated terminal exposes API-backed transaction codes inside the existing menu", async ({ page }) => {
  await page.goto(baseUrl);

  await page.getByRole("button", { name: "업무메뉴" }).click();

  await page.getByRole("button", { name: /\[CUS101\].*고객 상세 조회/u }).click();
  await expect(page.locator(".screen-title")).toContainText("[CUS101] 고객 상세 조회");
  await expect(page.getByTestId("terminal-api-client-provider")).toContainText("Spring API");

  await page.getByRole("button", { name: /\[FDS201\].*FDS 보류 이체 심사/u }).click();
  await expect(page.locator(".screen-title")).toContainText("[FDS201] FDS 보류 이체 심사");
  await expect(page.getByText("release 승인요청").first()).toBeVisible();
  await expect(page.getByText("maker/checker").first()).toBeVisible();

  await page.getByRole("button", { name: /\[APR101\].*승인함 목록\/상세/u }).click();
  await expect(page.locator(".screen-title")).toContainText("[APR101] 승인함 목록/상세");
  await expect(page.getByText("maker").first()).toBeVisible();

  await page.getByRole("button", { name: /\[WRK003\].*workflow timeline/u }).click();
  await expect(page.locator(".screen-title")).toContainText("[WRK003] workflow timeline");
  await expect(page.getByText("businessReferenceId").first()).toBeVisible();
  await expect(page.getByText("journeyId").first()).toBeVisible();

  await page.getByRole("button", { name: /\[CALL101\].*상담 고객 검색/u }).click();
  await expect(page.locator(".screen-title")).toContainText("[CALL101] 상담 고객 검색");
  await expect(page.getByText("CALL106").first()).toBeVisible();
});

test("iWorks integrated terminal supports operator inputs and lookup dialogs", async ({ page }) => {
  await page.goto(baseUrl);

  const numberInput = page.getByLabel("번호선택");
  await numberInput.fill("12ab34");
  await expect(numberInput).toHaveValue("1234");

  await page.getByRole("button", { name: /외환/u }).click();
  await page.getByLabel("고객계좌번호 추가 조회").click();
  const lookupDialog = page.getByRole("dialog", { name: "고객계좌번호 추가 조회" });
  await expect(lookupDialog).toBeVisible();
  await lookupDialog.getByPlaceholder("번호 또는 이름 입력").fill("SYN");
  await expect(lookupDialog).toContainText("SYN-CUS-001");
  await lookupDialog.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(lookupDialog).toBeHidden();

  await page.getByRole("button", { name: "고객", exact: true }).click();
  await page.getByLabel("조회구분").click();
  await expect(page.getByRole("listbox", { name: "조회구분 선택" })).toBeVisible();
  await page.getByRole("option", { name: "3-전행고객번호" }).click();
  await expect(page.getByLabel("조회구분")).toContainText("3-전행고객번호");
});

test("iWorks integrated terminal source boundary keeps product and lab routes separated", async () => {
  const appDir = path.join(repoRoot, "apps", "staff-terminal", "src", "app");
  const componentDir = path.join(repoRoot, "apps", "staff-terminal", "src", "components");
  const pageSource = readFileSync(path.join(appDir, "page.tsx"), "utf8");
  const appFiles = readdirSync(appDir).sort();
  const componentFiles = readdirSync(componentDir).sort();

  expect(appFiles).toEqual(["api", "globals.css", "lab", "layout.tsx", "page.tsx"]);
  expect(readdirSync(path.join(appDir, "api")).sort()).toEqual(["terminal-status"]);
  expect(componentFiles).toEqual(["integrated-terminal.css", "integrated-terminal.tsx", "terminal"]);
  expect(pageSource).toContain("IntegratedTerminalApp");

  for (const forbidden of [
    "ApiBackedStaffPanel",
    "manifest-renderer",
    "terminal-screens",
    "terminal-ui",
    "workflow-routes",
    "staff-terminal-parity"
  ]) {
    expect(pageSource).not.toContain(forbidden);
  }
});
